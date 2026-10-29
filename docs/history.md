# 過去の設計・レビューの要点

0.12.0以前の記録を現行仕様と混同しないため、古い全体設計・初期レシピ表・完了済みレビューをここへ集約した。0.13.0の資料は[版別の目次](0.13.0/README.md)、現在の説明は[ドキュメント案内](README.md)を参照。

## 維持する方針

- 旧文明の技術を探索し、下位の工場から量子・時間・特異点へ進む。初期設計の数値を現在の実装へ戻さない。
- 新品は白い外装と淡い発光、遺構は同系統の傷んだ意匠を使う。実在する物理概念を題材とするが、ゲーム上のバランスを優先する。
- Minecraft 1.21.1のForge対応は0.8.1まで。以降はNeoForge。1.20.1は独立したブランチでForge / NeoForgeへ対応する。
- 現在の配布JARはCurseForgeのみ。GitHub Releaseは変更履歴とダウンロード先の案内に使う。

## 版ごとの経緯

| 時期 | 引き継ぐ要点 | 記録 |
|---|---|---|
| 初期〜0.6 | 5段階の技術体系、触媒寿命、遺構探索、巨大装置の構想。初期原価は後に改訂された | [公開済みの版別変更履歴](curseforge/)、[当時の設計書](https://github.com/GenichiMaruo/Singulo/blob/9ad0eb78/singulo_plan.md) |
| 0.7 | アンドロイドの作業・在庫維持・手順カード・アップグレードを拡充。1.20.1へ移植 | [自動化の旧設計](https://github.com/GenichiMaruo/Singulo/blob/9ad0eb78/docs/design/android-automation.md)、[移植時の詳細記録](https://github.com/GenichiMaruo/Singulo/blob/9ad0eb78/docs/reviews/port-0.7.0-1.20.1-findings.md) |
| 0.8 | アンドロイドの作業連続処理・巡回・実績を改良。PRの検証と製品JARの検証を区別した | [旧設計](https://github.com/GenichiMaruo/Singulo/blob/9ad0eb78/docs/design/android-0.8.0.md)、[PR #18](https://github.com/GenichiMaruo/Singulo/pull/18) |
| 0.9 | 1.21.1のローダー方針を確定。試験と設定・説明の整合性を見直した | [旧開発メモ](https://github.com/GenichiMaruo/Singulo/blob/9ad0eb78/docs/design/0.9.0.md) |
| 0.10 | レシピ・資源・説明・GUIを広く見直した。後の再評価では実効レシピを使う | [PR #34](https://github.com/GenichiMaruo/Singulo/pull/34) |
| 0.11 | 装置移設、面ごとの搬出、使用済み部品の排出、配線ネットワーク、日光・描画連携を修正 | [PR #37](https://github.com/GenichiMaruo/Singulo/pull/37)、[過去レビューの一覧](https://github.com/GenichiMaruo/Singulo/tree/9ad0eb78/docs/reviews) |
| 0.12 | 公開前の追加レビューと1.20.1更新。詳細は版別変更履歴を基準にする | [PR #44](https://github.com/GenichiMaruo/Singulo/pull/44)、[0.12.0](curseforge/changelog-0.12.0.md) |

## 移植・検証から残す知見

- NBTを一時オブジェクトへ読むとき、レベルや設置位置があると仮定しない。
- 言語切替後の翻訳は現在の言語から取得し、古い文字列を固定キャッシュしない。
- テストごとに床・水・照明・歩行空間・距離を用意する。無関係なプレイヤー・XP・エンティティを混ぜない。
- 空間外へ移動する個体・投射物も所有した検査で回収する。後始末で次の検査の強制読み込みを解除しない。
- 見た目のブロック変化だけで成功を決めず、実際のダメージ・製品・保存データを検査する。
- PR検証、公開元コミット、実際の配布JARを照合する。成功した公開処理を重複実行しない。

0.7.0移植時には、羊毛納品と応戦後の作業復帰が一部CIで失敗し、当時は製品と試験条件の切り分けが未完了だった。これを後の成功だけで解決済みと書き換えていない。対象は古い1.20.1移植時の記録であり、現行1.21.1で同じ不具合が再現したという意味でもない。元の証拠と注意点は[当時の課題一覧](https://github.com/GenichiMaruo/Singulo/blob/9ad0eb78/issue.md)で追える。

## 保管方法

公開済み変更履歴は公開ワークフローが参照するため、`curseforge/changelog-<版>.md`を保持する。過去のレビュー原文・画像・検証ログは上記の固定コミット、PR、Git履歴で確認できる。現行作業へ古いチェック件数・レシピ値を転記しない。
