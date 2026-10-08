package io.github.genichimaruo.singulo.multiblock;

/** マルチブロックの部品ブロック。役割で構造の判定をする。 */
public interface MultiblockPart {
    enum Role {
        /** 冷却塔の外壁 */
        TOWER_CASING,
        /** 冷却塔の観察窓 */
        TOWER_GLASS,
        /** 冷却塔の搬入出口。コントローラの入出力をそのまま外に出す */
        TOWER_PORT,
        /** 冷却塔の中心の熱交換コア */
        HEAT_EXCHANGE_CORE,
        /** 粒子加速器のリング */
        ACCELERATOR_TUBE,
        /** 粒子加速器のリングの曲がり角と、途中の収束 */
        FOCUSING_MAGNET,
        /** 縮退圧縮炉・カシミール空洞・縮退熱炉の外殻 */
        DEGENERATE_CASING,
        /** カシミール空洞の向かい合う鏡面 */
        MIRROR_PLATE,
        /** 縮退熱炉の上下の圧縮ピストン */
        FURNACE_PISTON,
        /** ペンローズ・リアクターのリング（縮退物質の炉殻） */
        REACTOR_SHELL,
        /** ペンローズ・リアクターのリングが交わる軸の6点。リングを回す */
        GYRO_DRIVE,
        /** ペンローズ・リアクターの電力・燃料の出入り口（リングの炉殻のどこに置いてもよい） */
        EXTRACTION_PORT,
        /** ワームホール生成器の入出力口（外殻のどこに置いてもよい）。電力を入れ、できた口を取り出す */
        WORMHOLE_IO
    }

    Role role();
}
