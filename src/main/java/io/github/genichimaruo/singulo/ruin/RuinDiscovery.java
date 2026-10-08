package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 遺構の発見。保管庫を自分の手で開くと、その遺構を発見したことになる（プレイヤーごと、死んでも消えない）。
 * 自動探査機は、発見済みの遺構にしか飛べない（発見そのものは必ず手動）。
 */
public final class RuinDiscovery {
    public record Discovered(String ruin, BlockPos pos) {
        public static final Codec<Discovered> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("ruin").forGetter(Discovered::ruin),
                BlockPos.CODEC.fieldOf("pos").forGetter(Discovered::pos)
        ).apply(i, Discovered::new));
    }

    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Singulo.MODID);

    public static final Supplier<AttachmentType<List<Discovered>>> DISCOVERED = REGISTER.register("discovered_ruins",
            () -> AttachmentType.<List<Discovered>>builder(() -> new ArrayList<>())
                    .serialize(Discovered.CODEC.listOf().xmap(ArrayList::new, l -> l)).copyOnDeath().build());

    /**
     * 遺構のティア（その回収物が使われる触媒の段階）。段階Nの探査機は「N − probeAutomationOffset」以下の遺構に飛べる
     * （設計書の N−2 ルール。最終実験施設と封鎖培養施設は常に手動）。
     */
    static final Map<String, Integer> RUIN_TIER = Map.of(
            "observation_post", 2, "research_building", 3, "culture_facility", 4, "final_lab", 5);
    /** 自動化できない遺構。 */
    static final java.util.Set<String> ALWAYS_MANUAL = java.util.Set.of("culture_facility", "final_lab");

    private RuinDiscovery() {}

    public static void record(Player player, String ruin, BlockPos pos) {
        if (ruin.isEmpty()) {
            return;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            io.github.genichimaruo.singulo.registry.SinguloTriggers.milestone(sp, "discover/" + ruin);
        }
        List<Discovered> list = player.getData(DISCOVERED);
        Discovered d = new Discovered(ruin, pos.immutable());
        if (!list.contains(d)) {
            List<Discovered> copy = new ArrayList<>(list);
            copy.add(d);
            player.setData(DISCOVERED, copy);
        }
    }

    public static List<Discovered> discovered(Player player) {
        return player.getData(DISCOVERED);
    }

    /** 段階 stationTier の探査機がこの遺構に飛べるか。 */
    public static boolean automatable(String ruin, int stationTier) {
        if (ALWAYS_MANUAL.contains(ruin)) {
            return false;
        }
        int offset = ServerConfig.SPEC.isLoaded() ? ServerConfig.PROBE_AUTOMATION_OFFSET.get() : 2;
        return RUIN_TIER.getOrDefault(ruin, 99) <= stationTier - offset;
    }
}
