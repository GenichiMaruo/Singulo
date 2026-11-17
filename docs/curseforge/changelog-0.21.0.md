# Singulo 0.21.0 — Minecraft 1.21.1 / NeoForge

## Features

- Jade and WTHIT support: looking at a machine shows its status and why it has stopped, progress, power used or generated, the catalyst left, and for an unfinished multiblock the parts it still needs. Looking at a part of a multiblock shows its controller (Jade only). Both mods stay optional.
- The Desk Monitor now opens a factory monitor: power generated, used and stored by machines within 16 blocks, the stalled machines nearest first with their reasons and direction, and how the drone stations are doing. Each Server Rack within 2 blocks of the monitor widens the range by 8 blocks, up to 4 racks.
- Before a Genesis Engine gives birth, materials used on its core shape the galaxy: a dye sets the arm color, a Blaze Rod makes a barred spiral, a Slime Ball an elliptical galaxy, an Eye of Ender a ring galaxy, Magma Cream an irregular galaxy, and a Nether Star makes it grand (1.5 times larger in the night sky). Galaxies born before this version look the same as before.
- Using another Primordial Seed on a Genesis Engine that has given birth kindles it again for the next galaxy, so one engine can fill the night sky (up to eight galaxies). The epilogue plays only for its first galaxy.
- Glass slabs for White Glass Panel, Black Reinforced Glass, Android Gate Glass and Android Barrier Glass. Like the glass stairs, they hide the seams with the same glass, and a full glass block next to them draws only the part they leave open. The android glass slabs let androids through (or stop them) like the glass itself.
- Portable Android Printer: a briefcase printer for androids on the go. Put a compact crystal memory and android kits in it, then right-click the ground: the case pops open, prints one android on the bed inside, closes and returns to your inventory. The android follows you as an android without a station, and can be linked to an Android Assembly Station later. The Portable Android Printer Mk2 holds three sets (three types of android) and prints the next set each time; its screen shows which one comes next and lets you choose it. Each print uses twice the power of the station, and the printer charges as much as one print of the loaded type needs; charge the printer in the charging slot of a power storage. Self-repair kits stocked in it go to the printed android, and while you carry the printer it refills your nearby androids without a station. An android armor plate in it is fitted to the printed android.
- Guard androids pick up items lying nearby, such as drops from enemies they defeat (not items thrown by players), and then store them in a nearby chest or in a work console or supply port. Two new buttons on the gear page switch picking up on or off and choose where to store: chests first, work storage first, or keep the items.

## Fixes

- Androids no longer pick a spot on Android Gate Glass to stand on while working or when leaving water, and no longer treat it as a floor while flying or as a lid over water. Android Barrier Glass now counts as a floor for them.
- Waterlogged glass stairs draw their water like other stairs but no longer show water inside the glass (not with Sodium).
- A finished Genesis Engine no longer shows what it is synthesizing above the hotbar every time you walk near it. Its screen still shows the progress.

## 追加

- Jade・WTHIT に対応しました。装置を見るだけで、状態と止まっている理由・進み具合・使っている（作っている）電力・触媒の残り、組み上がっていないマルチブロックでは足りない部品が分かります。マルチブロックの部品を見ると、そのコントローラーの様子が出ます（Jade のみ）。どちらも入れなくても遊べます。
- 机のモニターを使うと、工場モニターが開くようにしました。まわり16ブロックの装置の発電・消費・蓄電、止まっている装置（近い順に、理由と方角つき）、ドローン・ステーションの様子が分かります。モニターから2ブロック以内にサーバーラックを置くと、1台ごとに範囲が8ブロック広がります（4台まで）。
- 創世炉で宇宙が生まれる前に、素材を核に使うと銀河の形と色を選べるようにしました。染料で腕の色、ブレイズロッドで棒渦巻銀河、スライムボールで楕円銀河、エンダーアイで環状銀河、マグマクリームで不規則銀河、ネザースターで大きな銀河（夜空で1.5倍）になります。これまでに生まれた銀河の見た目は変わりません。
- 宇宙を生んだ創世炉にもう一度原初の種を使うと、種火からやり直して次の銀河を生めるようにしました。1つの炉で夜空を飾れます（8個まで）。エピローグは最初の銀河のときだけ流れます。
- ホワイトガラスパネル・黒色強化ガラス・アンドロイド透過ガラス・アンドロイド遮断ガラスのハーフブロックを追加しました。ガラスの階段と同じく、同じガラスとの境目は描かず、隣の同じガラスのブロックは、ハーフブロックに隠れていない所だけを描きます。アンドロイド用のガラスのハーフブロックは、元のガラスと同じくアンドロイドを通します（遮断ガラスは止めます）。
- 携帯型アンドロイド印刷機を追加しました。カバン型の印刷機で、小型クリスタルメモリとアンドロイドキットを入れて地面を右クリックすると、カバンがパカっと開いて中の印刷面でアンドロイドを1体印刷し、閉じて持ち物へ戻ります。印刷したアンドロイドは、ついて歩く身寄りのないアンドロイドになり、あとで組立ステーションにリンクできます。携帯型アンドロイド印刷機（Mk2）は3組（3種類のアンドロイド）を積め、印刷するたびに次の組で1体ずつ印刷します。次に印刷する組は画面で分かり、選ぶこともできます。印刷ごとに組立ステーションの2倍の電力を使い（入れたメモリの型の1体分だけ充電できます）、蓄電装置の充電の枠で充電します。自己修理キットを入れておくと、印刷したアンドロイドに持たせ、持ち歩いている間は近くの身寄りのないアンドロイドに補充します。アンドロイド用装甲板を入れておくと、印刷したアンドロイドに積みます。
- 護衛のアンドロイドが、近くに落ちている物（倒した敵の落とし物など。プレイヤーが投げた物は除く）を拾い、近くのチェストか、作業指令台・資材口へ預けるようにしました。装備のページの新しいボタンで、拾うかどうかと、預け先（チェスト優先・作業優先・預けない）を切り替えられます。

## 修正

- アンドロイドが、作業する立ち位置や水から上がる場所にアンドロイド透過ガラスの上を選ばないようにしました。飛ぶときの床や、水面のふたとしても扱いません。アンドロイド遮断ガラスの上は、アンドロイドの床として扱います。
- 水に浸かったガラスの階段で、透けるガラスの中まで水が見えていたのを直しました。水はふつうの階段と同じに見え、ガラスの中には入っていないように見えます（Sodium を入れているときは除く）。
- 宇宙を生んだ創世炉のそばを通るたびに、生成中の物が画面下に出ていたのをなくしました。進み具合は創世炉の画面で見られます。
