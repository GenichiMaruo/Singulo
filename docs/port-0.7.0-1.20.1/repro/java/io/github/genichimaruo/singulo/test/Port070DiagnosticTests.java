package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;

/** Opt-in diagnostics only; ordinary build and publication never include this class. */
@GameTestHolder("singulo_port_checks")
@PrefixGameTestTemplate(false)
public final class Port070DiagnosticTests {
    public static void observe(GameTestHelper h) {
        h.onEachTick(() -> {
            if (h.getTick() % 100 != 0 && h.getTick() != 1) return;
            var box = new AABB(h.absolutePos(new BlockPos(-1, -1, -1)), h.absolutePos(new BlockPos(17, 17, 17)));
            for (LivingEntity entity : h.getLevel().getEntitiesOfClass(LivingEntity.class, box)) {
                Singulo.LOGGER.info("PORT070_DIAG helperTick={} entityTick={} ticking={} entity={} pos={} health={} death={}",
                        h.getTick(), entity.tickCount, h.getLevel().isPositionEntityTicking(entity.blockPosition()),
                        entity.getClass().getSimpleName(), entity.position(), entity.getHealth(), entity.deathTime);
                if (entity instanceof RuinAndroid a) {
                    Singulo.LOGGER.info("PORT070_ANDROID active={} charge={} state={} work={} checkpoint={}",
                            a.active(), a.charge(), a.state(), a.isWorking(), a.saveWorkState());
                    Singulo.LOGGER.info("PORT070_DAMAGE source={} attacker={}", a.getLastDamageSource(), a.getLastHurtByMob());
                }
            }
        });
    }
    @GameTest(template="empty", batch="port_defense", timeoutTicks=2400)
    public static void defense(GameTestHelper h) { AndroidGameTests.workingAndroidFightsBack(h); observe(h); }
    @GameTest(template="empty", batch="port_reload", timeoutTicks=2400)
    public static void reload(GameTestHelper h) { AndroidGameTests.androidResumesCraftAfterReload(h); observe(h); }
    @GameTest(template="empty", batch="port_mine", timeoutTicks=4000)
    public static void mine(GameTestHelper h) { AndroidGameTests.androidMinesWithProcedure(h); observe(h); }
    @GameTest(template="empty", batch="port_harvest", timeoutTicks=4000)
    public static void harvest(GameTestHelper h) { AndroidGameTests.androidHarvestsAndReplants(h); observe(h); }
    @GameTest(template="empty", batch="port_death", timeoutTicks=100)
    public static void death(GameTestHelper h) { BossRoomGameTests.sentinelDeathPlaysOut(h); observe(h); }
}
