# ドキュメント案内

現在の開発は **0.13.0 / Minecraft 1.21.1 / NeoForge**。まず目的別に次を読む。過去の設計値やレビュー結果を現行仕様として使わない。

| 目的 | 読むもの |
|---|---|
| 遊び方 | [日本語](player/ja.md) · [English](player/en.md) · [简体中文](player/zh-cn.md) · [繁體中文](player/zh-tw.md) · [한국어](player/ko-kr.md) |
| 実際の材料・製作場所 | [生成されたレシピ一覧](recipes/README.md)。実効値はサーバー設定・データパックでも変わる |
| コードを変更する | [開発ガイド](development/README.md) → [テスト手順](development/testing.md) |
| 0.13.0の変更・根拠・検証 | [0.13.0資料の目次](0.13.0/README.md)。最新版は[バランス調整](0.13.0/balance-adjustment.md) |
| 残る課題と対応済み項目 | [issue.md](../issue.md) |
| 支援者の登録 | [支援者名簿](development/supporters.md) |
| 配布 | [公開手順](development/releasing.md)、[CurseForge説明](curseforge/description.md)、[登録情報](curseforge/listing.md) |
| 過去の経緯 | [履歴の要点](history.md)、[公開済み変更履歴](curseforge/) |

## 配置と維持

- `player/`: 現行のプレイヤー向け説明。機能変更時は5言語を同期する。
- `development/`: ビルド・生成・検査・運用の現行手順。
- `0.13.0/`: この版で作成した調査・修正・検証資料をすべて保存。変更前の資料と調整後の結果を区別する。
- `recipes/`: `tools/gen_data.py`から生成する一覧と画像。手で数値を修正しない。
- `curseforge/`: 公開用説明と版ごとの正式な変更履歴。公開ワークフローがファイル名を参照するため、版別ファイルは保持する。
- `history.md`: 過去の設計・レビューから引き継ぐ要点。元の詳しい記録はGit履歴で確認できる。

古い全体設計・初期レシピ表・完了済みの過去レビューは履歴へ集約した。現行仕様の説明、現在の課題、検証時点の記録を同じ文書へ追記し続けない。

Minecraft 1.20.1向けの差分と検証は[移植記録](0.13.0/port-1.20.1.md)を参照してください。
