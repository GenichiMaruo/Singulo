package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntityType;

/** アンドロイドの型（印刷台や設計メモリが、どの型を印刷するか）。 */
public enum AndroidKind {
    BLADE("blade", SinguloEntities.BLADE_ANDROID),
    WHIP("whip", SinguloEntities.WHIP_ANDROID),
    SABER("saber", SinguloEntities.SABER_ANDROID);

    public final String id;
    private final Supplier<? extends EntityType<? extends RuinAndroid>> type;

    AndroidKind(String id, Supplier<? extends EntityType<? extends RuinAndroid>> type) {
        this.id = id;
        this.type = type;
    }

    public EntityType<? extends RuinAndroid> type() {
        return type.get();
    }

    /** 愛称（ルミナ・ヴェルナ・ソレイユ）の翻訳キー。 */
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
