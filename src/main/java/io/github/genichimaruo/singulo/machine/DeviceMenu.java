package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.item.ExoticCharge;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * スロットの少ない装置の画面をまとめたもの（ワームホール生成器・口・ポート、閉じ込めタンク、ハロー捕集器、ミュオン収集器、重力波検出器）。
 * 装置ごとの並び（Kind）で、スロットの位置・同期する数・ボタンが決まる。描くのはクライアントの DeviceScreen。
 */
public class DeviceMenu extends AbstractContainerMenu {
    /** 装置の種類。slots はスロットの数、data は同期する数の数、space は暗い宇宙の配色（ワームホール系）。 */
    public enum Kind {
        WORMHOLE_GENERATOR(2, Gen.COUNT, true),
        WORMHOLE_MOUTH(1, Mouth.COUNT, true),
        WORMHOLE_PORT(0, Port.COUNT, true),
        CONTAINMENT_TANK(0, Tank.COUNT, false),
        HALO_COLLECTOR(0, Halo.COUNT, false),
        MUON_COLLECTOR(1, Muon.COUNT, false),
        DETECTOR(0, Detector.COUNT, false);

        public final int slots;
        public final int data;
        public final boolean space;

        Kind(int slots, int data, boolean space) {
            this.slots = slots;
            this.data = data;
            this.space = space;
        }
    }

    /** 同期する数の並び（装置ごと）。 */
    public static final class Gen {
        public static final int PROGRESS = 0, FORMED = 1, RECEIVED = 2, REMAIN_0 = 3, REMAIN_1 = 4, REQUIRED = 5, COUNT = 6;
    }

    public static final class Mouth {
        public static final int SIZE = 0, TARGET = 1, PARTNER = 2, PX = 3, PY = 4, PZ = 5, CROSS = 6, STARVE = 7,
                TICKS_PER_MATTER = 8, COUNT = 9;
        public static final int BUTTON_SMALLER = 0, BUTTON_BIGGER = 1;
    }

    public static final class Port {
        public static final int STATE = 0, SIZE = 1, ENERGY = 2, ITEMS = 3, FLUIDS = 4, COUNT = 5;
    }

    public static final class Tank {
        public static final int AMOUNT = 0, CAPACITY = 1, CONTAINED = 2, ENERGY = 3, ENERGY_MAX = 4, COUNT = 5;
    }

    public static final class Halo {
        public static final int RATE = 0, AMOUNT = 1, BUFFER = 2, ENERGY = 3, ENERGY_MAX = 4, CORE = 5, CORE_MASS = 6, COUNT = 7;
    }

    public static final class Muon {
        public static final int RATE = 0, Y = 1, SKY = 2, PROGRESS = 3, COUNT = 4;
    }

    public static final class Detector {
        public static final int ENERGY = 0, ENERGY_MAX = 1, OBSERVED = 2, FOUND = 3, DIRECTION = 4, BAND = 5, COST = 6, COUNT = 7;
        public static final int BUTTON_OBSERVE = 0;
    }

    /** ボタンを受け取る装置。 */
    public interface Host {
        boolean onButton(ServerPlayer player, int id);
    }

    public static final int WIDTH = 200;
    public static final int HEIGHT = 206;
    public static final int INV_X = 20;
    public static final int INV_Y = 124;
    public static final int HOTBAR_Y = 182;

    /** スロットの位置（menu と screen で共有）。 */
    public static int[][] slotPos(Kind kind) {
        return switch (kind) {
            case WORMHOLE_GENERATOR -> new int[][]{{62, 84}, {122, 84}};
            case WORMHOLE_MOUTH -> new int[][]{{20, 84}};
            case MUON_COLLECTOR -> new int[][]{{150, 60}};
            default -> new int[0][];
        };
    }

    private final Kind kind;
    private final BlockPos pos;
    private final SyncedInts data;
    @Nullable
    private final Host host;

    public DeviceMenu(int containerId, Inventory inventory, Kind kind, BlockPos pos, IItemHandler items, SyncedInts data,
                      @Nullable Host host) {
        super(SinguloMenus.DEVICE.get(), containerId);
        this.kind = kind;
        this.pos = pos;
        this.data = data;
        this.host = host;
        int[][] at = slotPos(kind);
        for (int i = 0; i < kind.slots; i++) {
            addSlot(new SlotItemHandler(items, i, at[i][0], at[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // 燃料を入れられるのは口だけ。生成器とミュオン収集器は取り出すだけ
                    return kind == Kind.WORMHOLE_MOUTH && ExoticCharge.isExoticMatter(stack);
                }
            });
        }
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

    /** サーバー側で開く。 */
    public static void open(ServerPlayer player, BlockEntity be, Kind kind, IItemHandler items,
                            java.util.function.IntUnaryOperator values, Host host) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new DeviceMenu(id, inv, kind, be.getBlockPos(),
                        items, SyncedInts.server(kind.data, values), host),
                net.minecraft.network.chat.Component.translatable(be.getBlockState().getBlock().getDescriptionId())),
                buf -> {
                    buf.writeVarInt(kind.ordinal());
                    buf.writeBlockPos(be.getBlockPos());
                });
    }

    public static DeviceMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        Kind kind = Kind.values()[buf.readVarInt()];
        return new DeviceMenu(containerId, inventory, kind, buf.readBlockPos(), new ItemStackHandler(Math.max(1, kind.slots)),
                SyncedInts.client(kind.data), null);
    }

    public Kind kind() {
        return kind;
    }

    public BlockPos pos() {
        return pos;
    }

    public int value(int index) {
        return data.getInt(index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return host != null && player instanceof ServerPlayer sp && host.onButton(sp, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machine = kind.slots;
        if (index < machine) {
            if (!moveItemStackTo(stack, machine, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (kind == Kind.WORMHOLE_MOUTH && ExoticCharge.isExoticMatter(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
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
        BlockEntity be = player.level().getBlockEntity(pos);
        return be != null && io.github.genichimaruo.singulo.multiblock.Blueprints.withinMenuReach(player, pos);
    }
}
