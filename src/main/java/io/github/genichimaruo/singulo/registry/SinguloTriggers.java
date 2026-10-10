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
import net.minecraftforge.registries.DeferredRegister;

/**
 * 進捗の条件。singulo:milestone は Singulo の出来事（id）で達成する:
 * formed/&lt;マルチブロック&gt;・discover/&lt;遺構&gt;・ignite・wormhole_open・dark_matter・records_all。
 */
public final class SinguloTriggers {
    private static final Milestone TRIGGER = new Milestone();
    public static final Supplier<Milestone> MILESTONE = () -> TRIGGER;
    public static void register() { net.minecraft.advancements.CriteriaTriggers.register(TRIGGER); }

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
        public net.minecraft.resources.ResourceLocation getId() { return Singulo.id("milestone"); }
        protected Instance createInstance(com.google.gson.JsonObject json, ContextAwarePredicate player,
                net.minecraft.advancements.critereon.DeserializationContext context) {
            return new Instance(player, json.get("id").getAsString());
        }
        public void trigger(ServerPlayer player, String id) { trigger(player, i -> i.id.equals(id)); }
        public static final class Instance extends net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance {
            private final String id;
            public Instance(ContextAwarePredicate player, String id) { super(Singulo.id("milestone"), player); this.id = id; }
            public com.google.gson.JsonObject serializeToJson(net.minecraft.advancements.critereon.SerializationContext context) {
                var json = super.serializeToJson(context); json.addProperty("id", id); return json;
            }
        }
    }
}
