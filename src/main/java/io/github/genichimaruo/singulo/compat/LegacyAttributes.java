package io.github.genichimaruo.singulo.compat;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Backports the physical attributes used by Singulo to Forge 1.20.1. */
public final class LegacyAttributes {
    private static final String FLIGHT_GRANTED = "singulo:granted_flight";
    private record ExplosionMotion(net.minecraft.world.phys.Vec3 before, double resistance) {}
    private static final java.util.Map<net.minecraft.world.entity.LivingEntity, ExplosionMotion> EXPLOSION_MOTION = new java.util.WeakHashMap<>();
    public static final DeferredRegister<Attribute> REGISTER = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, "singulo");
    public static final RegistryObject<Attribute> FALL = REGISTER.register("fall_damage_multiplier", () -> new RangedAttribute("attribute.singulo.fall_damage_multiplier", 1, 0, 16).setSyncable(true));
    public static final RegistryObject<Attribute> FLIGHT = REGISTER.register("creative_flight", () -> new RangedAttribute("attribute.singulo.creative_flight", 0, 0, 1).setSyncable(true));
    public static final RegistryObject<Attribute> EXPLOSION = REGISTER.register("explosion_knockback_resistance", () -> new RangedAttribute("attribute.singulo.explosion_knockback_resistance", 0, 0, 1).setSyncable(true));
    public static void add(net.minecraftforge.event.entity.EntityAttributeModificationEvent event) {
        for (var type : event.getTypes()) { event.add(type, FALL.get()); event.add(type, FLIGHT.get()); event.add(type, EXPLOSION.get()); }
    }
    public static void fall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        var attribute = event.getEntity().getAttribute(FALL.get());
        if (attribute != null) event.setDamageMultiplier(event.getDamageMultiplier() * (float)attribute.getValue());
    }
    /** Forge exposes detonation before vanilla applies its velocity impulse. */
    public static void explosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) return;
        for (var entity : event.getAffectedEntities()) {
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                var attribute = living.getAttribute(EXPLOSION.get());
                if (attribute != null && attribute.getValue() > 0) {
                    EXPLOSION_MOTION.putIfAbsent(living, new ExplosionMotion(living.getDeltaMovement(), attribute.getValue()));
                }
            }
        }
    }
    public static void afterExplosions(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || event.level.isClientSide) return;
        var iterator = EXPLOSION_MOTION.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var entity = entry.getKey();
            if (entity.level() != event.level) continue;
            var motion = entry.getValue();
            entity.setDeltaMovement(motion.before().add(entity.getDeltaMovement().subtract(motion.before()).scale(1 - motion.resistance())));
            entity.hurtMarked = true;
            if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            }
            iterator.remove();
        }
    }
    public static void flight(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || event.player.level().isClientSide) return;
        var player = event.player;
        if (player.isCreative() || player.isSpectator()) {
            player.getPersistentData().remove(FLIGHT_GRANTED);
            return;
        }
        boolean enabled = player.getAttributeValue(FLIGHT.get()) > 0;
        if (enabled && !player.getAbilities().mayfly) {
            player.getPersistentData().putBoolean(FLIGHT_GRANTED, true);
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        } else if (!enabled && player.getPersistentData().getBoolean(FLIGHT_GRANTED)) {
            player.getPersistentData().remove(FLIGHT_GRANTED);
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }
    private LegacyAttributes() {}
}
