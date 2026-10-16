package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntityType;

/** アンドロイドの型（印刷台や設計メモリが、どの型を印刷するか）。 */
public enum AndroidKind {
    BLADE("blade", SinguloEntities.BLADE_ANDROID, true),
    WHIP("whip", SinguloEntities.WHIP_ANDROID, true),
    SABER("saber", SinguloEntities.SABER_ANDROID, true),
    /** 地下アンドロイド研究所の試作機（遺構の印刷台からは出ない。設計メモリは研究所の統括AIが落とす）。 */
    AXE("axe", SinguloEntities.AXE_ANDROID, false),
    BIT("bit", SinguloEntities.BIT_ANDROID, false);

    public final String id;
    private final Supplier<? extends EntityType<? extends RuinAndroid>> type;
    /** 遺構の印刷台が印刷する型か。 */
    public final boolean ruinPrinted;

    AndroidKind(String id, Supplier<? extends EntityType<? extends RuinAndroid>> type, boolean ruinPrinted) {
        this.id = id;
        this.type = type;
        this.ruinPrinted = ruinPrinted;
    }

    /** 遺構の印刷台が印刷する型。 */
    public static AndroidKind[] ruinKinds() {
        return java.util.Arrays.stream(values()).filter(k -> k.ruinPrinted).toArray(AndroidKind[]::new);
    }

    /** 研究所の試作機の型。 */
    public static AndroidKind[] prototypes() {
        return java.util.Arrays.stream(values()).filter(k -> !k.ruinPrinted).toArray(AndroidKind[]::new);
    }

    public EntityType<? extends RuinAndroid> type() {
        return type.get();
    }

    /** 愛称（ルミナ・ヴェルナ・ソレイユ・アルマ・ノクス）の翻訳キー。 */
    public String nicknameKey() {
        return "gui.singulo.android.nickname." + id;
    }

    /** この型の設計メモリのアイテムID（singulo:android_memory_blade など）。 */
    public String memoryId() {
        return "android_memory_" + id;
    }

    @Nullable
    public static AndroidKind byId(String id) {
        for (AndroidKind k : values()) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return null;
    }
}
