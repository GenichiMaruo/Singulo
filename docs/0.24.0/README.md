# 0.24.0

開発中のバージョン。

## ブラックホール爆弾のブラックホールを、ほかの mod の実体消去で消せないようにする

ブラックホール爆弾が開く小さなブラックホール（`reactor/MicroBlackHole`、実体 `singulo:micro_black_hole_entity`）は実体なので、`/kill @e` や、実体を消す・倒す mod（範囲の実体を一掃する武器、実体の掃除 mod など）で寿命の前に消えていた。

0.24.0 から、寿命（`LIFE` = 120 tick）が尽きるまでは次のように扱う。

- `kill()`、`discard()`、`remove(KILLED / DISCARDED)` は受け入れない。寿命で閉じるときだけ自分で消す。
- ダメージを受けず（`isInvulnerableTo`）、攻撃の対象にならず、ポータルなどで次元を移らない。
- 炉心・野良ブラックホール・ウォーデンの特異点など、ほかのブラックホールにも飲み込まれない（`EventHorizonTargets.affected`）。
- `setRemoved` を直に呼ぶなど、拒めない方法で消された場合は、サーバーの tick の終わり（`RuntimeLifecycle.tick` → `MicroBlackHole.guard`）に同じ場所へ同じ年齢で開き直す。年齢は同期するデータ `AGE_OFFSET` で引き継ぎ、地平線の大きさの見た目も続きから描く。

チャンクの解放・ワールドの終了（`UNLOADED_TO_CHUNK` など）では、これまでどおり保存されずに消える。見張りの一覧はワールドを閉じたときとサーバーを止めたときに捨てる。

確認は `ExtrasGameTests.microBlackHoleResistsExternalRemoval`。

## Jade・WTHIT で現象の実体の名前を出さない

次の実体は実装の都合で実体にしているが、見た目は現象なので、Jade・WTHIT で見たときに実体の名前の表示を出さない。

- `micro_black_hole_entity`: ブラックホール爆弾の小さなブラックホール。
- `warden_singularity`: ホライズン・ウォーデンの小型の特異点。
- `infalling_block`: リアクターの炉心に吸い込まれていくブロック。
- `android_crescent`: 光剣型アンドロイドの斬撃波。
- `android_laser`: アンドロイドが床から立てる光の柱。

一覧は `SinguloEntities.hiddenFromTooltips()` にまとめ、Jade は `hideTarget`、WTHIT は `blacklist` に登録する（`compat/jade/SinguloJadePlugin`、`compat/wthit/SinguloWthitPlugin`）。

## アンドロイドのプログラム

プログラムカードに Python に似た書き方でアンドロイドの動きを書き、組立ステーションの動き方「コード」で動かす。言語・関数・守り・負荷の決まりは [programming.md](programming.md)。
