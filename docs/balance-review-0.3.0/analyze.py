#!/usr/bin/env python3
"""Reproduce the static balance model. Python 3.10+; plots require matplotlib.
Run: MPLCONFIGDIR=/tmp/singulo-mpl python docs/balance-review-0.3.0/analyze.py
No source/gameplay files are changed. Resource model is a construction BOM,
not a survival completion simulation; see README.md for exclusions.
"""
from pathlib import Path
from collections import Counter, defaultdict
from fractions import Fraction as F
import sys, math, json, csv, re, copy
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT/'tools'))
from recipes import RECIPES, MILESTONES, RESTORED, REPAIR, EXPLORE
from ids import MOD, STATIONS, OUTPUT_ALIASES
R = copy.deepcopy(RECIPES)
for restored, raw in RESTORED.items():
    x = REPAIR[raw]
    R[restored] = dict(inputs={raw:1, **x['inputs']}, out=1, t=x['t'], fe=x['fe'], station=x['station'])
# Explicit vanilla subrecipes; furnace fuel tracked separately (not in BOM).
for name, inp, out in [
    ('ピストン', {'鉄インゴット':1,'丸石':4,'木材':3,'レッドストーン':1},1),
    ('バケツ', {'鉄インゴット':3},1), ('コンパス', {'鉄インゴット':4,'レッドストーン':1},1),
    ('時計', {'金インゴット':4,'レッドストーン':1},1),
    ('溶鉱炉', {'鉄インゴット':5,'かまど':1,'滑らかな石':3},1),
    ('かまど', {'丸石':8},1), ('ガラス', {'砂':1},1),
    ('避雷針', {'銅インゴット':3},1), ('エンダーアイ', {'エンダーパール':1,'ブレイズパウダー':1},1)]:
    R[name]=dict(inputs=inp, out=out, t=0, fe=0, station='vanilla')
# H-condensates are obtained in reactor operation, not free crafted outputs.
for n in ['H凝縮体','ジェット凝縮体']:
    R.pop(n,None)

class Model:
    def __init__(self):
        self.stock=defaultdict(F); self.raw=Counter(); self.batches=Counter(); self.time=Counter(); self.energy=Counter()
    def need(self,n,q,stage):
        q=F(q); used=min(q,self.stock[n]); q-=used; self.stock[n]-=used
        if not q: return
        if n not in R:
            self.raw[n]+=q
            return
        r=R[n]; b=math.ceil(q/F(r['out'])); self.batches[n]+=b
        for a,c in r['inputs'].items(): self.need(a,F(c)*b,stage)
        self.stock[n]+=b*F(r['out'])-q
        ticks=round(r['t']*20)
        if r['station']=='極低温冷却塔': ticks=math.floor(ticks/(1.4 if stage<3 else 2)+0.5)
        # Main BOM uses vanilla furnace recipes; machine material processing remains explicit.
        self.time[r['station']]+=ticks*b
        self.energy[r['station']]+=ticks*b*r['fe']
    def basket(self,d,stage):
        for n,q in d.items(): self.need(n,q,stage)
    def snap(self):
        return dict(raw={k:float(v) for k,v in self.raw.items()}, batches=dict(self.batches),
                    machine_minutes={k:v/1200 for k,v in self.time.items()}, energy_fe=dict(self.energy))

stages=copy.deepcopy(MILESTONES)
# Split stage 5 before ignition / after ignition. Include Hawking collector before ignition.
stages[5].pop('シンギュラリティ・コア')
stages[5].pop('ジェット・コレクター')
stages[6]={'ジェット・コレクター':1,'シンギュラリティ・コア':1}
m=Model(); snapshots={}
for s,basket in stages.items():
    m.basket(basket,s)
    snapshots[s]=m.snap()

# Audit actual generated recipes: a non-stackable catalyst can only enter one catalyst slot.
cat_ids={v[0] for v in MOD.values() if v[2]=='catalyst'}
cat_stations={'precision_assembler','laser_cooler','casimir_cavity','time_crystal_incubator','singularity_encapsulator'}
blocked=[]
for p in sorted((ROOT/'src/generated/resources/data/singulo/recipe').rglob('*.json')):
    r=json.loads(p.read_text())
    if r.get('station') in cat_stations:
        cat_count=sum(x.get('count',1) for x in r.get('ingredients',[]) if x.get('ingredient',{}).get('item','').removeprefix('singulo:') in cat_ids)
        if cat_count>1: blocked.append({'path':str(p.relative_to(ROOT)), 'catalyst_count':cat_count})

