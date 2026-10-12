package io.github.genichimaruo.singulo.ruin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/**
 * ボス部屋を包む力場（球）。張られている間は、中のプレイヤーは外へ出られず、外のプレイヤーは中へ入れない。
 * 歩いて越えようとしたときの押し戻しは張った番人が毎 tick 行い（{@link LastArchivist}）、ここではエンダーパールと
 * コーラスフルーツで力場を飛び越えるのを止める。創造・観戦モードとコマンドでの移動は止めない。
 * <p>
 * 張っている間は、部屋の床（床の高さで、中心から {@code floorRadius} 以内）を壊せない（プレイヤーも爆発も）。
 */
public final class ArenaShield {
    /** 張っている力場（張った番人の UUID → 力場）。 */
    private static final Map<UUID, Sphere> ACTIVE = new ConcurrentHashMap<>();

    public record Sphere(ResourceKey<Level> dimension, Vec3 center, double radius, int floorY, double floorRadius) {
        public boolean inside(Vec3 p) {
            return p.distanceToSqr(center) < radius * radius;
        }

        /** 守っている床のブロックか。 */
        public boolean floor(net.minecraft.core.BlockPos pos) {
            double dx = pos.getX() + 0.5 - center.x;
            double dz = pos.getZ() + 0.5 - center.z;
            return pos.getY() == floorY && dx * dx + dz * dz <= floorRadius * floorRadius;
        }
    }

    private ArenaShield() {}

    public static void raise(UUID owner, Level level, Vec3 center, double radius, int floorY, double floorRadius) {
        ACTIVE.put(owner, new Sphere(level.dimension(), center, radius, floorY, floorRadius));
    }

    public static void lower(UUID owner) {
        ACTIVE.remove(owner);
    }

    public static boolean active(UUID owner) {
        return ACTIVE.containsKey(owner);
    }

    /** 力場に縛られないプレイヤー（創造・観戦モード）。 */
    public static boolean exempt(Entity e) {
        return !(e instanceof Player p) || p.isCreative() || p.isSpectator();
    }

    /** from から to への移動が、どれかの力場を越えるか。 */
    public static boolean crosses(Level level, Vec3 from, Vec3 to) {
        for (Sphere s : ACTIVE.values()) {
            if (s.dimension() == level.dimension() && s.inside(from) != s.inside(to)) {
                return true;
            }
        }
        return false;
    }

    /** 張っている力場のどれかが守っている床か。 */
    public static boolean protectedFloor(Level level, net.minecraft.core.BlockPos pos) {
        for (Sphere s : ACTIVE.values()) {
            if (s.dimension() == level.dimension() && s.floor(pos)) {
                return true;
            }
        }
        return false;
    }

    /** 守っている床は、プレイヤーには壊せない（創造モードは除く）。 */
    public static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!ACTIVE.isEmpty() && event.getLevel() instanceof Level level && protectedFloor(level, event.getPos())
                && (event.getPlayer() == null || !event.getPlayer().isCreative())) {
            event.setCanceled(true);
        }
    }

    /** 守っている床は、爆発でも壊れない。 */
    public static void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (!ACTIVE.isEmpty()) {
            event.getAffectedBlocks().removeIf(pos -> protectedFloor(event.getLevel(), pos));
        }
    }

    /** エンダーパール・コーラスフルーツなどで力場を飛び越えるのを止める（コマンドでの移動は止めない）。 */
    public static void onTeleport(EntityTeleportEvent event) {
        if (event instanceof EntityTeleportEvent.TeleportCommand || event instanceof EntityTeleportEvent.SpreadPlayersCommand) {
            return;
        }
        Entity e = event.getEntity();
        if (exempt(e) || ACTIVE.isEmpty()) {
            return;
        }
        Vec3 from = e.position().add(0, e.getBbHeight() / 2, 0);
        Vec3 to = event.getTarget().add(0, e.getBbHeight() / 2, 0);
        if (crosses(e.level(), from, to)) {
            event.setCanceled(true);
        }
    }
}
