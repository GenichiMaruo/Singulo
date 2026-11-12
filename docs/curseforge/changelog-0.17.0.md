# Singulo 0.17.0 — Minecraft 1.21.1 / NeoForge

## Features

- Fluid pipes now come in four tiers: Fluid Pipe (2,000 mB/t), Vacuum-Jacketed Pipe (32,000 mB/t), Superfluid Conduit (512,000 mB/t) and Horizon Duct (no limit). Each tier has its own look, with flanged joints and windows so pipes are easy to tell apart from cables, and flowing fluid shows inside the pipe.
- Pipe networks now work like cable networks: unbranched runs act as a single stretch, and the narrowest part of a route sets how much it carries. Long networks stay light.
- Add the Fluid Drone Port, which sends fluids by drone in Fluid Capsules. The same stations and drones carry them, and each capsule holds up to 64,000 mB of one fluid.
- Add three tiers of Fluid Capsule with the same ranges as packing crates. Capsules are 3D pressure vessels that show their contents, in your hand, under a drone and on the port lift.
- Add Collapsar Lining. Place it in the casing positions of the Degenerate Compactor, Casimir Cavity or Cryogenic Cooling Tower for 5% faster processing per lining, up to 2× at 20. The Holo Projector highlights the positions in gold.
- Add the Event Horizon Vault, which stores a practically unlimited amount of one item inside a small black hole and keeps its contents when broken.
- Add the Wormhole Drone Beacon. With one beside both mouths of a wormhole, drones fly through it to deliver to ports in another dimension and come back the same way. The far side needs no Drone Station. Within one dimension, drones also use a beaconed wormhole as a shortcut when it is shorter.
- The Tidal Spindle can now stretch vanilla materials from its left slot once its core is formed (1 MFE/t, no coolant): Wool → 4 String, Iron Ingot → 2 Chains, 4 Blaze Powder → Blaze Rod, Leather → Phantom Membrane, Stone → 2 Pointed Dripstone.

## Changes

- The Accretion Jet Forge now makes more of the moment the jet punches through each focusing ring: a white flash fills the ring, two shock rings and light streaks spread outward, a pulse runs along the beam to the next ring, sparks fly, and the screen flashes and shakes briefly (both follow the existing flash and shake settings).
- The Relocation Wrench in cable mode now also takes back Fluid Pipes, one at a time or a whole joined run of one tier.
- A broken Fluid Drone Port keeps the fluid in both tanks in its item and restores it when placed.
- Walking through a seamed wormhole now works like a Nether portal: you come out at the same spot in the far opening with your facing and momentum turned to match, instead of being placed in front of the far mouth. You are not sent back until you step out of the opening.
- Arrows, tridents, thrown items and other projectiles now pass through a seamed wormhole like a Nether portal, coming out of the same spot in the far opening with their direction turned to match.
- A route made only of Fluid Pipes carries up to 2,000 mB per tick. Before this version each port moved 4,000 mB per 5 ticks with no shared limit. Upgrade busy trunks to a higher tier.

## Fixes

- Fix empty-hand vault withdrawal, rapid inventory deposits and nested packed-container rejection.
- Let fluid capsules pour before opening machine screens. Source pumping requires room for a whole bucket at one endpoint, preventing shared tank ports from double-counting free space.
- Keep visiting drones separate from local stations at the same coordinates, and return intact cargo when remote ports or return mouths disappear.
- Projectiles still hit entities in front of a wormhole before crossing it.

- The Suspension Reservoir, Nuclear Pasta Accumulator and Tidal Spindle now play the formation sparks and sound when completed, like other multiblocks.

## 追加

- 液体パイプが4段階になりました。液体パイプ（2,000 mB/t）、真空断熱パイプ（32,000 mB/t）、超流動導管（512,000 mB/t）、ホライズン・ダクト（上限なし）。段階ごとに見た目が違い、継ぎ手のつばと窓で電線と見分けられます。流れている液体は管の中に見えます。
- パイプの配管が電線と同じ作りになりました。分かれ目のない並びを1つにまとめ、経路のいちばん細いところで運べる量が決まります。長くつないでも重くなりにくくなりました。
- 液体ドローン発着ポートを追加しました。液体を液体カプセルに詰めて、ドローンで送ります。いつものステーションとドローンが運び、カプセル1つに1種類の液体を 64,000 mB まで詰められます。
- 液体カプセル3段階を追加しました。射程は梱包箱と同じです。中の液体が見える立体の耐圧容器で、手に持っても、ドローンに吊るしても、ポートの台の上でも中身が見えます。
- コラプサー内張りを追加しました。縮退圧縮炉・C空洞・極低温冷却塔の外装板の位置に置くと、1枚ごとに加工が5%速くなります（20枚で2倍）。置ける場所はホロ投影機が金色で示します。
- 事象の地平線倉庫を追加しました。1種類の物を中の小さなブラックホールに事実上無制限にしまい、壊しても中身を失いません。
- ワームホール航路標識を追加しました。ワームホールの両方の口のそばに置くと、ドローンが口をくぐって別の次元のポートへ荷物を届け、同じ口を通って帰ってきます。向こう側にドローン・ステーションは要りません。同じ次元の中でも、くぐるほうが近ければ近道に使います。
- 潮汐紡糸機が、炉心の形成後に左の枠のバニラ素材を引き伸ばせるようになりました（1 MFE/t、冷却材なし）。羊毛→糸4、鉄インゴット→鎖2、ブレイズパウダー4→ブレイズロッド、革→ファントムの皮膜、石→鍾乳石2。

## 変更

- 降着ジェット鍛造炉で、ジェットが収束リングを突き抜ける瞬間を派手にしました。リングの内側が白く光り、2重の衝撃の輪と光の筋が外へ広がり、光の塊がビームを次のリングへ走り、火花が飛び、画面が短く光って揺れます（閃光と揺れは、これまでの設定に従います）。
- 移設レンチの撤去モードで、液体パイプも1本ずつ、またはつながった同じ段階をまとめて回収できるようになりました。
- 液体ドローン発着ポートを壊すと、2つのタンクの液体をアイテムに残し、置き直すと戻ります。
- 縫い開いたワームホールを歩いて通ると、ネザーポータルと同じく、向こうの開口部の同じ位置に、体の向きと勢いを合わせて出るようになりました。これまでは向こうの口の前に置かれていました。開口部からいったん出るまでは戻されません。
- 矢・トライデント・投げた物などの飛び道具も、縫い開いたワームホールをネザーポータルと同じようにくぐり、向こうの開口部の同じ位置から向きを合わせて飛び出すようになりました。
- 液体パイプだけの経路で運べるのは 1 tick に 2,000 mB までになりました。これまでは口ごとに 5 tick に 4,000 mB を、全体の上限なしで運んでいました。多く流す幹は上の段階に替えてください。

## 修正

- 倉庫の素手での取り出し・連続クリックでの投入・梱包済み容器の入れ子拒否を修正しました。
- 液体カプセルは装置の画面より先に注げます。水源を汲む際は1つの受け口に1バケツ分の空きを要求し、共有タンクの空き容量を重複して数えないようにしました。
- 別次元の同座標のステーションがドローンを誤認しないようにし、輸送先や帰還口の消失時も荷物を保って発送元へ返すようにしました。
- ワームホール直前の生物にも飛び道具が正しく当たるようにしました。

- 重力懸垂タンク・核パスタ積層蓄電庫・潮汐紡糸機が、ほかのマルチブロックと同じく、完成したときに光と起動音の演出を出すようになりました。
