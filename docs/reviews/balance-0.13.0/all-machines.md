# 全装置の原料内訳（0.13.0開発版）

評価条件は[本報告](../game-balance-0.13.0.md)を参照。初回一式＝整数バッチ、平均＝余剰を使い切る場合。
下位加工設備は既設とし、その建設費は各行へ重ねていない。完成構造とコントローラを二重加算しないこと。
水などの液体はmB、その他は個。石炭欄は材料用で、かまど燃料は別記。消耗品の残使用回数はJSONに収録。
加工時間は全工程の仕事量の合計で、並列工場での経過時間ではない。

## Tier 1（9項目）

### アーカイブ端末

`singulo:archive_terminal`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 3 | 2.5 |
| `minecraft:clay_ball` | 6 | 5 |
| `minecraft:coal` | 8 | 8 |
| `minecraft:glowstone_dust` | 1 | 1 |
| `minecraft:quartz` | 3 | 2.5 |
| `minecraft:raw_copper` | 2 | 2 |
| `minecraft:raw_iron` | 4 | 4 |
| `minecraft:redstone` | 3 | 3 |
| `minecraft:sand` | 7 | 6.5 |

初回加工電力: **0.018 MFE**。加工仕事量: **2.0417分**。別途かまど燃料: 石炭換算 **1.25個**。

### 圧縮機

`singulo:compressor`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 3 | 3 |
| `minecraft:clay_ball` | 6 | 6 |
| `minecraft:coal` | 12 | 12 |
| `minecraft:cobblestone` | 8 | 8 |
| `minecraft:oak_log` | 2 | 1.5 |
| `minecraft:quartz` | 3 | 3 |
| `minecraft:raw_copper` | 4 | 4 |
| `minecraft:raw_iron` | 10 | 10 |
| `minecraft:redstone` | 7 | 7 |
| `minecraft:sand` | 3 | 3 |

初回加工電力: **0.024 MFE**。加工仕事量: **2.8333分**。別途かまど燃料: 石炭換算 **1.75個**。

### 銅導線

`singulo:copper_wire`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 1 | 0.125 |
| `minecraft:clay_ball` | 2 | 0.25 |
| `minecraft:quartz` | 1 | 0.125 |
| `minecraft:raw_copper` | 3 | 0.375 |
| `minecraft:sand` | 1 | 0.125 |

初回加工電力: **0.004 MFE**。加工仕事量: **0.5833分**。別途かまど燃料: 石炭換算 **0.375個**。

### 電解槽

`singulo:electrolyzer`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 3 | 2.5 |
| `minecraft:clay_ball` | 6 | 5 |
| `minecraft:coal` | 8 | 8 |
| `minecraft:quartz` | 3 | 2.5 |
| `minecraft:raw_copper` | 4 | 4 |
| `minecraft:raw_iron` | 7 | 7 |
| `minecraft:redstone` | 3 | 3 |
| `minecraft:sand` | 5 | 4.5 |

初回加工電力: **0.018 MFE**。加工仕事量: **2.5417分**。別途かまど燃料: 石炭換算 **1.625個**。

### 水素燃料電池

`singulo:hydrogen_fuel_cell`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 5 | 4.5 |
| `minecraft:clay_ball` | 10 | 9 |
| `minecraft:coal` | 8 | 8 |
| `minecraft:quartz` | 5 | 4.5 |
| `minecraft:raw_copper` | 4 | 4 |
| `minecraft:raw_iron` | 4 | 4 |
| `minecraft:redstone` | 5 | 5 |
| `minecraft:sand` | 7 | 6.5 |

初回加工電力: **0.034 MFE**。加工仕事量: **2.375分**。別途かまど燃料: 石炭換算 **1.25個**。

### 焼成炉

`singulo:kiln`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 5 | 4.5 |
| `minecraft:clay_ball` | 10 | 9 |
| `minecraft:coal` | 8 | 8 |
| `minecraft:cobblestone` | 11 | 11 |
| `minecraft:quartz` | 5 | 4.5 |
| `minecraft:raw_copper` | 2 | 2 |
| `minecraft:raw_iron` | 11 | 11 |
| `minecraft:redstone` | 3 | 3 |
| `minecraft:sand` | 5 | 4.5 |

初回加工電力: **0.026 MFE**。加工仕事量: **3.7083分**。別途かまど燃料: 石炭換算 **2.375個**。

### ペルチェ冷却器

`singulo:peltier_cooler`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 3 | 2.5 |
| `minecraft:clay_ball` | 6 | 5 |
| `minecraft:coal` | 8 | 8 |
| `minecraft:quartz` | 3 | 2.5 |
| `minecraft:raw_copper` | 8 | 8 |
| `minecraft:raw_iron` | 11 | 11 |
| `minecraft:redstone` | 5 | 5 |
| `minecraft:sand` | 5 | 4.5 |

初回加工電力: **0.018 MFE**。加工仕事量: **3.875分**。別途かまど燃料: 石炭換算 **2.625個**。

### レイリー散乱パネル

`singulo:rayleigh_panel`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 1 | 0.25 |
| `minecraft:clay_ball` | 2 | 0.5 |
| `minecraft:coal` | 4 | 2 |
| `minecraft:glowstone_dust` | 2 | 1 |
| `minecraft:lapis_lazuli` | 1 | 0.5 |
| `minecraft:quartz` | 1 | 0.25 |
| `minecraft:raw_copper` | 2 | 1 |
| `minecraft:raw_iron` | 2 | 1 |
| `minecraft:redstone` | 2 | 1 |
| `minecraft:sand` | 3 | 1.25 |

初回加工電力: **0.0076 MFE**。加工仕事量: **1.1917分**。別途かまど燃料: 石炭換算 **0.75個**。

別途必要な再使用可能な設計図: `singulo:blueprint_rayleigh`。

### 熱電発電機

`singulo:thermoelectric_generator`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 3 | 3 |
| `minecraft:clay_ball` | 6 | 6 |
| `minecraft:coal` | 8 | 8 |
| `minecraft:quartz` | 3 | 3 |
| `minecraft:raw_copper` | 8 | 8 |
| `minecraft:raw_iron` | 10 | 10 |
| `minecraft:redstone` | 5 | 5 |
| `minecraft:sand` | 3 | 3 |

初回加工電力: **0.02 MFE**。加工仕事量: **3.4167分**。別途かまど燃料: 石炭換算 **2.25個**。

## Tier 2（20項目）

### 加速器コントローラ

`singulo:accelerator_controller`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 8 | 5.5469 |
| `minecraft:clay_ball` | 16 | 11.0938 |
| `minecraft:coal` | 12 | 10 |
| `minecraft:diamond` | 1 | 1 |
| `minecraft:quartz` | 8 | 5.5469 |
| `minecraft:raw_copper` | 14 | 6.125 |
| `minecraft:raw_iron` | 6 | 5 |
| `minecraft:redstone` | 14 | 8.0938 |
| `minecraft:sand` | 8 | 5.5469 |
| `singulo:degraded_control_unit` | 1 | 0.0312 |

初回加工電力: **0.2353 MFE**。加工仕事量: **5.4358分**。別途かまど燃料: 石炭換算 **2.5個**。

消費する使用回数: `singulo:control_unit` 1。

### アンドロイド納品口

`singulo:android_delivery_port`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 1 | 0.5 |
| `minecraft:clay_ball` | 2 | 1 |
| `minecraft:coal` | 8 | 8 |
| `minecraft:oak_log` | 2 | 2 |
| `minecraft:quartz` | 1 | 0.5 |
| `minecraft:raw_copper` | 2 | 2 |
| `minecraft:raw_iron` | 9 | 9 |
| `minecraft:redstone` | 3 | 3 |
| `minecraft:sand` | 1 | 0.5 |

初回加工電力: **0.0132 MFE**。加工仕事量: **2.175分**。別途かまど燃料: 石炭換算 **1.375個**。

### アンドロイドレシピ棚

`singulo:android_recipe_rack`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 1 | 1 |
| `minecraft:clay_ball` | 2 | 2 |
| `minecraft:coal` | 8 | 8 |
| `minecraft:quartz` | 1 | 1 |
| `minecraft:raw_copper` | 4 | 4 |
| `minecraft:raw_iron` | 4 | 4 |
| `minecraft:redstone` | 4 | 4 |
| `minecraft:sand` | 3 | 3 |

初回加工電力: **0.0152 MFE**。加工仕事量: **2.05分**。別途かまど燃料: 石炭換算 **1.25個**。

### アンドロイド組立ステーション

`singulo:android_station`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 9 | 6.0469 |
| `minecraft:clay_ball` | 18 | 12.0938 |
| `minecraft:coal` | 12 | 10 |
| `minecraft:quartz` | 9 | 6.0469 |
| `minecraft:raw_copper` | 16 | 8.125 |
| `minecraft:raw_iron` | 6 | 5 |
| `minecraft:redstone` | 16 | 10.0938 |
| `minecraft:sand` | 11 | 8.0469 |
| `singulo:degraded_control_unit` | 1 | 0.0312 |

初回加工電力: **0.2573 MFE**。加工仕事量: **6.3108分**。別途かまど燃料: 石炭換算 **3個**。

消費する使用回数: `singulo:control_unit` 1。

### アンドロイド資材口

`singulo:android_supply_port`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 1 | 0.5 |
| `minecraft:clay_ball` | 2 | 1 |
| `minecraft:coal` | 8 | 8 |
| `minecraft:oak_log` | 2 | 2 |
| `minecraft:quartz` | 1 | 0.5 |
| `minecraft:raw_copper` | 2 | 2 |
| `minecraft:raw_iron` | 9 | 9 |
| `minecraft:redstone` | 2 | 2 |
| `minecraft:sand` | 1 | 0.5 |

初回加工電力: **0.0132 MFE**。加工仕事量: **2.175分**。別途かまど燃料: 石炭換算 **1.375個**。

### アンドロイド作業指令台

`singulo:android_work_console`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 10 | 6.5234 |
| `minecraft:clay_ball` | 20 | 13.0469 |
| `minecraft:coal` | 12 | 9 |
| `minecraft:quartz` | 10 | 6.5234 |
| `minecraft:raw_copper` | 20 | 10.0625 |
| `minecraft:raw_iron` | 6 | 4.5 |
| `minecraft:redstone` | 20 | 12.5469 |
| `minecraft:sand` | 11 | 7.5234 |
| `singulo:degraded_control_unit` | 1 | 0.0156 |

初回加工電力: **0.2613 MFE**。加工仕事量: **6.8942分**。別途かまど燃料: 石炭換算 **3.375個**。

消費する使用回数: `singulo:control_unit` 1。

### アンドロイド作業台

`singulo:android_workbench`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 1 | 1 |
| `minecraft:clay_ball` | 2 | 2 |
| `minecraft:coal` | 8 | 8 |
| `minecraft:cobblestone` | 8 | 8 |
| `minecraft:oak_log` | 3 | 2.5 |
| `minecraft:quartz` | 1 | 1 |
| `minecraft:raw_copper` | 4 | 4 |
| `minecraft:raw_iron` | 6 | 6 |
| `minecraft:redstone` | 6 | 6 |
| `minecraft:sand` | 1 | 1 |

初回加工電力: **0.0152 MFE**。加工仕事量: **2.05分**。別途かまど燃料: 石炭換算 **1.25個**。

### 触媒反応器