# Compare generated item-recipe time/energy/output tuples with the post-shape source.
verified_tuples = []
recipe_dir = ROOT/'src/generated/resources/data/singulo/recipe'
for name, recipe in RECIPES.items():
    station = STATIONS.get(recipe['station'])
    output_name = OUTPUT_ALIASES.get(name, name)
    if not station or output_name not in MOD:
        continue
    output_id = 'singulo:' + MOD[output_name][0]
    candidates = [json.loads(p.read_text()) for p in (recipe_dir/station).glob('*.json')]
    candidates = [r for r in candidates if r.get('result', {}).get('id') == output_id and not r.get('restore')]
    if not candidates:
        continue
    assert any((r['time'], r['energy'], r['result'].get('count', 1)) ==
               (round(recipe['t']*20), recipe['fe'], recipe['out']) for r in candidates), name
    verified_tuples.append(name)

# Known runtime values, independently cross-checked against Java / generated JSON.
assert RECIPES['基礎回路']['inputs']['銅インゴット']==2 # shaped override matters
assert RECIPES['縮退物質殻']['inputs']['圧縮ブロックLv3']==4
assert RECIPES['時間結晶触媒']['t']==720
assert len(blocked)>=4
shells={}
for n in [1,13,14]:
    rock=n*(4*729+4*6*81); metal_mass=n*2*81
    compressor_seconds=n*((4*81+4*6*9)*.5+(4*9+4*6)*1+4*2+2*9*.5+2*1)
    shells[n]={'rock_mass':rock,'copper_or_iron_ingots':metal_mass*3,'pressure_crystals':4*n,
        'nitrogen_mb':6000*n,'compressor_minutes':compressor_seconds/60}

# Tick simulation of Penrose run() in an isolated, continuously loaded reactor.
# Applies feed, evaporation, spin injection and output. No entities, time fields, or dark matter.
def efficiency(a):
    z1=1+(1-a*a)**(1/3)*((1+a)**(1/3)+(1-a)**(1/3)); z2=math.sqrt(3*a*a+z1*z1)
    r=3+z2-math.sqrt((3-z1)*(3+z1+2*z2))
    return 1-math.sqrt(1-2/(3*r))
def simulate(load, target, seconds=1800, fuel=True):
    mass=500.; spin=0.; buffer=0; carry=0.; htimer=0; h=0; pellets=0; rows=[]
    for tick in range(seconds*20):
        per=math.floor(20e9*efficiency(spin)+.5); cap=mass/1000/20
        rate=max(.03*cap,min(cap,(40e9-buffer)/per))
        carry+=rate; whole=int(carry); taken=whole if fuel else 0
        carry=0 if taken<whole else carry-whole
        pellets+=taken; buffer=min(40e9,buffer+taken*per); mass=max(mass,min(5000,mass+taken*4))
        mass-=.006/(mass/1000)**2/20
        if mass<100: break
        if spin<target and buffer>0:
            p=min(buffer,200e6); buffer-=p; spin=min(target,spin+p/(mass*1.2e8))
        if 150<=mass<=500:
            htimer+=1
            if htimer>=6000: h+=1; htimer=0
        output=min(buffer,load,2.1e9); buffer-=output
        if tick%20==19: rows.append(dict(seconds=(tick+1)/20,mass=mass,spin=spin,buffer=buffer,pellets=pellets,hawking=h,output=output))
    return rows
sims={'no_fuel':simulate(0,0,fuel=False), 'auto_idle':simulate(0,0), 'full_load':simulate(2.1e9,1)}

java=(ROOT/'src/main/java').rglob('*.java')
all_java='\n'.join(p.read_text() for p in java)
config=(ROOT/'src/generated/java/io/github/genichimaruo/singulo/generated/ServerConfig.java').read_text()
constants=re.findall(r'public static final ModConfigSpec\.\w+(?:<[^>]+>)? (\w+);',config)
unused=[c for c in constants if not re.search(r'\bServerConfig\.'+c+r'\b',all_java)]
result={'scope_commit':'b45f478819fa205091358a0f715edd9761d45862','baskets':stages,'cumulative':snapshots,'verified_generated_item_recipe_tuples':len(verified_tuples),
        'blocked_recipes':blocked,'config_no_main_java_reference':unused,'shells':shells,
        'penrose_efficiency':{a:efficiency(a) for a in [0,.5,.9,.99,1]},
        'simulation_final':{k:v[-1] for k,v in sims.items()}}
