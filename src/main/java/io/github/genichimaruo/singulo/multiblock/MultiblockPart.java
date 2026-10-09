package io.github.genichimaruo.singulo.multiblock;

/** マルチブロックの部品ブロック。役割で構造の判定をする（決まった形のものは ShapeSpec でブロックそのものを見る）。 */
public interface MultiblockPart {
    enum Role {
        /** 決まった形（ShapeSpec）の部品。判定はブロックそのもので行う */
        STRUCTURE,
        /** 粒子加速器のリング */
        ACCELERATOR_TUBE,
        /** 粒子加速器のリングの曲がり角と、途中の収束 */
        FOCUSING_MAGNET,
        /** ペンローズ・リアクターのリング（縮退物質の炉殻） */
        REACTOR_SHELL,
        /** ペンローズ・リアクターのリングが交わる軸の6点。リングを回す */
        GYRO_DRIVE,
        /** ペンローズ・リアクターの電力・燃料の出入り口（リングの炉殻のどこに置いてもよい） */
        EXTRACTION_PORT,
        /** ペンローズ・リアクターの各リングの斜め45°の点。地平線を安定させるコイル */
        REACTOR_STABILIZER,
        /** ペンローズ・リアクターの炉心質量警報器（リングの炉殻のどこに置いてもよい。質量が上限に達すると赤石信号を出す） */
        MASS_ALARM,
        /** マルチブロック搬入出ポート（外装板の代わりに置く。出力は自動で押し出す） */
        MULTIBLOCK_PORT
    }

    Role role();
}
