"""Reproducible bill-of-materials audit. Reads effective runtime data; never edits recipes.

Canonical routes are deliberately explicit, rather than pretending one weighted
material score is a global optimum. Fractions describe amortized demand; integers
make whole batches and retain leftovers and catalyst life within each scenario.
"""
from collections import Counter, defaultdict
from fractions import Fraction as F
from pathlib import Path
import argparse, json, math, zipfile
import ids, recipes

ROOT = Path(__file__).resolve().parent.parent
def vanilla_jar():
    jars=list((ROOT/'build/moddev/artifacts').glob('*client-extra-aka-minecraft-resources.jar'))
    if len(jars)!=1:raise RuntimeError('Build once or pass --vanilla-jar explicitly')
    return jars[0]
LIFE = {'muon_catalyst':24000,'bose_condensate_catalyst':36000,'time_crystal_catalyst':54000,'singularity_core':72000,
        'observation_log':16,'quantum_data_fragment':16,'culture_data':16,'control_unit':16,'cold_atom_trap':32,'time_crystal_seed':8,'anomaly_sample':4}
LIFE = {'singulo:'+k:v for k,v in LIFE.items()}
# These are natural/loot drops, not free resources. Their acquisition is assessed separately.
NATURAL = {'minecraft:'+x for x in ['coal','diamond','redstone','quartz','amethyst_shard','glowstone_dust','blue_ice','ice',
    'cobblestone','deepslate','obsidian','sand','clay_ball','bone','bone_meal','echo_shard','sculk','sculk_sensor','ender_pearl',
    'slime_ball','phantom_membrane','blaze_rod','nether_star','oak_log','raw_iron','raw_copper','raw_gold','string','leather']}
NATURAL |= {'singulo:'+x for x in ['observation_log','degraded_control_unit','quantum_data_fragment','degraded_cold_atom_trap',
 'degraded_time_crystal_seed','culture_data','degraded_anomaly_sample','dormant_singularity_seed','sentinel_core','degenerate_nucleus',
 'ergosphere_chapter','void_dust','raw_meteoric_iron','stardust','fulgurite','pressure_crystal','hawking_condensate','jet_condensate','strange_matter']}
PREFERRED_TAG = {'c:ingots/steel':'singulo:steel_ingot','c:plates/steel':'singulo:steel_plate','c:ingots/zinc':'singulo:zinc_ingot',
 'c:ingots/bismuth':'singulo:bismuth_ingot','singulo:carbon':'minecraft:coal','singulo:star_core':'minecraft:nether_star',
 'singulo:muon_catalysts':'singulo:muon_catalyst','minecraft:planks':'minecraft:oak_planks','minecraft:logs':'minecraft:oak_log',
 'minecraft:logs_that_burn':'minecraft:oak_log','minecraft:stone_crafting_materials':'minecraft:cobblestone',
 'c:ender_pearls':'minecraft:ender_pearl','c:obsidians':'minecraft:obsidian','c:sands':'minecraft:sand','c:glass_blocks/colorless':'minecraft:glass','c:stones':'minecraft:stone','c:cobblestones':'minecraft:cobblestone'}
for n,v in ids.VANILLA.items():
 if v.startswith('#'):
  tail=v.rsplit('/',1)[-1]; guesses={'iron':'iron_ingot','copper':'copper_ingot','gold':'gold_ingot','diamond':'diamond',
   'redstone':'redstone','quartz':'quartz','glowstone':'glowstone_dust','lapis':'lapis_lazuli','copper':'copper_ingot'}
  if tail in guesses:PREFERRED_TAG.setdefault(v[1:],'minecraft:'+guesses[tail])


def itemid(name):
    if name in ids.VANILLA and not ids.VANILLA[name].startswith('#'):return ids.VANILLA[name]
    return ids.OUTPUT_ALIASES.get(name,'singulo:'+ids.MOD.get(name,(name,))[0])


