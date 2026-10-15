# 0.7.0の1.20.1移植で得た知見（main向け）

対象のmainはMinecraft 1.21.1 / NeoForge専用であり、1.20.1の実装・依存関係・NBT互換コードをmainへマージするための報告ではない。基準はmain `933373a8f5158bf427d5ebccca33b1309ff23ad9`。記録日: 2026-10-15（日本時間）。

## 公開と検証の記録

| 項目 | NeoForge | Forge |
| --- | --- | --- |
| 移植PR | [#19](https://github.com/GenichiMaruo/Singulo/pull/19) | [#20](https://github.com/GenichiMaruo/Singulo/pull/20) |
| 検証修正PR | [#21](https://github.com/GenichiMaruo/Singulo/pull/21) | [#22](https://github.com/GenichiMaruo/Singulo/pull/22)・[#23](https://github.com/GenichiMaruo/Singulo/pull/23) |
| 最終PR検証（312件・GUI15画面） | [成功](https://github.com/GenichiMaruo/Singulo/actions/runs/38695591103) | [成功](https://github.com/GenichiMaruo/Singulo/actions/runs/38705701043) |
| マージコミット | `a672d799c1ebc3a00b80825522a4e62a0601e573` | `09cfe0aaad5102f222f2e431f5a2a2f698709893` |
| 公開ジョブ | [成功・312件再確認](https://github.com/GenichiMaruo/Singulo/actions/runs/38696469276) | [成功・検証済み成果物を昇格](https://github.com/GenichiMaruo/Singulo/actions/runs/38708612776) |
| 同一成果物のGitHub Release | [v0.7.0-1.20.1N-r1](https://github.com/GenichiMaruo/Singulo/releases/tag/v0.7.0-1.20.1N-r1) | [v0.7.0-1.20.1F-r2](https://github.com/GenichiMaruo/Singulo/releases/tag/v0.7.0-1.20.1F-r2) |
| 詳細・GUI画像 | [NeoForge検証報告](https://github.com/GenichiMaruo/Singulo/blob/a672d799c1ebc3a00b80825522a4e62a0601e573/docs/port-0.7.0-1.20.1/verification.md) | [Forge検証報告](https://github.com/GenichiMaruo/Singulo/blob/09cfe0aaad5102f222f2e431f5a2a2f698709893/docs/port-0.7.0-1.20.1/verification.md) |

NeoForge版は[CurseForge file 9188560](https://www.curseforge.com/minecraft/mc-mods/singulo/files/9188560)に掲載済み。Minecraft 1.20.1・NeoForge・Beta（releaseType 2）を確認し、CurseForge CDNから取得したJARとGitHub ReleaseのSHA-256が一致した。

Forge版も[CurseForge file 9189048](https://www.curseforge.com/minecraft/mc-mods/singulo/files/9189048)に掲載済み。Minecraft 1.20.1・Forge・Beta（releaseType 2）を確認し、CurseForge CDNから取得したJARとGitHub ReleaseのSHA-256が一致した。

GitHub ReleaseとCurseForgeには同じJARを配布した。NeoForgeは公開ジョブで再検証した成果物、Forgeは成功済みPR CIの成果物を内容照合して昇格した。ダウンロードした配布物のSHA-256:

- NeoForge `Singulo-0.7.0-1.20.1N.jar`: `b7cdf4623edfd91281604da7f76985d1da9f933966bc6791de9bd7a464b5eef4`
- Forge `Singulo-0.7.0-1.20.1F.jar`: `7a0e90f74760a754372a096d54928913090ad66b567addb43396ce45bf89109c`

mainの共通305試験に1.20.1固有の7試験を加え、各版312件を必須として実行した。両版ともJEIを有効にした実クライアントでGUI15画面、日本語・英語の854×480表示、3ページ各35個の空きインベントリ枠のPNG画素を確認した。Java 17 JAR・ローダーメタデータ・モデル・翻訳・支援者名簿も検査した。

243レシピは、バージョン間のJSON/NBT・タグ形式を正規化すると、材料・数量・出力・加工時間・消費電力・要求ティアがmainと一致する。移植で独自のコスト調整は行っていない。長時間の全ティア通しプレイ、全他Mod構成、実マルチプレイヤーの長時間負荷試験の完了を意味しない。

## mainにも役立つ検証上の知見

| 観点 | 確認したこと | mainでの扱い |
| --- | --- | --- |
| 試験の余白 | 15ブロック幅の炉の試験で、外向きのポートに接続した電源が領域外へはみ出し得た。引力や時間場は装置本体より広い | 接続装置・影響範囲まで試験領域へ収める。1.20.1では64×15×64の中央配置と炉ごとのバッチで検証した。1.21.1側は別途再現を確認してから変更する |
| 上方からの落下物 | 15ブロック高の炉試験へ砂利のドロップが入り、「ペレットだけ」の試験で外部質量も取り込んだ。外部投入なら上限を超えること自体は製品の仕様 | 水平の余白だけでは足りない。ペレット専用試験は6面を閉じ、開始前の落下物を除去。ペレット消費・質量5000以下・非崩壊・100tickの判定は維持する |
| 非同期チャンク準備 | 高速でtickを進める1.20.1のGameTestServerでは、固定setupTicksの経過だけではentity-ticking状態になった保証がなかった | 時計を進める前に実際の準備状態を確かめ、待機中はチャンク側の処理キューも進める。1.20.1固有の反射コードをコピーせず、対象版のAPI・実行順を確認する |
| 終了時の清掃 | 終了時の座標範囲だけを調べると、外へ移動した敵や投射物を取り逃がす。装置を撤去して荷物を落とすと、後の炉への外部投入になる | 生成時に所有を記録し、移動先によらず除去する。荷物を新たに落とさず装置を撤去する。領域の情報は開始時に保存し、撤去済みの構造物から終了時に再取得しない |
| 清掃の順番 | Minecraftのバッチ進行リスナーは次のバッチを同期的に開始する | 後片付けを次バッチ開始より先に済ませる。リスナー順も検証対象にする |
| 畑・歩行環境 | 空の構造物は工場の床や畑の水・照明を保証しない | 床・水・照明を明示し、明るさの更新を待って植える。2体印刷では、先に印刷した個体がステーションの上からどく足場も用意する。収穫2個の納品と2か所の植え直しという目的の判定は維持する |
| 物理更新と判定 | 投影器を壊した穴へ砂利が落ちても、防御装置の破壊が失敗したことにはならない | 装置数と実ダメージで確認する。mainは既にこの判定を行っており、移植検証中の「空気のまま」という診断を持ち込まない |
| 工場の歩行余白 | 診断では鉄2個はかまど内で完成済みだが、個体は8×8の床の外におり、注文が到達不能で取消済みだった | 16×8×16の領域と16×16の床を用意し、全相対配置を保って工場をX/Zへ4ずつ移す。模擬プレイヤーも装置の進入マスから離す。製品の経路探索・取消条件・要求数量・制限時間は変更しない |
| 経験値の合流 | 保護XPは一致していたが、通常XPの範囲内計数が不安定だった | 無関係なXPと近接プレイヤーを除外し、初速と重力を固定。バニラの合流処理は残して、保護XPと通常XPの両方の合計・保存印・寿命を検査する。飛翔中のXP回収そのものは別の試験として扱う |
| 公開直前の検証 | PRの成功後、タグから新しいワールドで再実行した際に不安定さを検出し、アップロード前に停止した | 成功した非空の必須テスト完了を要求し、公開する同じJARを検査する。成功済みの送信を再実行して重複させない |

これは1.20.1で得た証拠と対策の共有である。mainで同じ不具合を再現したという主張や、mainの試験をすべて再実行したという主張ではない。

## 1.20.1固有の修正をmainへ持ち込まない

- **位置のNBT型:** 1.20.1の`NbtUtils.writeBlockPos`はCompoundTag、1.21.1はIntArrayTagを返す。指令台の接続位置一覧の読込型を1.20.1だけ修正した。mainの正しい型をCompoundTagへ変更しない。
- **ツールチップ:** 1.20.1では言語再読込中にLevelがnullとなるカード説明を、Levelに依存しないNBT読込へ変えた。mainは既に`context.registries() == null`を検査しているため、同じnull参照の不具合が残るとは扱わない。
- **互換形式:** Java 17、Forge系API、SimpleChannel、複数形のデータディレクトリ、pack format 15、DataVersion 3465は1.20.1向け。mainのJava 21・NeoForge・データコンポーネント・1.21.1用リソース形式は維持する。
- **公開タグ:** 最初の1.20.1タグは試験失敗で送信前に停止したため変更せず残し、修正後のマージコミットをNeoForgeは`-r1`、Forgeは`-r2`付きタグで公開した。Forgeの`-r1`も送信前の試験失敗で停止している。modの版番号とJAR名は0.7.0を維持する。mainの公開タグを変更する必要はない。

## 検証時間を増やさない運用

`feature/0.8.0` の最新確認コミット `f11f1f54b5c80cd1185a7f64dfe32f343eca0025` では、GameTestの前に `build/gametest/world` を削除し、以前の世界線アンカーなどが動き続ける状態を避けている。1.20.1の両版にも同じ初期化を実装済み。任意連携の試験を専用名前空間に分ける方針も確認したが、0.8.0のMekanism機能そのものは0.7.0へ移植していない。

Forgeの全体検証では、ペレット専用試験への砂利混入に加えて採掘の納品が1件不安定になった。Forge側は、0.8.0の専用名前空間を応用し、その他254件（singulo）・作業自動化37件（singulo_automation）・炉21件（singulo_reactor）をそれぞれ新規ワールドで実行する方式へ変更した。CIは3群を並列、公開時は3群を順番に実行する。GUIはその他の群で1回だけ実行する。Gradleは各群の正確な必須件数も検査する。件数検査は、37件のログを21件の群へ渡すと明示的に失敗することも確認した。作業自動化37件はローカルの新規ワールドで成功している。通常のrunGameTestServerは3名前空間すべてが既定で有効になる。NeoForgeの公開済み版は既存の全312件一括実行の成功を証拠としている。

CIの実測は、一括実行の[9分35秒](https://github.com/GenichiMaruo/Singulo/actions/runs/38697040884)から、分離後の[3分57秒](https://github.com/GenichiMaruo/Singulo/actions/runs/38705701043)となった（最初のジョブ開始から最後のジョブ完了まで）。必須312件とGUI15画面を維持した当該実行の記録である。

原因切り分けは対象試験に絞り、修正後のCIと公開ジョブで必要な全体検証を行う。同じコードに対する追加のローカル全件実行は、未解決の懸念がなければ重ねない。

## 継続調査: AI試験の実行差異

- [ ] P2・検証継続: 毛刈り後の羊毛納品が一部実行で失敗する。
- [ ] P2・検証継続: 応戦後の納品・作業復帰が一部実行で失敗する。

両方とも[同じソースの公開前再実行](https://github.com/GenichiMaruo/Singulo/actions/runs/38706456865)で失敗した一方、[配布物の元CI](https://github.com/GenichiMaruo/Singulo/actions/runs/38705701043)では312件とGUI15画面が成功している。さらに[公開方式変更PRの通常CI](https://github.com/GenichiMaruo/Singulo/actions/runs/38708614289)でも312件・GUIが成功した。製品の問題か試験条件の問題かは未分離で、後の成功をもって解決済みとは扱わない。1.21.1で再現したという主張でもない。毛刈りでは羊の位置・剪毛状態・荷物・注文、応戦後は対象解除・作業状態・到達経路を同時に記録して切り分ける。

## 検証済み成果物の昇格

[PR #24](https://github.com/GenichiMaruo/Singulo/pull/24)でForgeの公開方式を追加し、`port/1.20.1-forge`へマージした（公開方式のマージ: `2b8b7184e3e818881b57e109f120a0c5e21efc27`）。公開JARのソースは上表の`09cfe0a`のタグを維持している。検査は省かず、成功済みCIの成果物を再ビルドせず送信する。

- 元CIの3ジョブ成功と、254+37+21件の完了ログ・GUI15画面を要求。
- PR head、CIで実際に試したマージコミット、既存公開タグのtreeが完全に一致することを確認。今回のtreeは`32005feca2fb9ae4501a0119f28420290437a881`。
- 3群が作成したJARの全エントリの内容一致を検査し、Java 17・ローダー形式も確認する。選んだ同じJARをCurseForgeとGitHubへ配布する。
- 送信前に既存タグのGitHubドラフトを作り、同じ昇格操作の再実行を送信前に止める。CurseForge送信成功後にドラフトを公開する。

成功した送信の再実行や既存タグの移動は行わない。照合スクリプトの件数・Forge用タグ制約はこの版固有なので、mainの1.21.1や0.8.0へはそのままコピーしない。

## 次回の確認

mainの試験配置を調整する場合は、接続用電源と生成個体が領域内に収まること、影響半径、終了時の所有とリスナー順を先に確認する。単に待ち時間を増やしたり、成功するまで再実行したり、期待する数量・電力・ティアを緩和して解決したと扱わない。
