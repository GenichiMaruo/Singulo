package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.registry.SinguloDamageTypes;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * グラビトン・マニピュレーター（段階5）。慣性制御ガントレットの完全版。
 * <ul>
 *   <li>浮遊: HP の制限なし（ボスと重力耐性は除く）</li>
 *   <li>牽引: 射程（設定 manipulatorRange、既定24ブロック）。落ちているアイテムも引き寄せる</li>
 *   <li>斥力: 自分の周り半径6ブロックにモブを寄せ付けず、飛んでくる飛び道具をそらす</li>
 *   <li>圧壊: 対象の動きを止め、毎秒「2＋最大HPの5%」のダメージ（防具無視）。ボスは止まらず、ダメージは BOSS_CRUSH_CAP まで</li>
 * </ul>
 * 操作: 左クリックを押している間、今のモードで重力を操る。浮遊モードでは、持ち上げたまま右クリックを押して力をため、
 * 離すと対象を視線の向きへ勢いよく吹き飛ばす。スニーク＋右クリックでモード切替。
 * 電力に加えて、エキゾチック物質を使用 CHARGE_PER_MATTER tick ごとに1個使う（なければ持ち物の重力閉じ込めタンクの
 * ダークマター DARK_MATTER_PER_CHARGE mB で代わりになる）。キー割り当て（既定 G）で前方の円錐範囲に切り替えられる。
 */
public class GravitonManipulatorItem extends GravityGauntletItem {
    public static final int CAPACITY = 2_000_000;
    public static final int FE_PER_TICK = 400;
    /** 射程の既定値（設定 manipulatorRange）。 */
    public static final double RANGE = 24;
    public static final double REPEL_RADIUS = 6;
    public static final float BOSS_CRUSH_CAP = 10;
    public static final int CHARGE_PER_MATTER = 6000;
    private static final Mode[] MODES = Mode.values();

