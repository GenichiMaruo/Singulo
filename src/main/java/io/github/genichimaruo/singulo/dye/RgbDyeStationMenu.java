package io.github.genichimaruo.singulo.dye;

import io.github.genichimaruo.singulo.machine.GuiFrame;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * RGB染色台の画面。左に色の輪（色みと鮮やかさ）と明るさのつまみ、右に染める物の枠・「染める」ボタン・色見本、
 * その下に赤・緑・青・白・黒の染料の枠とインクの目盛り。
 * 色を選ぶ・染めるは、画面のボタンの番号でサーバーへ送る（{@link #clickMenuButton}）。
 */
public class RgbDyeStationMenu extends AbstractContainerMenu {
    public static final int WIDTH = 212, HEIGHT = 194;
    public static final int WHEEL_X = 10, WHEEL_Y = 22, WHEEL_SIZE = 64;
    public static final int SLIDER_X = 80, SLIDER_W = 8;
    public static final int TARGET_X = 102, TARGET_Y = 24;
    public static final int APPLY_X = 124, APPLY_Y = 24, APPLY_W = 42, APPLY_H = 16;
    public static final int SWATCH_X = 172, SWATCH_Y = 24, SWATCH_SIZE = 16;
    /** カラーコードの入力欄（#RRGGBB）。 */
    public static final int HEX_X = 124, HEX_Y = 44, HEX_W = 64, HEX_H = 12;
    public static final int DYE_X = 100, DYE_Y = 60, DYE_STEP = 20;
    public static final int INV_X = GuiFrame.inventoryX(WIDTH), INV_Y = GuiFrame.inventoryY(HEIGHT), HOTBAR_Y = GuiFrame.hotbarY(HEIGHT);
    /** ボタンの番号: 染める。色を選ぶのは SET_COLOR | RGB。 */
    public static final int APPLY = 0, SET_COLOR = 0x1000000;

    private final BlockPos pos;
    private final ContainerData data;

    public RgbDyeStationMenu(int id, Inventory inventory, BlockPos pos, Container items, ContainerData data) {
        super(SinguloMenus.RGB_DYE_STATION.get(), id);
        this.pos = pos;
        this.data = data;
        for (int i = 0; i < RgbDyeStationBlockEntity.INKS; i++) {
            int slot = i;
            addSlot(new Slot(items, i, DYE_X + i * DYE_STEP, DYE_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return RgbDyeStationBlockEntity.accepts(slot, stack);
                }
            });
        }
        addSlot(new Slot(items, RgbDyeStationBlockEntity.TARGET, TARGET_X, TARGET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RgbDyeStationBlockEntity.accepts(RgbDyeStationBlockEntity.TARGET, stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    public static RgbDyeStationMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new RgbDyeStationMenu(id, inventory, buf.readBlockPos(), new SimpleContainer(RgbDyeStationBlockEntity.SLOTS),
                new SimpleContainerData(RgbDyeStationBlockEntity.INKS + 3));
    }

    public BlockPos pos() {
        return pos;
    }

    public int ink(int i) {
        return data.get(i);
    }

    /** 選んでいる色（RGB）。 */
    public int color() {
        int i = RgbDyeStationBlockEntity.INKS;
        return (data.get(i) & 0xFF) << 16 | (data.get(i + 1) & 0xFF) << 8 | (data.get(i + 2) & 0xFF);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level().getBlockEntity(pos) instanceof RgbDyeStationBlockEntity station)) {
            return false;
        }
        if (id == APPLY) {
            return station.apply();
        }
        if ((id & SET_COLOR) != 0) {
            station.setColor(id & 0xFFFFFF);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int own = RgbDyeStationBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < own && !moved; i++) {
                if (RgbDyeStationBlockEntity.accepts(i, stack)) {
                    moved = moveItemStackTo(stack, i, i + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof RgbDyeStationBlockEntity station && station.stillValid(player);
    }
}
