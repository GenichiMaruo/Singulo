package io.github.genichimaruo.singulo.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.Singulo;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 進捗の条件。singulo:milestone は Singulo の出来事（id）で達成する:
 * formed/&lt;マルチブロック&gt;・discover/&lt;遺構&gt;・ignite・wormhole_open・dark_matter・records_all。
 */
public final class SinguloTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> REGISTER = DeferredRegister.create(Registries.TRIGGER_TYPE, Singulo.MODID);

    public static final Supplier<Milestone> MILESTONE = REGISTER.register("milestone", Milestone::new);

    private SinguloTriggers() {}

    /** 1人に出来事を知らせる。 */
    public static void milestone(ServerPlayer player, String id) {
        MILESTONE.get().trigger(player, id);
    }

    /** pos から radius 以内のプレイヤーみんなに知らせる。 */
    public static void milestoneNear(Level level, BlockPos pos, double radius, String id) {
        if (level instanceof ServerLevel server) {
            for (ServerPlayer p : server.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(radius))) {
                milestone(p, id);
            }
        }
    }

    public static final class Milestone extends SimpleCriterionTrigger<Milestone.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, String id) {
            trigger(player, i -> i.id.equals(id));
        }

        public record Instance(Optional<ContextAwarePredicate> player, String id) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.fieldOf("id").forGetter(Instance::id)
            ).apply(i, Instance::new));
        }
    }
}