class Model:
    def __init__(self, runtime, jar=None):
        self.runtime=runtime; self.tags={}; self.all=[]; self.routes={}; self.meta={}; self.errors=[]
        with zipfile.ZipFile(jar or vanilla_jar()) as z:
            self.vanilla={n.removeprefix('data/minecraft/recipe/').removesuffix('.json'):json.loads(z.read(n))
                          for n in z.namelist() if n.startswith('data/minecraft/recipe/') and n.endswith('.json')}
            for n in z.namelist():
                if n.startswith('data/minecraft/tags/item/') and n.endswith('.json'):
                    self.tags['minecraft:'+n.split('/tags/item/')[1][:-5]]=json.loads(z.read(n))['values']
        for p in (ROOT/'src/generated/resources/data').glob('*/tags/item/**/*.json'):
            ns=p.relative_to(ROOT/'src/generated/resources/data').parts[0]
            self.tags[ns+':'+str(p).split('/tags/item/')[1][:-5]]=json.loads(p.read_text())['values']
        self.all=list(runtime['recipes'])
        for p in sorted((ROOT/'src/generated/resources/data/singulo/recipe').rglob('*.json')):
            r=json.loads(p.read_text())
            if r['type'].startswith('minecraft:'):
                row=self.parse(r,'singulo:'+str(p.relative_to(ROOT/'src/generated/resources/data/singulo/recipe'))[:-5])
                if row:self.all.append(row)
        self.all += [row for name,r in self.vanilla.items() if (row:=self.parse(r,'minecraft:'+name))]
        byout=defaultdict(list)
        for row in self.all:
            if row['output']:byout[row['output']].append(row)
            for f in row['fluid_outputs']:byout['fluid:'+f['id']].append({**row,'output':'fluid:'+f['id'],'count':f['count']})
        # Match primary data definitions to generated recipe paths; avoid repair/recycling loops.
        for name,r in recipes.RECIPES.items():
            if name in ids.OUTPUT_ALIASES or name not in ids.MOD:continue
            iid=itemid(name); self.meta[iid]={'name':name,'stage':r['stage'],'category':r['cat']}
            st=ids.STATIONS.get(r['station']); base=iid.split(':')[1]
            candidates=byout[iid]
            candidates=sorted(candidates,key=lambda x:(x['id']!=f'singulo:{st}/{base}',not x['id'].endswith('/'+base),x['id']))
            if candidates:self.routes[iid]=candidates[0]
        for out,rows in byout.items():
            if out.startswith('fluid:'):
                self.routes[out]=min(rows,key=lambda r:(not r['id'].endswith('/'+out.rsplit(':',1)[-1]),r['id']))
            elif out not in self.routes:
                fresh=[r for r in rows if r.get('restore') and out not in r['restore']]
                if fresh:self.routes[out]=fresh[0]
        # Stable, documented vanilla routes. Natural drops are stop points.
        wanted={'iron_ingot':'iron_ingot_from_smelting_raw_iron','copper_ingot':'copper_ingot_from_smelting_raw_copper',
                'gold_ingot':'gold_ingot_from_smelting_raw_gold','stone':'stone','smooth_stone':'smooth_stone',
                'glass':'glass','oak_planks':'oak_planks','stick':'stick','chest':'chest','hopper':'hopper','piston':'piston',
                'blast_furnace':'blast_furnace','furnace':'furnace','bucket':'bucket','clock':'clock','compass':'compass',
                'lightning_rod':'lightning_rod','ender_eye':'ender_eye','blaze_powder':'blaze_powder','paper':'paper',
                'book':'book','crafting_table':'crafting_table','iron_nugget':'iron_nugget','gold_nugget':'gold_nugget','iron_block':'iron_block','copper_block':'copper_block'}
        for out,rid in wanted.items():
            rows=[r for r in self.all if r['id']=='minecraft:'+rid]
            if rows:self.routes['minecraft:'+out]=rows[0]
        # Normal kiln/press recipes, no oxygen or mixed-material bonus in the baseline.
        for iid,path in [('steel_ingot','kiln/steel_ingot'),('white_ceramic','kiln/white_ceramic'),('steel_plate','compressor/steel_plate')]:
            row=next((r for r in self.all if r['id']=='singulo:'+path),None)
            if row:self.routes['singulo:'+iid]=row
        # Multi-output chemistry: charge the full raw-copper batch to bismuth (conservative, no copper credit).
        copper=next(r for r in self.all if r['id']=='singulo:catalytic_reactor/copper_ingot_refined')
        self.routes['singulo:bismuth_ingot']={**copper,'id':copper['id']+'#bismuth','output':'singulo:bismuth_ingot','count':1}
        for key,parts in runtime['structures'].items():
            kind,size=key.split('/'); iid='analysis:'+key
            self.routes[iid]=self.synthetic(iid,parts)
        # Virtual complete machines use the real Java blueprint, not the controller alone.
        self.struct_keys={'cryogenic_cooling_tower':'cooling_tower/7','particle_accelerator':'particle_accelerator/8',
            'degenerate_compactor':'degenerate_compactor/5','casimir_cavity':'casimir_cavity/5','degenerate_furnace':'degenerate_furnace/7',
            'penrose_reactor':'penrose_reactor/13','event_horizon_shield':'event_horizon_shield/9',
            'tipler_cylinder':'tipler_cylinder/9','wormhole_generator':'wormhole_generator/5'}
        for iid,key in self.struct_keys.items():self.routes['singulo:'+iid]=self.routes['analysis:'+key]
        # An upgrade must replace the occupied footprint. Count added blocks, not a second tower.
        a=Counter(runtime['structures']['cooling_tower/10']);a.subtract(runtime['structures']['cooling_tower/7'])
        self.routes['singulo:cooling_tower_extension']=self.synthetic('tower extension',{k:v for k,v in a.items() if v>0})
        larger=Counter(runtime['structures']['particle_accelerator/16']);larger.subtract(runtime['structures']['particle_accelerator/8'])
        self.routes['analysis:accelerator_upgrade']=self.synthetic('accelerator upgrade',{k:v for k,v in larger.items() if v>0})
        self.meta['analysis:genesis_engine/7']={'name':'創世炉（完成構造）','stage':5,'category':'multiblock'}
        # Custom stations do not have MachineRecipe JSON.
        for name,r in recipes.RECIPES.items():
            iid=itemid(name)
            if r['station'] in ids.CUSTOM_STATIONS and iid not in self.routes:
                self.routes[iid]=self.synthetic('custom:'+iid,{itemid(k):v for k,v in r['inputs'].items()},r['out'],r['t']*20,r['fe'])
        self.routes['singulo:unstable_wormhole_mouth']=self.synthetic('custom:mouth_pair',{},2,200,100_000_000)
        self.routes['singulo:unstable_wormhole_mouth']['station']='wormhole_generator'
        self.routes['singulo:wormhole_mouth']['ticks']=100
        self.routes['singulo:wormhole_mouth']['fe_per_tick']=10000
        self.targets=[k for k,m in self.meta.items() if m['category'] in {'machine','generator','multiblock','storage','romance'}]
        # Include other functional blocks that are classed as tools/parts/cables in the catalogue.
        extras=['smes_cell','worldline_anchor_small','low_gravity_panel','high_gravity_panel','zero_gravity_block','rayleigh_panel',
                'multiblock_port','wormhole_port','jet_collector','hawking_collector','extraction_port','reactor_mass_alarm']
        self.targets += [f'singulo:{v[0]}' for n,v in ids.MOD.items() if v[2] in {'machine','gravity'} and f'singulo:{v[0]}' in self.meta]
        self.targets=sorted(set(self.targets+[f'singulo:{x}' for x in extras if f'singulo:{x}' in self.meta]),key=lambda x:(self.meta[x]['stage'],x))

    def options(self, ing, seen=()):
        if isinstance(ing,list):return sum((self.options(i,seen) for i in ing),[])
        if 'item' in ing:return [ing['item']]
        if 'items' in ing:return [ing['items']] if isinstance(ing['items'],str) else ing['items']
        tag=ing.get('tag')
        if tag in PREFERRED_TAG:return [PREFERRED_TAG[tag]]
        if tag in seen:return []
        return sum((self.options({'tag':v[1:]} if v.startswith('#') else {'item':v},seen+(tag,))
                    for v in self.tags.get(tag,[]) if isinstance(v,str)),[]) or ['tag:'+str(tag)]

    def parse(self,r,rid):
        kind=r['type'];ins=[]
        if kind=='minecraft:crafting_shaped':
            counts=Counter(''.join(r['pattern']));ins=[{'options':self.options(v),'count':counts[k],'uses':0,'tier':0} for k,v in r['key'].items()]
        elif kind=='minecraft:crafting_shapeless':ins=[{'options':self.options(i),'count':1,'uses':0,'tier':0} for i in r['ingredients']]
        elif kind in {'minecraft:smelting','minecraft:blasting'}:ins=[{'options':self.options(r['ingredient']),'count':1,'uses':0,'tier':0}]
        else:return None
        out=r.get('result');out={'id':out} if isinstance(out,str) else out
        if not out:return None
        return dict(id=rid,station=kind,stage=0,ticks=r.get('cookingtime',0),fe_per_tick=0,inputs=ins,restore=[],fluids=[],
                    output=out.get('id',out.get('item')),count=out.get('count',1),fluid_outputs=[],byproduct='')

    @staticmethod
    def synthetic(rid,inputs,count=1,ticks=0,fe=0):
        return dict(id=rid,station='assembly',stage=0,ticks=ticks,fe_per_tick=fe,
                    inputs=[dict(options=[k],count=v,uses=0,tier=0) for k,v in inputs.items()],restore=[],fluids=[],
                    output=rid,count=count,fluid_outputs=[],byproduct='')

    def choose(self,options):
        def score(x):return (x not in self.routes and x not in NATURAL, x.startswith('singulo:spent_'),
                             not x.startswith('minecraft:oak_'),x)
        return min(options,key=score)

    def deps(self,r):
        out=[]
        for i in r['inputs']:
            iid=self.choose(i['options']);q=F(i['count'])
            if i['uses']>0:q=F(i['uses'],LIFE.get(iid,16))
            out.append((iid,q))
        if r['restore']:
            source=self.choose(r['restore'])
            # Fresh degraded components need a first restoration. Repair cycles are not used in this baseline.
            if source!=r['output']:out.append((source,F(1)))
        out += [('fluid:'+f['id'],F(f['count'])) for f in r['fluids']]
        if 'mass' in r:out.append(('minecraft:iron_block' if r['mass']['metal'] else 'minecraft:cobblestone',F(str(r['mass']['amount'])) / (3 if r['mass']['metal'] else 1)))
        return out


