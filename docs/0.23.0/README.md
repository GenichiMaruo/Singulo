# 0.23.0

開発中のバージョン。

## 地表観測拠点の周りの地面をならす（singulo:graded）

地表観測拠点は jigsaw の型紙を `beard_thin` で地面に合わせて置くが、斜面では箱の上の地面を削りきれない。型紙で何も置かないマス（八角形の建物の四隅や、外壁の外の縁の上）には元の地面が残り、建物の欠けた所に土が盛られたように見えていた。

0.23.0 から、`worldgen/structure/observation_post.json` は jigsaw を `singulo:graded`（`ruin/GradedStructure`）で包む。置いたあと（`afterPlace`）に、各チャンクで次の高さより上にある自然の地面（`#minecraft:overworld_carver_replaceables` のうち液体でないもの）を取り除く。

- 型紙の箱の中と、箱のすぐ外の1列: 床（型紙のいちばん下の段）の1段下。
- その先: 箱からの水平距離（切り上げ）1マスごとに1段高い斜面。範囲は箱から12マスまで（地形の調整のある構造物が置かれるチャンクの範囲と同じ）。

型紙のブロック・水には触れない。取り除いた列の上に土が出たら、取り除いた中でいちばん上にあった表面のブロック（草ブロック・ポドゾルなど）に戻す。それより急な山では、12マス先に段差が残る。

すでに生成されたチャンクは変わらない。生成ファイルは `tools/gen_data.py` の `graded()` から作る。確認は `RuinGameTests.gradedRuinClearsGroundAroundBuilding`。
