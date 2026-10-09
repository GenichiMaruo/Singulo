# Singulo

[日本語](docs/manual-ja.md) · [English](docs/manual-en.md) · [简体中文](docs/manual-zh-cn.md) · [繁體中文](docs/manual-zh-tw.md) · [한국어](docs/manual-ko-kr.md) · [レシピ一覧](docs/recipes/README.md)

**熱電発電から始めて、最後は回転ブラックホールを運用する工業modです。**
Minecraft 1.21.1 / NeoForge 向け。旧文明の遺構で記録や部品を回収し、工場で復元・加工して、極低温・量子・時間・特異点の技術へ進みます。物理学を題材にしたゲームで、現実の物理を完全に再現するシミュレーターではありません。

## 何ができる？

- **工場を育てる** — 温度差で発電し、素材の焼成・圧縮・電解から超伝導や量子部品の生産へ進む、5段階の技術体系。
- **大型装置を組む** — 冷却塔、粒子加速器、縮退圧縮炉などを専用部品で建造。ホロ投影機が形と不足する部品を案内します。
- **遺構を探索する** — 4種類の遺構から記録と劣化した部品を持ち帰り、復元して技術に利用。最終実験施設ではホライズン・ウォーデンに挑戦します。
- **ブラックホールを運用する** — Pリアクターの点火、燃料供給、質量・スピンの管理で発電し、副産物を回収。
- **重力と時空を使う** — 重力操作の道具、拠点を守るシールド、対応装置の時間加速、チャンクローダー、遠隔拠点間で電力・アイテム・液体をつなぐワームホール。

**Singulo** is a technology mod for Minecraft 1.21.1 / NeoForge. Build a factory, recover lost technology from guarded ruins, and progress from thermoelectric generators to a rotating black hole reactor. Late-game tools add gravity control, shields, time fields and wormhole logistics. Start with the [English manual](docs/manual-en.md).

## 遊び始めるには

Minecraft **1.21.1** と NeoForge **21.1.256以上の1.21.1対応版**が必要です。modのJARをクライアントとサーバーの `mods/` に入れます。JEIは任意で、レシピとマルチブロックの案内に対応しています。

初めてワールドに入ると **Singulo ハンドブック**を受け取ります。右クリックで開き、鋼鉄・セラミック・基礎回路から始めましょう。アイテムの説明はカーソルを合わせると表示され、Shiftで作り方と使い道を確認できます。

- [日本語の説明書](docs/manual-ja.md) — 初期手順、進行、装置、探索、危険とよくあるつまずき。
- [English manual](docs/manual-en.md) — The same player guide in English.
- [简体中文](docs/manual-zh-cn.md) / [繁體中文](docs/manual-zh-tw.md) / [한국어](docs/manual-ko-kr.md) — ゲーム内翻訳と各言語の説明書。
- [画像付きレシピ一覧](docs/recipes/README.md) / [詳細なレシピ資料](singulo_recipes.md) — 材料や製作工程を調べるときに。
- [説明と実装の照合記録](docs/documentation-audit.md) — 修正したずれと、確認に使った実装。
- [設計書](singulo_plan.md) — 開発の背景と構想。現行の遊び方は上の説明書を参照してください。

## 作者を支援する

Singulo を気に入ってもらえたら、[Ko-fi](https://ko-fi.com/graycat9) から支援できます。ゲーム内では、ハンドブックの右上の「作者を支援」から開けます。

## 開発するには

GradleはJDK 25、Minecraftのコンパイルと実行はJDK 21を使用します。`gradle.properties` の `org.gradle.java.home` は開発者環境のパスなので、自分のJDK 25の場所に合わせてください。JDK 21はGradleのツールチェーンが取得します。

```sh
./gradlew build
./gradlew runClient
./gradlew runGameTestServer
```

WindowsのPowerShellでは `./gradlew.bat` を使います。ビルドしたJARは `build/libs/` に出力されます。

| 場所 | 内容 |
| --- | --- |
| `src/main/java/` | mod本体とGameTest |
| `tools/recipes.py`, `tools/config_spec.py`, `tools/ids.py` | レシピ・設定・登録名の生成元 |
| `tools/guide.py`, `tools/descriptions.py` | ゲーム内ハンドブックとアイテム説明の生成元 |
| `tools/gen_data.py` | 検証後に `src/generated/` とレシピ画像を再生成 |
| `src/generated/` | 生成物。変更は生成元で行う |
| `docs/manual-ja.md`, `docs/manual-en.md` | 日本語・英語のプレイヤー向け説明書 |

```sh
uv run --no-project --python 3.12 --with pillow tools/gen_data.py
uv run --no-project --python 3.12 tools/check_models.py
uv run --no-project --python 3.12 tools/balance/gen_md.py
```

`gen_data.py` は生成ディレクトリを作り直します。生成物への手修正は残りません。レシピと設定はサーバー設定・データパックによって変わるため、説明書の数値は既定値です。
