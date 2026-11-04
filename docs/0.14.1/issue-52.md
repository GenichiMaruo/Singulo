# 0.14.1 — Issue #52: Create/Ponderの照明互換性

対象はまずMinecraft 1.21.1 / NeoForge。報告先は[Issue #52](https://github.com/GenichiMaruo/Singulo/issues/52)。報告環境は1.20.1 Forge / Singulo 0.13.0 / Create 6.0.8で、Embeddium 0.3.31とOculus 1.8.0も含まれる。報告中のAIによる原因推定は、再現結果とは分けて扱う。

## 妥当性

- Singuloのライトマップは16×512。通常の色は先頭16行、残りはレイリーパネル用。16×16前提の正規化UVで読む外部描画は未使用行を読んで暗くなり得る。
- PonderLevelは仮想Levelだが、WrappedLevel経由で実ワールドの光エンジンを共有する。従来のRayleighLighting.levelはエンジン所有者を無条件で返し、仮想座標にある物を実ワールド側のパネル照明で再梱包し得る。
- **1.21.1 + Create 6.0.10（Sodiumなし）では、修正前の機械プレスのPonder画面は正常で、黒化そのものは未再現。** 1.20.1と同じ症状が1.21.1の全構成で起こるとは結論しない。

## 修正

1. Level自身と光エンジンの所有Levelが異なる場合は、レイリーパネル用の光値を付与しない。通常の実ワールドとRenderChunkRegionは従来どおり。
2. PonderのrenderSceneを例外安全なスコープで包み、描画とバッファのフラッシュ中だけ通常の16×16ライトマップを使用する。色は元の先頭256画素と同一。メインワールドの拡張ライトマップは残す。
3. 入れ子の描画と例外時にも、元のシェーダーテクスチャを復元する。追加したGPUテクスチャは固定16×16の1枚だけで、プレビューごとには作成しない。LightTexture.closeと一緒に解放する。
4. Ponderへの依存は任意。非導入時にCreate/Ponderクラスへ直接リンクしない。

## 検証

`tools/review/ponder.gradle`はCreate 6.0.10（Ponder 1.0.82 / Flywheel 1.0.6同梱）を固定し、実際のPonder画面を開く。借用光エンジンの除外、256画素の一致、入れ子と例外時の復元を検証し、スクリーンショットを保存する。`check_ponder_png.py`は固定画面内の台座領域に十分な明るい画素があることを確認し、黒いシーンを失敗として扱う。`-PwithSodium`でSodium 0.6系を併用する。

```sh
python3 tools/review/prepare_ponder.py
bash gradlew --no-configuration-cache -I tools/review/ponder.gradle runClient -PwithSodium
```

既存の描画CIジョブ内へこの検査を追加し、ジョブ数は増やさない。通常のレイリーパネル検査では、晴れの真昼との全256画素一致、雨の真夜中、電源切替、Sodium・Distant Horizonsの描画と再入場を維持する。

1.20.1 Forge / NeoForgeへの移植・報告構成での再現は、この1.21.1の確認後に扱う。1.21.1の予防修正だけでIssue #52を解決済みにはしない。

## ローカル確認結果

- 修正前: 1.21.1 / Create 6.0.10 / Sodiumなしの機械プレスPonderは正常表示。黒化は未再現。
- 修正後: 同構成とSodium併用構成でPonderの実画面、仮想Levelの分離、256画素の一致、入れ子・例外時の状態復元が成功。
- Create非導入の通常構成: 全641件のGameTestと正確な所属照合が成功。Python単体37件成功。
- Create非導入 / Sodium + Distant Horizons: 256色の真昼照明一致、夜・雨、電源切替が成功。4画面の比較では84,000画素について真昼と通電した雨の真夜中のRGB差がゼロ、再通電後も差がゼロ。
- 最終のPonder検査では通常ライトマップのGPU補間（縮小・拡大ともLINEAR）も成功。台座領域の明るい画素は46,651で、黒化チェックの下限5,000を上回った。検査用コードは通常配布JARへ含めない。

これらは1.21.1の検証記録。報告にある1.20.1 / Embeddium / Oculusの組み合わせの成功を意味しない。
