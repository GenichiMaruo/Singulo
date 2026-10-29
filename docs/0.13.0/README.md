# 0.13.0 開発・検証記録

この版で作成した資料と添付データは保持している。各報告の数値は対象コミット時点の値。現行の実装・検査手順は[開発ガイド](../development/README.md)と[testing.md](../development/testing.md)、未完了の確認事項は[issue.md](../../issue.md)を参照。

## 最新の変更

- **[バランス調整・再評価・自己レビュー](balance-adjustment.md)**：実装済みのレシピ、探査機、討伐条件、変更前後の比較、最終検証。
- [調整後の全装置原料表](balance-adjusted/all-machines.md)、[素材の継続需要](balance-adjusted/continuity-all.md)。CSV・JSON・グラフも同じフォルダにある。

## この版の調査記録

| 記録 | 内容 |
|---|---|
| [サーバースケーリング](server-scaling.md) | 大量注文・多数装置の問題と修正、負荷の検証 |
| [作業台の内部収納](workbench-buffer.md) | 素材のまとめ置き、完成品・返却品の保存、落下防止 |
| [自動化の追加最適化](automation-performance.md) | 経路・注文・収納・同期処理の改善と比較 |
| [テスト全体の調査](test-suite-audit.md) | 当時の全検査の棚卸し、時間短縮、検査自身の改善 |
| [バランスの初期評価](balance-baseline.md) | 調整前の資材・電力・ボス条件 |
| [素材継続性の初期評価](material-continuity.md) | 調整前の下位素材・旧設備の継承状況 |

`balance-baseline/`は変更前の集計、`balance-adjusted/`は調整後の集計。初期評価で「未修正」と記載された項目の現在の状態は、最新の調整報告と課題一覧を見る。

- [Minecraft 1.20.1移植と検証](port-1.20.1.md)