class Bill:
    def __init__(self,model,integer=False,stage=5):
        self.model=model;self.integer=integer;self.raw=Counter();self.operations=Counter();self.life=Counter();self.stock=Counter();self.use_stock=Counter();self.trace=[];self.assets=set();self.stage=stage;self.energy=F(0);self.work=Counter()
    def need(self,item,q,path=()):
        q=F(q)
        if item in path:raise RuntimeError('Canonical cycle: '+' -> '.join(path+(item,)))
        if q<=0:return
        if self.integer:
            take=min(q,self.stock[item]);q-=take;self.stock[item]-=take
        if q<=0:return
        if item.startswith('singulo:blueprint_'):
            self.assets.add(item);return
        r=self.model.routes.get(item)
        if item in NATURAL or item.startswith('fluid:minecraft:') or r is None:
            self.raw[item]+=q;return
        n=q/F(r['count']);n=F(math.ceil(n)) if self.integer else n
        self.operations[r['id']]+=n
        ticks=r['ticks'];power=r['fe_per_tick']
        speed=1
        if r['station']=='cryogenic_cooling_tower':speed=max(10 if self.stage>=3 else 7,r.get('min_size',0))/5
        if r['station']=='particle_accelerator':speed=max(16 if self.stage>=4 else 8,r.get('min_size',0))/8
        if speed!=1:
            ticks=max(1,math.floor(ticks/speed+0.5))
            if r['station']=='cryogenic_cooling_tower' and not r['inputs'] and not r['fluids']:
                power=math.ceil(r['fe_per_tick']*r['ticks']/ticks)
        self.energy+=n*ticks*power;self.work[r['station']]+=n*ticks
        # Uses are shared within a scenario; a full catalyst need not be recreated for every recipe.
        for i in r['inputs']:
            iid=self.model.choose(i['options'])
            if i['uses']:
                uses=F(i['uses'])*n;self.life[iid]+=uses
                if self.integer:
                    take=min(uses,self.use_stock[iid]);uses-=take;self.use_stock[iid]-=take
                    count=math.ceil(uses/LIFE.get(iid,16))
                    if count:self.need(iid,count,path+(item,));self.use_stock[iid]+=count*LIFE.get(iid,16)-uses
                else:self.need(iid,uses/LIFE.get(iid,16),path+(item,))
            else:self.need(iid,F(i['count'])*n,path+(item,))
        for iid,amount in self.model.deps({**r,'inputs':[]}):self.need(iid,amount*n,path+(item,))
        if self.integer:self.stock[item]+=n*r['count']-q
    def summary(self):
        lookup={r['id']:r for r in self.model.all}
        lookup.update({r['id']:r for r in self.model.routes.values()})
        energy=self.energy
        ticks=sum(self.work.values())
        fuel=sum(n*lookup[k]['ticks']/(800 if lookup[k]['station']=='minecraft:blasting' else 1600)
                 for k,n in self.operations.items() if lookup[k]['station'] in {'minecraft:blasting','minecraft:smelting'})
        return dict(work_seconds={k:float(v/20) for k,v in self.work.items()},vanilla_fuel_coal_equivalent=float(fuel),reusable_assets=sorted(self.assets),raw={k:float(v) for k,v in sorted(self.raw.items())},energy_fe=float(energy),serial_machine_seconds=float(ticks/20),
                    life={k:float(v) for k,v in self.life.items()},operations={k:float(v) for k,v in self.operations.items()},
                    leftovers={k:float(v) for k,v in self.stock.items() if v>0},unused_life={k:float(v) for k,v in self.use_stock.items() if v>0})


