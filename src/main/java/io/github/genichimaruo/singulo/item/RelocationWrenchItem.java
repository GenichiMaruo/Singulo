package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.machine.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Relocates processing machines whose complete state is saved by MachineBlockEntity.
 *
 * <p>Two modes, switched by sneak-using the wrench in the air: relocation (sneak-use a device to pack it) and
 * cable removal (use a cable to take it back at once, sneak-use to take the whole connected run of that cable,
 * and left-click breaks cables instantly).
 */
public final class RelocationWrenchItem extends SinguloItem {
    public static final int MODE_RELOCATE = 0, MODE_CABLE = 1;
    private static final String MODE_KEY = "wrench_mode";
    /** Upper bound for one sneak-use in cable mode, so a huge grid is not removed by accident. */
    public static final int MAX_CABLE_RUN = 64;
    // Explicitly audited save/load pairs. Do not infer support from arbitrary block inheritance.
    private static final java.util.Set<Class<?>> DEVICES = java.util.Set.of(
            ThermoelectricGeneratorBlockEntity.class, SmesCellBlockEntity.class, SmesCellBlockEntity.Module.class,
            HydrogenFuelCellBlockEntity.class, CryogenicTurbineBlockEntity.class, MuonFusionReactorBlockEntity.class,
            CosmicMuonCollectorBlockEntity.class, ProbeStationBlockEntity.class, DegenerateFurnaceBlockEntity.class,
            QuantumHeatEngineBlockEntity.class, InertialStabilizerBlockEntity.class,
            ShieldTowerBlockEntity.class, TiplerCylinderBlockEntity.class,
            WorldlineAnchorBlockEntity.class, AdvancedWorldlineAnchorBlockEntity.class);

    public RelocationWrenchItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    public static int mode(ItemStack wrench) {
        var data = wrench.getTag();
        return data != null && data.getInt(MODE_KEY) == MODE_CABLE ? MODE_CABLE : MODE_RELOCATE;
    }

    public static void setMode(ItemStack wrench, int mode) {
        wrench.getOrCreateTag().putInt(MODE_KEY, mode);
    }

