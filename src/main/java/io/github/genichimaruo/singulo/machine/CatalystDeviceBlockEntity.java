package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.item.UsesHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 触媒を入れて動く装置の共通部分（アンカー・スタビライザー・量子熱機関）。
 * 毎tick、触媒のティアと残量から効き方と必要電力を決め、触媒を1 tick ぶん（×消費倍率）減らす。
 * 必要電力が足りないと不完全反応で減りが速くなり、まったくなければ止まる。使い切ると失活触媒が残る。
 */
public abstract class CatalystDeviceBlockEntity extends BlockEntity implements MenuProvider, AbstractMachineBlock.MenuOpener {
    public enum Status { NO_CATALYST, UNUSABLE, RUNNING, UNDERPOWERED, NO_POWER, NOT_FORMED, NO_FUEL, NO_TARGET, OFF }

    /** 画面の種類（表示する行が違う）。 */
    public enum Kind {
        WORLDLINE_ANCHOR, INERTIAL_STABILIZER, QUANTUM_HEAT_ENGINE, DEGENERATE_FURNACE, PROBE_STATION, SHIELD_TOWER, TIPLER_CYLINDER;

        /** 触媒のほかにもう1つあるスロットの名前（燃料・許可証）。なければ null。 */
        @javax.annotation.Nullable
        public String extraSlot() {
            return switch (this) {
                case DEGENERATE_FURNACE, TIPLER_CYLINDER -> "gui.singulo.slot.fuel";
                case SHIELD_TOWER -> "gui.singulo.slot.permit";
                default -> null;
            };
        }

        /** もう1つのスロットに入れられる物（画面の側でも同じ判定をする）。 */
        public boolean extraAccepts(ItemStack stack) {
            return switch (this) {
                case DEGENERATE_FURNACE -> DegenerateFurnaceBlockEntity.isFuel(stack);
                case TIPLER_CYLINDER -> TiplerCylinderBlockEntity.isFuel(stack);
                case SHIELD_TOWER -> stack.getItem() instanceof io.github.genichimaruo.singulo.item.ShieldPermitItem;
                default -> false;
            };
        }
    }

    public static final int D_ENERGY = 0, D_CAPACITY = 1, D_STATUS = 2, D_VALUE = 3, D_USAGE = 4, D_SPEED_X100 = 5,
            D_WEAR_X100 = 6, D_EXTRA = 7, D_ENABLED = 8, COUNT = 9;

