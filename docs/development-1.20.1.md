# Minecraft 1.20.1 共通開発ブランチ

`1.20.1` で Forge 47.4.26 と NeoForge 47.1.106 の共通ソースを管理する。旧 `port/1.20.1-forge` / `port/1.20.1-neoforge` の履歴をマージした。旧ブランチは履歴参照用に残し、今後の1.20.1向けPRは `1.20.1` を対象にする。デフォルトブランチ・main・既存タグ・配布済みJARは変更しない。

## ビルドと出力

Gradleの実行JDKは21、Minecraft用ツールチェーンは17。

```sh
./gradlew -Ploader=forge build
./gradlew -Ploader=neoforge build
```

`loader` は `forge` / `neoforge` のみを受け付け、省略時は `neoforge`。未知の値はエラーにする。

| 内容 | Forge | NeoForge |
| --- | --- | --- |
| JAR | `build/forge/libs/Singulo-<version>-1.20.1F.jar` | `build/neoforge/libs/Singulo-<version>-1.20.1N.jar` |
| GameTest | `build/forge/gametest/` | `build/neoforge/gametest/` |
| GUI検査 | `build/forge/gui-review/` | `build/neoforge/gui-review/` |
| 開発用ワールド | `run/forge/` | `run/neoforge/` |

コンパイル結果・展開済みmods.toml・テスト用ワールドを別々に生成するため、ローダーを切り替える前のcleanは不要。`clean` は選択中のローダーの出力のみ削除する。以前の `run/` 直下の開発ワールドは自動移動しない。必要ならバックアップして `run/<loader>/saves/` へコピーする。

## 検証

CIは両ローダーの通常360件（6グループ）、実Mekanism構成2件、実クライアントGUI20画面とPNG在庫欄105点を検査する。成果物名にはローダーを含める。

ローカルでグループを絞る例:

```sh
./gradlew -Ploader=forge runGameTestServer -PtestNamespace=singulo_progression
./gradlew -Ploader=neoforge runGameTestServer -PtestNamespace=singulo_progression
./gradlew -Ploader=forge runGameTestServer -PwithMekanism -PtestNamespace=singulo_mekanism
```

GUI検査は同じローダーで通常の `singulo` GameTestを実行した後、次を実行する。

```sh
python3 tools/review/prepare_gui.py --loader forge
./gradlew -Ploader=forge --no-configuration-cache -I tools/review/gui.gradle runClient
python3 tools/review/check_gui_png.py --loader forge
```

NeoForgeの場合は上の `forge` をすべて `neoforge` にする。GUI・第6段階の開発用ハーネスを有効にした後に配布用JARを作る場合は、`clean build` でハーネスのクラスを除去する。CIは通常JARを先に保存する。

## 公開

新しい公開では両ローダーのCI全14ジョブが成功した同一ツリーの成果物を使用する。バージョンは共通の `mod_version`。タグの末尾で配布先ローダーを選択する。

- Forge: `v<version>-1.20.1F`
- NeoForge: `v<version>-1.20.1N`

同じコミットへ両方のタグを付けられる。タグ作成だけでは公開されない。GitHub Actionsの `Publish CurseForge Beta` を `1.20.1` で実行し、`verified_run_id` と `release_tag` を指定する。成功したPR検証、または `1.20.1` のpush・手動検証が利用可能。

公開前にタグのコミット・CIのツリー・実際のテスト対象コミット・全14ジョブ・対象ローダーの6成果物・360件の完了ログ・GUI20画面・6つのJAR内容の一致を照合する。別ローダーの成果物は昇格しない。

既存の0.8.0タグは改変・再公開しない。GitHub Releasesは変更履歴とCurseForgeリンクのみで、JARは添付しない。アップロード失敗時は予約済みdraftを残すため、送信状況を確認するまで再試行・draft削除をしない。
