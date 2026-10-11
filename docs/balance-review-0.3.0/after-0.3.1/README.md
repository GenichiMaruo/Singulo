# 変更後の再計算（0.3.1）

[analyze.py](../analyze.py) を現行の `tools/recipes.py` で実行した出力。比較は [対応記録](../RESOLUTION.md) を参照。

```sh
MPLCONFIGDIR=/tmp/singulo-mpl python docs/balance-review-0.3.0/analyze.py
```

- 入力のコミットは `calculation-results.json` の `scope_commit` と `calculation-tables.md` の冒頭に記録される。
- 殻の数・炉殻の枚数・金属の核の数・発電機の出力は、解析したソースのレシピと設定の既定値から読む。
- `time_crystals_integer` は時間結晶を1個ずつ数えた結果（モジュール1個に新品1個、運転用は別枠、モジュールの残り寿命はエキゾチック物質のバッチに回す）。`cumulative` の時間結晶は寿命を分数で合算したモデルで、実物より少なく出る。
- 0.3.0 の評価結果（親フォルダ）は `--root <b45f478 のチェックアウト> --out docs/balance-review-0.3.0` で再生成でき、元のJSONと一致する。
- matplotlib がない環境ではグラフを作らない。Pリアクターの時系列モデルは回収運転を含まない。
