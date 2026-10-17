package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntityType;

/** アンドロイドの型（印刷台や設計メモリが、どの型を印刷するか）。並びの位置を保存に使うので、新しい型は最後に足す。 */
public enum AndroidKind {
    BLADE("blade", SinguloEntities.BLADE_ANDROID, Origin.RUIN, 1),
    WHIP("whip", SinguloEntities.WHIP_ANDROID, Origin.RUIN, 1),
    SABER("saber", SinguloEntities.SABER_ANDROID, Origin.RUIN, 1),
    /** 地下アンドロイド研究所の試作機（遺構の印刷台からは出ない。設計メモリは研究所の統括AIが落とす）。 */
    AXE("axe", SinguloEntities.AXE_ANDROID, Origin.PROTOTYPE, 1),
    BIT("bit", SinguloEntities.BIT_ANDROID, Origin.PROTOTYPE, 1),
    /**
     * 射撃型（隠し型）。遺構の印刷台にも、ふつうの研究所の報酬にも出ない。設計メモリは覚醒したファウンドリの隠し報酬で、
     * スポーンエッグと設計メモリはクリエイティブタブにも JEI にも出さない。
     * 珍しい分、バックパックを内蔵し（運べる欄が最初から多い）、充電の満タンが1.5倍。その代わり、印刷の電力が3倍かかる。
     */
    GUNNER("gunner", SinguloEntities.GUNNER_ANDROID, Origin.HIDDEN, 3);

    /** どこで手に入る型か（遺構の印刷台・研究所の試作機・隠し）。 */
    public enum Origin { RUIN, PROTOTYPE, HIDDEN }

    public final String id;
    private final Supplier<? extends EntityType<? extends RuinAndroid>> type;
    public final Origin origin;
    /** 遺構の印刷台が印刷する型か。 */
    public final boolean ruinPrinted;
    /** 組立ステーションで印刷するときの電力の倍率。 */
    public final int printCost;

    AndroidKind(String id, Supplier<? extends EntityType<? extends RuinAndroid>> type, Origin origin, int printCost) {
        this.id = id;
        this.type = type;
        this.origin = origin;
        this.printCost = printCost;
        this.ruinPrinted = origin == Origin.RUIN;
    }

    /** 遺構の印刷台が印刷する型。 */
    public static AndroidKind[] ruinKinds() {
        return java.util.Arrays.stream(values()).filter(k -> k.origin == Origin.RUIN).toArray(AndroidKind[]::new);
    }

    /** 研究所の試作機の型。 */
    public static AndroidKind[] prototypes() {
        return java.util.Arrays.stream(values()).filter(k -> k.origin == Origin.PROTOTYPE).toArray(AndroidKind[]::new);
    }

    public EntityType<? extends RuinAndroid> type() {
        return type.get();
    }

    /** 愛称（ルミナ・ヴェルナ・ソレイユ・アルマ・ノクス・シエル）の翻訳キー。 */
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
