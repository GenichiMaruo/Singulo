package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloTags;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.common.Tags;
import io.github.genichimaruo.singulo.compat.ComponentEnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * 慣性制御ガントレット（段階3）。右クリックを押している間、視線上の1体の重力を操る。
 * <ul>
 *   <li>浮遊: 目の前3ブロックに持ち上げて保持する。離すと落ちる</li>
 *   <li>牽引: 手元へ引き寄せる</li>
 * </ul>
 * 対象は射程（設定 gauntletRange、既定12ブロック）以内・最大HP40以下。ボスと重力耐性（#singulo:gravity_immune）とプレイヤーには効かない。
 * 使用中は電力を消費する。スニークして電力を持つブロックを右クリックすると充電する。
 * 完全版のグラビトン・マニピュレーター（段階5）はこのクラスを広げる。
 */
public class GravityGauntletItem extends SinguloItem {
    public enum Mode { LEVITATE, PULL, REPEL, CRUSH }

    public static final int CAPACITY = 100_000;
    public static final int FE_PER_TICK = 40;
    /** 射程の既定値（設定 gauntletRange）。 */
    public static final double RANGE = 12;
    public static final float MAX_TARGET_HEALTH = 40;
    public static final double HOLD_DISTANCE = 3;
    private static final Mode[] MODES = {Mode.LEVITATE, Mode.PULL};

    /** 使用中の対象（サーバー側、プレイヤーごと）。 */
    private static final Map<UUID, Integer> TARGETS = new HashMap<>();

    public GravityGauntletItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    // ------------------------------------------------------------------ 性能（完全版で上書きする）

    public int capacity() {
        return CAPACITY;
    }

    public int fePerTick() {
        return FE_PER_TICK;
    }

    public double range() {
        return io.github.genichimaruo.singulo.generated.ServerConfig.SPEC.isLoaded()
                ? io.github.genichimaruo.singulo.generated.ServerConfig.GAUNTLET_RANGE.get() : RANGE;
    }

    public Mode[] modes() {
        return MODES;
    }

    /** この道具で対象にできるか。 */
    public boolean affects(Entity entity, Mode mode) {
        return canAffect(entity);
    }

    /** 使っている間の追加の消費（エキゾチック物質など）。払えなければ false。 */
    protected boolean payExtra(ServerPlayer player, ItemStack stack) {
        return true;
    }

    /** 説明の最後に出す操作の案内。 */
    protected String hintKey() {
        return "tooltip.singulo.gauntlet.hint";
    }

    protected String noTargetKey() {
        return "gauntlet.singulo.no_target";
    }

    /** 前方の円錐範囲に切り替えられるか（完全版のみ）。 */
    public boolean supportsCone() {
        return false;
    }

    public static final double CONE_HALF_ANGLE = 25;
    public static final int CONE_MAX_TARGETS = 8;

    public static boolean cone(ItemStack stack) {
        return Boolean.TRUE.equals(SinguloComponents.get(stack, SinguloComponents.CONE.get()));
    }

    /** 視線上の1体と前方の円錐を切り替える（キー割り当てから呼ばれる）。 */
    public void toggleCone(Player player, ItemStack stack) {
        if (!supportsCone()) {
            return;
        }
        boolean next = !cone(stack);
        SinguloComponents.set(stack, SinguloComponents.CONE.get(), next);
        player.displayClientMessage(Component.translatable(next ? "gauntlet.singulo.area.cone" : "gauntlet.singulo.area.single"),
                true);
    }