`singulo:catalytic_reactor`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 8 | 5.0234 |
| `minecraft:clay_ball` | 16 | 10.0469 |
| `minecraft:coal` | 12 | 9 |
| `minecraft:quartz` | 8 | 5.0234 |
| `minecraft:raw_copper` | 14 | 4.0625 |
| `minecraft:raw_iron` | 6 | 4.5 |
| `minecraft:redstone` | 14 | 6.5469 |
| `minecraft:sand` | 10 | 7.0234 |
| `singulo:degraded_control_unit` | 1 | 0.0156 |

初回加工電力: **0.2353 MFE**。加工仕事量: **5.7692分**。別途かまど燃料: 石炭換算 **2.75個**。

消費する使用回数: `singulo:control_unit` 1。

### 冷却塔コントローラ

`singulo:cooling_tower_controller`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:blue_ice` | 2 | 2 |
| `minecraft:bone_meal` | 5 | 4.5 |
| `minecraft:clay_ball` | 10 | 9 |
| `minecraft:coal` | 16 | 16 |
| `minecraft:quartz` | 5 | 4.5 |
| `minecraft:raw_copper` | 6 | 6 |
| `minecraft:raw_iron` | 8 | 8 |
| `minecraft:redstone` | 5 | 5 |
| `minecraft:sand` | 5 | 4.5 |

初回加工電力: **0.0452 MFE**。加工仕事量: **3.3417分**。別途かまど燃料: 石炭換算 **1.75個**。

### 宇宙線ミュオン収集器

`singulo:cosmic_muon_collector`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 6 | 2.5234 |
| `minecraft:clay_ball` | 12 | 5.0469 |
| `minecraft:coal` | 16 | 13 |
| `minecraft:quartz` | 6 | 2.5234 |
| `minecraft:raw_copper` | 12 | 2.0625 |
| `minecraft:raw_iron` | 8 | 6.5 |
| `minecraft:redstone` | 10 | 2.5469 |
| `minecraft:sand` | 10 | 6.5234 |
| `singulo:degraded_control_unit` | 1 | 0.0156 |

初回加工電力: **0.2229 MFE**。加工仕事量: **5.8775分**。別途かまど燃料: 石炭換算 **3個**。

消費する使用回数: `singulo:control_unit` 1。

### 極低温冷却塔

`singulo:cryogenic_cooling_tower`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:blue_ice` | 9 | 9 |
| `minecraft:bone_meal` | 27 | 25.5 |
| `minecraft:clay_ball` | 54 | 51 |
| `minecraft:coal` | 118 | 113 |
| `minecraft:quartz` | 27 | 25.5 |
| `minecraft:raw_copper` | 20 | 20 |
| `minecraft:raw_iron` | 65 | 61 |
| `minecraft:redstone` | 5 | 5 |
| `minecraft:sand` | 31 | 29.5 |

初回加工電力: **0.276 MFE**。加工仕事量: **21.5分**。別途かまど燃料: 石炭換算 **11.125個**。

### 極低温タービン

`singulo:cryogenic_turbine`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:blue_ice` | 2 | 2 |
| `minecraft:bone_meal` | 6 | 3.0469 |
| `minecraft:clay_ball` | 12 | 6.0938 |
| `minecraft:coal` | 28 | 26 |
| `minecraft:quartz` | 6 | 3.0469 |
| `minecraft:raw_copper` | 16 | 8.125 |
| `minecraft:raw_iron` | 14 | 13 |
| `minecraft:redstone` | 10 | 4.0938 |
| `minecraft:sand` | 6 | 3.0469 |
| `singulo:degraded_control_unit` | 1 | 0.0312 |

初回加工電力: **0.2397 MFE**。加工仕事量: **7.3275分**。別途かまど燃料: 石炭換算 **3.75個**。

消費する使用回数: `singulo:control_unit` 1。

### マルチブロック搬入出ポート

`singulo:multiblock_port`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 1 | 0.25 |
| `minecraft:clay_ball` | 2 | 0.5 |
| `minecraft:quartz` | 1 | 0.25 |
| `minecraft:raw_copper` | 4 | 2 |
| `minecraft:raw_iron` | 4 | 2 |
| `minecraft:redstone` | 2 | 1 |
| `minecraft:sand` | 1 | 0.25 |

初回加工電力: **0.002 MFE**。加工仕事量: **1.375分**。別途かまど燃料: 石炭換算 **1個**。

### ミュオン触媒核融合炉

`singulo:muon_fusion_reactor`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 100 |
| `minecraft:bone_meal` | 8 | 5.3438 |
| `minecraft:clay_ball` | 16 | 10.6875 |
| `minecraft:coal` | 32 | 28 |
| `minecraft:quartz` | 8 | 5.3438 |
| `minecraft:raw_copper` | 14 | 10.25 |
| `minecraft:raw_iron` | 16 | 14 |
| `minecraft:redstone` | 15 | 9.9375 |
| `minecraft:sand` | 9 | 5.8438 |
| `singulo:degraded_control_unit` | 1 | 0.0625 |
| `singulo:observation_log` | 1 | 0.0625 |

初回加工電力: **0.5295 MFE**。加工仕事量: **9.0075分**。別途かまど燃料: 石炭換算 **3.875個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:muon_catalyst` 24,000、`singulo:observation_log` 1。

### 粒子加速器

`singulo:particle_accelerator`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 10 | 8.1641 |
| `minecraft:clay_ball` | 20 | 16.3281 |
| `minecraft:coal` | 108 | 107 |
| `minecraft:diamond` | 1 | 1 |
| `minecraft:quartz` | 10 | 8.1641 |
| `minecraft:raw_copper` | 22 | 16.4375 |
| `minecraft:raw_iron` | 54 | 53.5 |
| `minecraft:redstone` | 20 | 15.8281 |
| `minecraft:sand` | 28 | 26.1641 |
| `singulo:degraded_control_unit` | 1 | 0.1094 |
| `singulo:raw_meteoric_iron` | 5 | 5 |

初回加工電力: **0.5144 MFE**。加工仕事量: **22.4767分**。別途かまど燃料: 石炭換算 **12.375個**。

消費する使用回数: `singulo:control_unit` 2。

### 精密組立台

`singulo:precision_assembler`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 8 | 5.0234 |
| `minecraft:clay_ball` | 16 | 10.0469 |
| `minecraft:coal` | 12 | 9 |
| `minecraft:cobblestone` | 8 | 8 |
| `minecraft:oak_log` | 2 | 1.5 |
| `minecraft:quartz` | 8 | 5.0234 |
| `minecraft:raw_copper` | 14 | 4.0625 |
| `minecraft:raw_iron` | 8 | 6.5 |
| `minecraft:redstone` | 16 | 8.5469 |
| `minecraft:sand` | 8 | 5.0234 |
| `singulo:degraded_control_unit` | 1 | 0.0156 |

初回加工電力: **0.2353 MFE**。加工仕事量: **5.7692分**。別途かまど燃料: 石炭換算 **2.75個**。

消費する使用回数: `singulo:control_unit` 1。

### 封印コンテナ（磁気錠）

`singulo:sealed_container_1`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 7 | 4.0469 |
| `minecraft:clay_ball` | 14 | 8.0938 |
| `minecraft:coal` | 20 | 18 |
| `minecraft:quartz` | 7 | 4.0469 |
| `minecraft:raw_copper` | 12 | 4.125 |
| `minecraft:raw_iron` | 10 | 9 |
| `minecraft:redstone` | 10 | 4.0938 |
| `minecraft:sand` | 7 | 4.0469 |
| `singulo:degraded_control_unit` | 1 | 0.0312 |

初回加工電力: **0.2725 MFE**。加工仕事量: **5.9442分**。別途かまど燃料: 石炭換算 **2.75個**。

消費する使用回数: `singulo:control_unit` 1。

### SMESセル

`singulo:smes_cell`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 6 | 4.0938 |
| `minecraft:clay_ball` | 12 | 8.1875 |
| `minecraft:coal` | 20 | 20 |
| `minecraft:quartz` | 6 | 4.0938 |
| `minecraft:raw_copper` | 12 | 8.25 |
| `minecraft:raw_iron` | 10 | 10 |
| `minecraft:redstone` | 10 | 7.1875 |
| `minecraft:sand` | 6 | 4.0938 |
| `singulo:degraded_control_unit` | 1 | 0.0625 |

初回加工電力: **0.2285 MFE**。加工仕事量: **5.6942分**。別途かまど燃料: 石炭換算 **2.75個**。

消費する使用回数: `singulo:control_unit` 1。

### 超伝導ケーブル

`singulo:superconducting_cable`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 5 | 0.2936 |
| `minecraft:clay_ball` | 10 | 0.5872 |
| `minecraft:coal` | 4 | 0.0833 |
| `minecraft:quartz` | 5 | 0.2936 |
| `minecraft:raw_copper` | 12 | 0.1719 |
| `minecraft:raw_iron` | 2 | 0.0417 |
| `minecraft:redstone` | 9 | 0.1289 |
| `minecraft:sand` | 5 | 0.2936 |
| `singulo:degraded_control_unit` | 1 | 0.0013 |

初回加工電力: **0.2339 MFE**。加工仕事量: **3.93分**。別途かまど燃料: 石炭換算 **1.75個**。

消費する使用回数: `singulo:control_unit` 1。

### ワールドライン・アンカー（小）

`singulo:worldline_anchor_small`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 7 | 4.0234 |
| `minecraft:clay_ball` | 14 | 8.0469 |
| `minecraft:coal` | 12 | 9 |
| `minecraft:ender_pearl` | 2 | 2 |
| `minecraft:quartz` | 7 | 4.0234 |
| `minecraft:raw_copper` | 14 | 4.0625 |
| `minecraft:raw_iron` | 6 | 4.5 |
| `minecraft:redstone` | 12 | 4.5469 |
| `minecraft:sand` | 7 | 4.0234 |
| `singulo:degraded_control_unit` | 1 | 0.0156 |

初回加工電力: **0.2233 MFE**。加工仕事量: **5.1858分**。別途かまど燃料: 石炭換算 **2.5個**。

消費する使用回数: `singulo:control_unit` 1。

## Tier 3（18項目）

### 改良型アンドロイド作業台

`singulo:advanced_android_workbench`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 100 |
| `minecraft:amethyst_shard` | 4 | 4 |
| `minecraft:bone_meal` | 7 | 4.6458 |
| `minecraft:clay_ball` | 14 | 9.2917 |
| `minecraft:coal` | 28 | 20.6667 |
| `minecraft:cobblestone` | 8 | 8 |
| `minecraft:diamond` | 2 | 2 |
| `minecraft:oak_log` | 3 | 2.5 |
| `minecraft:quartz` | 11 | 8.6458 |
| `minecraft:raw_copper` | 20 | 13.5 |
| `minecraft:raw_iron` | 16 | 12.3333 |
| `minecraft:redstone` | 22 | 16.375 |
| `minecraft:sand` | 8 | 4.8958 |
| `singulo:degraded_control_unit` | 1 | 0.0417 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.125 |

初回加工電力: **5.257 MFE**。加工仕事量: **11.225分**。別途かまど燃料: 石炭換算 **4.625個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 2、`singulo:muon_catalyst` 24,000、`singulo:observation_log` 1。

### 冷却塔拡張（高さ10）

