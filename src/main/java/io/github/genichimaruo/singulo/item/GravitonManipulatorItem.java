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
    /** 投げたあと、次に持ち上げられるまでの tick。 */
    public static final int THROW_COOLDOWN = 20;
    /** 持ち上げた対象を置いておく距離（ガントレットより遠い）。 */
    public static final double MANIPULATOR_HOLD_DISTANCE = 5;

    /** 左クリックを押しているプレイヤー（サーバー側）。 */
    private static final java.util.Set<java.util.UUID> LEFT_DOWN = new java.util.HashSet<>();
    /** 右クリックで力をためているプレイヤーと、ため始めた時刻。 */
    private static final java.util.Map<java.util.UUID, Long> CHARGING = new java.util.HashMap<>();
    /** 投げた直後の対象。ほかの処理（モブの移動の AI など）に初速を消されないよう、数tickのあいだ速さをかけ直す。 */
    private record Thrown(java.util.List<Integer> entityIds, Vec3 velocity, int[] ticksLeft) {}
    private static final java.util.Map<java.util.UUID, Thrown> THROWN = new java.util.HashMap<>();
    private static final int THROW_HOLD_TICKS = 3;
    /**
     * 浮遊で持ち上げている対象と、最後に持ち上げた時刻（プレイヤーごと）。
     * 1体を狙うときも、円錐（G キー）で何体も持ち上げるときも、実際に浮かせたものをここに記録して、投げるときに使う。
     */
    private static final java.util.Map<java.util.UUID, java.util.Map<Integer, Long>> LIFTED = new java.util.HashMap<>();
    /** 何tick前まで持ち上げていれば「持ち上げている」とみなすか。 */
    private static final int LIFT_GRACE = 3;

    @Override
    protected double holdDistance() {
        return MANIPULATOR_HOLD_DISTANCE;
    }

    /** いま浮遊で持ち上げている対象（生きているもの）。 */
    private static java.util.List<Entity> lifted(ServerPlayer player) {
        java.util.Map<Integer, Long> map = LIFTED.get(player.getUUID());
        java.util.List<Entity> out = new java.util.ArrayList<>();
        if (map == null) {
            return out;
        }
        long now = player.level().getGameTime();
        map.entrySet().removeIf(e -> now - e.getValue() > LIFT_GRACE);
        for (int entityId : map.keySet()) {
            Entity e = player.serverLevel().getEntity(entityId);
            if (e != null && e.isAlive()) {
                out.add(e);
            }
        }
        return out;
    }

    private static void dropAll(ServerPlayer player) {
        release(player);
        LIFTED.remove(player.getUUID());
    }

    /**
     * クライアントから、左クリック（right = false）・右クリックを押した・離したを受け取る。
     * 右クリックのためは「アイテムを使う」状態にしない（使っている間は歩きが遅くなるため）。
     */
    public static void setInput(ServerPlayer player, boolean right, boolean down) {
        java.util.UUID id = player.getUUID();
        ItemStack stack = player.getMainHandItem();
        if (!right) {
            if (down) {
                LEFT_DOWN.add(id);
            } else {
                LEFT_DOWN.remove(id);
                if (!CHARGING.containsKey(id)) {
                    dropAll(player);
                }
            }
            return;
        }
        if (!(stack.getItem() instanceof GravitonManipulatorItem item)) {
            CHARGING.remove(id);
            return;
        }
        if (down) {
            if (player.getCooldowns().isOnCooldown(item)) {
                return;
            }
            if (item.mode(stack) != Mode.LEVITATE) {
                player.displayClientMessage(Component.translatable("gauntlet.singulo.left_click"), true);
                return;
            }
            CHARGING.put(id, player.level().getGameTime());
        } else {
            Long start = CHARGING.remove(id);
            if (start != null) {
                item.fling(player, stack, (int) Math.min(MAX_CHARGE, player.level().getGameTime() - start));
            }
        }
    }

    /** 毎tick: 力をためている間は持ち上げたまま保ち、左クリックを押している間は今のモードで重力を操る。 */
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        java.util.UUID id = player.getUUID();
        Thrown thrown = THROWN.get(id);
        if (thrown != null) {
            if (thrown.ticksLeft()[0]-- > 0) {
                for (int entityId : thrown.entityIds()) {
                    Entity e = player.serverLevel().getEntity(entityId);
                    if (e != null && e.isAlive()) {
                        launch(e, thrown.velocity());
                    }
                }
            } else {
                THROWN.remove(id);
            }
        }
        if (!LEFT_DOWN.contains(id) && !CHARGING.containsKey(id)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GravitonManipulatorItem item)) {
            LEFT_DOWN.remove(id);
            CHARGING.remove(id);
            dropAll(player);
            return;
        }
        // 投げた直後は持ち上げられない
        if (player.getCooldowns().isOnCooldown(item)) {
            dropAll(player);
            return;
        }
        Long start = CHARGING.get(id);
        if (start != null) {
            int charged = (int) Math.min(MAX_CHARGE, player.level().getGameTime() - start);
            // ためている間も、狙っている対象（円錐なら範囲の対象）を持ち上げ続ける（左クリックと同じ）
            if (!item.operate(player, stack, player.tickCount)) {
                CHARGING.remove(id);
                return;
            }
            if (charged % 4 == 0) {
                for (Entity held : lifted(player)) {
                    player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                            held.getX(), held.getY() + held.getBbHeight() / 2, held.getZ(), 2 + charged / 8, 0.3, 0.3, 0.3, 0.02);
                }
            }
            if (charged % 5 == 0) {
                player.displayClientMessage(Component.translatable("gauntlet.singulo.charging", charged * 100 / MAX_CHARGE), true);
            }
            return;
        }
        if (!item.operate(player, stack, player.tickCount)) {
            LEFT_DOWN.remove(id);
            dropAll(player);
        }
    }

    /** 持ち上げていた対象を視線の向きへ勢いよく吹き飛ばす（ためた時間ほど強い）。そのあと少しの間は持ち上げられない。 */
    void fling(ServerPlayer player, ItemStack stack, int charged) {
        java.util.List<Entity> held = lifted(player);
        if (held.isEmpty()) {
            player.displayClientMessage(Component.translatable("gauntlet.singulo.nothing_held"), true);
            return;
        }
        // 電力が足りなければ、ある分だけの力で投げる（投げられないことはない）
        var energy = energy(stack);
        int cost = FE_PER_CHARGE_TICK * Math.max(1, charged);
        int paid = energy.extractEnergy(cost, false);
        int power = Math.max(0, Math.min(charged, paid / FE_PER_CHARGE_TICK));
        dropAll(player);
        // 左クリックを押したままでも、投げた対象をすぐ掴み直して引き戻さないよう、押し直すまで操作を止める
        LEFT_DOWN.remove(player.getUUID());
        player.getCooldowns().addCooldown(this, THROW_COOLDOWN);
        // プレイヤーが向いている方向へ初速を与える
        Vec3 velocity = flingVelocity(player.getLookAngle(), power);
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (Entity e : held) {
            launch(e, velocity);
            ids.add(e.getId());
        }
        THROWN.put(player.getUUID(), new Thrown(ids, velocity, new int[]{THROW_HOLD_TICKS}));
        player.displayClientMessage(Component.translatable("gauntlet.singulo.thrown", power * 100 / MAX_CHARGE), true);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), io.github.genichimaruo.singulo.registry.SinguloSounds.get("graviton_manipulator_throw"),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.85F + power / 160.0F);
    }

    /** 右クリック: スニーク中はモード切替。それ以外（ため）はキーの状態をクライアントから受け取って扱う。 */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player,
                                                                       net.minecraft.world.InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }
        return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /** 対象に速さを与える（モブなら移動の AI を止めて、すぐ打ち消されないようにする）。 */
    private static void launch(Entity target, Vec3 velocity) {
        if (target instanceof net.minecraft.world.entity.Mob mob) {
            mob.getNavigation().stop();
        }
        target.setOnGround(false);
        target.setDeltaMovement(velocity);
        target.hasImpulse = true;
        target.hurtMarked = true;
        target.fallDistance = 0;
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
        if (mode == Mode.LEVITATE) {
            LIFTED.computeIfAbsent(player.getUUID(), k -> new java.util.HashMap<>()).put(target.getId(), player.level().getGameTime());
        }
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
