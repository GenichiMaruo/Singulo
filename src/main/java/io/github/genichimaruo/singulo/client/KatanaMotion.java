package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.KatanaAndroid;
import net.minecraft.util.Mth;

/** The arm, scabbard and visible steel all sample the same draw at render time. Distances are in blocks. */
final class KatanaMotion {
    private KatanaMotion() {}

    static float stroke(boolean crimson) {
        return crimson ? 1.022F : 0.882F;
    }

    // The blade curves towards -Z. Keep its cross-section at the scabbard mouth centred as it slides.
    static float curveOffset(float exposure) {
        return 0.06F * exposure * exposure;
    }

    static float exposure(int state, float time, boolean crimson, boolean inHand) {
        if (crimson) {
            return switch (state) {
                case KatanaAndroid.STATE_VICTORY -> 0.52F * ease(time, KatanaAndroid.VICTORY_GRIP, KatanaAndroid.VICTORY_RAISE)
                        * (1 - ease(time, KatanaAndroid.VICTORY_RETURN, KatanaAndroid.VICTORY_SEAT));
                case KatanaAndroid.STATE_IAI_STANCE -> 0.12F * ease(time, KatanaAndroid.IAI_STANCE - KatanaAndroid.IAI_LOCK, KatanaAndroid.IAI_STANCE);
                case KatanaAndroid.STATE_IAI_FLASH -> Mth.lerp(ease(time, 0, 1), 0.12F, 1F);
                case KatanaAndroid.STATE_IAI_ZANSHIN -> 1 - ease(time, KatanaAndroid.IAI_SHEATHE_GUIDE, KatanaAndroid.IAI_SHEATHE_SEAT);
                case KatanaAndroid.STATE_IAI_VICTORY -> 1 - ease(time, KatanaAndroid.IAI_VICTORY_GUIDE, KatanaAndroid.IAI_VICTORY_SEAT);
                default -> inHand ? 1 : 0;
            };
        }
        return switch (state) {
            case KatanaAndroid.STATE_MAINTENANCE -> ease(time, KatanaAndroid.MAINTENANCE_DRAW, KatanaAndroid.MAINTENANCE_CLEAR)
                    * (1 - ease(time, KatanaAndroid.MAINTENANCE_GUIDE, KatanaAndroid.MAINTENANCE_SEAT));
            case KatanaAndroid.STATE_DRAW -> ease(time, 2, KatanaAndroid.DRAW_CLEAR);
            case KatanaAndroid.STATE_SHEATHE -> 1 - ease(time, 3, KatanaAndroid.SHEATHE_SEAT);
            default -> inHand ? 1 : 0;
        };
    }

    /** Zero while the tip is guided by the mouth, one while the sword follows the wrist freely. */
    static float handBlend(int state, float time, boolean crimson, boolean inHand) {
        if (crimson) {
            return switch (state) {
                case KatanaAndroid.STATE_IAI_FLASH -> ease(time, 1, 3);
                case KatanaAndroid.STATE_IAI_ZANSHIN -> 1 - ease(time, KatanaAndroid.IAI_RELEASE, KatanaAndroid.IAI_SHEATHE_GUIDE);
                case KatanaAndroid.STATE_IAI_VICTORY -> 1 - ease(time, KatanaAndroid.IAI_VICTORY_RETURN, KatanaAndroid.IAI_VICTORY_GUIDE);
                default -> inHand ? 1 : 0;
            };
        }
        return switch (state) {
            case KatanaAndroid.STATE_MAINTENANCE -> ease(time, KatanaAndroid.MAINTENANCE_CLEAR, KatanaAndroid.MAINTENANCE_WIPE)
                    * (1 - ease(time, KatanaAndroid.MAINTENANCE_RETURN, KatanaAndroid.MAINTENANCE_GUIDE));
            case KatanaAndroid.STATE_DRAW -> ease(time, KatanaAndroid.DRAW_CLEAR, 8);
            case KatanaAndroid.STATE_SHEATHE -> 1 - ease(time, 0, 3);
            default -> inHand ? 1 : 0;
        };
    }

    static boolean clothInHand(int state, float time) {
        return state == KatanaAndroid.STATE_MAINTENANCE && time >= KatanaAndroid.MAINTENANCE_CLOTH && time < KatanaAndroid.MAINTENANCE_STOW;
    }

    static boolean clothInHand(KatanaAndroid android, float pt) {
        if (clothInHand(android.state(), android.stateTime(pt))) return true;
        return clothInHand(KatanaAndroid.STATE_MAINTENANCE, android.maintenanceExitTime(pt)) && android.maintenanceExitProgress(pt) < 0.8F;
    }

    /** Work and combat own their right hand. An otherwise free hand can finish a partial insertion. */
    static float interruptedBladeTime(KatanaAndroid android, float pt) {
        float from = android.maintenanceExitTime(pt);
        if (android.drawn() || from <= KatanaAndroid.MAINTENANCE_DRAW || from >= KatanaAndroid.MAINTENANCE_SEAT) return -1;
        if (from < KatanaAndroid.MAINTENANCE_CLEAR) {
            from = Mth.lerp((from - KatanaAndroid.MAINTENANCE_DRAW) / (KatanaAndroid.MAINTENANCE_CLEAR - KatanaAndroid.MAINTENANCE_DRAW),
                    KatanaAndroid.MAINTENANCE_SEAT, KatanaAndroid.MAINTENANCE_GUIDE);
        }
        float time = Mth.lerp(android.maintenanceExitProgress(pt), from, KatanaAndroid.MAINTENANCE_SEAT);
        float current = exposure(android.state(), android.stateTime(pt), false, android.bladeInHand(pt));
        return exposure(KatanaAndroid.STATE_MAINTENANCE, time, false, false) > current ? time : -1;
    }

    static float clothUnfold(float time) {
        return ease(time, KatanaAndroid.MAINTENANCE_CLOTH, KatanaAndroid.MAINTENANCE_CLEAR)
                * (1 - ease(time, KatanaAndroid.MAINTENANCE_SEAT, KatanaAndroid.MAINTENANCE_STOW));
    }

    static float wipePosition(float time) {
        // 刀身上の位置。ひと拭きして布を少し浮かせ、中ほどから切っ先側へもうひと拭きする。
        return 0.10F + 0.49F * ease(time, KatanaAndroid.MAINTENANCE_WIPE, 47)
                - 0.36F * ease(time, 47, 53) + 0.49F * ease(time, 53, KatanaAndroid.MAINTENANCE_RETURN);
    }

    static float ease(float time, float start, float end) {
        float t = Mth.clamp((time - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
