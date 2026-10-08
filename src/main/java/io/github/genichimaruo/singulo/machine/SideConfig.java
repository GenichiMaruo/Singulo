package io.github.genichimaruo.singulo.machine;

import net.minecraft.core.Direction;

/**
 * 面ごとの搬入出の設定（アイテム用と液体用で1つずつ持つ）。面は装置の向きに対する相対（前・後・左・右・上・下）で持つので、
 * 装置を回しても設定は変わらない。各面は「無効・入力・出力・入出力」と「自動排出」を持ち、3ビットずつ int に詰めて同期する。
 */
public final class SideConfig {
    public enum Face { FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM }

    public static final int NONE = 0, INPUT = 1, OUTPUT = 2, BOTH = 3;
    private static final int EJECT = 4;
    /** 自動排出の全体スイッチ（ビット18）。オフなら面ごとの自動排出は働かない。既定はオン。 */
    private static final int MASTER = 1 << 18;

    /** 6面 × 3ビット。既定はすべて「入出力」で自動排出なし。 */
    private int bits;

    public SideConfig() {
        for (Face f : Face.values()) {
            set(f, BOTH, false);
        }
        bits |= MASTER;
    }

    public static boolean ejectEnabled(int packed) {
        return (packed & MASTER) != 0;
    }

    public boolean ejectEnabled() {
        return ejectEnabled(bits);
    }

    public void setEjectEnabled(boolean on) {
        bits = on ? bits | MASTER : bits & ~MASTER;
    }

    public int packed() {
        return bits;
    }

    public void setPacked(int packed) {
        bits = packed & (0x3FFFF | MASTER);
    }

    public static int mode(int packed, Face face) {
        return (packed >> (face.ordinal() * 3)) & 3;
    }

    public static boolean eject(int packed, Face face) {
        return ((packed >> (face.ordinal() * 3)) & EJECT) != 0;
    }

    public int mode(Face face) {
        return mode(bits, face);
    }

    public boolean eject(Face face) {
        return eject(bits, face);
    }

    public void set(Face face, int mode, boolean eject) {
        int shift = face.ordinal() * 3;
        bits = (bits & ~(7 << shift)) | (((mode & 3) | (eject ? EJECT : 0)) << shift);
    }

    public boolean canInsert(Face face) {
        return (mode(face) & INPUT) != 0;
    }

    public boolean canExtract(Face face) {
        return (mode(face) & OUTPUT) != 0;
    }

    /** ワールドの向き side を、正面 facing の装置から見た面にする。 */
    public static Face faceOf(Direction facing, Direction side) {
        if (side == Direction.UP) {
            return Face.TOP;
        }
        if (side == Direction.DOWN) {
            return Face.BOTTOM;
        }
        if (side == facing) {
            return Face.FRONT;
        }
        if (side == facing.getOpposite()) {
            return Face.BACK;
        }
        // 正面を向いて立ったときの左右（装置から見て）
        return side == facing.getClockWise() ? Face.LEFT : Face.RIGHT;
    }

    /** 面からワールドの向きへ。 */
    public static Direction directionOf(Direction facing, Face face) {
        return switch (face) {
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
        };
    }
}
