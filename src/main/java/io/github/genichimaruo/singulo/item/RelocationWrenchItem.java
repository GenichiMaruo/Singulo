package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.machine.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Relocates processing machines whose complete state is saved by MachineBlockEntity. */
public final class RelocationWrenchItem extends SinguloItem {
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

    @Override
    public InteractionResult onItemUseFirst(ItemStack wrench, UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (player == null || !player.isSecondaryUseActive()) return InteractionResult.PASS;
        var be = level.getBlockEntity(pos);
        if (!(be instanceof MachineBlockEntity) && (be == null || !DEVICES.contains(be.getClass()))) return InteractionResult.PASS;
        var state = level.getBlockState(pos);
        if (state.getDestroySpeed(level, pos) < 0 || !player.mayBuild() || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), wrench)) return InteractionResult.FAIL;
        ItemStack packed = new ItemStack(state.getBlock());
        if (packed.isEmpty()) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
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
        var tag = be.saveCustomOnly(level.registryAccess());
        if (be instanceof WorldlineAnchorBlockEntity anchor) {
            // Radius is a ticket cache for the OLD position, rather than a user setting.
            tag.putInt("radius", -1);
            anchor.prepareForRelocation();
        }
        BlockItem.setBlockEntityData(packed, be.getType(), tag);
        packed.set(DataComponents.MAX_STACK_SIZE, 1);
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
}
