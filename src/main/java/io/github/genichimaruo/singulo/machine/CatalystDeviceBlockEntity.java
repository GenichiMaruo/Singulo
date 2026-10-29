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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 触媒を入れて動く装置の共通部分（アンカー・スタビライザー・量子熱機関）。
 * 毎tick、触媒のティアと残量から効き方と必要電力を決め、触媒を1 tick ぶん（×消費倍率）減らす。
 * 必要電力が足りないと不完全反応で減りが速くなり、まったくなければ止まる。使い切ると失活触媒が残る。
 */
public abstract class CatalystDeviceBlockEntity extends BlockEntity implements MenuProvider, AbstractMachineBlock.MenuOpener {
    /**
     * STANDBY: 発電機の蓄電がいっぱいなので待機中（燃料・触媒を使わない）。LIMITED: 持ち主のアンカーが上限に達している。
     * 番号で同期するので、新しい値は後ろに足す。
     */
    public enum Status { NO_CATALYST, UNUSABLE, RUNNING, UNDERPOWERED, NO_POWER, NOT_FORMED, NO_FUEL, NO_TARGET, OFF, STANDBY, LIMITED, WAITING, OUTPUT_FULL, NEEDS_UPGRADE }

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
            D_WEAR_X100 = 6, D_EXTRA = 7, D_ENABLED = 8, D_LOADED_ITEM = 9, D_LOADED_FILL = 10, COUNT = 11;

    protected final ItemStackHandler slot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return CatalystHelper.tierOf(stack) > 0;
        }

        @Override
        public int getSlotLimit(int s) {
            return CatalystMagazine.SUPPLY_LIMIT;
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    /** 触媒の装填（触媒スロットは予備の置き場。使う1個は中に装填する）。 */
    protected final CatalystMagazine magazine = new CatalystMagazine(this::setChanged);
    /** 使い切った失活触媒と、入れ替えで外した使いかけの出口（取り出すだけ）。 */
    protected final ItemStackHandler spent = new ItemStackHandler(1) {
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

    /** 触媒の予備の置き場（スロット）。 */
    public ItemStackHandler catalystSlot() {
        return slot;
    }

    /** いま使っている触媒（装填中のもの。まだなければ予備のいちばん上）。 */
    public ItemStack catalyst() {
        return magazine.current(slot, 0);
    }

    public CatalystMagazine magazine() {
        return magazine;
    }

    /** 失活触媒の出口。 */
    public ItemStackHandler spentSlot() {
        return spent;
    }

    /** パイプやホッパーが失活触媒を取り出せる口（入れられない）。 */
    protected net.neoforged.neoforge.items.IItemHandlerModifiable spentOut() {
        return new net.neoforged.neoforge.items.wrapper.RangedWrapper(spent, 0, 1) {
            @Override
            public ItemStack insertItem(int s, ItemStack stack, boolean simulate) {
                return stack;
            }
        };
    }

    /** 画面から: 装填中の触媒をプレイヤーへ取り出す。 */
    public void ejectLoaded(Player player) {
        ItemStack out = magazine.eject();
        if (!out.isEmpty() && !player.getInventory().add(out)) {
            player.drop(out, false);
        }
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
     * 発電機なら true。蓄電が {@link #STANDBY_FULL} 以上になると待機し（燃料・触媒を使わない）、{@link #STANDBY_RESUME} を
     * 割ると再開する（毎tickの入り切りを避けるため、間をあける）。
     */
    protected boolean isGenerator() {
        return false;
    }

    public static final double STANDBY_FULL = 0.98, STANDBY_RESUME = 0.75;
    private boolean standby;

    /** 待機中か（発電機の蓄電がいっぱい）。 */
    private boolean checkStandby() {
        if (!isGenerator()) {
            return false;
        }
        double fill = energy.getMaxEnergyStored() <= 0 ? 0 : (double) energy.getEnergyStored() / energy.getMaxEnergyStored();
        if (standby ? fill < STANDBY_RESUME : fill >= STANDBY_FULL) {
            standby = !standby;
        }
        return standby;
    }

    /**
     * 触媒も電力も使わずに動く場合の効き方（シンギュラリティ・コアを埋め込んだアンカー）。普通は null。
     */
    @javax.annotation.Nullable
    protected CatalystHelper.Effect permanentEffect() {
        return null;
    }

    /** 画面に出すスロット。0 は触媒、もう1つあれば 1（Kind.extraSlot）。 */
    public net.neoforged.neoforge.items.IItemHandler menuItems() {
        return slot;
    }

    /** パイプやホッパーから見えるアイテムの入れ物（触媒を入れ、失活触媒を取り出す）。 */
    public net.neoforged.neoforge.items.IItemHandler automationItems() {
        return new net.neoforged.neoforge.items.wrapper.CombinedInvWrapper(slot, spentOut());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CatalystDeviceBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        magazine.tick(slot, 0, s -> spent.insertItem(0, s, false));
        ItemStack catalyst = catalyst();
        CatalystHelper.Effect effect = CatalystHelper.effect(machineTier(), catalyst);
        boolean active = false;
        lastUsage = 0;
        Status notReady = readiness(level);
        CatalystHelper.Effect permanent = permanentEffect();
        if (!enabled) {
            status = Status.OFF;
        } else if (notReady != null) {
            status = notReady;
        } else if (checkStandby()) {
            status = Status.STANDBY;
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
                    magazine.consume(whole, slot, 0);
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
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(dir), dir.getOpposite());
            if (target != null && target.canReceive()) {
                int sent = target.receiveEnergy(Math.min(energy.getEnergyStored(), perTick), false);
                if (sent > 0) {
                    energy.consume(sent);
                }
            }
        }
    }

    /** ブロックが壊されたとき。予備の触媒・装填中の触媒・出口の失活触媒を落とす。 */
    public void onBroken(Level level) {
        Block.popResource(level, worldPosition, slot.getStackInSlot(0));
        for (ItemStack s : magazine.contents()) {
            Block.popResource(level, worldPosition, s);
        }
        Block.popResource(level, worldPosition, spent.getStackInSlot(0));
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
            case D_LOADED_ITEM -> magazine.loadedItemId();
            case D_LOADED_FILL -> Math.round(magazine.fill() * 1000);
            default -> 0;
        });
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CatalystDeviceMenu(containerId, inventory, worldPosition, kind(), menuItems(), spent, syncData());
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.openMenu(this, buf -> {
            buf.writeBlockPos(worldPosition);
            buf.writeVarInt(kind().ordinal());
        });
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("catalyst", slot.serializeNBT(registries));
        tag.put("magazine", magazine.save(registries));
        tag.put("spent", spent.serializeNBT(registries));
        tag.putInt("energy", energy.getEnergyStored());
        tag.putDouble("wear", wear);
        tag.putBoolean("enabled", enabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        slot.deserializeNBT(registries, tag.getCompound("catalyst"));
        magazine.load(tag.getCompound("magazine"), registries);
        spent.deserializeNBT(registries, tag.getCompound("spent"));
        energy.setEnergy(tag.getInt("energy"));
        wear = tag.getDouble("wear");
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
    }
}