`singulo:cooling_tower_extension`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:blue_ice` | 5 | 5 |
| `minecraft:bone_meal` | 2 | 2 |
| `minecraft:clay_ball` | 4 | 4 |
| `minecraft:coal` | 24 | 24 |
| `minecraft:quartz` | 2 | 2 |
| `minecraft:raw_copper` | 10 | 10 |
| `minecraft:raw_iron` | 12 | 12 |
| `minecraft:sand` | 10 | 10 |

初回加工電力: **0.0416 MFE**。加工仕事量: **6.0667分**。別途かまど燃料: 石炭換算 **3.75個**。

### 深海圧力収集器

`singulo:deep_sea_collector`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 100 |
| `minecraft:bone_meal` | 4 | 3.25 |
| `minecraft:clay_ball` | 8 | 6.5 |
| `minecraft:coal` | 20 | 20 |
| `minecraft:cobblestone` | 8 | 8 |
| `minecraft:diamond` | 1 | 1 |
| `minecraft:oak_log` | 2 | 1.5 |
| `minecraft:quartz` | 4 | 3.25 |
| `minecraft:raw_copper` | 2 | 2 |
| `minecraft:raw_iron` | 12 | 12 |
| `minecraft:redstone` | 8 | 5.75 |
| `minecraft:sand` | 6 | 5.25 |
| `singulo:observation_log` | 1 | 0.0625 |

初回加工電力: **0.2314 MFE**。加工仕事量: **4.6083分**。別途かまど燃料: 石炭換算 **2個**。

消費する使用回数: `singulo:muon_catalyst` 24,000、`singulo:observation_log` 1。

### 残響共鳴器

`singulo:echo_resonator`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 100 |
| `minecraft:amethyst_shard` | 6 | 6 |
| `minecraft:bone_meal` | 8 | 4.1979 |
| `minecraft:clay_ball` | 16 | 8.3958 |
| `minecraft:coal` | 12 | 9.3333 |
| `minecraft:diamond` | 1 | 1 |
| `minecraft:quartz` | 10 | 6.1979 |
| `minecraft:raw_copper` | 14 | 4.75 |
| `minecraft:raw_iron` | 6 | 4.6667 |
| `minecraft:redstone` | 15 | 6.5625 |
| `minecraft:sand` | 8 | 4.1979 |
| `minecraft:sculk_sensor` | 1 | 1 |
| `singulo:degraded_control_unit` | 1 | 0.0208 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.0625 |
| `singulo:sentinel_core` | 1 | 1 |

初回加工電力: **2.7782 MFE**。加工仕事量: **6.8333分**。別途かまど燃料: 石炭換算 **2.5個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 1、`singulo:muon_catalyst` 24,000、`singulo:observation_log` 1。

### 量子もつれ合成器

`singulo:entanglement_synthesizer`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 100 |
| `minecraft:bone_meal` | 10 | 6.2969 |
| `minecraft:clay_ball` | 20 | 12.5938 |
| `minecraft:coal` | 12 | 10 |
| `minecraft:diamond` | 1 | 1 |
| `minecraft:quartz` | 10 | 6.2969 |
| `minecraft:raw_copper` | 14 | 6.125 |
| `minecraft:raw_iron` | 6 | 5 |
| `minecraft:redstone` | 17 | 8.8438 |
| `minecraft:sand` | 14 | 10.2969 |
| `singulo:degraded_control_unit` | 1 | 0.0312 |
| `singulo:observation_log` | 1 | 0.0625 |

初回加工電力: **0.3682 MFE**。加工仕事量: **7.1分**。別途かまど燃料: 石炭換算 **3個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:muon_catalyst` 24,000、`singulo:observation_log` 1。

### 重力波検出器

`singulo:gravitational_wave_detector`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 100 |
| `minecraft:amethyst_shard` | 4 | 4 |
| `minecraft:bone_meal` | 8 | 4.599 |
| `minecraft:clay_ball` | 16 | 9.1979 |
| `minecraft:coal` | 20 | 10.6667 |
| `minecraft:diamond` | 4 | 4 |
| `minecraft:quartz` | 12 | 8.599 |
| `minecraft:raw_copper` | 16 | 5.375 |
| `minecraft:raw_iron` | 14 | 9.3333 |
| `minecraft:redstone` | 18 | 9.2812 |
| `minecraft:sand` | 9 | 4.849 |
| `singulo:degraded_control_unit` | 1 | 0.0104 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.125 |
| `singulo:raw_meteoric_iron` | 2 | 2 |

初回加工電力: **5.2446 MFE**。加工仕事量: **10.025分**。別途かまど燃料: 石炭換算 **4.125個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 2、`singulo:muon_catalyst` 24,000、`singulo:observation_log` 1。

### パネル受電器

`singulo:gravity_panel_receiver`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 6 | 3.5469 |
| `minecraft:clay_ball` | 12 | 7.0938 |
| `minecraft:coal` | 16 | 14 |
| `minecraft:quartz` | 6 | 3.5469 |
| `minecraft:raw_copper` | 14 | 6.125 |
| `minecraft:raw_iron` | 8 | 7 |
| `minecraft:redstone` | 12 | 6.0938 |
| `minecraft:sand` | 6 | 3.5469 |
| `singulo:degraded_control_unit` | 1 | 0.0312 |

初回加工電力: **0.1982 MFE**。加工仕事量: **5.575分**。別途かまど燃料: 石炭換算 **2.75個**。

消費する使用回数: `singulo:control_unit` 1。

### 高重力パネル

`singulo:high_gravity_panel`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 4 | 0.1309 |
| `minecraft:clay_ball` | 8 | 0.2617 |
| `minecraft:coal` | 16 | 1.75 |
| `minecraft:obsidian` | 3 | 0.625 |
| `minecraft:quartz` | 4 | 0.1309 |
| `minecraft:raw_copper` | 12 | 0.5156 |
| `minecraft:raw_iron` | 6 | 0.625 |
| `minecraft:redstone` | 9 | 0.3867 |
| `minecraft:sand` | 4 | 0.1309 |
| `singulo:degraded_control_unit` | 1 | 0.0039 |

初回加工電力: **0.1838 MFE**。加工仕事量: **4.6667分**。別途かまど燃料: 石炭換算 **2.25個**。

消費する使用回数: `singulo:control_unit` 1。

### 衝撃発電パネル

`singulo:impact_generator_panel`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 6 | 3.5469 |
| `minecraft:clay_ball` | 12 | 7.0938 |
| `minecraft:coal` | 16 | 14 |
| `minecraft:cobblestone` | 8 | 8 |
| `minecraft:oak_log` | 2 | 1.5 |
| `minecraft:quartz` | 6 | 3.5469 |
| `minecraft:raw_copper` | 16 | 8.125 |
| `minecraft:raw_iron` | 12 | 11 |
| `minecraft:redstone` | 15 | 9.0938 |
| `minecraft:sand` | 6 | 3.5469 |
| `minecraft:slime_ball` | 4 | 4 |
| `singulo:degraded_control_unit` | 1 | 0.0312 |

初回加工電力: **0.2102 MFE**。加工仕事量: **6.675分**。別途かまど燃料: 石炭換算 **3.5個**。

消費する使用回数: `singulo:control_unit` 1。

### 慣性スタビライザー

`singulo:inertial_stabilizer`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:amethyst_shard` | 2 | 2 |
| `minecraft:bone_meal` | 7 | 3.4714 |
| `minecraft:clay_ball` | 14 | 6.9427 |
| `minecraft:coal` | 12 | 10.3333 |
| `minecraft:diamond` | 1 | 1 |
| `minecraft:obsidian` | 4 | 4 |
| `minecraft:quartz` | 9 | 5.4714 |
| `minecraft:raw_copper` | 12 | 4.8125 |
| `minecraft:raw_iron` | 6 | 5.1667 |
| `minecraft:redstone` | 13 | 5.3594 |
| `minecraft:sand` | 7 | 3.4714 |
| `singulo:degraded_control_unit` | 1 | 0.0365 |
| `singulo:quantum_data_fragment` | 1 | 0.0625 |

初回加工電力: **2.6326 MFE**。加工仕事量: **5.6833分**。別途かまど燃料: 石炭換算 **2.25個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 1。

### ケラウノス放電塔

`singulo:keraunos_tower`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 100 |
| `minecraft:bone_meal` | 8 | 5.8438 |
| `minecraft:clay_ball` | 16 | 11.6875 |
| `minecraft:coal` | 16 | 16 |
| `minecraft:quartz` | 8 | 5.8438 |
| `minecraft:raw_copper` | 23 | 19.25 |
| `minecraft:raw_iron` | 8 | 8 |
| `minecraft:redstone` | 17 | 11.9375 |
| `minecraft:sand` | 8 | 5.8438 |
| `singulo:degraded_control_unit` | 1 | 0.0625 |
| `singulo:observation_log` | 1 | 0.0625 |

初回加工電力: **0.3638 MFE**。加工仕事量: **8.1583分**。別途かまど燃料: 石炭換算 **3.875個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:muon_catalyst` 24,000、`singulo:observation_log` 1。

### レーザー冷却器

`singulo:laser_cooler`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 100 |
| `minecraft:bone_meal` | 10 | 5.7734 |
| `minecraft:clay_ball` | 20 | 11.5469 |
| `minecraft:coal` | 12 | 9 |
| `minecraft:diamond` | 2 | 2 |
| `minecraft:glowstone_dust` | 4 | 4 |
| `minecraft:quartz` | 10 | 5.7734 |
| `minecraft:raw_copper` | 14 | 4.0625 |
| `minecraft:raw_iron` | 6 | 4.5 |
| `minecraft:redstone` | 17 | 7.2969 |
| `minecraft:sand` | 10 | 5.7734 |
| `singulo:degraded_control_unit` | 1 | 0.0156 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:sentinel_core` | 1 | 1 |

初回加工電力: **0.3722 MFE**。加工仕事量: **6.4667分**。別途かまど燃料: 石炭換算 **2.5個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:muon_catalyst` 24,000、`singulo:observation_log` 1。

### 低重力パネル

`singulo:low_gravity_panel`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 6 | 0.3809 |
| `minecraft:clay_ball` | 12 | 0.7617 |
| `minecraft:coal` | 6 | 0.5 |
| `minecraft:phantom_membrane` | 2 | 0.5 |
| `minecraft:quartz` | 6 | 0.3809 |
| `minecraft:raw_copper` | 12 | 0.5156 |
| `minecraft:raw_iron` | 3 | 0.25 |
| `minecraft:redstone` | 9 | 0.3867 |
| `minecraft:sand` | 6 | 0.3809 |
| `singulo:degraded_control_unit` | 1 | 0.0039 |

初回加工電力: **0.1834 MFE**。加工仕事量: **4.1083分**。別途かまど燃料: 石炭換算 **1.875個**。

消費する使用回数: `singulo:control_unit` 1。

### ニュートリノ観測所

`singulo:neutrino_observatory`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 100 |
| `minecraft:amethyst_shard` | 6 | 6 |
| `minecraft:blaze_rod` | 1 | 0.5 |
| `minecraft:bone_meal` | 8 | 6.5938 |
| `minecraft:clay_ball` | 16 | 13.1875 |
| `minecraft:coal` | 28 | 22 |
| `minecraft:diamond` | 3 | 3 |
| `minecraft:ender_pearl` | 1 | 1 |
| `minecraft:quartz` | 14 | 12.5938 |
| `minecraft:raw_copper` | 16 | 12.25 |
| `minecraft:raw_iron` | 14 | 11 |
| `minecraft:redstone` | 17 | 14.1875 |
| `minecraft:sand` | 9 | 6.8438 |
| `singulo:degraded_control_unit` | 1 | 0.0625 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.1875 |

初回加工電力: **7.6818 MFE**。加工仕事量: **10.975分**。別途かまど燃料: 石炭換算 **3.875個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 3、`singulo:muon_catalyst` 24,000、`singulo:observation_log` 1。

### 量子熱機関

