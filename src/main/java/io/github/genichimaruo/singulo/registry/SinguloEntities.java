package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.ruin.HorizonBolt;
import io.github.genichimaruo.singulo.ruin.HorizonWarden;
import io.github.genichimaruo.singulo.ruin.SecurityDrone;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SinguloEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, Singulo.MODID);

    public static final Supplier<EntityType<SecurityDrone>> SECURITY_DRONE = REGISTER.register("security_drone",
            () -> EntityType.Builder.of(SecurityDrone::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.6F).clientTrackingRange(8).build(Singulo.id("security_drone").toString()));

    public static final Supplier<EntityType<HorizonWarden>> HORIZON_WARDEN = REGISTER.register("horizon_warden",
            () -> EntityType.Builder.of(HorizonWarden::new, MobCategory.MONSTER)
                    .sized(1.4F, 3.2F).clientTrackingRange(10).fireImmune().build(Singulo.id("horizon_warden").toString()));

    public static final Supplier<EntityType<HorizonBolt>> HORIZON_BOLT = REGISTER.register("horizon_bolt",
            () -> EntityType.Builder.<HorizonBolt>of(HorizonBolt::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(2).build(Singulo.id("horizon_bolt").toString()));

    /** ホライズン・ウォーデンの小型の特異点（保存しない）。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.ruin.WardenSingularity>> WARDEN_SINGULARITY = REGISTER.register(
            "warden_singularity", () -> EntityType.Builder.<io.github.genichimaruo.singulo.ruin.WardenSingularity>of(
                    io.github.genichimaruo.singulo.ruin.WardenSingularity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(8).updateInterval(10).noSave().fireImmune()
                    .build(Singulo.id("warden_singularity").toString()));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SECURITY_DRONE.get(), SecurityDrone.createAttributes().build());
        event.put(HORIZON_WARDEN.get(), HorizonWarden.createAttributes().build());
    }

    private SinguloEntities() {}
}