    protected final ItemStackHandler slot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return CatalystHelper.tierOf(stack) > 0;
        }

        @Override
        public int getSlotLimit(int s) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    protected final SinguloEnergyStorage energy;
    private double wear;
    private Status status = Status.NO_CATALYST;
    private int lastUsage;
    private CatalystHelper.Effect lastEffect = CatalystHelper.Effect.NONE;
    /** 電源スイッチ。切ると効果を止め、触媒も電力も使わない。 */
    private boolean enabled = true;

    protected CatalystDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                        int capacity, int maxReceive, int maxExtract) {
        super(type, pos, state);
        this.energy = new SinguloEnergyStorage(capacity, maxReceive, maxExtract, this::setChanged);
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public ItemStackHandler catalystSlot() {
        return slot;
    }

    public Status status() {
        return status;
    }

    /** 電源スイッチ（GUIから）。 */
    public void togglePower() {
        enabled = !enabled;
        setChanged();
    }

    public abstract Kind kind();

    /** 装置のティア（触媒のティア制約に使う）。 */
    protected abstract int machineTier();

    /** 触媒が新品のときの必要電力（FE/t）。発電機は 0。 */
    protected abstract int baseUsage(ItemStack catalyst);

    /** 効果を出す（または止める）。active が false なら止める。 */
    protected abstract void apply(ServerLevel level, CatalystHelper.Effect effect, boolean active);

    /** 画面に出す値（半径や出力）。 */
    protected abstract int displayValue();

    /** 画面に出すもう1つの値（燃料の数など）。 */
    protected int extraValue() {
        return 0;
    }

    /** 動ける状態でなければその理由（未形成・燃料なしなど）。動けるなら null。触媒は減らない。 */
    @javax.annotation.Nullable
    protected Status readiness(ServerLevel level) {
        return null;
    }

    /**
     * 触媒も電力も使わずに動く場合の効き方（シンギュラリティ・コアを埋め込んだアンカー）。普通は null。
     */
    @javax.annotation.Nullable
    protected CatalystHelper.Effect permanentEffect() {
        return null;
    }

    /** 画面に出すスロット。0 は触媒、もう1つあれば 1（Kind.extraSlot）。 */
    public net.minecraftforge.items.IItemHandler menuItems() {
        return slot;
    }

    /** パイプやホッパーから見えるアイテムの入れ物。 */
    public net.minecraftforge.items.IItemHandler automationItems() {
        return slot;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CatalystDeviceBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack catalyst = slot.getStackInSlot(0);
        CatalystHelper.Effect effect = CatalystHelper.effect(machineTier(), catalyst);
        boolean active = false;
        lastUsage = 0;
        Status notReady = readiness(level);
        CatalystHelper.Effect permanent = permanentEffect();
        if (!enabled) {
            status = Status.OFF;
        } else if (notReady != null) {
            status = notReady;
        } else if (permanent != null) {
            effect = permanent;
            status = Status.RUNNING;
            active = true;
        } else if (CatalystHelper.tierOf(catalyst) == 0 || UsesHelper.remaining(catalyst) <= 0) {
            status = Status.NO_CATALYST;
        } else if (!effect.usable()) {
            status = Status.UNUSABLE;
        } else {
            int required = (int) Math.ceil(baseUsage(catalyst) * CatalystHelper.powerMultiplier(catalyst));
            int drawn = required == 0 ? 0 : Math.min(required, energy.getEnergyStored());
            if (required > 0 && drawn == 0) {
                status = Status.NO_POWER;
            } else {
                if (drawn > 0) {
                    energy.consume(drawn);
                }
                lastUsage = drawn;
                double supplied = required == 0 ? 1 : (double) drawn / required;
                double factor = CatalystHelper.underpowerFactor(supplied);
                status = factor > 1 ? Status.UNDERPOWERED : Status.RUNNING;
                // 時間の場の中では触媒の減りも変わる（膨張なら半分、加速なら2倍）
                wear += effect.consumption() * factor * TimeFields.wear(level, pos);
                int whole = (int) wear;
                if (whole > 0) {
                    wear -= whole;
                    slot.setStackInSlot(0, UsesHelper.consume(catalyst, whole));
                }
                active = true;
            }
        }
        lastEffect = active ? effect : CatalystHelper.Effect.NONE;
        apply(level, effect, active);
        if (state.getValue(AbstractMachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    /** 発電機用: 隣のブロックへ電力を押し出す。 */
    protected void pushEnergy(Level level, int perTick) {
        for (Direction dir : Direction.values()) {
            if (energy.getEnergyStored() <= 0) {
                return;
            }
            IEnergyStorage target = Capabilities.get(level, Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(dir), dir.getOpposite());
            if (target != null && target.canReceive()) {
                int sent = target.receiveEnergy(Math.min(energy.getEnergyStored(), perTick), false);
                if (sent > 0) {
                    energy.consume(sent);
                }
            }
        }
    }

    /** ブロックが壊されたとき。触媒を落とす。 */
    public void onBroken(Level level) {
        Block.popResource(level, worldPosition, slot.getStackInSlot(0));
    }

    // ------------------------------------------------------------------ GUI

    public SyncedInts syncData() {
        return SyncedInts.server(COUNT, i -> switch (i) {
            case D_ENERGY -> energy.getEnergyStored();
            case D_CAPACITY -> energy.getMaxEnergyStored();
            case D_STATUS -> status.ordinal();
            case D_VALUE -> displayValue();
            case D_USAGE -> lastUsage;
            case D_SPEED_X100 -> (int) Math.round(lastEffect.speed() * 100);
            case D_WEAR_X100 -> (int) Math.round(lastEffect.consumption() * 100);
            case D_EXTRA -> extraValue();
            case D_ENABLED -> enabled ? 1 : 0;
            default -> 0;
        });
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CatalystDeviceMenu(containerId, inventory, worldPosition, kind(), menuItems(), syncData());
    }

    @Override
    public void openMenu(ServerPlayer player) {
        net.minecraftforge.network.NetworkHooks.openScreen(player, this, buf -> {
            buf.writeBlockPos(worldPosition);
            buf.writeVarInt(kind().ordinal());
        });
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("catalyst", slot.serializeNBT());
        tag.putInt("energy", energy.getEnergyStored());
        tag.putDouble("wear", wear);
        tag.putBoolean("enabled", enabled);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        slot.deserializeNBT(tag.getCompound("catalyst"));
        energy.setEnergy(tag.getInt("energy"));
        wear = tag.getDouble("wear");
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
    }
}