`singulo:quantum_heat_engine`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 100 |
| `minecraft:amethyst_shard` | 4 | 4 |
| `minecraft:bone_meal` | 10 | 6.6927 |
| `minecraft:clay_ball` | 20 | 13.3854 |
| `minecraft:coal` | 32 | 22.6667 |
| `minecraft:diamond` | 2 | 2 |
| `minecraft:quartz` | 14 | 10.6927 |
| `minecraft:raw_copper` | 24 | 13.625 |
| `minecraft:raw_iron` | 16 | 11.3333 |
| `minecraft:redstone` | 23 | 14.4688 |
| `minecraft:sand` | 11 | 6.9427 |
| `singulo:degraded_control_unit` | 1 | 0.0729 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.125 |

初回加工電力: **5.3944 MFE**。加工仕事量: **12.5833分**。別途かまど燃料: 石炭換算 **5.125個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 2、`singulo:muon_catalyst` 24,000、`singulo:observation_log` 1。

### 封印コンテナ（量子錠）

`singulo:sealed_container_2`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:amethyst_shard` | 4 | 2.5 |
| `minecraft:bone_meal` | 8 | 4.5775 |
| `minecraft:clay_ball` | 16 | 9.1549 |
| `minecraft:coal` | 20 | 18.4167 |
| `minecraft:diamond` | 2 | 1.25 |
| `minecraft:quartz` | 13 | 7.5775 |
| `minecraft:raw_copper` | 12 | 4.9844 |
| `minecraft:raw_iron` | 10 | 9.2083 |
| `minecraft:redstone` | 13 | 5.6758 |
| `minecraft:sand` | 8 | 4.5775 |
| `singulo:degraded_control_unit` | 1 | 0.0378 |
| `singulo:fulgurite` | 1 | 0.5 |
| `singulo:quantum_data_fragment` | 1 | 0.0781 |

初回加工電力: **7.1578 MFE**。加工仕事量: **7.95分**。別途かまど燃料: 石炭換算 **2.75個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 2。

### 無重力ブロック

`singulo:zero_gravity_block`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 6 | 0.5117 |
| `minecraft:clay_ball` | 12 | 1.0234 |
| `minecraft:coal` | 6 | 0.75 |
| `minecraft:ender_pearl` | 2 | 0.5 |
| `minecraft:quartz` | 6 | 0.5117 |
| `minecraft:raw_copper` | 12 | 1.0312 |
| `minecraft:raw_iron` | 3 | 0.375 |
| `minecraft:redstone` | 9 | 0.7734 |
| `minecraft:sand` | 6 | 0.5117 |
| `singulo:degraded_control_unit` | 1 | 0.0078 |

初回加工電力: **0.1834 MFE**。加工仕事量: **4.1083分**。別途かまど燃料: 石炭換算 **1.875個**。

消費する使用回数: `singulo:control_unit` 1。

### 無重力コントロールボックス

`singulo:zero_gravity_controller`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 7 | 3.5234 |
| `minecraft:clay_ball` | 14 | 7.0469 |
| `minecraft:coal` | 12 | 9 |
| `minecraft:ender_pearl` | 1 | 1 |
| `minecraft:quartz` | 7 | 3.5234 |
| `minecraft:raw_copper` | 16 | 6.0625 |
| `minecraft:raw_iron` | 6 | 4.5 |
| `minecraft:redstone` | 14 | 6.5469 |
| `minecraft:sand` | 7 | 3.5234 |
| `singulo:degraded_control_unit` | 1 | 0.0156 |

初回加工電力: **0.1946 MFE**。加工仕事量: **5.4667分**。別途かまど燃料: 石炭換算 **2.75個**。

消費する使用回数: `singulo:control_unit` 1。

## Tier 4（13項目）

### C空洞

`singulo:casimir_cavity`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,100 |
| `minecraft:amethyst_shard` | 6 | 5 |
| `minecraft:bone_meal` | 16 | 12.8372 |
| `minecraft:clay_ball` | 32 | 25.6745 |
| `minecraft:coal` | 194 | 179.4167 |
| `minecraft:cobblestone` | 32 | 32 |
| `minecraft:deepslate` | 80 | 38.5 |
| `minecraft:diamond` | 6 | 4.5625 |
| `minecraft:oak_log` | 6 | 6 |
| `minecraft:quartz` | 72 | 67.8372 |
| `minecraft:raw_copper` | 80 | 69.3438 |
| `minecraft:raw_iron` | 105 | 97.7083 |
| `minecraft:redstone` | 49 | 40.6328 |
| `minecraft:sand` | 45 | 40.8372 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0625 |
| `singulo:degraded_control_unit` | 1 | 0.2526 |
| `singulo:fulgurite` | 1 | 1 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.1562 |
| `singulo:raw_meteoric_iron` | 4 | 4 |
| `singulo:stardust` | 2 | 2 |

初回加工電力: **22.1254 MFE**。加工仕事量: **54.675分**。別途かまど燃料: 石炭換算 **23.25個**。

消費する使用回数: `singulo:control_unit` 5、`singulo:quantum_data_fragment` 3、`singulo:bose_condensate_catalyst` 36,000、`singulo:cold_atom_trap` 2、`singulo:observation_log` 1。

### C空洞コントローラ

`singulo:casimir_cavity_controller`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,100 |
| `minecraft:amethyst_shard` | 6 | 5 |
| `minecraft:bone_meal` | 10 | 6.556 |
| `minecraft:clay_ball` | 20 | 13.112 |
| `minecraft:coal` | 28 | 14.4167 |
| `minecraft:deepslate` | 80 | 38.5 |
| `minecraft:diamond` | 6 | 4.5625 |
| `minecraft:quartz` | 19 | 14.556 |
| `minecraft:raw_copper` | 56 | 44.5938 |
| `minecraft:raw_iron` | 14 | 7.2083 |
| `minecraft:redstone` | 23 | 14.0703 |
| `minecraft:sand` | 11 | 6.806 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0625 |
| `singulo:degraded_control_unit` | 1 | 0.0651 |
| `singulo:fulgurite` | 1 | 1 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.1562 |
| `singulo:stardust` | 2 | 2 |

初回加工電力: **21.08 MFE**。加工仕事量: **18.6083分**。別途かまど燃料: 石炭換算 **4.875個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 3、`singulo:bose_condensate_catalyst` 36,000、`singulo:cold_atom_trap` 2、`singulo:observation_log` 1。

### 縮退圧縮炉

`singulo:degenerate_compactor`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,100 |
| `minecraft:amethyst_shard` | 6 | 5 |
| `minecraft:bone_meal` | 10 | 8.1263 |
| `minecraft:clay_ball` | 20 | 16.2526 |
| `minecraft:coal` | 154 | 142.4167 |
| `minecraft:cobblestone` | 8 | 8 |
| `minecraft:deepslate` | 80 | 38.5 |
| `minecraft:diamond` | 4 | 2.5625 |
| `minecraft:oak_log` | 2 | 1.5 |
| `minecraft:obsidian` | 22 | 21.75 |
| `minecraft:quartz` | 19 | 16.1263 |
| `minecraft:raw_copper` | 59 | 53.7812 |
| `minecraft:raw_iron` | 79 | 73.2083 |
| `minecraft:redstone` | 25 | 20.7109 |
| `minecraft:sand` | 15 | 12.3763 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0625 |
| `singulo:degraded_control_unit` | 1 | 0.112 |
| `singulo:fulgurite` | 1 | 1 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:pressure_crystal` | 2 | 2 |
| `singulo:quantum_data_fragment` | 1 | 0.1562 |
| `singulo:stardust` | 2 | 2 |

初回加工電力: **21.5264 MFE**。加工仕事量: **37.5833分**。別途かまど燃料: 石炭換算 **13.875個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 3、`singulo:bose_condensate_catalyst` 36,000、`singulo:cold_atom_trap` 2、`singulo:observation_log` 1。

### 縮退圧縮炉コントローラ

`singulo:degenerate_compactor_controller`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,100 |
| `minecraft:amethyst_shard` | 6 | 5 |
| `minecraft:bone_meal` | 10 | 7.6029 |
| `minecraft:clay_ball` | 20 | 15.2057 |
| `minecraft:coal` | 28 | 16.4167 |
| `minecraft:deepslate` | 80 | 38.5 |
| `minecraft:diamond` | 4 | 2.5625 |
| `minecraft:quartz` | 19 | 15.6029 |
| `minecraft:raw_copper` | 56 | 48.7188 |
| `minecraft:raw_iron` | 14 | 8.2083 |
| `minecraft:redstone` | 23 | 17.1641 |
| `minecraft:sand` | 11 | 7.8529 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0625 |
| `singulo:degraded_control_unit` | 1 | 0.0964 |
| `singulo:fulgurite` | 1 | 1 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.1562 |
| `singulo:stardust` | 2 | 2 |

初回加工電力: **21.08 MFE**。加工仕事量: **18.6083分**。別途かまど燃料: 石炭換算 **4.875個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 3、`singulo:bose_condensate_catalyst` 36,000、`singulo:cold_atom_trap` 2、`singulo:observation_log` 1。

### 縮退熱炉

`singulo:degenerate_furnace`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,100 |
| `minecraft:amethyst_shard` | 12 | 12 |
| `minecraft:bone_meal` | 14 | 10.4609 |
| `minecraft:clay_ball` | 28 | 20.9219 |
| `minecraft:coal` | 194 | 182.25 |
| `minecraft:cobblestone` | 16 | 16 |
| `minecraft:deepslate` | 80 | 38.5 |
| `minecraft:diamond` | 7 | 6.0625 |
| `minecraft:oak_log` | 3 | 3 |
| `minecraft:obsidian` | 23 | 22.75 |
| `minecraft:quartz` | 30 | 26.4609 |
| `minecraft:raw_copper` | 93 | 83.5625 |
| `minecraft:raw_iron` | 101 | 95.125 |
| `minecraft:redstone` | 38 | 30.1719 |
| `minecraft:sand` | 27 | 22.9609 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0625 |
| `singulo:degraded_control_unit` | 1 | 0.1406 |
| `singulo:fulgurite` | 2 | 2 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.375 |
| `singulo:stardust` | 2 | 2 |

初回加工電力: **30.7886 MFE**。加工仕事量: **54.5333分**。別途かまど燃料: 石炭換算 **21.875個**。

消費する使用回数: `singulo:control_unit` 3、`singulo:quantum_data_fragment` 6、`singulo:bose_condensate_catalyst` 36,000、`singulo:cold_atom_trap` 2、`singulo:observation_log` 1。

### 縮退熱炉コントローラ

`singulo:degenerate_furnace_controller`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,100 |
| `minecraft:amethyst_shard` | 10 | 10 |
| `minecraft:bone_meal` | 12 | 9.1641 |
| `minecraft:clay_ball` | 24 | 18.3281 |
| `minecraft:coal` | 28 | 19.25 |
| `minecraft:deepslate` | 80 | 38.5 |
| `minecraft:diamond` | 6 | 5.0625 |
| `minecraft:quartz` | 26 | 23.1641 |
| `minecraft:raw_copper` | 58 | 52.4375 |
| `minecraft:raw_iron` | 14 | 9.625 |
| `minecraft:redstone` | 28 | 22.3281 |
| `minecraft:sand` | 13 | 9.6641 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0625 |
| `singulo:degraded_control_unit` | 1 | 0.1094 |
| `singulo:fulgurite` | 2 | 2 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.3125 |
| `singulo:stardust` | 2 | 2 |

初回加工電力: **27.5572 MFE**。加工仕事量: **21.05分**。別途かまど燃料: 石炭換算 **5.125個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 5、`singulo:bose_condensate_catalyst` 36,000、`singulo:cold_atom_trap` 2、`singulo:observation_log` 1。

### 自動探査機ステーション