    /** Sneak-use in the air switches the mode. */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack wrench = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) return net.minecraft.world.InteractionResultHolder.pass(wrench);
        if (!level.isClientSide) {
            int next = mode(wrench) == MODE_CABLE ? MODE_RELOCATE : MODE_CABLE;
            setMode(wrench, next);
            player.displayClientMessage(Component.translatable("item.singulo.relocation_wrench.mode." + next), true);
            level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, next == MODE_CABLE ? 1.4F : 1.0F);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(wrench, level.isClientSide);
    }

    /** Cable mode breaks cables instantly with a left click. */
    @Override
    public float getDestroySpeed(ItemStack wrench, net.minecraft.world.level.block.state.BlockState state) {
        return mode(wrench) == MODE_CABLE && state.getBlock() instanceof io.github.genichimaruo.singulo.cable.CableBlock
                ? 1000F : super.getDestroySpeed(wrench, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, java.util.List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int mode = mode(stack);
        tooltip.add(Component.translatable("item.singulo.relocation_wrench.mode." + mode)
                .withStyle(mode == MODE_CABLE ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.singulo.relocation_wrench.hint." + mode).withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.singulo.relocation_wrench.switch").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack wrench, UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (player != null && mode(wrench) == MODE_CABLE) return removeCables(wrench, context);
        if (player == null || !player.isSecondaryUseActive()) return InteractionResult.PASS;
        var be = level.getBlockEntity(pos);
        if (!(be instanceof MachineBlockEntity) && (be == null || !DEVICES.contains(be.getClass()))) return InteractionResult.PASS;
        var state = level.getBlockState(pos);
        if (state.getDestroySpeed(level, pos) < 0 || !player.mayBuild() || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), wrench)) return InteractionResult.FAIL;
        ItemStack packed = new ItemStack(state.getBlock());
        if (packed.isEmpty()) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, state, serverPlayer))) {
            return InteractionResult.FAIL;
        }
        // Close menus before taking the snapshot, including other players viewing the same device.
        for (var viewer : level.players()) {
            var menu = viewer.containerMenu;
            boolean viewing = menu instanceof MachineMenu m0 && m0.pos().equals(pos)
                    || menu instanceof CatalystDeviceMenu m1 && m1.pos().equals(pos)
                    || menu instanceof GeneratorSlotsMenu m2 && m2.pos().equals(pos)
                    || menu instanceof SmesMenu m3 && m3.pos().equals(pos)
                    || menu instanceof ThermoelectricGeneratorMenu m4 && m4.pos().equals(pos)
                    || menu instanceof DeviceMenu m5 && m5.pos().equals(pos);
            if (viewing) viewer.closeContainer();
        }
        var tag = be.saveWithoutMetadata();
        if (be instanceof WorldlineAnchorBlockEntity anchor) {
            // Radius is a ticket cache for the OLD position, rather than a user setting.
            tag.putInt("radius", -1);
            anchor.prepareForRelocation();
        }
        BlockItem.setBlockEntityData(packed, be.getType(), tag);
        packed.getOrCreateTag().putBoolean("singulo:packed_device", true);
        // setRemoved disconnects multiblock ports. Removing the BE before the block prevents
        // AbstractMachineBlock.onRemove from also dropping the serialized inventory.
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        // Inventory.add discards overflow for creative players; preserve filled machines there too.
        int slot = player.getInventory().getFreeSlot();
        if (slot >= 0) {
            player.getInventory().setItem(slot, packed);
            player.getInventory().setChanged();
        } else {
            player.drop(packed, false);
        }
        player.displayClientMessage(Component.translatable("item.singulo.relocation_wrench.packed"), true);
        return InteractionResult.CONSUME;
    }

    /**
     * Cable mode: use takes back the clicked cable, sneak-use the connected run of the same cable (up to
     * {@link #MAX_CABLE_RUN}). The cables go straight into the inventory (dropped at the player when full);
     * creative players get nothing back, as with breaking.
     */
    private static InteractionResult removeCables(ItemStack wrench, UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var start = context.getClickedPos();
        var state = level.getBlockState(start);
        if (!(state.getBlock() instanceof io.github.genichimaruo.singulo.cable.CableBlock cable)) return InteractionResult.PASS;
        if (!player.mayBuild() || !level.mayInteract(player, start)
                || !player.mayUseItemAt(start, context.getClickedFace(), wrench)) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        java.util.List<net.minecraft.core.BlockPos> run = player.isSecondaryUseActive()
                ? connectedRun(level, start, cable) : java.util.List.of(start);
        int removed = 0;
        for (var pos : run) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(cable)
                    || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), wrench)
                    || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                    || net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), serverPlayer))) continue;
            if (level.removeBlock(pos, false)) removed++;
        }
        if (removed == 0) return InteractionResult.FAIL;
        level.playSound(null, start, state.getSoundType(level, start, player).getBreakSound(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!player.getAbilities().instabuild) {
            int left = removed;
            while (left > 0) {
                ItemStack back = new ItemStack(cable, Math.min(left, 64));
                left -= back.getCount();
                if (!player.getInventory().add(back) && !back.isEmpty()) player.drop(back, false);
            }
        }
        if (run.size() > 1) {
            player.displayClientMessage(Component.translatable("item.singulo.relocation_wrench.cables_removed", removed), true);
        }
        return InteractionResult.CONSUME;
    }

    /** The cables of the same kind joined to start (breadth first, so the nearest go first when capped). */
    public static java.util.List<net.minecraft.core.BlockPos> connectedRun(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos start, Block cable) {
        java.util.List<net.minecraft.core.BlockPos> out = new java.util.ArrayList<>();
        java.util.Set<net.minecraft.core.BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<net.minecraft.core.BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && out.size() < MAX_CABLE_RUN) {
            var pos = queue.poll();
            out.add(pos);
            for (var dir : net.minecraft.core.Direction.values()) {
                var next = pos.relative(dir);
                if (seen.add(next) && level.isLoaded(next) && level.getBlockState(next).is(cable)) queue.add(next);
            }
        }
        return out;
    }
}
