package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidKind;
import io.github.genichimaruo.singulo.android.AndroidStationBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RougeMemoryGameTests {
    // The first nextFloat() for these seeds is 0.24998772 and 0.25000203 respectively.
    private static final long JUST_BELOW_QUARTER = 85270L;
    private static final long JUST_ABOVE_QUARTER = 7010L;

    private RougeMemoryGameTests() {}

    private static EchoSentinel defeat(GameTestHelper helper, boolean awakened, boolean minion, long seed) {
        ServerLevel level = helper.getLevel();
        EchoSentinel boss = new EchoSentinel(SinguloEntities.ECHO_SENTINEL.get(), level) {
            private long nextSeed = seed;

            @Override
            protected void dropAwakenedBonus(ServerLevel server) {
                // Keep the real death and loot path. Isolate this roll from unrelated vanilla loot RNG.
                getRandom().setSeed(nextSeed);
                nextSeed = seed == JUST_BELOW_QUARTER ? JUST_ABOVE_QUARTER : JUST_BELOW_QUARTER;
                super.dropAwakenedBonus(server);
            }
        };
        if (awakened) boss.makeAwakened();
        if (minion) boss.makeMinion();
        boss.setParticipants(4);
        boss.awaken(helper.absolutePos(new BlockPos(2, 1, 2)));
        boss.skipEmerging();
        boss.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(9, 2, 8))));
        level.addFreshEntity(boss);
        boss.setHealth(0);
        boss.die(level.damageSources().generic());
        return boss;
    }

    private static void advanceDeath(EchoSentinel boss, int tick) {
        while (boss.deathTime < tick && !boss.isRemoved()) boss.tickDeath();
    }

    private static List<ItemEntity> memories(ServerLevel level, EchoSentinel boss) {
        var memory = BuiltInRegistries.ITEM.get(Singulo.id(AndroidKind.KATANA.memoryId()));
        return level.getEntitiesOfClass(ItemEntity.class, boss.getBoundingBox().inflate(12), e -> e.getItem().is(memory));
    }

    @GameTest(template = "huge", batch = "rouge_memory_eligibility")
    public static void rougeMemoryRequiresWinningAwakenedBoss(GameTestHelper helper) {
        var ordinary = defeat(helper, false, false, JUST_BELOW_QUARTER);
        var projection = defeat(helper, true, true, JUST_BELOW_QUARTER);
        // Finish the minion before another boss dies and dismisses nearby projections.
        advanceDeath(projection, EchoSentinel.DEATH_TICKS);
        var miss = defeat(helper, true, false, JUST_ABOVE_QUARTER);
        // Even a second death notification with a winning seed cannot turn a miss into a reward.
        miss.die(helper.getLevel().damageSources().generic());
        for (EchoSentinel boss : List.of(ordinary, projection, miss)) {
            helper.assertFalse(boss.rougeMemoryAwarded(), "通常戦・分身・落選時に紅い一閃が有効になった");
            advanceDeath(boss, EchoSentinel.DEATH_TICKS);
            helper.assertTrue(memories(helper.getLevel(), boss).isEmpty(), "当選していないのに刀型メモリが落ちた");
        }
        helper.succeed();
    }

    private static EchoSentinel reload(ServerLevel level, EchoSentinel boss) {
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.discard();
        EchoSentinel restored = SinguloEntities.ECHO_SENTINEL.get().create(level);
        restored.load(saved);
        level.addFreshEntity(restored);
        return restored;
    }

    @GameTest(template = "huge", batch = "rouge_memory_reveal")
    public static void rougeMemoryAppearsOnceAtSlashedProjectionAfterReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var boss = defeat(helper, true, false, JUST_BELOW_QUARTER);
        helper.assertTrue(boss.rougeMemoryAwarded(), "当選した番人に紅い一閃の演出がない");
        advanceDeath(boss, EchoSentinel.ROUGE_SLASH_TICK);
        boss = reload(level, boss);
        helper.assertTrue(boss.rougeMemoryAwarded(), "残像の途中で再読込すると当選が消える");
        advanceDeath(boss, EchoSentinel.ROUGE_REVEAL_TICK - 1);
        helper.assertTrue(memories(level, boss).isEmpty(), "紅い一閃の途中に刀型メモリが出た");
        advanceDeath(boss, EchoSentinel.ROUGE_REVEAL_TICK);
        var drops = memories(level, boss);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1,
                "複数人の勝利で刀型メモリが1個だけ残らなかった");
        helper.assertTrue(AndroidStationBlockEntity.memoryKind(drops.getFirst().getItem()) == AndroidKind.KATANA,
                "落ちたメモリを組立ステーションが刀型の設計として読み取れない");
        Vec3 pos = drops.getFirst().position();
        helper.assertTrue(Math.abs(pos.x - boss.getX()) < 0.01 && Math.abs(pos.z - boss.getZ()) < 0.01
                && pos.y > boss.getY() && pos.y < boss.getY() + boss.getBbHeight(), "残像の位置から刀型メモリが出ていない");
        boss = reload(level, boss);
        advanceDeath(boss, EchoSentinel.DEATH_TICKS);
        drops = memories(level, boss);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1, "再読込後に刀型メモリが重複した");
        helper.succeed();
    }
}