`singulo:probe_station`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,100 |
| `minecraft:amethyst_shard` | 6 | 4.5 |
| `minecraft:bone_meal` | 10 | 6.4499 |
| `minecraft:clay_ball` | 20 | 12.8997 |
| `minecraft:coal` | 28 | 14.3333 |
| `minecraft:deepslate` | 80 | 38.5 |
| `minecraft:diamond` | 4 | 2.3125 |
| `minecraft:phantom_membrane` | 4 | 4 |
| `minecraft:quartz` | 19 | 13.4499 |
| `minecraft:raw_copper` | 56 | 44.4219 |
| `minecraft:raw_iron` | 14 | 7.1667 |
| `minecraft:redstone` | 23 | 13.7539 |
| `minecraft:sand` | 11 | 6.6999 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0625 |
| `singulo:degraded_control_unit` | 1 | 0.0638 |
| `singulo:fulgurite` | 1 | 0.5 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.1406 |
| `singulo:sentinel_core` | 1 | 1 |
| `singulo:stardust` | 2 | 2 |

初回加工電力: **21.082 MFE**。加工仕事量: **18.625分**。別途かまど燃料: 石炭換算 **4.875個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 3、`singulo:bose_condensate_catalyst` 36,000、`singulo:cold_atom_trap` 2、`singulo:observation_log` 1。

### 量子アンドロイド作業台

`singulo:quantum_android_workbench`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 200 |
| `minecraft:amethyst_shard` | 10 | 9 |
| `minecraft:bone_meal` | 12 | 8.8529 |
| `minecraft:clay_ball` | 24 | 17.7057 |
| `minecraft:coal` | 36 | 26.4167 |
| `minecraft:cobblestone` | 8 | 8 |
| `minecraft:deepslate` | 80 | 38.5 |
| `minecraft:diamond` | 6 | 4.5625 |
| `minecraft:oak_log` | 3 | 2.5 |
| `minecraft:quartz` | 25 | 20.8529 |
| `minecraft:raw_copper` | 32 | 24.7188 |
| `minecraft:raw_iron` | 20 | 15.2083 |
| `minecraft:redstone` | 35 | 28.4141 |
| `minecraft:sand` | 13 | 9.3529 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0625 |
| `singulo:degraded_control_unit` | 1 | 0.0964 |
| `singulo:fulgurite` | 1 | 1 |
| `singulo:observation_log` | 1 | 0.125 |
| `singulo:quantum_data_fragment` | 1 | 0.2812 |
| `singulo:stardust` | 2 | 2 |

初回加工電力: **24.9544 MFE**。加工仕事量: **20.75分**。別途かまど燃料: 石炭換算 **6.625個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 5、`singulo:muon_catalyst` 24,000、`singulo:observation_log` 2、`singulo:bose_condensate_catalyst` 36,000、`singulo:cold_atom_trap` 2。

### 封印コンテナ（時間錠）

`singulo:sealed_container_3`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 0.625 |
| `minecraft:amethyst_shard` | 18 | 3.2785 |
| `minecraft:bone_meal` | 14 | 4.8435 |
| `minecraft:clay_ball` | 28 | 9.687 |
| `minecraft:coal` | 36 | 18.8002 |
| `minecraft:cobblestone` | 4,860 | 4,860 |
| `minecraft:deepslate` | 160 | 0.2663 |
| `minecraft:diamond` | 7 | 1.6328 |
| `minecraft:echo_shard` | 1 | 0.0056 |
| `minecraft:ender_pearl` | 8 | 1 |
| `minecraft:nether_star` | 1 | 0.125 |
| `minecraft:quartz` | 34 | 8.8761 |
| `minecraft:raw_copper` | 26 | 5.5183 |
| `minecraft:raw_iron` | 261 | 252.4001 |
| `minecraft:redstone` | 31 | 6.527 |
| `minecraft:sand` | 15 | 4.8747 |
| `singulo:culture_data` | 1 | 0.0078 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0005 |
| `singulo:degraded_control_unit` | 1 | 0.0398 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0007 |
| `singulo:fulgurite` | 3 | 0.7528 |
| `singulo:observation_log` | 1 | 0.0004 |
| `singulo:pressure_crystal` | 4 | 4 |
| `singulo:quantum_data_fragment` | 1 | 0.102 |
| `singulo:stardust` | 5 | 0.0153 |

初回加工電力: **810.6444 MFE**。加工仕事量: **87.6833分**。別途かまど燃料: 石炭換算 **36個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 6、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 2,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。

### SMESモジュール

`singulo:smes_module`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:amethyst_shard` | 4 | 3 |
| `minecraft:bone_meal` | 5 | 2.0326 |
| `minecraft:clay_ball` | 10 | 4.0651 |
| `minecraft:coal` | 20 | 15.1667 |
| `minecraft:diamond` | 2 | 1.5 |
| `minecraft:quartz` | 9 | 5.0326 |
| `minecraft:raw_copper` | 12 | 6.5312 |
| `minecraft:raw_iron` | 10 | 7.5833 |
| `minecraft:redstone` | 12 | 6.0234 |
| `minecraft:sand` | 6 | 2.5326 |
| `singulo:degraded_control_unit` | 1 | 0.0495 |
| `singulo:quantum_data_fragment` | 1 | 0.0938 |

初回加工電力: **6.7894 MFE**。加工仕事量: **8.3333分**。別途かまど燃料: 石炭換算 **2.875個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 2。

### 時間結晶育成槽

`singulo:time_crystal_incubator`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,100 |
| `minecraft:amethyst_shard` | 4 | 4 |
| `minecraft:bone_meal` | 8 | 5.8438 |
| `minecraft:clay_ball` | 16 | 11.6875 |
| `minecraft:coal` | 16 | 12.25 |
| `minecraft:deepslate` | 80 | 38.5 |
| `minecraft:diamond` | 3 | 2.0625 |
| `minecraft:quartz` | 16 | 13.8438 |
| `minecraft:raw_copper` | 46 | 42.25 |
| `minecraft:raw_gold` | 4 | 4 |
| `minecraft:raw_iron` | 8 | 6.125 |
| `minecraft:redstone` | 16 | 12.4375 |
| `minecraft:sand` | 8 | 5.8438 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:degenerate_nucleus` | 1 | 1 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0625 |
| `singulo:degraded_control_unit` | 1 | 0.0625 |
| `singulo:fulgurite` | 2 | 2 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.125 |
| `singulo:stardust` | 2 | 2 |

初回加工電力: **20.061 MFE**。加工仕事量: **14.7917分**。別途かまど燃料: 石炭換算 **3.25個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 2、`singulo:bose_condensate_catalyst` 36,000、`singulo:cold_atom_trap` 2、`singulo:observation_log` 1。

### トポロジカル導線

`singulo:topological_wire`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:amethyst_shard` | 2 | 0.125 |
| `minecraft:bone_meal` | 5 | 0.1138 |
| `minecraft:clay_ball` | 10 | 0.2275 |
| `minecraft:coal` | 4 | 0.1875 |
| `minecraft:diamond` | 1 | 0.0625 |
| `minecraft:quartz` | 7 | 0.2388 |
| `minecraft:raw_copper` | 12 | 0.3867 |
| `minecraft:raw_iron` | 2 | 0.0938 |
| `minecraft:redstone` | 12 | 0.3369 |
| `minecraft:sand` | 5 | 0.1138 |
| `singulo:degraded_control_unit` | 1 | 0.0029 |
| `singulo:quantum_data_fragment` | 1 | 0.0039 |

初回加工電力: **3.4186 MFE**。加工仕事量: **4.7833分**。別途かまど燃料: 石炭換算 **1.75個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 1。

### 虚空捕集器

`singulo:void_collector`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,100 |
| `minecraft:amethyst_shard` | 6 | 5 |
| `minecraft:bone_meal` | 8 | 5.5091 |
| `minecraft:clay_ball` | 16 | 11.0182 |
| `minecraft:coal` | 24 | 12.4167 |
| `minecraft:deepslate` | 80 | 38.5 |
| `minecraft:diamond` | 4 | 2.5625 |
| `minecraft:ender_pearl` | 4 | 4 |
| `minecraft:quartz` | 17 | 13.5091 |
| `minecraft:raw_copper` | 48 | 40.4688 |
| `minecraft:raw_iron` | 12 | 6.2083 |
| `minecraft:redstone` | 17 | 10.9766 |
| `minecraft:sand` | 11 | 7.7591 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0625 |
| `singulo:degraded_control_unit` | 1 | 0.0339 |
| `singulo:fulgurite` | 1 | 1 |
| `singulo:observation_log` | 1 | 0.0625 |
| `singulo:quantum_data_fragment` | 1 | 0.1562 |
| `singulo:stardust` | 2 | 2 |

初回加工電力: **20.9724 MFE**。加工仕事量: **16.575分**。別途かまど燃料: 石炭換算 **3.875個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 3、`singulo:bose_condensate_catalyst` 36,000、`singulo:cold_atom_trap` 2、`singulo:observation_log` 1。

## Tier 5（24項目）

### 創世炉（完成構造）

`analysis:genesis_engine/7`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 400 | 157.5 |
| `minecraft:amethyst_shard` | 48 | 37.175 |
| `minecraft:bone_meal` | 32 | 24.8765 |
| `minecraft:clay_ball` | 64 | 49.7529 |
| `minecraft:coal` | 84 | 67.1437 |
| `minecraft:cobblestone` | 24,300 | 21,060 |
| `minecraft:deepslate` | 160 | 67.1125 |
| `minecraft:diamond` | 21 | 16.9578 |
| `minecraft:echo_shard` | 2 | 1.4 |
| `minecraft:ender_pearl` | 16 | 12 |
| `minecraft:nether_star` | 2 | 1.5 |
| `minecraft:quartz` | 97 | 79.1015 |
| `minecraft:raw_copper` | 90 | 75.4539 |
| `minecraft:raw_iron` | 1,743 | 1,572.5719 |
| `minecraft:redstone` | 92 | 74.0623 |
| `minecraft:sand` | 33 | 25.7515 |
| `singulo:culture_data` | 1 | 0.0938 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.1203 |
| `singulo:degraded_control_unit` | 1 | 0.4947 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.175 |
| `singulo:fulgurite` | 18 | 16.7 |
| `singulo:hawking_condensate` | 4 | 4 |
| `singulo:jet_condensate` | 20 | 20 |
| `singulo:observation_log` | 1 | 0.0984 |
| `singulo:pressure_crystal` | 20 | 17.3333 |
| `singulo:quantum_data_fragment` | 2 | 1.0523 |
| `singulo:stardust` | 15 | 11.85 |

初回加工電力: **4,017.6708 MFE**。加工仕事量: **425.1917分**。別途かまど燃料: 石炭換算 **229.25個**。

消費する使用回数: `singulo:control_unit` 9、`singulo:quantum_data_fragment` 20、`singulo:culture_data` 2、`singulo:time_crystal_catalyst` 76,800、`singulo:time_crystal_seed` 2、`singulo:bose_condensate_catalyst` 108,000、`singulo:cold_atom_trap` 7、`singulo:observation_log` 3。

### 炉心制御装置

`singulo:core_controller`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,400 | 1,303.75 |
| `minecraft:amethyst_shard` | 38 | 30.3375 |
| `minecraft:bone_meal` | 18 | 13.7745 |
| `minecraft:clay_ball` | 36 | 27.549 |
| `minecraft:coal` | 44 | 37.051 |
| `minecraft:deepslate` | 240 | 129.4313 |
| `minecraft:diamond` | 15 | 12.0258 |
| `minecraft:echo_shard` | 3 | 2.7 |
| `minecraft:ender_pearl` | 8 | 6 |
| `minecraft:nether_star` | 1 | 0.75 |
| `minecraft:quartz` | 64 | 51.637 |
| `minecraft:raw_copper` | 66 | 57.2348 |
| `minecraft:raw_iron` | 22 | 18.5255 |
| `minecraft:redstone` | 45 | 34.5058 |
| `minecraft:sand` | 19 | 14.462 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0469 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.232 |
| `singulo:degraded_control_unit` | 1 | 0.1035 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.3375 |
| `singulo:fulgurite` | 8 | 6.85 |
| `singulo:observation_log` | 1 | 0.1898 |
| `singulo:quantum_data_fragment` | 1 | 0.7371 |
| `singulo:stardust` | 10 | 7.425 |
| `singulo:void_dust` | 8 | 8 |

初回加工電力: **1,660.5504 MFE**。加工仕事量: **84.075分**。別途かまど燃料: 石炭換算 **7.125個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 14、`singulo:time_crystal_catalyst` 146,400、`singulo:time_crystal_seed` 3、`singulo:bose_condensate_catalyst` 144,000、`singulo:cold_atom_trap` 10、`singulo:observation_log` 4、`singulo:culture_data` 1。

### イベントホライズン・シールド発生塔

`singulo:event_horizon_shield`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,400 | 1,155 |
| `minecraft:amethyst_shard` | 44 | 35.6861 |
| `minecraft:bone_meal` | 30 | 24.4125 |
| `minecraft:clay_ball` | 60 | 48.825 |
| `minecraft:coal` | 150 | 136.0472 |
| `minecraft:deepslate` | 160 | 66.0472 |
| `minecraft:diamond` | 19 | 16.2392 |
| `minecraft:echo_shard` | 2 | 1.3778 |
| `minecraft:ender_pearl` | 8 | 8 |
| `minecraft:nether_star` | 1 | 1 |
| `minecraft:obsidian` | 20 | 20 |
| `minecraft:quartz` | 87 | 73.5069 |
| `minecraft:raw_copper` | 112 | 99.0018 |
| `minecraft:raw_iron` | 75 | 68.0236 |
| `minecraft:redstone` | 82 | 67.5295 |
| `minecraft:sand` | 49 | 42.1625 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0625 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.1184 |
| `singulo:degraded_control_unit` | 1 | 0.4387 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.1722 |
| `singulo:fulgurite` | 14 | 13.0639 |
| `singulo:observation_log` | 1 | 0.0969 |
| `singulo:quantum_data_fragment` | 2 | 1.0076 |
| `singulo:stardust` | 7 | 3.7889 |

初回加工電力: **1,238.3798 MFE**。加工仕事量: **103.4333分**。別途かまど燃料: 石炭換算 **21.75個**。

消費する使用回数: `singulo:control_unit` 8、`singulo:quantum_data_fragment` 18、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 74,400、`singulo:time_crystal_seed` 2、`singulo:bose_condensate_catalyst` 108,000、`singulo:cold_atom_trap` 7、`singulo:observation_log` 3。

### 抽出ポート

`singulo:extraction_port`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:amethyst_shard` | 2 | 2 |
| `minecraft:bone_meal` | 5 | 0.4899 |
| `minecraft:clay_ball` | 10 | 0.9798 |
| `minecraft:coal` | 16 | 0.9583 |
| `minecraft:cobblestone` | 4,860 | 202.5 |
| `minecraft:diamond` | 1 | 1 |
| `minecraft:quartz` | 8 | 3.4899 |
| `minecraft:raw_copper` | 12 | 0.9453 |
| `minecraft:raw_iron` | 251 | 10.6042 |
| `minecraft:redstone` | 12 | 1.459 |
| `minecraft:sand` | 5 | 0.4899 |
| `singulo:degraded_control_unit` | 1 | 0.0072 |
| `singulo:fulgurite` | 1 | 1 |
| `singulo:pressure_crystal` | 4 | 0.1667 |
| `singulo:quantum_data_fragment` | 1 | 0.0625 |