    public GravitonManipulatorItem(Properties properties, int stage) {
        super(properties, stage);
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    @Override
    public int fePerTick() {
        return FE_PER_TICK;
    }

    @Override
    public double range() {
        return io.github.genichimaruo.singulo.generated.ServerConfig.SPEC.isLoaded()
                ? io.github.genichimaruo.singulo.generated.ServerConfig.MANIPULATOR_RANGE.get() : RANGE;
    }

    @Override
    public Mode[] modes() {
        return MODES;
    }

    @Override
    protected String hintKey() {
        return "tooltip.singulo.manipulator.hint";
    }

    @Override
    public boolean supportsCone() {
        return true;
    }

    // ------------------------------------------------------------------ 操作: 左クリックで操り、浮遊中は右クリックでためて投げる

    /** 投げる力をためきるまでの tick。 */
    public static final int MAX_CHARGE = 40;
    /** ためた1 tick ごとの電力。 */
    public static final int FE_PER_CHARGE_TICK = 2_000;

    /** 左クリックを押しているプレイヤー（サーバー側）。 */
    private static final java.util.Set<java.util.UUID> LEFT_DOWN = new java.util.HashSet<>();

    /** クライアントから、左クリックを押した・離したを受け取る。 */
    public static void setLeftDown(ServerPlayer player, boolean down) {
        if (down) {
            LEFT_DOWN.add(player.getUUID());
        } else {
            LEFT_DOWN.remove(player.getUUID());
            if (!player.isUsingItem()) {
                release(player);
            }
        }
    }

    /** 左クリックを押している間、毎tick 今のモードで重力を操る。 */
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !LEFT_DOWN.contains(player.getUUID())) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GravitonManipulatorItem item)) {
            LEFT_DOWN.remove(player.getUUID());
            release(player);
            return;
        }
        if (!item.operate(player, stack, player.tickCount)) {
            LEFT_DOWN.remove(player.getUUID());
            release(player);
        }
    }

    /** 右クリック: スニーク中はモード切替。浮遊モードでは投げる力をためる（左クリックで持ち上げている間）。 */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player,
                                                                       net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }
        if (mode(stack) != Mode.LEVITATE) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("gauntlet.singulo.left_click"), true);
            }
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return net.minecraft.world.InteractionResultHolder.consume(stack);
    }

    /** ためている間は、持ち上げている対象を手放さず、力のたまり具合を出す。 */
    @Override
    public void onUseTick(net.minecraft.world.level.Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide || !(user instanceof ServerPlayer player)) {
            return;
        }
        int charged = Math.min(MAX_CHARGE, getUseDuration(stack, user) - remaining);
        Entity held = heldTarget(player);
        if (held != null && held.isAlive()) {
            super.apply(player, held, Mode.LEVITATE, remaining);
            if (charged % 4 == 0) {
                ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                        held.getX(), held.getY() + held.getBbHeight() / 2, held.getZ(), 2 + charged / 8, 0.3, 0.3, 0.3, 0.02);
            }
        }
        if (charged % 5 == 0) {
            player.displayClientMessage(Component.translatable("gauntlet.singulo.charging", charged * 100 / MAX_CHARGE), true);
        }
    }

    /** 右クリックを離すと、持ち上げていた対象を視線の向きへ勢いよく吹き飛ばす（ためた時間ほど強い）。 */
    @Override
    public void releaseUsing(ItemStack stack, net.minecraft.world.level.Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide || !(user instanceof ServerPlayer player)) {
            return;
        }
        int charged = Math.min(MAX_CHARGE, getUseDuration(stack, user) - timeLeft);
        Entity held = heldTarget(player);
        if (held == null || !held.isAlive()) {
            player.displayClientMessage(Component.translatable("gauntlet.singulo.nothing_held"), true);
            return;
        }
        var energy = energy(stack);
        int cost = FE_PER_CHARGE_TICK * Math.max(1, charged);
        if (energy.extractEnergy(cost, true) < cost) {
            player.displayClientMessage(Component.translatable("gauntlet.singulo.no_energy"), true);
            return;
        }
        energy.extractEnergy(cost, false);
        held.setDeltaMovement(flingVelocity(player.getLookAngle(), charged));
        held.hasImpulse = true;
        held.hurtMarked = true;
        held.fallDistance = 0;
        release(player);
        level.playSound(null, held.blockPosition(), net.minecraft.sounds.SoundEvents.WIND_CHARGE_BURST.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F + charged / 80.0F);
    }

    /** 吹き飛ばす速さ（ためた tick から）。 */
    public static Vec3 flingVelocity(Vec3 look, int charged) {
        double power = 0.8 + 2.4 * Math.min(MAX_CHARGE, charged) / MAX_CHARGE;
        return look.normalize().scale(power).add(0, 0.25, 0);
    }

    /** 左クリックでブロックを壊したり、生き物を叩いたりしない（重力を操るのに使う）。 */
    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                                  net.minecraft.core.BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return true;
    }

    @Override
    protected String noTargetKey() {
        return "gauntlet.singulo.no_target_manipulator";
    }

    @Override
    public boolean affects(Entity entity, Mode mode) {
        if (mode == Mode.PULL && entity instanceof ItemEntity) {
            return true;
        }
        if (!(entity instanceof LivingEntity living) || entity instanceof Player || !living.isAlive()) {
            return false;
        }
        // ボスは圧壊のダメージだけ受ける（拘束はされない）
        if (mode == Mode.CRUSH && entity.getType().is(Tags.EntityTypes.BOSSES)) {
            return true;
        }
        return !isGravityImmune(entity);
    }

    /** 圧壊の1秒あたりのダメージ。 */
    public static float crushDamage(LivingEntity target) {
        float damage = 2 + target.getMaxHealth() * 0.05F;
        return target.getType().is(Tags.EntityTypes.BOSSES) ? Math.min(BOSS_CRUSH_CAP, damage) : damage;
    }

    @Override
    protected boolean payExtra(ServerPlayer player, ItemStack stack) {
        if (ExoticCharge.draw(player, stack, CHARGE_PER_MATTER, true)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("gauntlet.singulo.no_exotic"), true);
        return false;
    }

    @Override
    protected void apply(ServerPlayer player, Entity target, Mode mode, int remaining) {
        if (mode != Mode.CRUSH) {
            super.apply(player, target, mode, remaining);
            return;
        }
        LivingEntity living = (LivingEntity) target;
        boolean boss = target.getType().is(Tags.EntityTypes.BOSSES);
        if (!boss) {
            // 自重で押しつぶす: 横の動きを止め、下へ押しつける
            Vec3 v = target.getDeltaMovement();
            target.setDeltaMovement(0, Math.min(v.y, 0) - 0.08, 0);
            target.hurtMarked = true;
        }
        if (remaining % 20 == 0) {
            living.hurt(SinguloDamageTypes.tidal(player.level(), player), crushDamage(living));
        }
    }

    /** 実際の斥力（テストからも呼ぶ）。 */
    @Override
    public void repel(ServerLevel level, ServerPlayer player) {
        repelAround(level, player, player.position().add(0, player.getBbHeight() / 2, 0));
    }

    /** center の周り REPEL_RADIUS のモブを押し出し、飛び道具をそらす。owner の飛び道具はそらさない。 */
    public static void repelAround(ServerLevel level, Entity owner, Vec3 center) {
        AABB box = new AABB(center, center).inflate(REPEL_RADIUS);
        for (Entity e : level.getEntities(owner, box)) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(center);
            if (to.lengthSqr() > REPEL_RADIUS * REPEL_RADIUS) {
                continue;
            }
            Vec3 out = to.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : to.normalize();
            if (e instanceof Projectile p) {
                if (p.getOwner() == owner) {
                    continue;
                }
                double speed = Math.max(0.5, p.getDeltaMovement().length());
                p.setDeltaMovement(out.scale(speed));
                p.hasImpulse = true;
                p.hurtMarked = true;
            } else if (e instanceof LivingEntity living && !(e instanceof Player) && !isGravityImmune(living)) {
                e.setDeltaMovement(out.multiply(1, 0, 1).scale(0.6).add(0, 0.15, 0));
                e.hurtMarked = true;
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.singulo.exotic_charge", ExoticCharge.get(stack) / 20)
                .withStyle(ChatFormatting.GRAY));
    }
}
