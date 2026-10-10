package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cable.CableBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.reactor.BlackHoleBomb;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.ruin.HorizonWarden;
import io.github.genichimaruo.singulo.ruin.SecurityDrone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ケーブルの通電の記録・装置の電源と材料なしのスイッチ・液体の出力タンクごとの面・ウォーデンの守り。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ControlGameTests {
    private static final String EMPTY = "empty";

    private ControlGameTests() {}

    /** 電力が通った道のケーブルだけが、入ってきた面と出ていった面を記録する（行き止まりの枝は光らない）。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void cableRecordsOnlyTheActualPath(GameTestHelper helper) {
        BlockPos gen = new BlockPos(1, 1, 1);
        helper.setBlock(gen, SinguloBlocks.THERMOELECTRIC_GENERATOR.get());
        helper.setBlock(gen.west(), Blocks.CAMPFIRE);
        helper.setBlock(gen.above(), Blocks.BLUE_ICE);
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), SinguloBlocks.COPPER_WIRE.get());
        }
        // 行き止まりの枝
        helper.setBlock(new BlockPos(3, 1, 2), SinguloBlocks.COPPER_WIRE.get());
        helper.setBlock(new BlockPos(3, 1, 3), SinguloBlocks.COPPER_WIRE.get());
        helper.setBlock(new BlockPos(5, 1, 1), SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        helper.succeedWhen(() -> {
            CableBlockEntity middle = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, new BlockPos(3, 1, 1));
            CableBlockEntity last = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, new BlockPos(4, 1, 1));
            CableBlockEntity branch = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, new BlockPos(3, 1, 3));
            Direction east = Direction.EAST;
            helper.assertTrue(middle.flow() > 0, "電力が通っているのに記録されない");
            helper.assertTrue(middle.flowsIn(east.getOpposite()) && middle.flowsOut(east), "入った面・出た面が違う");
            helper.assertTrue(!middle.flowsOut(Direction.SOUTH), "電力の通らない枝の側まで光る");
            helper.assertTrue(last.flowsOut(east) && last.distance() > middle.distance(), "出口側のケーブルの記録が違う");
            helper.assertTrue(branch.flow() == 0, "行き止まりの枝が光る");
        });
    }

    /** 電源を切った装置は動かず、入れ直すと動く。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void powerSwitchStopsMachine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity m = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, pos);
        m.energy().setEnergy(m.energy().getMaxEnergyStored());
        m.togglePower();
        m.automationFluids().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(m.status() == MachineBlockEntity.Status.OFF, "電源を切っても止まらない: " + m.status());
            helper.assertTrue(m.automationFluids().drain(new FluidStack(SinguloFluids.get("hydrogen"), 1000),
                    IFluidHandler.FluidAction.SIMULATE).isEmpty(), "電源を切っているのに水素ができた");
            m.togglePower();
        });
        helper.runAfterDelay(61, () -> helper.succeedWhen(() -> helper.assertFalse(m.automationFluids().drain(
                new FluidStack(SinguloFluids.get("hydrogen"), 1000), IFluidHandler.FluidAction.SIMULATE).isEmpty(),
                "電源を入れ直しても動かない")));
    }

    /** 材料なしのレシピ（電力だけで作るもの）を見分けられる。 */
    @GameTest(template = EMPTY)
    public static void freeRecipesAreRecognized(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(
                io.github.genichimaruo.singulo.registry.SinguloRecipes.MACHINE.get());
        boolean any = recipes.stream().anyMatch(h -> MachineBlockEntity.isFree(h.value())
                && h.value().station().equals(MachineType.CRYOGENIC_COOLING_TOWER.id()));
        helper.assertTrue(any, "冷却塔の液体窒素が材料なしのレシピとして見つからない");
        helper.assertTrue(recipes.stream().filter(h -> h.value().station().equals(MachineType.KILN.id()))
                .noneMatch(h -> MachineBlockEntity.isFree(h.value())), "材料のあるレシピを材料なしと見なした");
        helper.succeed();
    }

    /** 電解槽は、タンクごとに面を決められる: 水を入れる面、水素と酸素を出す面を別々に。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void electrolyzerOutputsEachGasOnItsOwnFace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity m = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, pos);
        helper.assertTrue(m.sideChannels() == 1 + m.type().tanks(), "タンクごとの面の設定がない");
        for (SideConfig.Face f : SideConfig.Face.values()) {
            m.setSide(1, f, f == SideConfig.Face.FRONT ? SideConfig.INPUT : SideConfig.NONE, false);
        }
        net.minecraft.core.Direction front = m.getBlockState().getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING);
        IFluidHandler back = Capabilities.get(helper.getLevel(), Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), front.getOpposite());
        helper.assertTrue(back == null || back.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.SIMULATE) == 0,
                "入力にしていない面から水が入る");
        IFluidHandler frontHandler = Capabilities.get(helper.getLevel(), Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), front);
        helper.assertTrue(frontHandler != null && frontHandler.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.SIMULATE) > 0,
                "入力にした面から水が入らない");
        for (SideConfig.Face f : SideConfig.Face.values()) {
            m.setSide(2, f, f == SideConfig.Face.TOP ? SideConfig.OUTPUT : SideConfig.NONE, false);
            m.setSide(3, f, f == SideConfig.Face.BOTTOM ? SideConfig.OUTPUT : SideConfig.NONE, false);
        }
        m.energy().setEnergy(m.energy().getMaxEnergyStored());
        m.automationFluids().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> {
            IFluidHandler top = Capabilities.get(helper.getLevel(), Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
            IFluidHandler bottom = Capabilities.get(helper.getLevel(), Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), Direction.DOWN);
            helper.assertTrue(top != null && bottom != null, "面から液体に届かない");
            FluidStack fromTop = top.drain(1000, IFluidHandler.FluidAction.SIMULATE);
            FluidStack fromBottom = bottom.drain(1000, IFluidHandler.FluidAction.SIMULATE);
            helper.assertFalse(fromTop.isEmpty() || fromBottom.isEmpty(), "まだ出ていない");
            helper.assertTrue(!fromTop.getFluid().isSame(fromBottom.getFluid()), "上と下から同じ気体が出る");
            IFluidHandler side = Capabilities.get(helper.getLevel(), Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), Direction.EAST);
            helper.assertTrue(side == null || side.drain(1000, IFluidHandler.FluidAction.SIMULATE).isEmpty(),
                    "出力にしていない面から気体が出る");
        });
    }

    /** ウォーデンと警備ドローンは互いの攻撃で傷つかず、互いを味方とみなす。 */
    @GameTest(template = EMPTY)
    public static void guardsDoNotHurtEachOther(GameTestHelper helper) {
        HorizonWarden warden = helper.spawn(SinguloEntities.HORIZON_WARDEN.get(), new BlockPos(2, 1, 2));
        SecurityDrone drone = helper.spawn(SinguloEntities.SECURITY_DRONE.get(), new BlockPos(6, 2, 6));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 1, 2));
        float droneHealth = drone.getHealth();
        float wardenHealth = warden.getHealth();
        helper.assertFalse(drone.hurt(warden.damageSources().mobAttack(warden), 10), "ウォーデンの攻撃でドローンが傷つく");
        helper.assertFalse(warden.hurt(drone.damageSources().mobAttack(drone), 10), "ドローンの攻撃でウォーデンが傷つく");
        helper.assertTrue(drone.getHealth() == droneHealth && warden.getHealth() == wardenHealth, "体力が減った");
        helper.assertTrue(warden.isAlliedTo(drone) && drone.isAlliedTo(warden), "味方とみなさない");
        helper.assertTrue(zombie.hurt(warden.damageSources().mobAttack(warden), 1), "ほかのモブには攻撃が当たらない");
        helper.succeed();
    }

    /** ウォーデンは、飛んでくるブラックホール爆弾を届く前に打ち返し、近くでは開かせない。 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void wardenDeflectsBlackHoleBomb(GameTestHelper helper) {
        HorizonWarden warden = helper.spawn(SinguloEntities.HORIZON_WARDEN.get(), new BlockPos(4, 1, 4));
        Zombie thrower = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 0));
        BlackHoleBomb bomb = new BlackHoleBomb(helper.getLevel(), thrower);
        Vec3 from = helper.absoluteVec(new Vec3(4.5, 3, 12.5));
        bomb.setPos(from);
        bomb.setDeltaMovement(0, 0, -1.2);
        helper.getLevel().addFreshEntity(bomb);
        helper.succeedWhen(() -> {
            helper.assertTrue(bomb.deflectedBy(warden), "爆弾を打ち返さない");
            boolean holeNear = !helper.getLevel().getEntitiesOfClass(io.github.genichimaruo.singulo.reactor.MicroBlackHole.class,
                    warden.getBoundingBox().inflate(HorizonWarden.BOMB_SAFE_DISTANCE - 1)).isEmpty();
            helper.assertFalse(holeNear, "ウォーデンの近くでブラックホールが開いた");
        });
    }
}