初回加工電力: **125.6968 MFE**。加工仕事量: **55.1417分**。別途かまど燃料: 石炭換算 **32.875個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 1。

### 創世炉の核

`singulo:genesis_core`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 400 | 155 |
| `minecraft:amethyst_shard` | 30 | 22.0611 |
| `minecraft:bone_meal` | 14 | 8.1796 |
| `minecraft:clay_ball` | 28 | 16.3592 |
| `minecraft:coal` | 28 | 18.6097 |
| `minecraft:deepslate` | 160 | 66.0472 |
| `minecraft:diamond` | 12 | 9.4267 |
| `minecraft:echo_shard` | 2 | 1.3778 |
| `minecraft:ender_pearl` | 8 | 8 |
| `minecraft:nether_star` | 1 | 1 |
| `minecraft:quartz` | 47 | 34.274 |
| `minecraft:raw_copper` | 32 | 17.6307 |
| `minecraft:raw_iron` | 500 | 495.3049 |
| `minecraft:redstone` | 39 | 24.3918 |
| `minecraft:sand` | 15 | 8.9296 |
| `singulo:culture_data` | 1 | 0.0625 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.1184 |
| `singulo:degraded_control_unit` | 1 | 0.0646 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.1722 |
| `singulo:fulgurite` | 4 | 3.6889 |
| `singulo:hawking_condensate` | 4 | 4 |
| `singulo:jet_condensate` | 8 | 8 |
| `singulo:observation_log` | 1 | 0.0969 |
| `singulo:quantum_data_fragment` | 1 | 0.5818 |
| `singulo:stardust` | 15 | 11.7889 |

初回加工電力: **3,360.5672 MFE**。加工仕事量: **145.6333分**。別途かまど燃料: 石炭換算 **66.625個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 11、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 74,400、`singulo:time_crystal_seed` 2、`singulo:bose_condensate_catalyst` 108,000、`singulo:cold_atom_trap` 7、`singulo:observation_log` 3。

### 重力閉じ込めタンク

`singulo:gravitational_containment_tank`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,001.25 |
| `minecraft:amethyst_shard` | 20 | 5.5569 |
| `minecraft:bone_meal` | 14 | 5.2768 |
| `minecraft:clay_ball` | 28 | 10.5537 |
| `minecraft:coal` | 28 | 14.1003 |
| `minecraft:deepslate` | 160 | 0.5326 |
| `minecraft:diamond` | 8 | 2.7655 |
| `minecraft:echo_shard` | 1 | 0.0111 |
| `minecraft:ender_pearl` | 8 | 2 |
| `minecraft:nether_star` | 1 | 0.25 |
| `minecraft:quartz` | 36 | 11.3421 |
| `minecraft:raw_copper` | 60 | 41.9428 |
| `minecraft:raw_iron` | 14 | 7.0502 |
| `minecraft:redstone` | 33 | 11.3587 |
| `minecraft:sand` | 19 | 9.5893 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0156 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.001 |
| `singulo:degraded_control_unit` | 1 | 0.0562 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0014 |
| `singulo:fulgurite` | 3 | 0.5056 |
| `singulo:observation_log` | 1 | 0.0008 |
| `singulo:quantum_data_fragment` | 1 | 0.1728 |
| `singulo:stardust` | 5 | 0.0306 |

初回加工電力: **746.2882 MFE**。加工仕事量: **43.65分**。別途かまど燃料: 石炭換算 **5.875個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 7、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 2,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。

### ハロー捕集器

`singulo:halo_collector`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,400 | 1,115 |
| `minecraft:amethyst_shard` | 26 | 14.2389 |
| `minecraft:bone_meal` | 16 | 7.5424 |
| `minecraft:clay_ball` | 32 | 15.0849 |
| `minecraft:coal` | 28 | 14.7319 |
| `minecraft:deepslate` | 160 | 49.0028 |
| `minecraft:diamond` | 10 | 5.9295 |
| `minecraft:echo_shard` | 2 | 1.0222 |
| `minecraft:ender_pearl` | 8 | 4 |
| `minecraft:nether_star` | 1 | 0.5 |
| `minecraft:quartz` | 46 | 25.548 |
| `minecraft:raw_copper` | 62 | 44.2724 |
| `minecraft:raw_iron` | 14 | 7.366 |
| `minecraft:redstone` | 38 | 17.148 |
| `minecraft:sand` | 17 | 7.9174 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0312 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0878 |
| `singulo:degraded_control_unit` | 1 | 0.0528 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.1278 |
| `singulo:fulgurite` | 5 | 3.5111 |
| `singulo:observation_log` | 1 | 0.0719 |
| `singulo:quantum_data_fragment` | 1 | 0.3651 |
| `singulo:stardust` | 7 | 2.8111 |

初回加工電力: **1,197.6538 MFE**。加工仕事量: **59.9833分**。別途かまど燃料: 石炭換算 **5.625個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 9、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 56,400、`singulo:time_crystal_seed` 2、`singulo:bose_condensate_catalyst` 108,000、`singulo:cold_atom_trap` 7、`singulo:observation_log` 3。

### Hコレクター

`singulo:hawking_collector`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,000.625 |
| `minecraft:amethyst_shard` | 20 | 4.7785 |
| `minecraft:bone_meal` | 9 | 1.115 |
| `minecraft:clay_ball` | 18 | 2.23 |
| `minecraft:coal` | 16 | 1.0502 |
| `minecraft:deepslate` | 160 | 0.2663 |
| `minecraft:diamond` | 8 | 2.3828 |
| `minecraft:echo_shard` | 1 | 0.0056 |
| `minecraft:ender_pearl` | 8 | 1 |
| `minecraft:nether_star` | 1 | 0.125 |
| `minecraft:quartz` | 33 | 8.1476 |
| `minecraft:raw_copper` | 50 | 33.9089 |
| `minecraft:raw_iron` | 8 | 0.5251 |
| `minecraft:redstone` | 24 | 3.3825 |
| `minecraft:sand` | 10 | 1.1462 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0078 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0005 |
| `singulo:degraded_control_unit` | 1 | 0.0125 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0007 |
| `singulo:fulgurite` | 5 | 2.2528 |
| `singulo:observation_log` | 1 | 0.0004 |
| `singulo:quantum_data_fragment` | 1 | 0.1489 |
| `singulo:stardust` | 5 | 0.0153 |

初回加工電力: **749.1904 MFE**。加工仕事量: **39.45分**。別途かまど燃料: 石炭換算 **3.375個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 7、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 2,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。

### ホライズン・バス

`singulo:horizon_bus`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 700 | 62.5781 |
| `minecraft:amethyst_shard` | 18 | 0.1598 |
| `minecraft:bone_meal` | 11 | 0.0901 |
| `minecraft:clay_ball` | 22 | 0.1803 |
| `minecraft:coal` | 20 | 0.1417 |
| `minecraft:deepslate` | 160 | 0.0333 |
| `minecraft:diamond` | 7 | 0.0791 |
| `minecraft:echo_shard` | 1 | 0.0007 |
| `minecraft:ender_pearl` | 8 | 0.125 |
| `minecraft:nether_star` | 1 | 0.0156 |
| `minecraft:quartz` | 31 | 0.2817 |
| `minecraft:raw_copper` | 42 | 2.2601 |
| `minecraft:raw_iron` | 10 | 0.0708 |
| `minecraft:redstone` | 30 | 0.2749 |
| `minecraft:sand` | 12 | 0.094 |
| `minecraft:tuff` | 16 | 2 |
| `singulo:culture_data` | 1 | 0.001 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0001 |
| `singulo:degraded_control_unit` | 1 | 0.0017 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0001 |
| `singulo:fulgurite` | 3 | 0.0316 |
| `singulo:observation_log` | 1 | 0 |
| `singulo:quantum_data_fragment` | 1 | 0.0049 |
| `singulo:stardust` | 5 | 0.0019 |

