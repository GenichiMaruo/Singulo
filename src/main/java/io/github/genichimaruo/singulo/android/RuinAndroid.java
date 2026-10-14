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
    private static final Vec3[] STAND_OFFSETS = {new Vec3(2, 0, 0), new Vec3(-2, 0, 0), new Vec3(0, 0, 2), new Vec3(0, 0, -2)};

    /** 充電器から電力をもらって、ついて歩いている持ち主（見た目のため。いなければ -1）。 */
    private static final EntityDataAccessor<Integer> CHARGER_LINK = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    /** 護衛か（見た目と、右クリックで GUI を開けるかのため）。 */
    private static final EntityDataAccessor<Boolean> GUARD = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.BOOLEAN);
    /** 敵を見つけているか（GUI のひとこと）。 */
    private static final EntityDataAccessor<Boolean> ALERT = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.BOOLEAN);

    /** 持っているバッテリーアップグレードの数（見た目と、充電の満タンのため）。 */
    private static final EntityDataAccessor<Integer> BATTERIES = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.INT);
    /** バックパックアップグレードを積んでいるか（見た目のため）。 */
    private static final EntityDataAccessor<Boolean> BACKPACK = SynchedEntityData.defineId(RuinAndroid.class, EntityDataSerializers.BOOLEAN);

    /**
     * 護衛の持ち物: 0 は自己修理キット（1スタック）、1 は装甲板（胸当てとして働く）、2 はバッテリーアップグレード（2個まで）、
     * 3 はバックパックアップグレード、4 は採掘アップグレード。5〜9 は運ぶ物を入れる持ち物（ホッパーと同じ5スロット）、
     * 10〜33 はバックパックの中（24スロット。バックパックを積んでいるときだけ使える）。
     */
    public static final int SLOT_REPAIR = 0, SLOT_PLATE = 1, SLOT_BATTERY = 2, SLOT_BACKPACK = 3, SLOT_MINING = 4;
    public static final int CARRY_START = 5, CARRY_SLOTS = 5, PACK_START = 10, PACK_SLOTS = 24;
    public static final int INVENTORY_SIZE = PACK_START + PACK_SLOTS;
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
        builder.define(BACKPACK, false);
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
        if (guard && getTarget() != null && !guardTargets(getTarget())) {
            setTarget(null);
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
        tag.putBoolean("work", workEnabled);
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
        workEnabled = !tag.contains("work") || tag.getBoolean("work");
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
            } else if (home != null) {
                // ステーションのすぐ横で待つ。上に乗ったままにならないよう、上にいたら横へどく（ほかのアンドロイドの充電・印刷のため）
                Vec3 dock = Vec3.atBottomCenterOf(home.above());
                Vec3 spot = dock.add(STAND_OFFSETS[Math.floorMod(getUUID().hashCode(), STAND_OFFSETS.length)]);
                boolean onDock = position().subtract(dock).horizontalDistanceSqr() < 0.8 * 0.8 && Math.abs(getY() - dock.y) < 1;
                if ((spot.distanceToSqr(position()) > 3 * 3 || onDock) && (getNavigation().isDone() || tickCount % 20 == 0)) {
                    getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9);
                }
            }
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
