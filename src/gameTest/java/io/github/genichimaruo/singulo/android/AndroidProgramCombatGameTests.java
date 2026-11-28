package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * プログラムの戦い（0.24.0）: 持ち場のブロックの上で待ち、5ブロック以内に入ったモブとだけ、倒れるか範囲を出るまで戦う
 * （auto_fight(False) なので、範囲の外の敵には自分から向かわない）。
 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class AndroidProgramCombatGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos POST = new BlockPos(1, 1, 1);
    private static final String GUARD = """
            post = point("post")
            auto_fight(False)
            while True:
                go(post.up())
                for m in mobs(post, 5):
                    say(m.type, attack(m, post, 5))
                wait(0.25)
            """;

    private AndroidProgramCombatGameTests() {}

    /** 持ち場の石と、組立ステーション（動き方「コード」・プログラムカード・場所「post」）と、つないだアンドロイド。 */
    private static RuinAndroid guard(GameTestHelper h) {
        return guard(h, GUARD);
    }

    private static RuinAndroid guard(GameTestHelper h, String program) {
        return guard(h, program, java.util.UUID.randomUUID(), POST, POST.east(2));
    }

    /** 持ち主・持ち場・ステーションの場所を決めて（仲間のアンドロイドを並べるとき）。 */
    private static RuinAndroid guard(GameTestHelper h, String program, java.util.UUID owner, BlockPos post, BlockPos home) {
        h.setBlock(post, Blocks.STONE);
        h.setBlock(home, io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity station = h.getBlockEntity(home);
        station.press(AndroidStationBlockEntity.BUTTON_MODE_PROGRAM);
        net.minecraft.world.item.ItemStack card = new net.minecraft.world.item.ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.PROGRAM_CARD.get());
        io.github.genichimaruo.singulo.android.program.ProgramCardItem.setSource(card, program);
        station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_PROGRAM, card);
        net.minecraft.world.item.ItemStack place = new net.minecraft.world.item.ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.WORK_LOCATION_CARD.get());
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(place, h.getLevel(), h.absolutePos(post), Blocks.STONE);
        place.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("post"));
        station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_ROUTE, place);
        RuinAndroid a = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
        a.moveTo(Vec3.atBottomCenterOf(h.absolutePos(post.above())), 0, 0);
        h.getLevel().addFreshEntity(a);
        a.makeGuard(owner, h.absolutePos(home), RuinAndroid.MODE_PROGRAM, 0);
        a.setCharge(a.maxCharge());
        try {
            var field = AndroidStationBlockEntity.class.getDeclaredField("androids");
            field.setAccessible(true);
            ((java.util.UUID[]) field.get(station))[0] = a.getUUID();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return a;
    }

    private static Husk husk(GameTestHelper h, BlockPos at) {
        Husk husk = h.spawn(EntityType.HUSK, at);
        husk.setNoAi(true);
        husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        return husk;
    }

    /** 範囲に入ったモブを倒して、持ち場の上へ戻る。 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void programGuardDefeatsMobsInsideTheZone(GameTestHelper h) {
        RuinAndroid a = guard(h);
        Husk[] holder = new Husk[1];
        h.runAtTickTime(40, () -> holder[0] = husk(h, POST.offset(3, 0, 0)));
        h.succeedWhen(() -> {
            Husk husk = holder[0];
            h.assertTrue(husk != null, "まだ出していない");
            h.assertTrue(!husk.isAlive(), "範囲の中のモブを倒さない: " + husk.getHealth() + " at " + husk.position().subtract(Vec3.atCenterOf(h.absolutePos(POST))) + " log " + a.programLog() + " target " + a.getTarget() + " state " + a.state() + " android " + a.position().subtract(Vec3.atCenterOf(h.absolutePos(POST))) + " charge " + a.charge() + " hp " + a.getHealth() + " lastHurt " + husk.getLastDamageSource() + " inv " + husk.invulnerableTime + " status " + a.programStatus());
            h.assertTrue(a.programLog().contains("minecraft:husk True"), "倒した結果が違う: " + a.programLog());
            h.assertTrue(a.blockPosition().equals(h.absolutePos(POST.above())), "持ち場の上へ戻らない: " + a.blockPosition());
            a.discard();
        });
    }

    /** 範囲の外のモブには、近くにいても手を出さない（自分から戦わない）。 */
    @GameTest(template = EMPTY, timeoutTicks = 240)
    public static void programGuardIgnoresMobsOutsideTheZone(GameTestHelper h) {
        RuinAndroid a = guard(h);
        Husk[] holder = new Husk[1];
        // プログラムが動き出して auto_fight(False) になってから出す（ステーションが設定を渡すまでの間は、いつもどおり戦う）
        h.runAtTickTime(40, () -> holder[0] = husk(h, POST.offset(6, 0, 6)));
        h.runAtTickTime(200, () -> {
            Husk husk = holder[0];
            h.assertTrue(husk.isAlive() && husk.getHealth() == husk.getMaxHealth(), "範囲の外のモブを攻撃した: " + husk.getHealth() + " by " + husk.getLastDamageSource() + " at " + husk.position().subtract(Vec3.atCenterOf(h.absolutePos(POST))) + " log " + a.programLog());
            h.assertTrue(a.getTarget() == null, "範囲の外のモブを狙っている");
            h.assertTrue(a.programStatus() == AndroidProgram.P_RUNNING || a.programStatus() == AndroidProgram.P_WAITING,
                    "見張りが止まった: " + a.programStatus() + " " + a.programMessage());
            husk.discard();
            a.discard();
            h.succeed();
        });
    }

    /** 戦っている途中で相手が範囲を出たら、攻撃をやめて False。 */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void programGuardStopsWhenTheMobLeaves(GameTestHelper h) {
        RuinAndroid a = guard(h);
        Husk[] holder = new Husk[1];
        h.runAtTickTime(40, () -> {
            holder[0] = husk(h, POST.offset(3, 0, 0));
            holder[0].setInvulnerable(true);
        });
        boolean[] moved = {false};
        h.onEachTick(() -> {
            Husk husk = holder[0];
            if (!moved[0] && husk != null && a.getTarget() == husk) {
                moved[0] = true;
                husk.teleportTo(husk.getX() + 2, husk.getY(), husk.getZ() + 5);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(moved[0], "攻撃を始めない");
            h.assertTrue(a.programLog().contains("minecraft:husk False"), "範囲を出ても攻撃をやめない: " + a.programLog());
            h.assertTrue(a.getTarget() == null, "範囲を出た相手を狙い続ける");
            holder[0].discard();
            a.discard();
        });
    }

    /** 絞り込み: ["all", "!husk"] なら、範囲の中のハスクには手を出さず、ほかのモブ（牛）とだけ戦う。matches も同じ決まり。 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void programGuardFiltersTargets(GameTestHelper h) {
        RuinAndroid a = guard(h, """
                post = point("post")
                auto_fight(False)
                while True:
                    go(post.up())
                    for m in mobs(post, 5, ["all", "!husk"]):
                        say(m.type, matches(m, "animal"), matches(m, "#minecraft:undead"), attack(m, post, 5))
                    wait(0.25)
                """);
        Husk[] husk = new Husk[1];
        net.minecraft.world.entity.animal.Cow[] cow = new net.minecraft.world.entity.animal.Cow[1];
        h.runAtTickTime(40, () -> {
            husk[0] = husk(h, POST.offset(0, 0, 3));
            cow[0] = h.spawn(EntityType.COW, POST.offset(3, 0, 0));
            cow[0].setNoAi(true);
            cow[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        });
        h.succeedWhen(() -> {
            h.assertTrue(cow[0] != null && !cow[0].isAlive(), "絞り込みに合う牛と戦わない: " + a.programLog() + " " + a.programMessage());
            h.assertTrue(a.programLog().contains("minecraft:cow True False True"), "matches と結果が違う: " + a.programLog());
            h.assertTrue(husk[0].isAlive() && husk[0].getHealth() == husk[0].getMaxHealth(), "除いたハスクを攻撃した: " + husk[0].getHealth());
            husk[0].discard();
            a.discard();
        });
    }

    /**
     * go は隣で止まらず、その場所の真ん中まで行く（床の上の場所でも、一段高いブロックの上でも）。
     * テンプレートは空気だけで、床はその1つ下にあるので、持ち場を床に置いた一段の段差にする。
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void programGoStandsRightOnTheSpot(GameTestHelper h) {
        RuinAndroid a = guard(h, """
                step = point("post").down()
                say(go(step.south(3)), here() == step.south(3))
                say(go(step.up()), here() == step.up())
                """);
        h.setBlock(POST, Blocks.AIR);
        h.setBlock(POST.below(), Blocks.STONE);
        h.succeedWhen(() -> {
            h.assertTrue(a.programStatus() == AndroidProgram.P_DONE && !a.programLog().contains("False") && a.programLog().split("True True", -1).length == 3,
                    "真ん中まで行かない: " + a.programStatus() + " " + a.programMessage() + " log " + a.programLog() + " at " + a.position());
            Vec3 d = Vec3.atBottomCenterOf(h.absolutePos(POST)).subtract(a.position());
            h.assertTrue(a.blockPosition().equals(h.absolutePos(POST)) && d.horizontalDistance() < 0.5, "段の真上に乗らない: " + d);
            a.discard();
        });
    }

    /**
     * 仲間と守る: 見張りは敵を見つけたら send で知らせるだけ（自分では戦わない）。離れた持ち場の仲間は receive で受け、
     * 自分の見張りの範囲の外の敵を、知らせた見張りの所まで倒しに行く。持ち主の違うアンドロイドには届かない。
     */
    @GameTest(template = EMPTY, timeoutTicks = 500)
    public static void programTeammatesAnswerAMessage(GameTestHelper h) {
        java.util.UUID owner = java.util.UUID.randomUUID();
        RuinAndroid lookout = guard(h, """
                post = point("post")
                auto_fight(False)
                while True:
                    for m in mobs(post, 4, "hostile"):
                        say("sent", send("enemy", m, 32))
                    wait(1)
                """, owner, POST, POST.east(2));
        String helper = """
                post = point("post")
                auto_fight(False)
                while True:
                    go(post.up())
                    msg = receive("enemy")
                    if msg and msg.data.alive:
                        say("help", msg.topic, msg.sender != "", attack(msg.data, msg.pos, 12))
                    wait(0.25)
                """;
        BlockPos far = new BlockPos(6, 1, 6);
        RuinAndroid friend = guard(h, helper, owner, far, far.north(2));
        RuinAndroid stranger = guard(h, helper, java.util.UUID.randomUUID(), new BlockPos(1, 1, 6), new BlockPos(1, 1, 4));
        Husk[] husk = new Husk[1];
        h.runAtTickTime(40, () -> husk[0] = husk(h, POST.offset(3, 0, 0)));
        h.succeedWhen(() -> {
            h.assertTrue(husk[0] != null && !husk[0].isAlive(), "知らせを受けた仲間が倒さない: lookout " + lookout.programLog() + " / friend "
                    + friend.programLog() + " " + friend.programMessage());
            h.assertTrue(lookout.programLog().contains("sent 1"), "仲間の1体だけに届くはず: " + lookout.programLog());
            h.assertTrue(friend.programLog().contains("help enemy True True"), "受けた知らせが違う: " + friend.programLog());
            h.assertTrue(stranger.programLog().isEmpty(), "持ち主の違うアンドロイドに届いた: " + stranger.programLog());
            lookout.discard();
            friend.discard();
            stranger.discard();
        });
    }

    /** lever(p, on): レバーを決めた向きにする。もうその向きなら触らない。レバーでないブロックは誤り。 */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void programSetsLevers(GameTestHelper h) {
        BlockPos at = POST.south(3).below();
        h.setBlock(at, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACE,
                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        RuinAndroid a = guard(h, """
                lv = point("post").south(3).down()
                say(lever(lv, True), redstone(lv) > 0)
                say(lever(lv, True))
                say(lever(lv, False), redstone(lv))
                lever(lv, True)
                lever(point("post"), True)
                """);
        h.succeedWhen(() -> {
            h.assertTrue(a.programStatus() == AndroidProgram.P_ERROR && a.programLine() == 6 && a.programMessage().contains("not_lever"),
                    "レバーでないブロックで止まらない: " + a.programStatus() + " " + a.programLine() + " " + a.programMessage() + " log " + a.programLog());
            h.assertTrue(a.programLog().equals("True True" + "\u0002" + "True" + "\u0002" + "True 0")
                            || a.programLog().replace("\u0002", "|").equals("True True|True|True 0"),
                    "レバーの結果が違う: " + a.programLog().replace("\u0002", "|"));
            h.assertTrue(h.getBlockState(at).getValue(net.minecraft.world.level.block.LeverBlock.POWERED), "最後に入れたレバーが入っていない");
            a.discard();
        });
    }

    /** 分からない絞り込みは、その行の誤りで止まる。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void programStopsOnUnknownFilters(GameTestHelper h) {
        RuinAndroid a = guard(h, "post = point('post')\nx = mobs(post, 5, ['hostile', '!not_a_mob'])\n");
        h.succeedWhen(() -> {
            h.assertTrue(a.programStatus() == AndroidProgram.P_ERROR && a.programLine() == 2 && a.programMessage().contains("bad_filter"),
                    "分からない絞り込みで止まらない: " + a.programStatus() + " " + a.programMessage());
            a.discard();
        });
    }
}