初回加工電力: **763.9794 MFE**。加工仕事量: **39.3917分**。別途かまど燃料: 石炭換算 **4.625個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 6、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 2,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。

### ジェット・コレクター

`singulo:jet_collector`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,000 | 1,000 |
| `minecraft:amethyst_shard` | 2 | 2 |
| `minecraft:bone_meal` | 5 | 1.4714 |
| `minecraft:clay_ball` | 10 | 2.9427 |
| `minecraft:coal` | 4 | 2.3333 |
| `minecraft:diamond` | 1 | 1 |
| `minecraft:quartz` | 8 | 4.4714 |
| `minecraft:raw_copper` | 44 | 36.8125 |
| `minecraft:raw_iron` | 2 | 1.1667 |
| `minecraft:redstone` | 12 | 4.3594 |
| `minecraft:sand` | 5 | 1.4714 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:degraded_control_unit` | 1 | 0.0365 |
| `singulo:fulgurite` | 1 | 1 |
| `singulo:quantum_data_fragment` | 1 | 0.0625 |

初回加工電力: **5.2918 MFE**。加工仕事量: **8.0833分**。別途かまど燃料: 石炭換算 **1.75個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 1。

### Pリアクター

`singulo:penrose_reactor`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 4,400 | 4,303.75 |
| `minecraft:amethyst_shard` | 58 | 49.8375 |
| `minecraft:bone_meal` | 53 | 46.4405 |
| `minecraft:clay_ball` | 106 | 92.881 |
| `minecraft:coal` | 176 | 160.801 |
| `minecraft:cobblestone` | 29,160 | 26,730 |
| `minecraft:deepslate` | 240 | 129.4313 |
| `minecraft:diamond` | 25 | 21.7758 |
| `minecraft:echo_shard` | 3 | 2.7 |
| `minecraft:ender_pearl` | 8 | 6 |
| `minecraft:nether_star` | 1 | 0.75 |
| `minecraft:quartz` | 119 | 103.803 |
| `minecraft:raw_copper` | 286 | 272.3441 |
| `minecraft:raw_iron` | 1,546 | 1,416.9005 |
| `minecraft:redstone` | 147 | 131.1503 |
| `minecraft:sand` | 54 | 47.128 |
| `minecraft:tuff` | 128 | 128 |
| `singulo:culture_data` | 1 | 0.0469 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.232 |
| `singulo:degraded_control_unit` | 2 | 1.0058 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.3375 |
| `singulo:fulgurite` | 8 | 6.85 |
| `singulo:observation_log` | 1 | 0.1898 |
| `singulo:pressure_crystal` | 24 | 22 |
| `singulo:quantum_data_fragment` | 2 | 1.3465 |
| `singulo:raw_meteoric_iron` | 12 | 12 |
| `singulo:stardust` | 10 | 7.425 |
| `singulo:void_dust` | 8 | 8 |

初回加工電力: **2,420.7282 MFE**。加工仕事量: **443.3917分**。別途かまど燃料: 石炭換算 **214.625個**。

消費する使用回数: `singulo:control_unit` 17、`singulo:quantum_data_fragment` 24、`singulo:time_crystal_catalyst` 146,400、`singulo:time_crystal_seed` 3、`singulo:bose_condensate_catalyst` 144,000、`singulo:cold_atom_trap` 10、`singulo:observation_log` 4、`singulo:culture_data` 1。

### 炉心質量警報器

`singulo:reactor_mass_alarm`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `minecraft:bone_meal` | 4 | 0.5654 |
| `minecraft:clay_ball` | 8 | 1.1309 |
| `minecraft:coal` | 16 | 0.625 |
| `minecraft:cobblestone` | 4,860 | 202.5 |
| `minecraft:quartz` | 4 | 0.5654 |
| `minecraft:raw_copper` | 14 | 2.2578 |
| `minecraft:raw_iron` | 251 | 10.4375 |
| `minecraft:redstone` | 15 | 6.1934 |
| `minecraft:sand` | 4 | 0.5654 |
| `singulo:degraded_control_unit` | 1 | 0.002 |
| `singulo:pressure_crystal` | 4 | 0.1667 |

初回加工電力: **121.6628 MFE**。加工仕事量: **54.2667分**。別途かまど燃料: 石炭換算 **33.125個**。

消費する使用回数: `singulo:control_unit` 1。

### 封印コンテナ（特異点錠）

`singulo:sealed_container_4`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 5 |
| `minecraft:amethyst_shard` | 24 | 13.7278 |
| `minecraft:bone_meal` | 15 | 8.2668 |
| `minecraft:clay_ball` | 30 | 16.5336 |
| `minecraft:coal` | 52 | 40.3181 |
| `minecraft:cobblestone` | 4,860 | 4,860 |
| `minecraft:deepslate` | 160 | 2.1306 |
| `minecraft:diamond` | 10 | 6.8122 |
| `minecraft:echo_shard` | 1 | 0.0444 |
| `minecraft:ender_pearl` | 8 | 8 |
| `minecraft:nether_star` | 1 | 1 |
| `minecraft:quartz` | 42 | 25.5279 |
| `minecraft:raw_copper` | 28 | 12.9745 |
| `minecraft:raw_iron` | 269 | 263.159 |
| `minecraft:redstone` | 33 | 17.6496 |
| `minecraft:sand` | 16 | 8.7668 |
| `singulo:culture_data` | 1 | 0.0625 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0038 |
| `singulo:degraded_control_unit` | 1 | 0.0672 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0056 |
| `singulo:fulgurite` | 4 | 3.5222 |
| `singulo:observation_log` | 1 | 0.0031 |
| `singulo:pressure_crystal` | 4 | 4 |
| `singulo:quantum_data_fragment` | 1 | 0.4255 |
| `singulo:stardust` | 5 | 0.1222 |
| `singulo:void_dust` | 4 | 4 |

初回加工電力: **899.624 MFE**。加工仕事量: **93.475分**。別途かまど燃料: 石炭換算 **37.25個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 9、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 2,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。

### シールド発生塔コア

`singulo:shield_tower_core`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,400 | 1,153.75 |
| `minecraft:amethyst_shard` | 32 | 22.5042 |
| `minecraft:bone_meal` | 16 | 10.421 |
| `minecraft:clay_ball` | 32 | 20.842 |
| `minecraft:coal` | 36 | 26.8427 |
| `minecraft:deepslate` | 160 | 65.5146 |
| `minecraft:diamond` | 13 | 9.6612 |
| `minecraft:echo_shard` | 2 | 1.3667 |
| `minecraft:ender_pearl` | 8 | 6 |
| `minecraft:nether_star` | 1 | 0.75 |
| `minecraft:quartz` | 53 | 38.4501 |
| `minecraft:raw_copper` | 64 | 50.6254 |
| `minecraft:raw_iron` | 18 | 13.4214 |
| `minecraft:redstone` | 40 | 25.9862 |
| `minecraft:sand` | 17 | 11.1085 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0469 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.1174 |
| `singulo:degraded_control_unit` | 1 | 0.0761 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.1708 |
| `singulo:fulgurite` | 6 | 5.1833 |
| `singulo:observation_log` | 1 | 0.0961 |
| `singulo:quantum_data_fragment` | 1 | 0.5965 |
| `singulo:stardust` | 7 | 3.7583 |

初回加工電力: **1,207.3852 MFE**。加工仕事量: **64.3分**。別途かまど燃料: 石炭換算 **6.375個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 12、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 74,400、`singulo:time_crystal_seed` 2、`singulo:bose_condensate_catalyst` 108,000、`singulo:cold_atom_trap` 7、`singulo:observation_log` 3。

### 特異点封入台

`singulo:singularity_encapsulator`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,400 | 1,152.5 |
| `minecraft:amethyst_shard` | 26 | 14.9472 |
| `minecraft:bone_meal` | 18 | 8.1809 |
| `minecraft:clay_ball` | 36 | 16.3619 |
| `minecraft:coal` | 52 | 23.7007 |
| `minecraft:cobblestone` | 4,860 | 202.5 |
| `minecraft:deepslate` | 160 | 64.9819 |
| `minecraft:diamond` | 10 | 5.8957 |
| `minecraft:echo_shard` | 2 | 1.3556 |
| `minecraft:ender_pearl` | 8 | 4 |
| `minecraft:nether_star` | 1 | 0.5 |
| `minecraft:quartz` | 47 | 26.1448 |
| `minecraft:raw_copper` | 70 | 45.7529 |
| `minecraft:raw_iron` | 269 | 21.9753 |
| `minecraft:redstone` | 44 | 18.6803 |
| `minecraft:sand` | 19 | 8.5559 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0312 |
| `singulo:degenerate_nucleus` | 1 | 1 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.1165 |
| `singulo:degraded_control_unit` | 1 | 0.0584 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.1694 |
| `singulo:fulgurite` | 4 | 2.6778 |
| `singulo:observation_log` | 1 | 0.0953 |
| `singulo:pressure_crystal` | 4 | 0.1667 |
| `singulo:quantum_data_fragment` | 1 | 0.3612 |
| `singulo:stardust` | 7 | 3.7278 |

初回加工電力: **1,318.4878 MFE**。加工仕事量: **113.5333分**。別途かまど燃料: 石炭換算 **38.5個**。

消費する使用回数: `singulo:control_unit` 3、`singulo:quantum_data_fragment` 9、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 74,400、`singulo:time_crystal_seed` 2、`singulo:bose_condensate_catalyst` 108,000、`singulo:cold_atom_trap` 7、`singulo:observation_log` 3。

### Tコア

`singulo:tipler_core`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,080 |
| `minecraft:amethyst_shard` | 24 | 18.1444 |
| `minecraft:bone_meal` | 14 | 8.8518 |
| `minecraft:clay_ball` | 28 | 17.7036 |
| `minecraft:coal` | 32 | 22.1722 |
| `minecraft:deepslate` | 160 | 34.0889 |
| `minecraft:diamond` | 10 | 8.2444 |
| `minecraft:echo_shard` | 1 | 0.7111 |
| `minecraft:ender_pearl` | 8 | 8 |
| `minecraft:nether_star` | 1 | 1 |
| `minecraft:quartz` | 40 | 30.0296 |
| `minecraft:raw_copper` | 62 | 47.701 |
| `minecraft:raw_iron` | 16 | 11.0861 |
| `minecraft:redstone` | 35 | 22.1633 |
| `minecraft:sand` | 15 | 9.6018 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0625 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0611 |
| `singulo:degraded_control_unit` | 1 | 0.0614 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0889 |
| `singulo:fulgurite` | 3 | 2.8556 |
| `singulo:observation_log` | 1 | 0.05 |
| `singulo:quantum_data_fragment` | 1 | 0.5115 |
| `singulo:stardust` | 5 | 1.9556 |

初回加工電力: **751.561 MFE**。加工仕事量: **45.5583分**。別途かまど燃料: 石炭換算 **5.875個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 9、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 38,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。

### Tシリンダー

`singulo:tipler_cylinder`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,080 |
| `minecraft:amethyst_shard` | 44 | 38.1444 |
| `minecraft:bone_meal` | 17 | 11.7945 |
| `minecraft:clay_ball` | 34 | 23.589 |
| `minecraft:coal` | 266 | 255.8389 |
| `minecraft:deepslate` | 160 | 34.0889 |
| `minecraft:diamond` | 12 | 10.2444 |
| `minecraft:echo_shard` | 1 | 0.7111 |
| `minecraft:ender_pearl` | 8 | 8 |
| `minecraft:nether_star` | 1 | 1 |
| `minecraft:obsidian` | 33 | 32.75 |
| `minecraft:quartz` | 51 | 40.9723 |
| `minecraft:raw_copper` | 70 | 57.326 |
| `minecraft:raw_iron` | 133 | 127.9194 |
| `minecraft:redstone` | 44 | 30.882 |
| `minecraft:sand` | 38 | 32.5445 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0625 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0611 |
| `singulo:degraded_control_unit` | 1 | 0.1343 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0889 |
| `singulo:fulgurite` | 7 | 6.8556 |
| `singulo:observation_log` | 1 | 0.05 |
| `singulo:quantum_data_fragment` | 1 | 0.6365 |
| `singulo:stardust` | 5 | 1.9556 |

初回加工電力: **763.7936 MFE**。加工仕事量: **86.425分**。別途かまど燃料: 石炭換算 **24個**。

消費する使用回数: `singulo:control_unit` 3、`singulo:quantum_data_fragment` 11、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 38,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。

### 不安定なワームホールの口

`singulo:unstable_wormhole_mouth`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|

初回加工電力: **20,000 MFE**。加工仕事量: **0.1667分**。別途かまど燃料: 石炭換算 **0個**。

### ワールドライン・アンカー（上位）

`singulo:worldline_anchor_advanced`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,001.25 |
| `minecraft:amethyst_shard` | 20 | 5.5569 |
| `minecraft:bone_meal` | 14 | 4.23 |
| `minecraft:clay_ball` | 28 | 8.4599 |
| `minecraft:coal` | 36 | 20.1003 |
| `minecraft:deepslate` | 160 | 0.5326 |
| `minecraft:diamond` | 8 | 2.7655 |
| `minecraft:echo_shard` | 1 | 0.0111 |
| `minecraft:ender_pearl` | 12 | 6 |
| `minecraft:nether_star` | 1 | 0.25 |
| `minecraft:quartz` | 36 | 10.2952 |
| `minecraft:raw_copper` | 60 | 37.8178 |
| `minecraft:raw_iron` | 18 | 10.0502 |
| `minecraft:redstone` | 33 | 8.2649 |
| `minecraft:sand` | 15 | 4.5425 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0156 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.001 |
| `singulo:degraded_control_unit` | 1 | 0.0249 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0014 |
| `singulo:fulgurite` | 3 | 0.5056 |
| `singulo:observation_log` | 1 | 0.0008 |
| `singulo:quantum_data_fragment` | 1 | 0.1728 |
| `singulo:stardust` | 5 | 0.0306 |

初回加工電力: **746.3034 MFE**。加工仕事量: **43.9833分**。別途かまど燃料: 石炭換算 **5.875個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 7、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 2,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。

### ワームホール生成器

`singulo:wormhole_generator`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,400 | 1,156.25 |
| `minecraft:amethyst_shard` | 52 | 40.4931 |
| `minecraft:bone_meal` | 32 | 24.413 |
| `minecraft:clay_ball` | 64 | 48.8259 |
| `minecraft:coal` | 104 | 92.5226 |
| `minecraft:deepslate` | 160 | 66.5799 |
| `minecraft:diamond` | 23 | 18.6298 |
| `minecraft:echo_shard` | 2 | 1.3889 |
| `minecraft:ender_pearl` | 16 | 10 |
| `minecraft:nether_star` | 2 | 1.25 |
| `minecraft:obsidian` | 10 | 9.75 |
| `minecraft:quartz` | 100 | 80.3227 |
| `minecraft:raw_copper` | 114 | 96.3743 |
| `minecraft:raw_iron` | 52 | 46.2613 |
| `minecraft:redstone` | 87 | 67.6792 |
| `minecraft:sand` | 37 | 29.2255 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0781 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.1194 |
| `singulo:degraded_control_unit` | 1 | 0.4148 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.1736 |
| `singulo:fulgurite` | 17 | 15.0694 |
| `singulo:observation_log` | 1 | 0.0977 |
| `singulo:quantum_data_fragment` | 2 | 1.1569 |
| `singulo:stardust` | 7 | 3.8194 |
| `singulo:void_dust` | 4 | 4 |

初回加工電力: **1,495.8906 MFE**。加工仕事量: **101.1分**。別途かまど燃料: 石炭換算 **17.375個**。

消費する使用回数: `singulo:control_unit` 8、`singulo:quantum_data_fragment` 22、`singulo:culture_data` 2、`singulo:time_crystal_catalyst` 76,800、`singulo:time_crystal_seed` 2、`singulo:bose_condensate_catalyst` 108,000、`singulo:cold_atom_trap` 7、`singulo:observation_log` 3。

### ワームホール生成器コア

`singulo:wormhole_generator_core`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,400 | 1,155 |
| `minecraft:amethyst_shard` | 34 | 26.0611 |
| `minecraft:bone_meal` | 17 | 11.3775 |
| `minecraft:clay_ball` | 34 | 22.755 |
| `minecraft:coal` | 36 | 27.9431 |
| `minecraft:deepslate` | 160 | 66.0472 |
| `minecraft:diamond` | 14 | 11.4267 |
| `minecraft:echo_shard` | 2 | 1.3778 |
| `minecraft:ender_pearl` | 8 | 8 |
| `minecraft:nether_star` | 1 | 1 |
| `minecraft:quartz` | 58 | 45.4719 |
| `minecraft:raw_copper` | 64 | 52.3807 |
| `minecraft:raw_iron` | 18 | 13.9715 |
| `minecraft:redstone` | 43 | 28.9543 |
| `minecraft:sand` | 18 | 12.1275 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0625 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.1184 |
| `singulo:degraded_control_unit` | 1 | 0.0855 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.1722 |
| `singulo:fulgurite` | 8 | 7.6889 |
| `singulo:observation_log` | 1 | 0.0969 |
| `singulo:quantum_data_fragment` | 1 | 0.7068 |
| `singulo:stardust` | 7 | 3.7889 |

初回加工電力: **1,213.0012 MFE**。加工仕事量: **65.8分**。別途かまど燃料: 石炭換算 **6.375個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 13、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 74,400、`singulo:time_crystal_seed` 2、`singulo:bose_condensate_catalyst` 108,000、`singulo:cold_atom_trap` 7、`singulo:observation_log` 3。

### ワームホールの口

`singulo:wormhole_mouth`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 1.25 |
| `minecraft:amethyst_shard` | 16 | 1.5569 |
| `minecraft:bone_meal` | 8 | 1.0555 |
| `minecraft:clay_ball` | 16 | 2.1109 |
| `minecraft:coal` | 24 | 9.767 |
| `minecraft:deepslate` | 160 | 0.5326 |
| `minecraft:diamond` | 6 | 0.7655 |
| `minecraft:echo_shard` | 1 | 0.0111 |
| `minecraft:ender_pearl` | 8 | 2 |
| `minecraft:nether_star` | 1 | 0.25 |
| `minecraft:obsidian` | 2 | 2 |
| `minecraft:quartz` | 26 | 3.1208 |
| `minecraft:raw_copper` | 18 | 3.1303 |
| `minecraft:raw_iron` | 12 | 4.8835 |
| `minecraft:redstone` | 21 | 3.2493 |
| `minecraft:sand` | 9 | 1.118 |
| `singulo:culture_data` | 1 | 0.0156 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.001 |
| `singulo:degraded_control_unit` | 1 | 0.0197 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0014 |
| `singulo:fulgurite` | 3 | 0.5056 |
| `singulo:observation_log` | 1 | 0.0008 |
| `singulo:quantum_data_fragment` | 1 | 0.0478 |
| `singulo:stardust` | 5 | 0.0306 |
| `singulo:void_dust` | 2 | 2 |

初回加工電力: **20,741.0724 MFE**。加工仕事量: **35.2667分**。別途かまど燃料: 石炭換算 **3.875個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 5、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 2,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。

### ワームホール・ポート

`singulo:wormhole_port`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 200 | 0.625 |
| `minecraft:amethyst_shard` | 18 | 1.7785 |
| `minecraft:bone_meal` | 9 | 0.4783 |
| `minecraft:clay_ball` | 18 | 0.9565 |
| `minecraft:coal` | 20 | 4.5502 |
| `minecraft:deepslate` | 160 | 0.2663 |
| `minecraft:diamond` | 7 | 0.8828 |
| `minecraft:echo_shard` | 1 | 0.0056 |
| `minecraft:ender_pearl` | 8 | 1 |
| `minecraft:nether_star` | 1 | 0.125 |
| `minecraft:quartz` | 29 | 2.5109 |
| `minecraft:raw_copper` | 18 | 0.8777 |
| `minecraft:raw_iron` | 10 | 2.2751 |
| `minecraft:redstone` | 24 | 1.484 |
| `minecraft:sand` | 10 | 0.5095 |
| `singulo:culture_data` | 1 | 0.0078 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0005 |
| `singulo:degraded_control_unit` | 1 | 0.0047 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0007 |
| `singulo:fulgurite` | 3 | 0.2528 |
| `singulo:observation_log` | 1 | 0.0004 |
| `singulo:quantum_data_fragment` | 1 | 0.0551 |
| `singulo:stardust` | 5 | 0.0153 |

初回加工電力: **742.5008 MFE**。加工仕事量: **35.4333分**。別途かまど燃料: 石炭換算 **3.625個**。

消費する使用回数: `singulo:control_unit` 1、`singulo:quantum_data_fragment` 6、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 2,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。

### ワームホール固定化装置

`singulo:wormhole_stabilizer`

| 原料 | 初回一式 | 平均 |
|---|---:|---:|
| `fluid:minecraft:water` | 1,200 | 1,002.5 |
| `minecraft:amethyst_shard` | 22 | 9.1139 |
| `minecraft:bone_meal` | 14 | 5.1865 |
| `minecraft:clay_ball` | 28 | 10.3729 |
| `minecraft:coal` | 28 | 13.2007 |
| `minecraft:deepslate` | 160 | 1.0653 |
| `minecraft:diamond` | 9 | 4.5311 |
| `minecraft:echo_shard` | 1 | 0.0222 |
| `minecraft:ender_pearl` | 8 | 4 |
| `minecraft:nether_star` | 1 | 0.5 |
| `minecraft:quartz` | 40 | 17.317 |
| `minecraft:raw_copper` | 60 | 39.5732 |
| `minecraft:raw_iron` | 14 | 6.6003 |
| `minecraft:redstone` | 33 | 11.233 |
| `minecraft:sand` | 15 | 5.5615 |
| `minecraft:tuff` | 32 | 32 |
| `singulo:culture_data` | 1 | 0.0312 |
| `singulo:degraded_cold_atom_trap` | 1 | 0.0019 |
| `singulo:degraded_control_unit` | 1 | 0.0343 |
| `singulo:degraded_time_crystal_seed` | 1 | 0.0028 |
| `singulo:fulgurite` | 5 | 3.0111 |
| `singulo:observation_log` | 1 | 0.0016 |
| `singulo:quantum_data_fragment` | 1 | 0.2831 |
| `singulo:stardust` | 5 | 0.0611 |

初回加工電力: **751.8962 MFE**。加工仕事量: **44.3167分**。別途かまど燃料: 石炭換算 **5.375個**。

消費する使用回数: `singulo:control_unit` 2、`singulo:quantum_data_fragment` 8、`singulo:culture_data` 1、`singulo:time_crystal_catalyst` 2,400、`singulo:time_crystal_seed` 1、`singulo:bose_condensate_catalyst` 72,000、`singulo:cold_atom_trap` 5、`singulo:observation_log` 2。
