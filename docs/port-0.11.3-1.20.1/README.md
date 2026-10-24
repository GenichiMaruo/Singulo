# Singulo 0.11.3 — Minecraft 1.20.1

Forge 47.4.26 / NeoForge 47.1.106、Java 17。1.21.1公開タグ `v0.11.3` の修正を共通ソースへ移植する。

- 0.11.2の描画スナップショットと、ワールド再入場時の照明デッドロック修正を含む。
- 警備ドックの出撃数、防具で軽減される光弾、低い飛行高度、既存の移動を保つ攻撃を反映。
- 1.20.1ではSodium 0.6.13の代わりに **Embeddium 0.3.31** のAPIを使用する。通常の圧縮頂点は光量32ビットをそのまま保持し、非圧縮頂点には独立した人工光のワードを追加する。天然光・ブロック光・人工光を分離して補間する。
- **Distant Horizons 3.3.3 / 1.20.1** には標準の16×16自然光だけを渡す。元の拡張ライトマップを変更せず、一時画像は終了時に解放する。
- 描画modは任意。サードパーティーのシェーダーパックは互換性検査の対象外。

355レシピの費用・数量・時間・電力は、公開ソースを1.20.1形式に正規化した `recipe-parity.json` で独立に照合する。

```sh
bash ./gradlew -Ploader=forge build runGameTestServer
bash ./gradlew -Ploader=neoforge build runGameTestServer
python tools/review/prepare_rayleigh.py --loader forge
bash ./gradlew -Ploader=forge --no-configuration-cache -I tools/review/rayleigh.gradle runClient -PwithEmbeddium -PwithDistantHorizons
bash ./gradlew -Ploader=forge --no-configuration-cache -I tools/review/rejoin.gradle runClient -PwithEmbeddium
```

必須GameTestは9群・529件、Mekanism連携は各ローダー2件。CIはさらに両ローダーの実クライアントGUIと、Embeddium / DHの光量・画像比較・同一クライアントで5回の再入場を必須とする。描画検査用のJavaソースは配布JARへ含めない。

成功した検証の配布JARだけを昇格し、`v0.11.3-1.20.1F` / `v0.11.3-1.20.1N` でCurseForgeへ公開する。GitHub ReleasesにJARは添付しない。