(HERE/'calculation-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
with (HERE/'resource-costs.csv').open('w',newline='') as f:
    w=csv.writer(f); w.writerow(['stage','resource','cumulative_quantity','unit_note'])
    for s,ss in snapshots.items():
        for n,v in sorted(ss['raw'].items()): w.writerow([s,n,v,'fractional originals=use equivalent; ceil for new originals'])
with (HERE/'penrose-simulation.csv').open('w',newline='') as f:
    w=csv.DictWriter(f,fieldnames=['scenario',*sims['no_fuel'][0]])
    w.writeheader()
    for k,rows in sims.items():
        for row in rows:w.writerow({'scenario':k,**row})
# Markdown numerical appendix is generated to prevent transcription errors.
lines=['# 計算付表（自動生成）','','設備バスケットは calculation-results.json を参照。到達時間ではなく資材・加工負荷の計算。','',
'| 累計資材 | T1 | T2 | T3 | T4 | 点火前 | 初コア後 |','|---|---:|---:|---:|---:|---:|---:|']
for n in ['鉄インゴット','銅インゴット','石炭','ネザー水晶','ダイヤモンド','青氷','質量値','金属質量','高圧結晶','ネザースター','星屑','雷ガラス','残響の欠片（型）','H凝縮体','観測ログ','量子データ片','培養データ','劣化した制御ユニット','劣化した時間結晶の種']:
    lines.append('| '+n+' | '+' | '.join(f"{snapshots[s]['raw'].get(n,0):,.3f}".rstrip('0').rstrip('.') for s in snapshots)+' |')
lines+=['','| 累計加工負荷（分・台） | T1 | T2 | T3 | T4 | 点火前 | 初コア後 |','|---|---:|---:|---:|---:|---:|---:|']
for station in ['圧縮機','極低温冷却塔','レーザー冷却器','量子もつれ合成器','時間結晶育成槽','縮退圧縮炉','精密組立台','アーカイブ端末']:
    lines.append('| '+station+' | '+' | '.join(f"{snapshots[s]['machine_minutes'].get(station,0):,.1f}" for s in snapshots)+' |')
(HERE/'calculation-tables.md').write_text('\n'.join(lines)+'\n')
print(json.dumps({'blocked':blocked,'shells':shells,'sim_final':result['simulation_final'],'unused_config':unused},ensure_ascii=False,indent=2))

try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    plt.rcParams.update({'font.family':'DejaVu Sans','font.size':10,'axes.spines.top':False,'axes.spines.right':False})
    assets=HERE/'assets'; assets.mkdir(exist_ok=True)
    fig,ax=plt.subplots(figsize=(10,4.6))
    labels=['T1 stable\nTEG x8','T2 cryo\nx2 (gross)','T3 muon\nbridge','T3 Bose','T4 Bose\nbridge','T4 time\ncrystal','T5 fresh\na=0','T5 max\na=1']
    vals=[8*75.7,10000,100000,200000,10000000,20000000,500/1000/20*20e9*efficiency(0),2.1e9]
    ax.bar(labels,vals,color=['#648fff']*2+['#dc9d3a','#648fff','#dc9d3a','#648fff','#785ef0','#785ef0'])
    ax.set_yscale('log'); ax.set_ylabel('FE/t (log scale; gross output)'); ax.set_title('Power progression: large gains before matching-tier catalysts')
    for i,v in enumerate(vals): ax.text(i,v*1.25,f'{v:,.0f}',ha='center',fontsize=9)
    ax.set_ylim(100,1e10);fig.tight_layout();fig.savefig(assets/'power-progression.png',dpi=160);plt.close(fig)
    fig,axes=plt.subplots(1,2,figsize=(11,4.4))
    ns=[1,4,8,12,16]
    axes[0].bar([str(x) for x in ns],[shells[13]['compressor_minutes']/x for x in ns],color='#648fff')
    axes[0].set_xlabel('Compressors (ideal load balancing)'); axes[0].set_ylabel('Minutes'); axes[0].set_title('Pre-ignition: 13 shells, compression only')
    axes[1].bar(['100/min','300/min','600/min','1200/min'],[shells[13]['rock_mass']/x/60 for x in [100,300,600,1200]],color='#dc9d3a')
    axes[1].set_xlabel('Rock acquisition rate (assumption)');axes[1].set_ylabel('Hours');axes[1].set_title('Pre-ignition: 63,180 rock-equivalent blocks')
    fig.tight_layout();fig.savefig(assets/'bottlenecks.png',dpi=160);plt.close(fig)
    fig,axes=plt.subplots(1,2,figsize=(11,4.5))
    for k,c,label in [('no_fuel','#648fff','No fuel'),('auto_idle','#dc9d3a','Auto feed, no load'),('full_load','#785ef0','Auto feed, max load/spin')]:
        rows=sims[k];axes[0].plot([r['seconds']/60 for r in rows],[r['mass'] for r in rows],color=c,label=label)
        axes[1].plot([r['seconds']/60 for r in rows],[r['hawking'] for r in rows],color=c,label=label)
    axes[0].axhspan(150,500,color='#648fff',alpha=.12);axes[0].set_ylabel('Core mass');axes[0].set_yscale('log');axes[0].set_title('Fuel input leaves the Hawking collection window')
    axes[1].set_ylabel('Hawking condensates');axes[1].set_title('First singularity core needs 6 condensates total')
    for ax in axes:ax.set_xlabel('Minutes after ignition');ax.legend(fontsize=8)
    fig.tight_layout();fig.savefig(assets/'penrose-operation.png',dpi=160);plt.close(fig)
except ImportError:
    print('matplotlib missing; data and tables were generated without plots.')
