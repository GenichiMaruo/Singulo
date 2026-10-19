# Singulo

[日本語](docs/manual-ja.md) · [English](docs/manual-en.md) · [简体中文](docs/manual-zh-cn.md) · [繁體中文](docs/manual-zh-tw.md) · [한국어](docs/manual-ko-kr.md) · [レシピ一覧](docs/recipes/README.md)

**熱電発電から始めて、回転ブラックホールを運用し、最後は宇宙をひとつ生み出す工業modです。**
Minecraft 1.21.1（NeoForge）と 1.20.1（Forge / NeoForge）向け。旧文明の遺構で記録や部品を回収し、工場で復元・加工して、極低温・量子・時間・特異点の技術へ進みます。物理学を題材にしたゲームで、現実の物理を完全に再現するシミュレーターではありません。

## 対応バージョン / Minecraft versions

Minecraft と mod ローダーに合うファイルを選んでください。各版の最新ファイルは、下の「ダウンロード」から開けます。
Choose the download that matches your Minecraft version and mod loader. Each "Download" link lists the newest files for that version.

| Minecraft | ローダー / Loader | Java | ソース / Source | ダウンロード / Download |
| --- | --- | --- | --- | --- |
| **1.21.1** | **NeoForge** 21.1.256+ | **21** | [1.21.1](https://github.com/GenichiMaruo/Singulo/tree/1.21.1) | [1.21.1N](https://www.curseforge.com/minecraft/mc-mods/singulo/files/all?version=1.21.1&gameVersionTypeId=6) |
| **1.20.1** | **Forge** 47.4.26+ | **17** | [1.20.1](https://github.com/GenichiMaruo/Singulo/tree/1.20.1) | [1.20.1F](https://www.curseforge.com/minecraft/mc-mods/singulo/files/all?version=1.20.1&gameVersionTypeId=1) |
| **1.20.1** | **NeoForge** 47.1.106+ | **17** | [1.20.1](https://github.com/GenichiMaruo/Singulo/tree/1.20.1) | [1.20.1N](https://www.curseforge.com/minecraft/mc-mods/singulo/files/all?version=1.20.1&gameVersionTypeId=6) |

ファイル名は `Singulo-<版>-<Minecraft><ローダー>` で、末尾は `N = NeoForge`、`F = Forge` です。[全配布ファイル / All files](https://www.curseforge.com/minecraft/mc-mods/singulo/files/all) からも選べます。配布用JARはCurseForgeからダウンロードしてください。GitHub Releasesには変更履歴と配布先へのリンクのみを掲載します。
Download mod JARs from CurseForge. GitHub Releases contain release notes and a link to CurseForge only.

## 何ができる？

- **工場を育てる** — 温度差で発電し、素材の焼成・圧縮・電解から超伝導や量子部品の生産へ進む、5段階の技術体系。
- **大型装置を組む** — 冷却塔、粒子加速器、縮退圧縮炉などを専用部品で建造。ホロ投影機が形と不足する部品を案内します。
- **遺構を探索する** — 5種類の遺構から記録と劣化した部品を持ち帰り、復元して技術に利用。研究棟・封鎖培養施設では番人、最終実験施設ではホライズン・ウォーデン、最も深い沈んだ炉心区画ではエルゴ球の大蛇に挑戦します。攻略後は、4体とも覚醒した強化版との再戦に挑めます。そして、記録の断章を集めた先には……。
- **ブラックホールを運用する** — Pリアクターの点火、燃料供給、質量・スピンの管理で発電し、副産物を回収。
- **重力と時空を使う** — 重力操作の道具、拠点を守るシールド、対応装置の時間加速、チャンクローダー、遠隔拠点間で電力・アイテム・液体をつなぐワームホール。
- **宇宙を生む** — 最終目標の「創世炉」で、ブラックホールの電力から宇宙をひとつ生み出します。生まれた銀河は夜空に昇り、炉はその後、電力から物を作り出す質量生成ができるようになります。
- **アンドロイドと戦い、仲間にする** — 遺構の印刷台から出撃する3種類のアンドロイドを倒して設計メモリを集め、組立ステーションで自分の護衛を印刷。迎撃・徘徊・追従を選び、装備や名前も付けられます。
- **アンドロイドに作業を頼む** — 作業指令台で製作・加工・採掘を注文し、手順カードで農作業や装置の操作も任せられます。在庫やレッドストーンで注文を自動化でき、流体搬送用のタンクや装飾も付けられます。
- **みんなで挑む** — ボスは挑戦者の人数に合わせて強くなり、全員の攻撃がそれぞれ通ります。報酬は戦いが始まったときの人数ぶん。

**Singulo** is a technology mod for Minecraft 1.21.1 (NeoForge) and 1.20.1 (Forge / NeoForge). Build a factory, recover lost technology from guarded ruins, and progress from thermoelectric generators to a rotating black hole reactor. Late-game tools add gravity control, shields, time fields and wormhole logistics, ruin androids can be defeated and rebuilt as your own guards, and the final goal, the Genesis Engine, uses a black hole's power to create a universe. Start with the [English manual](docs/manual-en.md).

## 遊び始めるには

0.10.0では、第2段階の発電にミュオン触媒核融合炉を追加し、極低温タービンを液体窒素の蓄電装置へ変更しています。以前のタービンを主電源にしているワールドは、核融合炉への切り替えが必要です。
In 0.10.0, the Muon-Catalyzed Fusion Reactor supplies stage 2 power, while the Cryogenic Turbine stores and recovers energy through liquid nitrogen. Existing worlds using turbines as their main power source need to switch to fusion reactors.

上の表の Minecraft とローダーが必要です（1.21.1 版なら NeoForge **21.1.256以上の1.21.1対応版**）。modのJARをクライアントとサーバーの `mods/` に入れます。JEIは任意で、レシピとマルチブロックの案内に対応しています。

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

この `1.20.1` ブランチは Forge / NeoForge の共通ソースです。Gradleの実行にはJDK 21、Minecraftのコンパイル・実行にはJDK 17を使用します。JDK 17はGradleのツールチェーンでも取得できます。

```sh
# Forge
./gradlew -Ploader=forge build
./gradlew -Ploader=forge runClient
./gradlew -Ploader=forge runGameTestServer

# NeoForge（-Ploaderを省略した場合の既定値）
./gradlew -Ploader=neoforge build
./gradlew -Ploader=neoforge runClient
./gradlew -Ploader=neoforge runGameTestServer
```

WindowsのPowerShellでは `./gradlew.bat` を使います。JARは `build/forge/libs/*F.jar` と `build/neoforge/libs/*N.jar`、テスト結果は各 `build/<loader>/gametest/` に出力します。開発用ワールドは `run/<loader>/` に分離しています。

両ローダーの依存バージョンは `gradle.properties` の `forge_version` / `neoforge_version` で管理します。共通ソース・レシピ・翻訳への変更は、両ローダーでCIを通してから統合してください。[統合構成・検証・公開手順](docs/development-1.20.1.md)も参照してください。

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
```

`gen_data.py` は生成ディレクトリを作り直します。生成物への手修正は残りません。レシピと設定はサーバー設定・データパックによって変わるため、説明書の数値は既定値です。