    /** 前方の円錐（半角 CONE_HALF_ANGLE、射程 range）の中の対象。近い順に CONE_MAX_TARGETS 体まで。 */
    public List<Entity> coneTargets(Level level, Player player, Mode mode) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = range();
        double cos = Math.cos(Math.toRadians(CONE_HALF_ANGLE));
        List<Entity> out = new java.util.ArrayList<>(level.getEntities(player, player.getBoundingBox().inflate(range), e -> {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double d = to.length();
            return d <= range && d > 1e-3 && to.dot(look) / d >= cos && affects(e, mode);
        }));
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        return out.size() > CONE_MAX_TARGETS ? out.subList(0, CONE_MAX_TARGETS) : out;
    }

    // ------------------------------------------------------------------

    public static IEnergyStorage energy(ItemStack stack) {
        int capacity = stack.getItem() instanceof GravityGauntletItem g ? g.capacity() : CAPACITY;
        return new ComponentEnergyStorage(stack, SinguloComponents.ENERGY.get(), capacity);
    }

    public Mode mode(ItemStack stack) {
        Integer m = SinguloComponents.get(stack, SinguloComponents.GRAVITY_MODE.get());
        for (Mode mode : modes()) {
            if (m != null && mode.ordinal() == m) {
                return mode;
            }
        }
        return modes()[0];
    }

    /** 段階3のガントレットで対象にできるか。 */
    public static boolean canAffect(Entity entity) {
        return entity instanceof LivingEntity living && !(entity instanceof Player) && living.isAlive()
                && living.getMaxHealth() <= MAX_TARGET_HEALTH && !isGravityImmune(entity);
    }

    /** ボスと重力耐性のモブ（浮遊・圧壊の拘束を受けない）。 */
    public static boolean isGravityImmune(Entity entity) {
        return entity.getType().is(Tags.EntityTypes.BOSSES) || entity.getType().is(SinguloTags.GRAVITY_IMMUNE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                Mode[] modes = modes();
                int index = 0;
                for (int i = 0; i < modes.length; i++) {
                    if (modes[i] == mode(stack)) {
                        index = i;
                    }
                }
                Mode next = modes[(index + 1) % modes.length];
                SinguloComponents.set(stack, SinguloComponents.GRAVITY_MODE.get(), next.ordinal());
                player.displayClientMessage(Component.translatable("tooltip.singulo.gauntlet.mode", modeName(next)), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (energy(stack).getEnergyStored() < fePerTick()) {
            player.displayClientMessage(Component.translatable("gauntlet.singulo.no_energy"), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    static Component modeName(Mode mode) {
        return Component.translatable("gauntlet.singulo.mode." + mode.name().toLowerCase());
    }

    /** スニークして電力を持つブロックを右クリックすると、そこから充電する。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        IEnergyStorage source = Capabilities.get(level, Capabilities.EnergyStorage.BLOCK, context.getClickedPos(),
                context.getClickedFace());
        if (source == null || !source.canExtract()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            IEnergyStorage own = energy(context.getItemInHand());
            int want = own.receiveEnergy(Integer.MAX_VALUE, true);
            int got = source.extractEnergy(want, false);
            own.receiveEnergy(got, false);
            player.displayClientMessage(Component.translatable("gauntlet.singulo.charged", got), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72_000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide || !(user instanceof ServerPlayer player)) {
            return;
        }
        if (!operate(player, stack, remaining)) {
            stop(player);
        }
    }

    /**
     * 1 tick ぶん重力を操る（今のモードの効果を出す）。電力やエキゾチック物質が足りず止めるべきなら false。
     * ガントレットは右クリックを押している間、マニピュレーターは左クリックを押している間に呼ばれる。
     */
    public boolean operate(ServerPlayer player, ItemStack stack, int tick) {
        Level level = player.level();
        int remaining = tick;
        IEnergyStorage energy = energy(stack);
        if (energy.extractEnergy(fePerTick(), true) < fePerTick()) {
            player.displayClientMessage(Component.translatable("gauntlet.singulo.no_energy"), true);
            return false;
        }
        Mode mode = mode(stack);
        if (mode == Mode.REPEL) {
            if (!payExtra(player, stack)) {
                return false;
            }
            energy.extractEnergy(fePerTick(), false);
            repel((ServerLevel) level, player);
            return true;
        }
        if (supportsCone() && cone(stack)) {
            List<Entity> targets = coneTargets(level, player, mode);
            if (targets.isEmpty()) {
                if (remaining % 20 == 0) {
                    player.displayClientMessage(Component.translatable(noTargetKey(), (int) range()), true);
                }
                return true;
            }
            // 円錐では対象の数だけ電力を使う
            int cost = fePerTick() * targets.size();
            if (energy.extractEnergy(cost, true) < cost) {
                player.displayClientMessage(Component.translatable("gauntlet.singulo.no_energy"), true);
                return false;
            }
            if (!payExtra(player, stack)) {
                return false;
            }
            energy.extractEnergy(cost, false);
            for (Entity e : targets) {
                apply(player, e, mode, remaining);
            }
            return true;
        }
        Entity target = currentTarget((ServerLevel) level, player, mode);
        if (target == null) {
            if (remaining % 20 == 0) {
                player.displayClientMessage(Component.translatable(noTargetKey(), (int) range()), true);
            }
            return true;
        }
        if (!payExtra(player, stack)) {
            return false;
        }
        energy.extractEnergy(fePerTick(), false);
        apply(player, target, mode, remaining);
        return true;
    }

    private static void stop(ServerPlayer player) {
        player.stopUsingItem();
        TARGETS.remove(player.getUUID());
    }

    /** いま捕まえている対象（浮遊・圧壊などで1体を捕まえているとき）。 */
    @Nullable
    static Entity heldTarget(ServerPlayer player) {
        Integer id = TARGETS.get(player.getUUID());
        return id == null ? null : player.serverLevel().getEntity(id);
    }

    static void release(ServerPlayer player) {
        TARGETS.remove(player.getUUID());
    }

    /** 浮遊で持ち上げた対象を置いておく距離（ブロック）。 */
    protected double holdDistance() {
        return HOLD_DISTANCE;
    }

    /** 電力の残りが毎tick変わるので、手に持った表示が持ち直しのたびに揺れないようにする（別のアイテムに替えたときだけ）。 */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    /** 対象に効果を出す（毎tick）。 */
    protected void apply(ServerPlayer player, Entity target, Mode mode, int remaining) {
        Vec3 velocity = switch (mode) {
            case LEVITATE -> {
                Vec3 hold = player.getEyePosition().add(player.getLookAngle().scale(holdDistance()));
                Vec3 center = target.position().add(0, target.getBbHeight() / 2, 0);
                yield hold.subtract(center).scale(0.3);
            }
            case PULL -> {
                Vec3 toPlayer = player.position().subtract(target.position());
                yield toPlayer.length() < 1.5 ? Vec3.ZERO : toPlayer.normalize().scale(0.5);
            }
            default -> target.getDeltaMovement();
        };
        target.setDeltaMovement(velocity);
        target.fallDistance = 0;
        target.hurtMarked = true;
    }

    /** 斥力（完全版のみ）。 */
    protected void repel(ServerLevel level, ServerPlayer player) {}

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        TARGETS.remove(user.getUUID());
    }

    /** 使い始めに視線上の1体を捕まえ、離すまで同じ対象を保つ。 */
    @Nullable
    private Entity currentTarget(ServerLevel level, ServerPlayer player, Mode mode) {
        double range = range();
        Integer id = TARGETS.get(player.getUUID());
        if (id != null) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive() && affects(e, mode) && e.distanceTo(player) <= range * 2) {
                return e;
            }
            TARGETS.remove(player.getUUID());
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box, e -> affects(e, mode), range * range);
        if (hit == null) {
            return null;
        }
        TARGETS.put(player.getUUID(), hit.getEntity().getId());
        return hit.getEntity();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * energy(stack).getEnergyStored() / capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x78D2F0;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.singulo.energy", energy(stack).getEnergyStored(), capacity())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.singulo.gauntlet.mode", modeName(mode(stack))).withStyle(ChatFormatting.GRAY));
        if (supportsCone()) {
            tooltip.add(Component.translatable(cone(stack) ? "gauntlet.singulo.area.cone" : "gauntlet.singulo.area.single")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(hintKey()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
