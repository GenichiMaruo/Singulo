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
