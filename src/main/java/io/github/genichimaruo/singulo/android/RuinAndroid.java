package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.ruin.RuinGuards;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 遺構のアンドロイド（光刃型・鞭型・光剣型の共通部分）。印刷台の上で、下から積み上がるように印刷されて現れる。
 *
 * <p>充電（{@link #CHARGE_MAX}）を持ち、技を使うたびに減る（大技10回ほどで切れる）。0 になると攻撃をやめ（目の光が消える）、
 * 近くの印刷台へ戻って充電する（5秒で満タン）。近くに印刷台がなければ、昼の空の下では左手からソーラーパネルを広げて
 * 充電し（10秒）、夜や屋内では地面に座ってゆっくり充電する（14秒）。死ぬことはない。
 */
public abstract class RuinAndroid extends Monster {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    /** 印刷の進み（0〜PRINT_TICKS。PRINT_TICKS で完成）。 */
    private static final EntityDataAccessor<Integer> PRINT = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);

    /** 共通の動き。型ごとの技は 10 から。 */
    public static final int STATE_IDLE = 0;
    public static final int STATE_LOW_POWER = 1;
    public static final int STATE_CHARGING = 2;
    /** 昼の空の下で、左手のソーラーパネルで充電している。 */
    public static final int STATE_SOLAR = 3;
    /** 夜や屋内で、地面に座って充電している。 */
    public static final int STATE_RESTING = 4;

    /** 充電の満タン。 */
    public static final int CHARGE_MAX = 2800;
    /** 1 tick に充電する量（印刷台: 5秒、ソーラー: 10秒、座って: 14秒で満タン）。 */
    public static final int RATE_DOCK = CHARGE_MAX / 100;
    public static final int RATE_SOLAR = CHARGE_MAX / 200;
    public static final int RATE_REST = CHARGE_MAX / 280;
    /** これより遠い印刷台へは戻らない（その場で充電する）。 */
    public static final double DOCK_RANGE = 32;
    /** 印刷にかかる tick。 */
    public static final int PRINT_TICKS = 60;
    /** 印刷台から離れていい距離（遺構のもの）。 */
    public static final int HOME_RADIUS = 18;

    /** 体力が減ってステーションへ戻っているところ（護衛）。 */
    public static final int STATE_RETREAT = 5;
    /** 追従中に、持ち主の無線充電器で充電しながらついて歩いている（護衛）。 */
    public static final int STATE_CHARGING_FOLLOW = 6;
    /** ソーラー充電が終わって、パネルをたたんでいる（{@link #FOLD_TICKS}）。 */
    public static final int STATE_SOLAR_FOLD = 7;
    /** 作業場所で物を作っている（作業の自動化）。 */
    public static final int STATE_CRAFTING = 8;
    /** 採掘の手順でブロックを掘っている（左手に光るツルハシ）。 */
    public static final int STATE_MINING = 9;
    public static final int FOLD_TICKS = 20;
    /** 無線充電器から1 tick にもらう電力（満充電 PER_ANDROID を 100 tick、5秒で）。 */
    static final int CHARGER_FE_PER_TICK = AndroidChargerItem.PER_ANDROID / 100;

    /** 護衛の動き方（組立ステーションの設定）。 */
    public static final int MODE_GUARD = 0;
    public static final int MODE_WANDER = 1;
    public static final int MODE_FOLLOW = 2;
    /** 護衛が敵を探す範囲（ステーションか持ち主から）。 */
    public static final double GUARD_RANGE = 14;
    public static final int WANDER_RADIUS = 16;
    /** ステーションの上が空くのを待つときの、ステーションからの距離。 */
    static final double WAIT_RADIUS = 2.6;
    /** 迎撃・待機のときに立つ場所（ステーションの上を空けておくため、横へずらす。アンドロイドごとに向きを変える）。 */
    // ------------------------------------------------------------------ 挨拶

    /** 仕草の残り（サーバー）と、そのあいだ向く相手。仕草が始まった時（クライアント）。 */
    private int gestureTicks;
    @Nullable
    private Entity gestureLook;
    private int clientGestureStart;
    /** 持ち主・許可証の人が最後にそばにいた時刻、仲間と最後に挨拶した時刻（保存しない）。 */
    private final java.util.Map<java.util.UUID, Long> lastClose = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastPeerGreet = new java.util.HashMap<>();
    /** これだけ離れていた人がそばに来たら挨拶する（5分）。仲間とは1分に1回まで。 */
    static final int AWAY_TICKS = 20 * 60 * 5, PEER_GREET_TICKS = 20 * 60;

    public int gesture() {
        return entityData.get(GESTURE);
    }

    public int tool() {
        return entityData.get(TOOL);
    }

    void setTool(int tool) {
        if (entityData.get(TOOL) != tool) {
            entityData.set(TOOL, tool);
        }
    }

    /** 仕草が始まってからの時間（クライアント、tick）。 */
    public float gestureTime(float partialTick) {
        return tickCount - clientGestureStart + partialTick;
    }

    /** 仕草をする（look があれば、そのあいだそちらを向く）。 */
    public void gesture(int kind, @Nullable Entity look) {
        entityData.set(GESTURE, GESTURE_NONE);
        entityData.set(GESTURE, kind);
        gestureTicks = GESTURE_TICKS;
        gestureLook = look;
    }

    /** 毎秒: 久しぶりにそばに来た持ち主へ手を振り、そばを通る仲間と会釈する。 */
    private void greetings(ServerLevel level) {
        if (!guard || !printed() || !active() || getTarget() != null || gestureTicks > 0) {
            return;
        }
        long now = level.getGameTime();
        for (Player p : level.players()) {
            if (p.isSpectator() || !mayOperate(p) || p.distanceToSqr(this) > 8 * 8) {
                continue;
            }
            Long seen = lastClose.put(p.getUUID(), now);
            if (seen == null || now - seen > AWAY_TICKS) {
                gesture(GESTURE_WAVE, p);
                chirp(level, null, 1.5F);
                return;
            }
        }
        for (RuinAndroid a : level.getEntitiesOfClass(RuinAndroid.class, getBoundingBox().inflate(2.5),
                a -> a != this && a.isAlive() && a.guard && a.printed() && a.active() && a.getTarget() == null && a.gestureTicks <= 0
                        && java.util.Objects.equals(a.owner, owner))) {
            Long last = lastPeerGreet.get(a.getUUID());
            if (last == null || now - last > PEER_GREET_TICKS) {
                lastPeerGreet.put(a.getUUID(), now);
                a.lastPeerGreet.put(getUUID(), now);
                gesture(GESTURE_NOD, a);
                a.gesture(GESTURE_NOD, this);
                chirp(level, a, 1.2F);
                return;
            }
        }
    }

    /** 作業中に襲ってきた相手を追う距離。 */
    static final double WORK_DEFEND_RANGE = 16;
    /** ステーションの上の番が、仲間に上を譲っているところ。 */
    private boolean yielding;
    private static final Vec3[] STAND_OFFSETS = {new Vec3(2, 0, 0), new Vec3(-2, 0, 0), new Vec3(0, 0, 2), new Vec3(0, 0, -2)};

    /** 充電器から電力をもらって、ついて歩いている持ち主（見た目のため。いなければ -1）。 */
    private static final EntityDataAccessor<Integer> CHARGER_LINK = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    /** 護衛か（見た目と、右クリックで GUI を開けるかのため）。 */
    private static final EntityDataAccessor<Boolean> GUARD = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.BOOLEAN);
    /** 敵を見つけているか（GUI のひとこと）。 */
    private static final EntityDataAccessor<Boolean> ALERT = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.BOOLEAN);

    /** 持っているバッテリーアップグレードの数（見た目と、充電の満タンのため）。 */
    private static final EntityDataAccessor<Integer> BATTERIES = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    /** タンクの見た目: 流体の番号（+1。0 は空）と入っている割合（0〜100）を、タンクごとに1つの数にまとめる。-1 はタンクなし。 */
    private static final EntityDataAccessor<Integer> TANK_A = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    /** 装飾（頭・背中/腰・手。クライアントで描くため）。 */
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<net.minecraft.world.item.ItemStack>[] DECOR = new EntityDataAccessor[] {
            SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.ITEM_STACK),
            SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.ITEM_STACK),
            SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.ITEM_STACK)};
    private static final EntityDataAccessor<Integer> TANK_B = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    /** 作業（自動化）を割り当てられているか（持ち物を取り出せなくするため、クライアントでも見る）。 */
    private static final EntityDataAccessor<Boolean> WORKING = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.BOOLEAN);
    /** 仕草（挨拶。{@link #GESTURE_WAVE} など。クライアントでも見える）。 */
    private static final EntityDataAccessor<Integer> GESTURE = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    public static final int GESTURE_NONE = 0, GESTURE_WAVE = 1, GESTURE_NOD = 2;
    /** 採掘の姿勢で持つ道具（0: ツルハシ、1: 斧）。 */
    private static final EntityDataAccessor<Integer> TOOL = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    public static final int TOOL_PICKAXE = 0, TOOL_AXE = 1;
    /** 仕草の長さ（tick）。 */
    public static final int GESTURE_TICKS = 30;
    /** 作業中に今していること（{@link #ACT_FETCH} など。クライアントでも見える）。 */
    private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    public static final int ACT_NONE = 0, ACT_FETCH = 1, ACT_CRAFT = 2, ACT_PROCESS = 3, ACT_MINE = 4, ACT_DELIVER = 5, ACT_RETURN = 6,
            ACT_WAIT = 7, ACT_CHARGE = 8, ACT_FIGHT = 9, ACT_FARM = 10, ACT_RANCH = 11, ACT_FELL = 12, ACT_USE = 13;
    public static final String[] ACTIVITY_KEYS = {"none", "fetch", "craft", "process", "mine", "deliver", "return", "wait", "charge", "fight",
            "farm", "ranch", "fell", "use"};
    /** バックパックアップグレードを積んでいるか（見た目のため）。 */
    private static final EntityDataAccessor<Boolean> BACKPACK = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.BOOLEAN);

    /**
     * 護衛の持ち物: 0 は自己修理キット（1スタック）、1 は装甲板（胸当てとして働く）、2 はバッテリーアップグレード（2個まで）、
     * 3 はバックパックアップグレード、4 は採掘アップグレード。5〜9 は運ぶ物を入れる持ち物（ホッパーと同じ5スロット）、
     * 10〜33 はバックパックの中（24スロット。バックパックを積んでいるときだけ使える）。
     */
    public static final int SLOT_REPAIR = 0, SLOT_PLATE = 1, SLOT_BATTERY = 2, SLOT_BACKPACK = 3, SLOT_MINING = 4;
    public static final int CARRY_START = 5, CARRY_SLOTS = 5, PACK_START = 10, PACK_SLOTS = 24;
    /** 伐採アップグレード・タンクアップグレードの枠（あとから足したので、持ち物のうしろ）。 */
    public static final int SLOT_FELLING = PACK_START + PACK_SLOTS, SLOT_TANK = SLOT_FELLING + 1;
    /** 装飾の枠（頭・背中/腰・手）。 */
    public static final int SLOT_DECOR = SLOT_TANK + 1, DECOR_SLOTS = 3;
    public static final int INVENTORY_SIZE = SLOT_DECOR + DECOR_SLOTS;
    /** タンクアップグレードのタンクの数と、1つの容量（mB）。1つのタンクには1種類の流体。 */
    public static final int TANKS = 2, TANK_CAPACITY = 32000;
    /** バッテリーアップグレードは2個まで。1個につき充電の満タンが半分（CHARGE_MAX / 2）ずつ増える。 */
    public static final int MAX_BATTERIES = 2;
    /** 自己修理キット1つで直す体力の割合と、続けて使えるまでの tick。 */
    static final float REPAIR_FRACTION = 0.5F;
    static final int REPAIR_COOLDOWN = 60;

    @Nullable
    private BlockPos home;
    /** 護衛（プレイヤーの組立ステーションで印刷された）か。護衛は敵対モブだけを狙い、プレイヤーには傷つけられない。 */
    private boolean guard;
    @Nullable
    private java.util.UUID owner;
    private int mode = MODE_GUARD;
    /** 体力がこの割合（%）を下回ったら、ステーションへ戻る。 */
    private int retreatPercent = 30;
    /** 追従中に戦うか（false なら、ついていくだけ）。 */
    private boolean escort = true;
    /** アンドロイドの画面で切り替えたが、まだステーションに伝えていない（ステーションが読み込まれていない）。 */
    private boolean escortPending;
    /** ステーションの許可証に登録された人（持ち主のほかに画面を開け、持ち主がいないときはこの人たちに追従する）。 */
    private final java.util.List<java.util.UUID> members = new java.util.ArrayList<>();
    /** 追従する人（許可証に登録された人から選ぶ。null は持ち主）。 */
    @Nullable
    private java.util.UUID leader;
    private final net.minecraft.world.SimpleContainer inventory = new net.minecraft.world.SimpleContainer(INVENTORY_SIZE);
    /** 作業（自動化）に割り当ててよいか（アンドロイドの画面で切り替える）。 */
    private boolean workEnabled = true;
    /** 割り当てられた作業の、作業指令台と注文（なければ null と -1）。 */
    @Nullable
    private BlockPos workConsole;
    private int workOrder = -1;
    /** 作業の途中も保存し、再読込・転送後に同じ所から続ける。 */
    private AndroidWorkGoal workGoal;
    /** 今進めている作業（作業指令台に毎秒知らせる）。 */
    @Nullable
    io.github.genichimaruo.singulo.android.work.WorkJob workJob;
    /**
     * 作業を始めたときに、持ち物・バックパックの中に入っていた物の場所（ビット。持ち物 0〜4、バックパック 5〜28）。
     * 持ち主が入れておいた物なので、作業では使わず（空いている場所だけ使う）、資材置き場へも戻さない。
     */
    private int personal;
    private int repairCooldown;
    /** ステーションが遠すぎて戻れなかったとき、しばらく戻ろうとしない（tick）。 */
    private int retreatCooldown;
    /** 解体中（印刷を逆にたどって消えていく）。 */
    private boolean dismantling;
    /** 今の動きを始めてからの tick（サーバー）。 */
    protected int stateTicks;
    /** クライアント: 今の動きが始まった時刻（見た目のため）。 */
    private int clientStateStart;
    private int clientPrevState;

    protected RuinAndroid(EntityType<? extends RuinAndroid> type, Level level) {
        super(type, level);
        this.xpReward = 8;
        setPersistenceRequired();
        setDropChance(net.minecraft.world.entity.EquipmentSlot.CHEST, 0);
        // 装甲板は胸当ての装備として働かせる（防御の値とエンチャントが効く）。アップグレードは見た目と充電の満タンに出す
        inventory.addListener(c -> {
            setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, inventory.getItem(SLOT_PLATE).copy());
            if (!level().isClientSide) {
                net.minecraft.world.item.ItemStack battery = inventory.getItem(SLOT_BATTERY);
                entityData.set(BATTERIES, isBatteryUpgrade(battery) ? Math.min(MAX_BATTERIES, battery.getCount()) : 0);
                entityData.set(BACKPACK, isBackpackUpgrade(inventory.getItem(SLOT_BACKPACK)));
                syncTanks();
                for (int i = 0; i < DECOR_SLOTS; i++) {
                    net.minecraft.world.item.ItemStack d = inventory.getItem(SLOT_DECOR + i);
                    if (!net.minecraft.world.item.ItemStack.matches(d, entityData.get(DECOR[i]))) {
                        entityData.set(DECOR[i], d.copy());
                    }
                }
                if (charge() > maxCharge()) {
                    setCharge(maxCharge());
                }
            }
        });
    }

    private static boolean isItem(net.minecraft.world.item.ItemStack stack, String id) {
        return !stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(id);
    }

    public static boolean isBatteryUpgrade(net.minecraft.world.item.ItemStack stack) {
        return isItem(stack, "android_battery_upgrade");
    }

    public static boolean isBackpackUpgrade(net.minecraft.world.item.ItemStack stack) {
        return isItem(stack, "android_backpack_upgrade");
    }

    public static boolean isMiningUpgrade(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof AndroidMiningUpgradeItem;
    }

    public static boolean isFellingUpgrade(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof AndroidFellingUpgradeItem;
    }

    /** 手に持つ装飾を見せるか: 待機していて（戦い・作業・充電をしていない）、挨拶の仕草もしていない。 */
    public boolean showsHeldDecor() {
        return printed() && state() == STATE_IDLE && !isWorking() && !entityData.get(ALERT) && gesture() == GESTURE_NONE
                && !decor(AndroidAccessoryItem.HAND).isEmpty();
    }

    /** 着けている装飾（part: {@link AndroidAccessoryItem#HEAD} など。クライアントでも見える）。 */
    public net.minecraft.world.item.ItemStack decor(int part) {
        return entityData.get(DECOR[part]);
    }

    public static boolean isTankUpgrade(net.minecraft.world.item.ItemStack stack) {
        return isItem(stack, "android_tank_upgrade");
    }

    // ------------------------------------------------------------------ タンク（流体）

    /** タンクアップグレードのタンク（積んでいなければ使えない）。 */
    private final net.neoforged.neoforge.fluids.capability.templates.FluidTank[] tanks = {
            new net.neoforged.neoforge.fluids.capability.templates.FluidTank(TANK_CAPACITY),
            new net.neoforged.neoforge.fluids.capability.templates.FluidTank(TANK_CAPACITY)};

    /** タンクアップグレードを積んでいるか。 */
    public boolean hasTank() {
        return isTankUpgrade(inventory.getItem(SLOT_TANK));
    }

    /** タンクが空か（タンクアップグレードを外せるか）。 */
    public boolean tanksEmpty() {
        for (var t : tanks) {
            if (!t.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** タンクの中身。 */
    public java.util.List<net.neoforged.neoforge.fluids.FluidStack> tankFluids() {
        java.util.List<net.neoforged.neoforge.fluids.FluidStack> out = new java.util.ArrayList<>();
        for (var t : tanks) {
            if (!t.isEmpty()) {
                out.add(t.getFluid().copy());
            }
        }
        return out;
    }

    /** この流体が、タンクにいくつあるか（mB）。 */
    public int tankAmount(net.neoforged.neoforge.fluids.FluidStack like) {
        int n = 0;
        for (var t : tanks) {
            if (net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(t.getFluid(), like)) {
                n += t.getFluidAmount();
            }
        }
        return n;
    }

    /** あと、この流体をどれだけ入れられるか（同じ流体のタンクの空きと、空のタンク）。 */
    public int tankSpace(net.neoforged.neoforge.fluids.FluidStack like) {
        if (!hasTank()) {
            return 0;
        }
        int n = 0;
        for (var t : tanks) {
            if (t.isEmpty() || net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(t.getFluid(), like)) {
                n += t.getSpace();
            }
        }
        return n;
    }

    /** タンクに入れる（同じ流体のタンクから、そのあと空のタンク）。入った量。 */
    public int fillTanks(net.neoforged.neoforge.fluids.FluidStack fluid, boolean simulate) {
        if (!hasTank() || fluid.isEmpty()) {
            return 0;
        }
        var action = simulate ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        int left = fluid.getAmount();
        int[] planned = new int[TANKS];
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int i = 0; i < TANKS && left > 0; i++) {
                var t = tanks[i];
                boolean same = net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(t.getFluid(), fluid);
                boolean empty = t.isEmpty() && planned[i] == 0;
                if (pass == 0 ? !same : !empty) {
                    continue;
                }
                int room = t.getCapacity() - t.getFluidAmount() - planned[i];
                int put = Math.min(room, left);
                if (put <= 0) {
                    continue;
                }
                if (simulate) {
                    planned[i] += put;
                } else {
                    t.fill(fluid.copyWithAmount(put), action);
                }
                left -= put;
            }
        }
        if (!simulate) {
            syncTanks();
        }
        return fluid.getAmount() - left;
    }

    /** タンクから出す（この流体を最大 amount）。出した流体。 */
    public net.neoforged.neoforge.fluids.FluidStack drainTanks(net.neoforged.neoforge.fluids.FluidStack like, int amount, boolean simulate) {
        var action = simulate ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        int got = 0;
        for (var t : tanks) {
            if (got >= amount || !net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(t.getFluid(), like)) {
                continue;
            }
            got += t.drain(amount - got, action).getAmount();
        }
        if (!simulate && got > 0) {
            syncTanks();
        }
        return got <= 0 ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : like.copyWithAmount(got);
    }

    /** タンクの見た目を、クライアントへ知らせる。 */
    private void syncTanks() {
        if (level().isClientSide) {
            return;
        }
        boolean on = hasTank();
        for (int i = 0; i < TANKS; i++) {
            int v = -1;
            if (on) {
                var f = tanks[i].getFluid();
                v = f.isEmpty() ? 0 : (net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(f.getFluid()) + 1) << 8
                        | Math.max(1, f.getAmount() * 100 / TANK_CAPACITY);
            }
            entityData.set(i == 0 ? TANK_A : TANK_B, v);
        }
    }

    /** タンク i の入っている割合（クライアント。0〜1、タンクアップグレードがなければ -1）。 */
    public float tankFill(int i) {
        int v = entityData.get(i == 0 ? TANK_A : TANK_B);
        return v < 0 ? -1 : (v & 0xFF) / 100F;
    }

    /** タンク i の流体（クライアント。空なら null）。 */
    @Nullable
    public net.minecraft.world.level.material.Fluid tankFluid(int i) {
        int v = entityData.get(i == 0 ? TANK_A : TANK_B);
        if (v <= 0 || (v >> 8) == 0) {
            return null;
        }
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.byId((v >> 8) - 1);
    }

    /** 伐採アップグレードを積んでいるか。 */
    public boolean canFell() {
        return isFellingUpgrade(inventory.getItem(SLOT_FELLING));
    }

    /** 積んでいるバッテリーアップグレードの数（クライアントでも見える）。 */
    public int batteries() {
        return entityData.get(BATTERIES);
    }

    /** バックパックアップグレードを積んでいるか（クライアントでも見える）。 */
    public boolean hasBackpack() {
        return entityData.get(BACKPACK);
    }

    /** 採掘アップグレードを積んでいるか。 */
    public boolean canMine() {
        return isMiningUpgrade(inventory.getItem(SLOT_MINING));
    }

    /** 充電の満タン（バッテリーアップグレード1個につき +50%）。 */
    public int maxCharge() {
        return CHARGE_MAX * (2 + batteries()) / 2;
    }

    /** 作業（自動化）に割り当ててよいか。 */
    public boolean workEnabled() {
        return workEnabled;
    }

    public void setWorkEnabled(boolean enabled) {
        this.workEnabled = enabled;
    }

    /** 作業指令台の注文を割り当てる（作業中は戦わず、持ち物を取り出せない）。 */
    public void assignWork(BlockPos console, int order) {
        this.workConsole = console.immutable();
        this.workOrder = order;
        this.workJob = null;
        workGoal.loadWork(new CompoundTag());
        int mask = 0;
        int[] all = allCarrySlots();
        for (int i = 0; i < all.length; i++) {
            if (!inventory.getItem(all[i]).isEmpty()) {
                mask |= 1 << i;
            }
        }
        this.personal = mask;
        entityData.set(WORKING, true);
        setTarget(null);
    }

    /** 同じ作業指令台の、受け持つほかの注文へ移る（手が空いているとき。持ち主の物の区別はそのまま）。 */
    public void switchOrder(int order) {
        this.workOrder = order;
    }

    /** 死亡後の転送・再印刷でも、持ち主の物と作業用の物を区別して復元する。 */
    public CompoundTag saveWorkState() {
        CompoundTag t = workGoal.saveWork();
        t.putInt("personal", personal);
        var fluids = new net.minecraft.nbt.ListTag();
        for (var tank : tanks) {
            fluids.add(tank.writeToNBT(registryAccess(), new CompoundTag()));
        }
        t.put("tanks", fluids);
        return t;
    }

    public void restoreWorkState(CompoundTag t) {
        personal = t.getInt("personal");
        workGoal.loadWork(t);
        var fluids = t.getList("tanks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < TANKS; i++) {
            tanks[i].setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
            if (i < fluids.size()) {
                tanks[i].readFromNBT(registryAccess(), fluids.getCompound(i));
            }
        }
        syncTanks();
    }

    /** 作業をやめる（元の動き方に戻る）。 */
    public void clearWork() {
        workGoal.loadWork(new CompoundTag());
        this.workConsole = null;
        this.workOrder = -1;
        this.workJob = null;
        this.personal = 0;
        entityData.set(WORKING, false);
        entityData.set(ACTIVITY, ACT_NONE);
        if (state() == STATE_CRAFTING || state() == STATE_MINING) {
            setState(STATE_IDLE);
        }
    }

    @Nullable
    public BlockPos workConsole() {
        return workConsole;
    }

    public int workOrder() {
        return workOrder;
    }

    /** 作業中か（クライアントでも見える）。 */
    public boolean isWorking() {
        return entityData.get(WORKING);
    }

    void setActivity(int activity) {
        if (entityData.get(ACTIVITY) != activity) {
            entityData.set(ACTIVITY, activity);
        }
    }

    /** 作業中に今していること（充電に戻っている間は充電、戦っている間は戦い）。作業中でなければ {@link #ACT_NONE}。 */
    public int activity() {
        if (!isWorking()) {
            return ACT_NONE;
        }
        int s = state();
        if (s == STATE_LOW_POWER || s == STATE_RETREAT || charging(s)) {
            return ACT_CHARGE;
        }
        if (getTarget() != null || entityData.get(ALERT)) {
            return ACT_FIGHT;
        }
        return entityData.get(ACTIVITY);
    }

    /** 持ち物の5つと、バックパックがあればその24（全部）。 */
    private int[] allCarrySlots() {
        int n = RuinAndroid.CARRY_SLOTS + (isBackpackUpgrade(inventory.getItem(SLOT_BACKPACK)) ? PACK_SLOTS : 0);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = i < CARRY_SLOTS ? CARRY_START + i : PACK_START + (i - CARRY_SLOTS);
        }
        return out;
    }

    /** 作業で運ぶ物を入れるスロット: 持ち物とバックパックのうち、作業を始めたときに空いていた所。 */
    public int[] carrySlots() {
        int[] all = allCarrySlots();
        int n = 0;
        for (int i = 0; i < all.length; i++) {
            if ((personal & (1 << i)) == 0) {
                n++;
            }
        }
        int[] out = new int[n];
        n = 0;
        for (int i = 0; i < all.length; i++) {
            if ((personal & (1 << i)) == 0) {
                out[n++] = all[i];
            }
        }
        return out;
    }

    /** 持ち物とバックパックの空いているスロットの数（作業に使える場所。作業を始める前の見積もり）。 */
    public int freeCarry() {
        int n = 0;
        for (int slot : allCarrySlots()) {
            if (inventory.getItem(slot).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public int carryCapacity() {
        return carrySlots().length;
    }

    public boolean carryEmpty() {
        for (int slot : carrySlots()) {
            if (!inventory.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return tanksEmpty();
    }

    /** 持ち物に入れる（同じ物に重ねてから、空きへ）。入りきらなかった分を返す。 */
    public net.minecraft.world.item.ItemStack addToCarry(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.item.ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int slot : carrySlots()) {
                net.minecraft.world.item.ItemStack s = inventory.getItem(slot);
                if (pass == 0 && !s.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, rest)) {
                    int move = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
                    s.grow(move);
                    rest.shrink(move);
                } else if (pass == 1 && s.isEmpty()) {
                    inventory.setItem(slot, rest);
                    rest = net.minecraft.world.item.ItemStack.EMPTY;
                }
                if (rest.isEmpty()) {
                    break;
                }
            }
        }
        inventory.setChanged();
        return rest;
    }

    /** 持ち物から、見本と同じ物を count 個取り出す（足りなければ取らずに false）。 */
    public boolean takeFromCarry(net.minecraft.world.item.ItemStack like, int count) {
        int have = 0;
        for (int slot : carrySlots()) {
            if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(inventory.getItem(slot), like)) {
                have += inventory.getItem(slot).getCount();
            }
        }
        if (have < count) {
            return false;
        }
        for (int slot : carrySlots()) {
            net.minecraft.world.item.ItemStack s = inventory.getItem(slot);
            if (count > 0 && net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, like)) {
                int take = Math.min(count, s.getCount());
                s.shrink(take);
                count -= take;
            }
        }
        inventory.setChanged();
        return true;
    }

    /** 護衛の持ち物（GUI のスロット）。 */
    public net.minecraft.world.SimpleContainer inventory() {
        return inventory;
    }

    public static boolean isRepairKit(net.minecraft.world.item.ItemStack stack) {
        return isItem(stack, "android_repair_kit");
    }

    public static boolean isArmorPlate(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof AndroidArmorPlateItem;
    }

    /** 自己修理キットを1つ使って、体力を半分直す。使えたら true。 */
    private boolean useRepairKit(ServerLevel level) {
        if (repairCooldown > 0 || !isRepairKit(inventory.getItem(SLOT_REPAIR))) {
            return false;
        }
        inventory.removeItem(SLOT_REPAIR, 1);
        heal(getMaxHealth() * REPAIR_FRACTION);
        repairCooldown = REPAIR_COOLDOWN;
        sparks(level, position().add(0, 1.0, 0), 14, 0.4);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 6, 0.3, 0.4, 0.3, 0);
        playSound(SoundEvents.SMITHING_TABLE_USE, 0.8F, 1.5F);
        return true;
    }

    /** 持ち主（か、ステーションの許可証に登録された人）が右クリックすると、持ち物と様子の GUI を開く。 */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (!entityData.get(GUARD) || player.isShiftKeyDown()) {
            return super.mobInteract(player, hand);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && mayOperate(player)) {
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new AndroidMenu(id, inv, this), getDisplayName()),
                    buf -> buf.writeVarInt(getId()));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** 倒れたら、持ち物（自己修理キット・装甲板・アップグレード・運んでいる物）をその場に落とす。 */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        releaseInventory(level);
    }

    /**
     * 持ち物（自己修理キット・装甲板・アップグレード・運んでいる物）を手放す。ステーションに転送アップグレードがあれば、ステーションへ転送して
     * 次に印刷するアンドロイドに持たせる。なければその場に落とす。
     */
    private void releaseInventory(ServerLevel level) {
        AndroidStationBlockEntity station = home != null
                && level.getBlockEntity(home) instanceof AndroidStationBlockEntity s && s.hasTransfer() ? s : null;
        boolean sent = false;
        if (station != null && workConsole != null) {
            station.keepWork(getUUID(), workConsole, workOrder, saveWorkState());    // 印刷し直したアンドロイドが、同じ注文の作業を続ける
        }
        if (station == null && workConsole != null && level.isLoaded(workConsole)
                && level.getBlockEntity(workConsole) instanceof io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity c) {
            c.cancel(workOrder, null);           // 消費済みの作業を別の個体に最初からやり直させない
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inventory.removeItemNoUpdate(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (station != null) {
                stack = station.keep(getUUID(), i, stack);
                sent = true;
            }
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
        if (sent) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, getX(), getY() + 0.8, getZ(), 20, 0.3, 0.5, 0.3, 0.2);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.6F);
        }
    }

    /** 持ち物を読み込む。0.6.0 までの保存（位置なしの並び）は、自己修理キットと装甲板をそれぞれの場所へ戻す。 */
    private void loadInventory(CompoundTag tag) {
        inventory.clearContent();
        if (tag.contains("items")) {
            net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items = net.minecraft.core.NonNullList.withSize(INVENTORY_SIZE,
                    net.minecraft.world.item.ItemStack.EMPTY);
            net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound("items"), items, registryAccess());
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                inventory.setItem(i, items.get(i));
            }
            return;
        }
        for (net.minecraft.nbt.Tag t : tag.getList("inventory", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            net.minecraft.world.item.ItemStack.parse(registryAccess(), t).ifPresent(stack ->
                    inventory.setItem(isArmorPlate(stack) ? SLOT_PLATE : SLOT_REPAIR, stack));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    /** 光の色（目・刃・印刷の光）。型ごとに違う。 */
    public abstract int accent();

    public abstract AndroidKind kind();

    /** 呼び名: 付けた名前があればそれ、なければ型の愛称（ルミナなど）。 */
    public net.minecraft.network.chat.Component nickname() {
        return hasCustomName() ? getCustomName() : net.minecraft.network.chat.Component.translatable(kind().nicknameKey());
    }

    /** 名前を付け直す（空なら愛称に戻す）。ステーションにも覚えさせる（印刷し直しても同じ名前）。 */
    public void rename(String name) {
        String n = name == null ? "" : name.strip();
        if (n.length() > 32) {
            n = n.substring(0, 32);
        }
        setCustomName(n.isEmpty() ? null : net.minecraft.network.chat.Component.literal(n));
        if (home != null && level() instanceof ServerLevel level && level.getBlockEntity(home) instanceof AndroidStationBlockEntity station) {
            station.rememberName(getUUID(), n);
        }
    }

    /** 敵を見つけているか（GUI のひとことのため）。 */
    public boolean alert() {
        return entityData.get(ALERT);
    }

    /**
     * 解体する（護衛の GUI のボタン）。持ち物をその場に落とし、印刷を逆にたどるように上から消えていく。
     * 消えたらステーションに知らせ、ステーションはすぐに新しく印刷する（キットがあれば）。
     */
    public void dismantle() {
        if (dismantling || !guard || !printed()) {
            return;
        }
        dismantling = true;
        if (level() instanceof ServerLevel level) {
            releaseInventory(level);
        }
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
        setTarget(null);
        getNavigation().stop();
        playSound(SoundEvents.BEACON_DEACTIVATE, 0.9F, 1.4F);
    }

    /** ステーションを失う（身寄りのないアンドロイドになり、新しいステーションでリンクし直せる）。 */
    void loseStation() {
        if (guard) {
            setHome(null);
            clearRestriction();
        }
    }

    /** ステーションを失った護衛か（新しいステーションでリンクし直せる）。 */
    public boolean orphan() {
        return guard && home == null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, STATE_IDLE);
        builder.define(CHARGE, CHARGE_MAX);
        builder.define(PRINT, PRINT_TICKS);
        builder.define(CHARGER_LINK, -1);
        builder.define(GUARD, false);
        builder.define(ALERT, false);
        builder.define(BATTERIES, 0);
        builder.define(TANK_A, -1);
        for (EntityDataAccessor<net.minecraft.world.item.ItemStack> d : DECOR) {
            builder.define(d, net.minecraft.world.item.ItemStack.EMPTY);
        }
        builder.define(TANK_B, -1);
        builder.define(BACKPACK, false);
        builder.define(WORKING, false);
        builder.define(ACTIVITY, ACT_NONE);
        builder.define(GESTURE, GESTURE_NONE);
        builder.define(TOOL, TOOL_PICKAXE);
    }

    // ------------------------------------------------------------------ 護衛（プレイヤーのアンドロイド）

    /** 組立ステーションで印刷した護衛にする。 */
    public void makeGuard(@Nullable java.util.UUID owner, BlockPos station, int mode, int retreatPercent) {
        this.guard = true;
        entityData.set(GUARD, true);
        this.owner = owner;
        setHome(station);
        configure(mode, retreatPercent);
        setTarget(null);
    }

    /** ステーションの設定を受け取る。 */
    public void configure(int mode, int retreatPercent) {
        this.mode = Mth.clamp(mode, MODE_GUARD, MODE_FOLLOW);
        this.retreatPercent = Mth.clamp(retreatPercent, 0, 90);
        if (home != null) {
            if (this.mode == MODE_WANDER) {
                restrictTo(home, WANDER_RADIUS);
            } else if (this.mode == MODE_FOLLOW) {
                clearRestriction();
            } else {
                restrictTo(home, 4);
            }
        }
    }

    public boolean isGuard() {
        return level().isClientSide ? entityData.get(GUARD) : guard;
    }

    @Nullable
    public java.util.UUID owner() {
        return owner;
    }

    public int mode() {
        return mode;
    }

    /** 追従中に戦うか（ステーションの設定）。 */
    public void setEscort(boolean escort) {
        this.escort = escort;
        if (!escort && mode == MODE_FOLLOW) {
            setTarget(null);
        }
    }

    public boolean escort() {
        return escort;
    }

    /** アンドロイドの画面で切り替えたのを、まだステーションに伝えていないか（その間はステーションの設定で上書きしない）。 */
    public boolean escortPending() {
        return escortPending;
    }

    /** アンドロイドの画面から、追従中に戦うかを切り替える。ステーションの設定も同じにする（読み込まれていなければ、あとで）。 */
    public void chooseEscort(boolean escort) {
        setEscort(escort);
        escortPending = true;
        pushEscort();
    }

    /** 切り替えた設定を、ステーションに伝える（読み込まれていれば）。 */
    private void pushEscort() {
        if (escortPending && home != null && level().isLoaded(home)
                && level().getBlockEntity(home) instanceof AndroidStationBlockEntity station && station.manages(getUUID())) {
            station.setEscort(escort);
            escortPending = false;
        }
    }

    /** ステーションの許可証に登録された人を受け取る。 */
    public void setMembers(java.util.List<java.util.UUID> ids) {
        members.clear();
        members.addAll(ids);
    }

    /** 追従する人を受け取る（null は持ち主）。 */
    public void setLeader(@Nullable java.util.UUID leader) {
        this.leader = leader;
    }

    /** この人が画面を開けるか（持ち主か、許可証に登録された人。持ち主がいなければだれでも）。 */
    public boolean mayOperate(Player player) {
        return owner == null || owner.equals(player.getUUID()) || members.contains(player.getUUID());
    }

    /**
     * ついていく相手: ステーションで選んだ人（許可証に登録された人。同じ世界にいれば）、次に持ち主。
     * どちらもいなければ、許可証に登録された人のうち一番近い人。
     */
    @Nullable
    public Player ownerPlayer() {
        Player chosen = leader != null && members.contains(leader) ? level().getPlayerByUUID(leader) : null;
        if (chosen != null && !chosen.isSpectator()) {
            return chosen;
        }
        Player p = owner == null ? null : level().getPlayerByUUID(owner);
        if (p != null) {
            return p;
        }
        Player best = null;
        for (java.util.UUID id : members) {
            Player m = level().getPlayerByUUID(id);
            if (m != null && !m.isSpectator() && (best == null || m.distanceToSqr(this) < best.distanceToSqr(this))) {
                best = m;
            }
        }
        return best;
    }

    /** 新しいステーションにつなぎ直す（身寄りのないアンドロイドのリンク）。持ち物はそのまま。 */
    public void link(BlockPos station, int mode, int retreatPercent) {
        setHome(station);
        configure(mode, retreatPercent);
    }

    /** ステーションへ呼び戻す（GUI のボタン）。 */
    public void recall() {
        if (printed() && state() != STATE_RETREAT && !charging(state())) {
            setTarget(null);
            setState(STATE_RETREAT);
        }
    }

    /** 敵を探す中心（迎撃・徘徊はステーション、追従は持ち主）。 */
    @Nullable
    Vec3 guardAnchor() {
        if (mode == MODE_FOLLOW) {
            Player p = ownerPlayer();
            if (p != null) {
                return p.position();
            }
        }
        return home == null ? null : Vec3.atBottomCenterOf(home.above());
    }

    /** 護衛が戦う相手か（敵対モブ。ボス・護衛のアンドロイド・飼われた動物は除く。範囲は見ない）。 */
    static boolean hostileToGuards(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.monster.Enemy && !(e instanceof RuinAndroid a && a.isGuard()) && !isBoss(e)
                && !(e instanceof net.minecraft.world.entity.TamableAnimal);
    }

    /**
     * 技（斬撃・光柱・斬撃波・衝撃波）を当ててよい相手か。護衛の技は {@link #hostileToGuards} の相手だけに当たり、
     * 遺構のアンドロイドの技は守り手（遺構のアンドロイド・ドローン・ボス）とその味方以外に当たる。
     * owner は技を出したアンドロイド（もういなければ null。そのときも guardSide で決める）。
     */
    public static boolean canHit(boolean guardSide, @Nullable Entity owner, LivingEntity e) {
        if (!e.isAlive() || e == owner || e instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return false;
        }
        if (guardSide) {
            return hostileToGuards(e);
        }
        return !RuinGuards.isGuard(e) && !(owner != null && owner.isAlliedTo(e));
    }

    /** このアンドロイドの技を当ててよい相手か。 */
    boolean canHit(LivingEntity e) {
        return canHit(guard, this, e);
    }

    /** 護衛が狙う相手か（敵対モブ。護衛のアンドロイドと飼われた動物は除く）。 */
    boolean guardTargets(LivingEntity e) {
        if (workConsole != null) {
            // 作業中は、自分を襲ってきた相手だけ迎え撃つ（近くにいる間）。片付けたら作業に戻る
            return hostileToGuards(e) && e == getLastHurtByMob() && e.isAlive() && e.distanceToSqr(this) < WORK_DEFEND_RANGE * WORK_DEFEND_RANGE;
        }
        if (!hostileToGuards(e) || mode == MODE_FOLLOW && !escort) {
            return false;
        }
        Vec3 anchor = guardAnchor();
        return anchor == null || e.position().distanceToSqr(anchor) < GUARD_RANGE * GUARD_RANGE;
    }

    /**
     * 村のゴーレムや、飼われた動物が護衛のアンドロイドを狙おうとしたら止める（アンドロイドは敵対モブの扱いなので、
     * ゴーレムが狙ってしまうため）。
     */
    public static void onChangeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof RuinAndroid a && a.isGuard()
                && (event.getEntity() instanceof net.minecraft.world.entity.animal.AbstractGolem
                || event.getEntity() instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame())) {
            event.setCanceled(true);
        }
    }

    /** この作品のボス（番人・ウォーデンなど）。護衛のアンドロイドとは互いに敵対しない。 */
    static boolean isBoss(@Nullable Entity e) {
        return e instanceof io.github.genichimaruo.singulo.ruin.RuinBoss || e instanceof io.github.genichimaruo.singulo.ruin.HorizonWarden;
    }

    /** 充電器でつながっている持ち主（クライアントでも見える）。 */
    @Nullable
    public Entity chargerLink() {
        int id = entityData.get(CHARGER_LINK);
        return id < 0 ? null : level().getEntity(id);
    }

    // ------------------------------------------------------------------ 状態

    public int state() {
        return entityData.get(STATE);
    }

    protected void setState(int state) {
        if (state() != state) {
            entityData.set(STATE, state);
        }
        stateTicks = 0;
    }

    public int charge() {
        return entityData.get(CHARGE);
    }

    public float chargeFraction() {
        return charge() / (float) maxCharge();
    }

    protected void setCharge(int charge) {
        entityData.set(CHARGE, Mth.clamp(charge, 0, maxCharge()));
    }

    /**
     * 充電を cost 使う。0 になったら充電切れ（戻る）。足りなければ使わずに false を返し、充電切れとして充電しに行く
     * （大技に足りない残りのまま、技を出せずに立ち尽くさないように）。
     */
    protected boolean spend(int cost) {
        if (charge() < cost) {
            if (active()) {
                powerDown();
            }
            return false;
        }
        setCharge(charge() - cost);
        if (charge() <= 0) {
            powerDown();
        }
        return true;
    }

    /** 充電切れ: 攻撃をやめて、印刷台へ戻る。 */
    protected void powerDown() {
        setCharge(0);
        setState(STATE_LOW_POWER);
        getNavigation().stop();
        playSound(SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.6F);
    }

    /** 攻撃できるか（充電があり、印刷が終わり、戻り・充電中でない）。 */
    public boolean active() {
        int s = state();
        return printed() && s != STATE_LOW_POWER && s != STATE_RETREAT && s != STATE_SOLAR_FOLD && !charging(s);
    }

    /** 充電中の状態か（印刷台・ソーラー・座って・充電器）。 */
    public static boolean charging(int state) {
        return state == STATE_CHARGING || state == STATE_SOLAR || state == STATE_RESTING || state == STATE_CHARGING_FOLLOW;
    }

    public boolean printed() {
        return entityData.get(PRINT) >= PRINT_TICKS;
    }

    /** 印刷の進み（0〜1）。 */
    public float printProgress(float partialTick) {
        int p = entityData.get(PRINT);
        return p >= PRINT_TICKS ? 1 : Math.min(1, (p + partialTick) / PRINT_TICKS);
    }

    /** 印刷を始める（印刷台から出すとき）。 */
    public void startPrinting() {
        entityData.set(PRINT, 0);
    }

    /** スポーンエッグやコマンドで出したときも、印刷されて現れる。 */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
                                                                   net.minecraft.world.DifficultyInstance difficulty,
                                                                   net.minecraft.world.entity.MobSpawnType reason,
                                                                   @Nullable net.minecraft.world.entity.SpawnGroupData data) {
        startPrinting();
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home;
        if (home != null) {
            restrictTo(home, HOME_RADIUS);
        }
    }

    /** クライアント: 今の動きを始めてからの時間（tick、小数）。 */
    public float stateTime(float partialTick) {
        return tickCount - clientStateStart + partialTick;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (GESTURE.equals(key) && level().isClientSide) {
            clientGestureStart = tickCount;
        }
        if (STATE.equals(key) && level().isClientSide) {
            int s = state();
            if (s != clientPrevState) {
                clientPrevState = s;
                clientStateStart = tickCount;
            }
        }
    }

    // ------------------------------------------------------------------ 行動

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PowerGoal());
        workGoal = new AndroidWorkGoal(this);
        goalSelector.addGoal(2, workGoal);
        registerCombat();
        goalSelector.addGoal(5, new GuardGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override
            public boolean canUse() {
                return (!guard || mode == MODE_WANDER) && super.canUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // 遺構のものはプレイヤーを、護衛は敵対モブを狙う。護衛はプレイヤーに打たれても仕返ししない
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return super.canUse() && !(guard && (getLastHurtByMob() instanceof Player
                        || getLastHurtByMob() instanceof RuinAndroid a && a.isGuard()));
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !guard));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.Mob.class, 5, true, false,
                e -> guard && guardTargets(e)));
    }

    /** 型ごとの戦い方（優先度 2〜5）。 */
    protected abstract void registerCombat();

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        stateTicks++;
        if (dismantling) {
            // 印刷を逆にたどって、上から消えていく
            int p = entityData.get(PRINT);
            if (p > 0) {
                entityData.set(PRINT, p - 1);
                if (p % 6 == 0) {
                    playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, 2.0F - p / (float) PRINT_TICKS);
                }
            } else {
                if (home != null && level().getBlockEntity(home) instanceof AndroidStationBlockEntity station) {
                    station.onAndroidDismantled(getUUID());
                }
                discard();
            }
            return;
        }
        if (repairCooldown > 0) {
            repairCooldown--;
        }
        if (retreatCooldown > 0) {
            retreatCooldown--;
        }
        entityData.set(ALERT, getTarget() != null && active());
        if (guard && home != null && tickCount % 20 == 0 && level().isLoaded(home)) {
            if (level().getBlockEntity(home) instanceof AndroidStationBlockEntity station && station.manages(getUUID())) {
                pushEscort();
                // 体力をステーションへ知らせる（コンパレーターの出力。どこにいても最後の値が残る）
                station.reportHealth(getUUID(), getHealth() / getMaxHealth());
                // 自己修理キットが1つでも減っていたら、ステーションのストックから補充する
                station.refill(this);
            } else {
                // ステーションが壊された（同じ場所に置き直されたものは、このアンドロイドを知らない）: 身寄りのないアンドロイドになる
                loseStation();
            }
        }
        if (guard && printed() && active() && getHealth() < getMaxHealth() * retreatPercent / 100F
                && !useRepairKit((ServerLevel) level()) && home != null && repairCooldown <= 0 && retreatCooldown <= 0) {
            // 体力が減ったら（自己修理キットがなければ）、戦いをやめてステーションへ戻る
            setTarget(null);
            setState(STATE_RETREAT);
            playSound(SoundEvents.BEACON_DEACTIVATE, 0.7F, 1.3F);
        }
        if (guard && printed() && active() && mode != MODE_FOLLOW && getTarget() == null && !isWorking() && home != null
                && retreatCooldown <= 0 && tickCount % 20 == 7 && getHealth() < getMaxHealth()
                && tickCount - getLastHurtByMobTimestamp() > 100 && level().getBlockEntity(home) instanceof AndroidStationBlockEntity) {
            // 傷ついていて、手が空いている（戦っていない・作業していない）: ステーションへ戻って直す
            setState(STATE_RETREAT);
        }
        if (guard && getTarget() != null && !guardTargets(getTarget())) {
            setTarget(null);
        }
        if (gestureTicks > 0) {
            if (gestureLook != null && gestureLook.isAlive()) {
                getLookControl().setLookAt(gestureLook, 60, 60);
            }
            if (--gestureTicks == 0) {
                entityData.set(GESTURE, GESTURE_NONE);
                gestureLook = null;
            }
        } else if (tickCount % 20 == 11) {
            greetings((ServerLevel) level());
        }
        // 作業中は、作業指令台に毎秒知らせる（充電で作業の手を止めている間も、作業を取り上げられないように）
        if (workJob != null && workJob.jobId() >= 0 && tickCount % 20 == 0 && workConsole != null && level().isLoaded(workConsole)
                && level().getBlockEntity(workConsole) instanceof io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity c
                && !c.heartbeat(this, workJob)) {
            workJob = null;
        }
        if (state() != STATE_CHARGING_FOLLOW && entityData.get(CHARGER_LINK) >= 0) {
            entityData.set(CHARGER_LINK, -1);
        }
        int p = entityData.get(PRINT);
        if (p < PRINT_TICKS) {
            entityData.set(PRINT, p + 1);
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            if (p + 1 == PRINT_TICKS) {
                playSound(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.8F);
            } else if (p % 6 == 0) {
                playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, 1.4F + p / (float) PRINT_TICKS);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!printed()) {
            getNavigation().stop();
        }
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (guard) {
            // 護衛はプレイヤーとほかの護衛の味方
            return other instanceof Player || other instanceof RuinAndroid a && a.isGuard()
                    || other instanceof net.minecraft.world.entity.animal.AbstractGolem
                    || other instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame() || super.isAlliedTo(other);
        }
        return RuinGuards.isGuard(other) || super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (!printed() || dismantling) {
            return false;
        }
        Entity by = source.getEntity();
        if (guard ? by instanceof Player || by instanceof RuinAndroid a && a.isGuard() || isBoss(by)
                || by instanceof net.minecraft.world.entity.animal.AbstractGolem : RuinGuards.isGuard(by)) {
            return false;
        }
        // ボスの叩きつけ・投げ飛ばしの落下も受けない（落下のダメージには、誰の技かが付かないので、近くにボスがいるかで見る）
        if (guard && source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                && !level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(48), RuinAndroid::isBoss).isEmpty()) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** 護衛は平和な難易度でも消えない。 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return !guard;
    }

    /** 護衛が倒れたら、ステーションに知らせる（キットがあれば印刷し直す）。護衛は経験値を落とさない。 */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (guard && home != null && level() instanceof ServerLevel level
                && level.getBlockEntity(home) instanceof AndroidStationBlockEntity station) {
            station.onAndroidDied(getUUID());
        }
    }

    /** 遺構のアンドロイドがいなくなったら、印刷台に知らせる（倒されたか、ただ消えたか）。読み込みが外れただけなら知らせない。 */
    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!guard && home != null && reason.shouldDestroy() && level() instanceof ServerLevel level && level.isLoaded(home)
                && level.getBlockEntity(home) instanceof AndroidFabricatorBlockEntity fabricator) {
            fabricator.forget(getUUID(), reason == RemovalReason.KILLED);
        }
    }

    @Override
    public boolean shouldDropExperience() {
        return !guard && super.shouldDropExperience();
    }

    /** 前方 arcDeg 度・距離 reach の扇の中の相手に当てる（技の当たり判定）。当たった数を返す。 */
    protected int sweep(double reach, double arcDeg, float damage, double knockback) {
        if (!(level() instanceof ServerLevel)) {
            return 0;
        }
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach), this::canHit)) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            double dist = to.length();
            if (dist > reach + e.getBbWidth() / 2) {
                continue;
            }
            if (arcDeg < 360 && dist > 0.6) {
                double cos = to.normalize().dot(look);
                if (cos < Math.cos(Math.toRadians(arcDeg / 2))) {
                    continue;
                }
            }
            if (e.hurt(damageSources().mobAttack(this), damage)) {
                hits++;
                Vec3 push = (dist > 0.01 ? to.normalize() : look).scale(knockback);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.2 + knockback * 0.2, push.z));
                e.hurtMarked = true;
            }
        }
        return hits;
    }

    /** 光の粒（型の色）。 */
    protected void sparks(ServerLevel level, Vec3 at, int count, double spread) {
        int c = accent();
        level.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F, (c & 0xFF) / 255F), 0.8F),
                at.x, at.y, at.z, count, spread, spread, spread, 0);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    @Override
    public float getVoicePitch() {
        return 1.25F;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("charge", charge());
        tag.putInt("print", entityData.get(PRINT));
        if (home != null) {
            tag.put("home", NbtUtils.writeBlockPos(home));
        }
        net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items = net.minecraft.core.NonNullList.withSize(INVENTORY_SIZE,
                net.minecraft.world.item.ItemStack.EMPTY);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            items.set(i, inventory.getItem(i));
        }
        tag.put("items", net.minecraft.world.ContainerHelper.saveAllItems(new CompoundTag(), items, registryAccess()));
        net.minecraft.nbt.ListTag tl = new net.minecraft.nbt.ListTag();
        for (var t : tanks) {
            tl.add(t.writeToNBT(registryAccess(), new CompoundTag()));
        }
        tag.put("tanks", tl);
        tag.putBoolean("work", workEnabled);
        if (workConsole != null) {
            tag.put("work_console", NbtUtils.writeBlockPos(workConsole));
            tag.putInt("work_order", workOrder);
            tag.putInt("work_personal", personal);
            tag.put("work_state", saveWorkState());
        }
        if (guard) {
            tag.putBoolean("guard", true);
            tag.putBoolean("dismantling", dismantling);
            if (owner != null) {
                tag.putUUID("owner", owner);
            }
            tag.putInt("mode", mode);
            tag.putInt("retreat", retreatPercent);
            tag.putBoolean("escort", escort);
            tag.putBoolean("escort_pending", escortPending);
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (java.util.UUID id : members) {
                list.add(NbtUtils.createUUID(id));
            }
            tag.put("members", list);
            if (leader != null) {
                tag.putUUID("leader", leader);
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("charge")) {
            setCharge(tag.getInt("charge"));
        }
        if (tag.contains("print")) {
            entityData.set(PRINT, Mth.clamp(tag.getInt("print"), 0, PRINT_TICKS));
        }
        NbtUtils.readBlockPos(tag, "home").ifPresent(this::setHome);
        guard = tag.getBoolean("guard");
        dismantling = tag.getBoolean("dismantling");
        entityData.set(GUARD, guard);
        loadInventory(tag);
        net.minecraft.nbt.ListTag tl = tag.getList("tanks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < TANKS; i++) {
            tanks[i].setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
            if (i < tl.size()) {
                tanks[i].readFromNBT(registryAccess(), tl.getCompound(i));
            }
        }
        syncTanks();
        workEnabled = !tag.contains("work") || tag.getBoolean("work");
        NbtUtils.readBlockPos(tag, "work_console").ifPresentOrElse(p -> {
            assignWork(p, tag.getInt("work_order"));
            personal = tag.getInt("work_personal");          // 作業を始めたときの持ち主の物の場所（読み込み直しても変えない）
            if (tag.contains("work_state")) {
                restoreWorkState(tag.getCompound("work_state"));
            }
        }, this::clearWork);
        if (tag.contains("charge")) {
            setCharge(tag.getInt("charge"));                // バッテリーの数が分かってから、満タンに合わせて戻す
        }
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        if (guard) {
            configure(tag.getInt("mode"), tag.contains("retreat") ? tag.getInt("retreat") : 30);
            escort = !tag.contains("escort") || tag.getBoolean("escort");
            escortPending = tag.getBoolean("escort_pending");
            members.clear();
            for (net.minecraft.nbt.Tag t : tag.getList("members", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                members.add(NbtUtils.loadUUID(t));
            }
            leader = tag.hasUUID("leader") ? tag.getUUID("leader") : null;
        }
        if (charge() <= 0) {
            setState(STATE_LOW_POWER);
        }
    }

    /** 護衛の立ち位置: 迎撃はステーションのそばへ戻って待ち、追従は持ち主についていく（離れすぎたらそばへ跳ぶ）。 */
    private final class GuardGoal extends Goal {
        GuardGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return guard && active() && getTarget() == null && mode != MODE_WANDER;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (mode == MODE_FOLLOW) {
                Player p = ownerPlayer();
                if (p == null) {
                    return;
                }
                double d = distanceTo(p);
                if (d > 24 && p.onGround()) {
                    // 離れすぎたら、持ち主のそばへ跳ぶ（ペットと同じ）
                    Vec3 back = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
                    teleportTo(back.x, p.getY(), back.z);
                    getNavigation().stop();
                } else if (d > 4) {
                    if (getNavigation().isDone() || tickCount % 10 == 0) {
                        getNavigation().moveTo(p, d > 10 ? 1.2 : 1.0);
                    }
                } else {
                    getNavigation().stop();
                }
            } else if (home != null && level() instanceof ServerLevel level) {
                // ステーションのそばで待つ。待っている仲間のうち1体（番）はステーションの上に立つ。
                // ほかの仲間が充電・修理に戻ってくるとき、ステーションが印刷したいときは、番が横へどいて譲る
                Vec3 dock = Vec3.atBottomCenterOf(home.above());
                boolean onDock = position().subtract(dock).horizontalDistanceSqr() < 0.8 * 0.8 && Math.abs(getY() - dock.y) < 1;
                RuinAndroid needer = dockNeeder(level);
                boolean keeper = mode == MODE_GUARD && dockKeeper(level) == RuinAndroid.this;
                boolean yield = keeper && (needer != null || level.getBlockEntity(home) instanceof AndroidStationBlockEntity s && s.wantsTop());
                if (keeper && yield && !yielding) {
                    // 「どうぞ」: 戻ってくる仲間の方を向いて知らせる
                    chirp(level, needer, 1.7F);
                }
                yielding = keeper && yield;
                if (keeper && !yield) {
                    Vec3 dv = dock.subtract(position()).multiply(1, 0, 1);
                    if (dv.length() > 0.6 || Math.abs(getY() - dock.y) > 1) {
                        if (getNavigation().isDone() || tickCount % 20 == 0) {
                            getNavigation().moveTo(dock.x, dock.y, dock.z, 0.9);
                        }
                    } else {
                        // 上に着いた: 中央に寄って止まる
                        getNavigation().stop();
                        if (dv.length() > 0.05) {
                            setDeltaMovement(dv.x * 0.3, getDeltaMovement().y, dv.z * 0.3);
                        }
                    }
                    return;
                }
                Vec3 spot = dock.add(STAND_OFFSETS[Math.floorMod(getUUID().hashCode(), STAND_OFFSETS.length)]);
                if ((spot.distanceToSqr(position()) > 3 * 3 || onDock) && (getNavigation().isDone() || tickCount % 20 == 0)) {
                    getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9);
                }
            }
        }
    }

    /** 同じステーションの護衛で、迎撃モードで手が空いて待っている仲間（自分も含む）。 */
    private java.util.List<RuinAndroid> idlePeers(ServerLevel level) {
        Vec3 dock = Vec3.atBottomCenterOf(home.above());
        return level.getEntitiesOfClass(RuinAndroid.class, new AABB(dock, dock).inflate(16),
                a -> a.isAlive() && a.guard && home.equals(a.home) && a.mode == MODE_GUARD && a.printed() && a.active()
                        && a.getTarget() == null && !a.isWorking() && a.state() == STATE_IDLE);
    }

    /** ステーションの上で待つ番: 今上にいる仲間がいればその仲間（入れ替わらないように）、いなければ UUID の一番小さい仲間。 */
    @Nullable
    private RuinAndroid dockKeeper(ServerLevel level) {
        Vec3 dock = Vec3.atBottomCenterOf(home.above());
        RuinAndroid best = null;
        for (RuinAndroid a : idlePeers(level)) {
            boolean on = a.position().subtract(dock).horizontalDistanceSqr() < 0.8 * 0.8 && Math.abs(a.getY() - dock.y) < 1;
            if (on) {
                return a;
            }
            if (best == null || a.getUUID().compareTo(best.getUUID()) < 0) {
                best = a;
            }
        }
        return best;
    }

    /** ステーションの上を使いたい仲間（充電・修理に戻ってくる、上で充電・印刷中）。いなければ null。 */
    @Nullable
    private RuinAndroid dockNeeder(ServerLevel level) {
        Vec3 dock = Vec3.atBottomCenterOf(home.above());
        for (RuinAndroid a : level.getEntitiesOfClass(RuinAndroid.class, new AABB(dock, dock).inflate(32),
                a -> a != this && a.isAlive() && a.guard && home.equals(a.home))) {
            int s = a.state();
            if (!a.printed() || s == STATE_LOW_POWER || s == STATE_RETREAT || s == STATE_CHARGING) {
                return a;
            }
        }
        return null;
    }

    /** 仲間に知らせる（短い電子音と音符。相手がいれば、互いに向き合う）。 */
    void chirp(ServerLevel level, @Nullable RuinAndroid other, float pitch) {
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, pitch);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, getX(), getY() + getBbHeight() + 0.4, getZ(), 1, 0, 0, 0,
                pitch / 2);
        if (other != null) {
            getLookControl().setLookAt(other, 60, 60);
            other.getLookControl().setLookAt(this, 60, 60);
        }
    }

    /**
     * 充電切れのとき: 印刷台の上へ歩いて戻り（ゆっくり）、着いたら充電する。満タンになったら戦いに戻る。
     * 印刷台がない（または遠すぎる）ときは、その場で充電する。
     */
    private final class PowerGoal extends Goal {
        PowerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return !printed() || state() == STATE_LOW_POWER || state() == STATE_RETREAT || state() == STATE_SOLAR_FOLD
                    || charging(state());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!printed() || !(level() instanceof ServerLevel level)) {
                return;
            }
            if (state() == STATE_LOW_POWER && guard && mode == MODE_FOLLOW) {
                // 追従中: 充電器を持った持ち主のほうがステーションより近いか、ステーションが遠すぎれば、持ち主のそばで充電する
                Player p = chargerOwner();
                double dock = home == null ? Double.MAX_VALUE : Math.sqrt(Vec3.atBottomCenterOf(home.above()).distanceToSqr(position()));
                if (p != null && (dock > DOCK_RANGE || distanceTo(p) < dock)) {
                    setState(STATE_CHARGING_FOLLOW);
                    return;
                }
            }
            if (state() == STATE_CHARGING_FOLLOW) {
                followCharge(level);
                return;
            }
            if (state() == STATE_SOLAR_FOLD) {
                getNavigation().stop();
                if (stateTicks >= FOLD_TICKS) {
                    setState(STATE_IDLE);
                }
                return;
            }
            if (state() == STATE_LOW_POWER || state() == STATE_RETREAT) {
                BlockPos h = home;
                Vec3 dock = h == null ? null : Vec3.atBottomCenterOf(h.above());
                if (state() == STATE_RETREAT && dock != null && dock.distanceToSqr(position()) > 96 * 96) {
                    // 遠すぎる（別の場所にいる）ときは戻らず、しばらく（30秒）戻ろうとしない
                    retreatCooldown = 600;
                    setState(STATE_IDLE);
                    return;
                }
                // 充電切れで、ステーション（印刷台）が遠すぎれば戻らずにその場で充電する。体力で戻るときは遠くからでも戻る
                double range = state() == STATE_RETREAT ? 96 : DOCK_RANGE;
                // ステーションの上でほかのアンドロイドが充電中なら、そばで待って、どいたら交代で乗る（待つ間は座り込まない）
                boolean taken = guard && h != null && dockTaken(level, h);
                if (dock == null || dock.distanceToSqr(position()) > range * range || stateTicks > (guard ? 1200 : 400) && !taken) {
                    getNavigation().stop();
                    setState(sunlit(level) ? STATE_SOLAR : STATE_RESTING);
                    playSound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.2F);
                    return;
                }
                if (taken && dock.distanceToSqr(position()) < WAIT_RADIUS * WAIT_RADIUS) {
                    getNavigation().stop();
                    getLookControl().setLookAt(dock.x, dock.y + 1, dock.z);
                    return;
                }
                if (!taken && dock.distanceToSqr(position()) < 1.6 * 1.6) {
                    getNavigation().stop();
                    setState(STATE_CHARGING);
                    playSound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.4F);
                    // 「ありがとう」: 譲ってくれた仲間がそばにいれば、そちらを向いて返す
                    if (guard) {
                        for (RuinAndroid a : level.getEntitiesOfClass(RuinAndroid.class, getBoundingBox().inflate(4),
                                a -> a != RuinAndroid.this && a.isAlive() && a.guard && h.equals(a.home) && a.yielding)) {
                            chirp(level, a, 1.3F);
                            break;
                        }
                    }
                    return;
                }
                if (stateTicks % 10 == 1) {
                    getNavigation().moveTo(dock.x, dock.y, dock.z, 0.75);
                }
            } else if (charging(state())) {
                getNavigation().stop();
                int s = state();
                if (s == STATE_CHARGING && home != null) {
                    // ステーション（印刷台）の真上の中心へ寄る
                    Vec3 c = Vec3.atBottomCenterOf(home.above());
                    Vec3 dv = c.subtract(position()).multiply(1, 0, 1);
                    if (dv.length() > 0.04) {
                        setDeltaMovement(dv.x * 0.3, getDeltaMovement().y, dv.z * 0.3);
                    } else {
                        setPos(c.x, getY(), c.z);
                    }
                }
                // 外で充電中に日が沈んだり屋根の下に入ったら座る。座っていて日が出たらパネルを広げる
                if (s != STATE_CHARGING && stateTicks % 40 == 0) {
                    boolean sun = sunlit(level);
                    if (s == STATE_SOLAR && !sun) {
                        setState(STATE_RESTING);
                        s = STATE_RESTING;
                    } else if (s == STATE_RESTING && sun) {
                        setState(STATE_SOLAR);
                        s = STATE_SOLAR;
                    }
                }
                int rate = s == STATE_CHARGING ? RATE_DOCK : s == STATE_SOLAR ? RATE_SOLAR : RATE_REST;
                if (s == STATE_CHARGING && guard && stateTicks % 10 == 0 && home != null
                        && level.getBlockEntity(home) instanceof AndroidStationBlockEntity dock) {
                    dock.refill(RuinAndroid.this);
                }
                if (s == STATE_CHARGING && guard) {
                    // 護衛はステーションの電力で充電し、体力も直す（電力が足りなければ待つ）
                    if (!(home != null && level.getBlockEntity(home) instanceof AndroidStationBlockEntity station
                            && station.drawCharge(AndroidStationBlockEntity.FE_PER_CHARGE_TICK))) {
                        rate = 0;
                    } else if (stateTicks % 4 == 0 && getHealth() < getMaxHealth()) {
                        heal(1);
                    }
                }
                setCharge(charge() + rate);
                if (stateTicks % 8 == 0) {
                    sparks(level, position().add(0, s == STATE_SOLAR ? 2.2 : 0.2, 0), 2, 0.3);
                }
                boolean healed = !(guard && s == STATE_CHARGING) || getHealth() >= getMaxHealth();
                if (charge() >= maxCharge() && healed) {
                    // ソーラーのときは、パネルをたたんでから戦いに戻る
                    setState(s == STATE_SOLAR ? STATE_SOLAR_FOLD : STATE_IDLE);
                    playSound(SoundEvents.BEACON_ACTIVATE, 0.7F, 1.9F);
                }
            }
        }

        /** ステーションの上で、ほかのアンドロイドが充電・印刷されているか。 */
        private boolean dockTaken(ServerLevel level, BlockPos station) {
            Vec3 dock = Vec3.atBottomCenterOf(station.above());
            return !level.getEntitiesOfClass(RuinAndroid.class, new AABB(dock, dock).inflate(0.9, 1, 0.9),
                    a -> a != RuinAndroid.this && a.isAlive() && (charging(a.state()) || !a.printed())).isEmpty();
        }

        /** 電力の残っている無線充電器を持っている持ち主（近くにいなければ null）。 */
        @Nullable
        private Player chargerOwner() {
            Player p = ownerPlayer();
            return p != null && p.isAlive() && p.distanceToSqr(RuinAndroid.this) < 64 * 64 && !AndroidChargerItem.find(p).isEmpty()
                    ? p : null;
        }

        /** 持ち主のそばへ寄って、ついて歩きながら充電器から充電する（5秒で満タン）。 */
        private void followCharge(ServerLevel level) {
            Player p = chargerOwner();
            if (p == null) {
                // 充電器が切れた・持ち主がいない: ふつうの充電切れに戻る
                setState(STATE_LOW_POWER);
                return;
            }
            entityData.set(CHARGER_LINK, p.getId());
            double d = distanceTo(p);
            if (d > 2.5) {
                if (stateTicks % 5 == 0) {
                    getNavigation().moveTo(p, 1.15);
                }
            } else {
                getNavigation().stop();
                getLookControl().setLookAt(p, 30, 30);
            }
            if (d < 4) {
                net.minecraft.world.item.ItemStack charger = AndroidChargerItem.find(p);
                int got = AndroidChargerItem.energy(charger).extractEnergy(CHARGER_FE_PER_TICK, false);
                setCharge(charge() + RATE_DOCK * got / CHARGER_FE_PER_TICK);
                if (stateTicks % 8 == 0) {
                    sparks(level, position().add(0, 1.0, 0), 2, 0.25);
                }
            }
            if (charge() >= maxCharge()) {
                setState(STATE_IDLE);
                playSound(SoundEvents.BEACON_ACTIVATE, 0.7F, 1.9F);
            }
        }

        /** 昼で、空が見えていて、雨でないか（ソーラーで充電できるか）。 */
        private boolean sunlit(ServerLevel level) {
            return level.isDay() && !level.isRaining() && level.canSeeSky(blockPosition().above());
        }
    }
}