def reachable(model, banned=()):
    """Monotone recipe AND/OR closure, with stations/structures and explicit loot gates.

    Quantity/FE are deliberately unbounded here. A blocked node is a hard gate;
    a reachable node is not a promise that combat or material collection is easy.
    """
    banned=set(banned)
    available={x for r in model.all for i in r['inputs'] for x in i['options'] if x.startswith('minecraft:')}
    available|={x for r in model.all for x in [r['output']] if x.startswith('minecraft:')}
    available|={x for x in NATURAL if x.startswith('minecraft:')}
    if 'wither' in banned:available.discard('minecraft:nether_star')
    available|={'fluid:minecraft:water','fluid:minecraft:lava'}
    available|={'singulo:'+x for x in ['observation_log','degraded_control_unit','raw_meteoric_iron','stardust','fulgurite','blueprint_rayleigh']}
    loot={'sentinel':['sentinel_core','quantum_data_fragment','degraded_cold_atom_trap'],
          'remnant':['degenerate_nucleus','culture_data','degraded_time_crystal_seed'],
          'warden':['dormant_singularity_seed','degraded_anomaly_sample'],
          'leviathan':['exotic_matter','hawking_condensate','jet_condensate','ergosphere_chapter'],
          'foundry':['precision_actuator','android_kit']}
    for boss,items in loot.items():
        if boss not in banned:available.update('singulo:'+x for x in items)
    rows=list(model.all)
    for iid,r in model.routes.items():
        if r['station']=='assembly' or r['id'].startswith('custom:'):rows.append({**r,'output':iid})
    tiers={'singulo:muon_catalyst':2,'singulo:bose_condensate_catalyst':3,'singulo:time_crystal_catalyst':4,'singulo:singularity_core':5}
    while True:
        before=set(available)
        for row in rows:
            station=row['station']
            if not station.startswith('minecraft:') and station!='assembly' and 'singulo:'+station not in available:continue
            if row.get('min_size',0)>=16 and 'analysis:particle_accelerator/16' not in available:continue
            if station=='cryogenic_cooling_tower' and row.get('min_size',0)>=10 and 'analysis:cooling_tower/10' not in available:continue
            if row['restore'] and not set(row['restore'])&available:continue
            if any(not ((any(t>=i['tier'] and x in available for x,t in tiers.items())) if i['tier'] else bool(set(i['options'])&available)) for i in row['inputs']):continue
            if any('fluid:'+f['id'] not in available for f in row['fluids']):continue
            if row['output']:available.add(row['output'])
            available.update('fluid:'+f['id'] for f in row['fluid_outputs'])
            if row['byproduct']:available.add(row['byproduct'])
        for parent,child in [('deep_sea_collector','pressure_crystal'),('void_collector','void_dust')]:
            if 'singulo:'+parent in available:available.add('singulo:'+child)
        if {'singulo:penrose_reactor','singulo:singularity_seed'}<=available:
            available.update({'singulo:jet_condensate','singulo:hawking_condensate','analysis:running_reactor'})
        # Sealed containers only reward after the corresponding craftable key exists.
        for key,items in [('magnetic_key',[]),('quantum_key',['cold_atoms']),('temporal_key',['exotic_matter','degenerate_matter_shell']),
                          ('singularity_key',['jet_condensate','hawking_condensate','exotic_matter'])]:
            if 'singulo:'+key in available:available.update('singulo:'+x for x in items)
        if available==before:return available


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',type=Path,default=Path('build/balance/runtime.json'))
    p.add_argument('--vanilla-jar',type=Path);p.add_argument('--out',type=Path,default=Path('build/balance/results.json'));args=p.parse_args()
    m=Model(json.loads(args.runtime.read_text()),args.vanilla_jar);rows=[]
    for target in m.targets:
        pair={}
        for integer in [False,True]:
            bill=Bill(m,integer,m.meta[target]['stage']);bill.need(target,1);pair['integer' if integer else 'amortized']=bill.summary()
        rows.append({'id':target,**m.meta[target],**pair})
    scenarios={}
    for stage,items in recipes.MILESTONES.items():
        bill=Bill(m,True,stage)
        for name,count in items.items():bill.need(itemid(name),count)
        if stage==4:bill.need('analysis:accelerator_upgrade',1)
        scenarios[str(stage)]=bill.summary()
    # One shared inventory for a practical cumulative progression, not a sum of rounded per-device bills.
    cumulative=Bill(m,True); cumulative_rows={}
    for stage,items in recipes.MILESTONES.items():
        cumulative.stage=stage
        for name,count in items.items():cumulative.need(itemid(name),count)
        if stage==4:cumulative.need('analysis:accelerator_upgrade',1)
        cumulative_rows[str(stage)]=cumulative.summary()
    ending=Bill(m,True)
    ending.need('analysis:genesis_engine/7',1);ending.need('singulo:primordial_seed',1)
    for iid,count in [('singulo:degenerate_furnace',20),('singulo:time_crystal_catalyst',20)]:ending.need(iid,count)
    scenarios['ending_without_reactor']=ending.summary()
    gates={}
    bosses=['sentinel','remnant','warden','leviathan','foundry','wither']
    for banned in [[],*[[b] for b in bosses],['wither','leviathan'],bosses]:
        state=reachable(m,banned)
        gates[','.join(banned) or 'none']={'reachable_devices':[x for x in m.targets if x in state],
            'blocked_devices':[x for x in m.targets if x not in state],
            'milestones':{x:x in state for x in ['singulo:bose_condensate_catalyst','singulo:time_crystal_catalyst','singulo:exotic_matter',
                'singulo:singularity_seed','analysis:running_reactor','singulo:singularity_core','singulo:artificial_star_core','singulo:primordial_seed']}}
    args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps({'machines':rows,'milestones':scenarios,'cumulative':cumulative_rows,'boss_gates':gates},ensure_ascii=False,indent=2))
    print('Evaluated',len(rows),'functional devices/structures')
    unknown=sorted({k for r in rows for k in r['amortized']['raw'] if k.startswith(('singulo:','tag:','fluid:singulo:')) and k not in NATURAL})
    print('Noncanonical terminals:',unknown)
    if unknown:raise RuntimeError('Unexpanded audit inputs: '+', '.join(unknown))

if __name__=='__main__':main()
