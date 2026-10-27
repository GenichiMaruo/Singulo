package io.github.genichimaruo.singulo.android;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 拠点の暮らし: 手が空いている護衛（戦い・作業・充電・修理・巡回をしていない）が、ときどきすること。持ち主がそばに来ても続ける。
 * 迎撃モードの護衛はステーションの上（2体目からはすぐ横）で待つことに専念し、暮らしはしない（夜はその場で立ったまま昼寝する）。
 * <ul>
 * <li>座る: ステーションのそばの階段・ハーフブロック・ほかの mod の椅子に座る（なければ、地面に座る）。夜は座って昼寝（目を閉じて Z）。</li>
 * <li>小さな手入れ: 植木鉢・作物に手をかざす（きらきら。成長は早めない）、床を掃く仕草、しばらく落ちたままの物を拾って、そばのチェストへしまう。</li>
 * <li>おしゃべり: 手の空いた仲間と向き合って、音符・うなずき・身ぶり。</li>
 * <li>天気と時間: 雨なら屋根の下へ（傘を持っていれば、そのまま）、夕焼けを眺める、夜空を見上げる。</li>
 * </ul>
 */
final class AndroidLife extends Goal {
    enum Kind { SIT, NAP, TEND, SWEEP, TIDY, CHAT, SHELTER, SUNSET, STARGAZE }

    /** 暮らしを探す範囲（ステーションから）と、次にするまでの間（tick）、たどり着けずにあきらめるまで。 */
    private static final int RANGE = 10, COOLDOWN_MIN = 20 * 15, COOLDOWN_MAX = 20 * 45, GIVE_UP = 20 * 15;
    /** 拾う物: しばらく（10秒）落ちたままの物だけ（投げた物をすぐ拾わない）。 */
    private static final int ITEM_AGE = 20 * 10;
    /** ほかの mod の椅子の名前に入る単語。 */
    private static final java.util.Set<String> SEAT_WORDS = java.util.Set.of("chair", "seat", "bench", "stool", "sofa", "couch", "armchair");

    private final RuinAndroid android;
    @Nullable
    private Kind kind;
    /** 向かう所（座る所・植木鉢・物・チェスト・見晴らしのよい所…）と、着いたか、残りの時間、向かってからの時間。 */
    @Nullable
    private BlockPos spot;
    @Nullable
    private Vec3 seat;
    private float seatYaw;
    private boolean arrived;
    private int left, travel;
    private int nextTick;
    /** 片付け: 拾った物と、しまうチェスト。 */
    private ItemStack held = ItemStack.EMPTY;
    @Nullable
    private ItemEntity item;
    @Nullable
    private BlockPos chest;
    /** おしゃべりの相手（相手からの誘いも含む）と、待ち合わせの場所。 */
    @Nullable
    private RuinAndroid partner;
    @Nullable
    private Vec3 meet;
    private boolean invited;

    AndroidLife(RuinAndroid android) {
        this.android = android;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        nextTick = 20 * 10 + android.getRandom().nextInt(20 * 20);
    }

    /** 次の暮らしを、すぐに始めてよいことにする（テスト用）。 */
    void hurry() {
        nextTick = 0;
    }

    /** 今、何かしているか（クライアントでは使わない）。 */
    boolean busy() {
        return kind != null;
    }

    /** 今の暮らしの様子（テストの説明用）。 */
    String describe() {
        return kind + " left=" + left + " travel=" + travel + " next=" + (nextTick - android.tickCount) + " free=" + free();
    }

    /** 手が空いているか（暮らしを始めてよい・続けてよい）。 */
    private boolean free() {
        if (!android.isGuard() || !android.printed() || !android.active() || android.getTarget() != null || android.isWorking()
                || android.home() == null || android.mode() == RuinAndroid.MODE_FOLLOW || android.mode() == RuinAndroid.MODE_GUARD) {
            return false;
        }
        int s = android.state();
        if (s != RuinAndroid.STATE_IDLE && !RuinAndroid.lifeState(s)) {
            return false;                              // 充電・修理に戻る・技の最中
        }
        if (android.getHealth() < android.getMaxHealth() && android.tickCount - android.getLastHurtByMobTimestamp() < 200) {
            return false;
        }
        if (android.level() instanceof ServerLevel level && android.mode() == RuinAndroid.MODE_PATROL && android.patrol.active(level)) {
            return false;                              // 巡回している
        }
        return true;
    }

    @Override
    public boolean canUse() {
        if (!(android.level() instanceof ServerLevel level) || !free()) {
            return false;
        }
        if (invited) {
            return true;
        }
        if (android.tickCount < nextTick) {
            return false;
        }
        nextTick = android.tickCount + COOLDOWN_MIN + android.getRandom().nextInt(COOLDOWN_MAX - COOLDOWN_MIN);
        return choose(level);
    }

    @Override
    public boolean canContinueToUse() {
        return kind != null && left > 0 && free();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        invited = false;
        arrived = false;
        travel = 0;
    }

    @Override
    public void stop() {
        if (RuinAndroid.lifeState(android.state())) {
            android.setState(RuinAndroid.STATE_IDLE);
        }
        android.setNapping(false);
        dropHeld();
        if (seat != null) {
            // 座っていた所から、前へ立ち上がる
            Vec3 front = seat.add(Vec3.directionFromRotation(0, seatYaw).scale(0.7));
            android.setPos(front.x, Math.ceil(seat.y), front.z);
        }
        kind = null;
        spot = null;
        seat = null;
        item = null;
        chest = null;
        meet = null;
        if (partner != null && partner.life != null && partner.life.partner == android) {
            partner.life.partner = null;
            partner.life.left = Math.min(partner.life.left, 1);
        }
        partner = null;
        invited = false;
    }

    // ------------------------------------------------------------------ 何をするか

    private BlockPos anchor() {
        return android.home() == null ? android.blockPosition() : android.home();
    }

    /** 場所・時間・天気に合う暮らしを1つ選んで、始める準備をする。できることがなければ false。 */
    private boolean choose(ServerLevel level) {
        long time = level.getDayTime() % 24000;
        boolean raining = level.isRainingAt(android.blockPosition().above());
        boolean umbrella = android.decor(AndroidAccessoryItem.HAND).getItem() instanceof AndroidAccessoryItem acc && "umbrella".equals(acc.style());
        if (raining && !umbrella) {
            BlockPos roof = findSpot(level, p -> !level.canSeeSky(p));
            if (roof != null) {
                return begin(Kind.SHELTER, roof, 20 * 60);
            }
        }
        if (prepare(level, Kind.TIDY)) {
            return true;                               // 落ちたままの物があれば、先に片付ける
        }
        List<Kind> options = new ArrayList<>();
        boolean night = level.isNight();
        if (night) {
            options.add(Kind.NAP);
            options.add(Kind.NAP);
            if (!level.isRaining()) {
                options.add(Kind.STARGAZE);
            }
        } else if (time >= 11800 && time < 13200 && !level.isRaining()) {
            options.add(Kind.SUNSET);
            options.add(Kind.SUNSET);
            options.add(Kind.SIT);
        } else {
            options.add(Kind.SIT);
            options.add(Kind.SIT);
            options.add(Kind.TEND);
            options.add(Kind.TEND);
            options.add(Kind.SWEEP);
            options.add(Kind.CHAT);
            options.add(Kind.CHAT);
        }
        java.util.Collections.shuffle(options, new java.util.Random(android.getRandom().nextLong()));
        for (Kind k : options) {
            if (prepare(level, k)) {
                return true;
            }
        }
        return false;
    }

    private boolean begin(Kind k, @Nullable BlockPos at, int ticks) {
        kind = k;
        spot = at;
        left = ticks;
        return true;
    }

    /** 暮らし k を始められるなら、行く所を決める。 */
    private boolean prepare(ServerLevel level, Kind k) {
        var rnd = android.getRandom();
        return switch (k) {
            case SIT, NAP -> {
                BlockPos s = findSeat(level);
                if (s == null) {
                    s = findSpot(level, p -> true);         // 座る物がなければ、地面に座る
                }
                yield s != null && begin(k, s, k == Kind.NAP ? 20 * (60 + rnd.nextInt(60)) : 20 * (20 + rnd.nextInt(20)));
            }
            case TEND -> {
                BlockPos plant = findBlock(level, s -> s.getBlock() instanceof CropBlock
                        || s.getBlock() instanceof FlowerPotBlock pot && pot.getPotted() != net.minecraft.world.level.block.Blocks.AIR);
                yield plant != null && begin(k, plant, 20 * (5 + rnd.nextInt(3)));
            }
            case SWEEP -> {
                BlockPos s = findSpot(level, p -> true);
                yield s != null && begin(k, s, 20 * (4 + rnd.nextInt(3)));
            }
            case TIDY -> {
                chest = findBlock(level, s -> s.getBlock() instanceof ChestBlock || s.getBlock() instanceof BarrelBlock);
                if (chest == null) {
                    yield false;
                }
                List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(anchor()).inflate(RANGE),
                        e -> e.isAlive() && e.getAge() > ITEM_AGE && !e.hasPickUpDelay());
                if (items.isEmpty()) {
                    yield false;
                }
                item = items.get(rnd.nextInt(items.size()));
                yield begin(k, item.blockPosition(), 20 * 40);
            }
            case CHAT -> {
                RuinAndroid peer = findPeer(level);
                if (peer == null) {
                    yield false;
                }
                Vec3 mid = android.position().add(peer.position()).scale(0.5);
                partner = peer;
                meet = mid;
                peer.life.invite(android, mid, 20 * (10 + rnd.nextInt(6)));
                yield begin(k, BlockPos.containing(mid), peer.life.left);
            }
            case SUNSET, STARGAZE -> {
                BlockPos s = findSpot(level, level::canSeeSky);
                yield s != null && begin(k, s, 20 * (12 + rnd.nextInt(10)));
            }
            default -> false;
        };
    }

    /** おしゃべりに誘われた。 */
    void invite(RuinAndroid from, Vec3 mid, int ticks) {
        partner = from;
        meet = mid;
        invited = true;
        kind = Kind.CHAT;
        spot = BlockPos.containing(mid);
        left = ticks;
        nextTick = android.tickCount + COOLDOWN_MIN;
    }

    /** おしゃべりの相手: 同じ持ち主の、手が空いていて何もしていない護衛。 */
    @Nullable
    private RuinAndroid findPeer(ServerLevel level) {
        for (RuinAndroid a : level.getEntitiesOfClass(RuinAndroid.class, new AABB(anchor()).inflate(RANGE),
                a -> a != android && a.isAlive() && a.isGuard() && java.util.Objects.equals(a.owner(), android.owner()) && a.life != null
                        && !a.napping() && !a.life.busy() && a.life.free())) {
            return a;
        }
        return null;
    }

    // ------------------------------------------------------------------ 場所を探す

    /** インベントリ外で片付け中の物も、死亡・破棄時には失わない。読み込み解除時はNBTで保持する。 */
    void dropHeld() {
        if (!held.isEmpty()) {
            ItemStack drop = held;
            held = ItemStack.EMPTY;
            android.spawnAtLocation(drop);
        }
    }

    private Vec3 seatPosition(ServerLevel level, BlockPos pos, BlockState state) {
        var shape = state.getCollisionShape(level, pos);
        double top = shape.isEmpty() ? 0.5 : shape.max(Direction.Axis.Y);
        if (top <= 0 || top > 0.9) {
            top = 0.5;
        }
        Direction front = seatFront(state);
        double shift = state.getBlock() instanceof StairBlock ? 0.3 : 0;
        return new Vec3(pos.getX() + 0.5 + front.getStepX() * shift, pos.getY() + top, pos.getZ() + 0.5 + front.getStepZ() * shift);
    }

    private Direction seatFront(BlockState state) {
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.fromYRot(android.getYRot());
        return state.getBlock() instanceof StairBlock ? facing.getOpposite() : facing;
    }

    private boolean seatClear(ServerLevel level, Vec3 position) {
        return level.noCollision(android, android.getBoundingBox().move(position.subtract(android.position())));
    }

    /** ステーションのそばの、座れる物（下半分の階段・ハーフブロック、名前に chair・seat・bench・stool が入るブロック）。仲間が使っている物は除く。 */
    @Nullable
    private BlockPos findSeat(ServerLevel level) {
        java.util.Set<BlockPos> taken = new java.util.HashSet<>();
        for (RuinAndroid a : level.getEntitiesOfClass(RuinAndroid.class, new AABB(anchor()).inflate(RANGE + 4), a -> a != android && a.life != null)) {
            if ((a.life.kind == Kind.SIT || a.life.kind == Kind.NAP) && a.life.spot != null) {
                taken.add(a.life.spot);
            }
        }
        return findBlock(level, (p, s) -> {
            if (taken.contains(p)) {
                return false;
            }
            if (s.getBlock() instanceof StairBlock) {
                return s.getValue(StairBlock.HALF) == Half.BOTTOM && seatClear(level, seatPosition(level, p, s));
            }
            if (s.getBlock() instanceof SlabBlock) {
                return s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM && seatClear(level, seatPosition(level, p, s));
            }
            // ほかの mod の椅子: 名前の単語で見る（android_workbench の "bench" などは椅子ではない）。座面が高すぎる物は除く
            boolean named = false;
            for (String w : BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().split("_")) {
                named |= SEAT_WORDS.contains(w);
            }
            if (!named) {
                return false;
            }
            var shape = s.getCollisionShape(level, p);
            return (shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.9) && seatClear(level, seatPosition(level, p, s));
        });
    }

    /** ステーションのまわりで、条件に合う一番近いブロック（座る・手入れ・しまう所。空間の条件は呼び出し側で確認）。 */
    @Nullable
    private BlockPos findBlock(ServerLevel level, java.util.function.Predicate<BlockState> ok) {
        return findBlock(level, (p, s) -> ok.test(s));
    }

    @Nullable
    private BlockPos findBlock(ServerLevel level, java.util.function.BiPredicate<BlockPos, BlockState> ok) {
        BlockPos center = anchor(), best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-RANGE, -3, -RANGE), center.offset(RANGE, 3, RANGE))) {
            BlockState s = level.getBlockState(p);
            if (!ok.test(p, s)) {
                continue;
            }
            double d = p.distSqr(android.blockPosition());
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /** ステーションのまわりの、立てる所（下が固く、2マス空いている）で、条件に合うものをいくつか試して1つ。 */
    @Nullable
    private BlockPos findSpot(ServerLevel level, java.util.function.Predicate<BlockPos> ok) {
        var rnd = android.getRandom();
        BlockPos center = anchor();
        for (int i = 0; i < 24; i++) {
            BlockPos p = center.offset(rnd.nextInt(RANGE * 2 + 1) - RANGE, rnd.nextInt(5) - 2, rnd.nextInt(RANGE * 2 + 1) - RANGE);
            if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP) && level.getBlockState(p).isAir()
                    && level.getBlockState(p.above()).isAir() && ok.test(p) && !p.equals(center.above())) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 毎 tick

    @Override
    public void tick() {
        if (!(android.level() instanceof ServerLevel level) || kind == null) {
            return;
        }
        left--;
        switch (kind) {
            case SIT, NAP -> sit(level);
            case TEND -> tend(level);
            case SWEEP -> sweep(level);
            case TIDY -> tidy(level);
            case CHAT -> chat(level);
            case SHELTER -> {
                if (reach(spot) && !level.isRainingAt(android.blockPosition().above())) {
                    left = Math.min(left, 20 * 5);         // 雨がやんだ: 少ししたら戻る
                }
            }
            case SUNSET -> {
                if (reach(spot)) {
                    android.getLookControl().setLookAt(android.getX() - 20, android.getEyeY() + 2, android.getZ());   // 西の空
                }
            }
            case STARGAZE -> {
                if (reach(spot)) {
                    android.getLookControl().setLookAt(android.getX() + 1, android.getEyeY() + 20, android.getZ() + 1, 10, 80);   // 真上
                }
            }
        }
    }

    /** spot（の上、立てる所）へ歩く。着いたら true（たどり着けなければ、暮らしをやめる）。 */
    private boolean reach(@Nullable BlockPos target) {
        if (target == null) {
            left = 0;
            return false;
        }
        Vec3 goal = Vec3.atBottomCenterOf(target);
        if (android.position().subtract(goal).horizontalDistance() < 1.3 && Math.abs(android.getY() - goal.y) < 1.5) {
            if (!arrived) {
                arrived = true;
                android.getNavigation().stop();
            }
            return true;
        }
        if (++travel > GIVE_UP) {
            left = 0;
            return false;
        }
        if (android.getNavigation().isDone() || travel % 20 == 1) {
            android.getNavigation().moveTo(goal.x, goal.y, goal.z, 0.7);
        }
        return false;
    }

    /** 座る（座る物の上に。なければ地面に座る）。昼寝なら目を閉じて、ときどき Z。 */
    private void sit(ServerLevel level) {
        if (seat == null) {
            BlockState s = level.getBlockState(spot);
            boolean chair = !s.isAir() && !s.canBeReplaced();
            BlockPos stand = chair ? spot : spot;
            if (!near(stand, chair ? 2.0 : 1.6)) {
                return;
            }
            if (chair) {
                Vec3 candidate = seatPosition(level, spot, s);
                if (!seatClear(level, candidate)) {
                    left = 0; // 選んだ後に頭上などが塞がれても、ブロック内へ移動しない
                    return;
                }
                seatYaw = seatFront(s).toYRot();
                seat = candidate;
                android.setState(RuinAndroid.STATE_SIT);
            } else {
                seat = android.position();
                seatYaw = android.getYRot();
                android.setState(RuinAndroid.STATE_SIT_GROUND);
            }
            android.getNavigation().stop();
            android.setNapping(kind == Kind.NAP);
        }
        // 座っている: その場に留まり、座った向きのまま
        android.setPos(seat.x, seat.y, seat.z);
        android.setDeltaMovement(Vec3.ZERO);
        android.setYRot(seatYaw);
        android.setYBodyRot(seatYaw);
        if (kind == Kind.NAP) {
            android.setYHeadRot(seatYaw);
        } else if (left % 80 == 0) {
            double a = android.getRandom().nextDouble() * Math.PI - Math.PI / 2;
            Vec3 look = android.getEyePosition().add(Vec3.directionFromRotation(0, seatYaw + (float) Math.toDegrees(a) * 0.5F).scale(5));
            android.getLookControl().setLookAt(look.x, look.y, look.z);
        }
    }

    /** spot の、横の立てる所に近いか（ブロックそのものには立てないので、そばまで）。 */
    private boolean near(BlockPos target, double dist) {
        Vec3 c = Vec3.atBottomCenterOf(target);
        if (android.position().subtract(c).horizontalDistance() < dist && Math.abs(android.getY() - c.y) < 1.6) {
            return true;
        }
        if (++travel > GIVE_UP) {
            left = 0;
            return false;
        }
        if (android.getNavigation().isDone() || travel % 20 == 1) {
            android.getNavigation().moveTo(c.x, c.y, c.z, 0.7);
        }
        return false;
    }

    /** 植木鉢・作物に手をかざす（きらきら）。 */
    private void tend(ServerLevel level) {
        if (!near(spot, 1.9)) {
            return;
        }
        android.getNavigation().stop();
        Vec3 c = Vec3.atCenterOf(spot);
        android.getLookControl().setLookAt(c.x, c.y, c.z, 30, 60);
        android.setYRot((float) (Mth.atan2(c.z - android.getZ(), c.x - android.getX()) * Mth.RAD_TO_DEG) - 90);
        android.setYBodyRot(android.getYRot());
        if (android.state() != RuinAndroid.STATE_TEND) {
            android.setState(RuinAndroid.STATE_TEND);
        }
        if (left % 10 == 0) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 0.3, c.z, 2, 0.25, 0.2, 0.25, 0);
            if (left % 30 == 0) {
                level.playSound(null, c.x, c.y, c.z, io.github.genichimaruo.singulo.registry.SinguloSounds.get("android.tend"), net.minecraft.sounds.SoundSource.NEUTRAL, 0.4F,
                        0.9F + android.getRandom().nextFloat() * 0.3F);
            }
        }
    }

    /** 床を掃く仕草（足元にほこり）。 */
    private void sweep(ServerLevel level) {
        if (!reach(spot)) {
            return;
        }
        if (android.state() != RuinAndroid.STATE_SWEEP) {
            android.setState(RuinAndroid.STATE_SWEEP);
        }
        if (left % 8 == 0) {
            Vec3 front = android.position().add(Vec3.directionFromRotation(0, android.getYRot()).scale(0.7));
            level.sendParticles(ParticleTypes.POOF, front.x, front.y + 0.1, front.z, 1, 0.2, 0.02, 0.2, 0.005);
            level.playSound(null, front.x, front.y, front.z, io.github.genichimaruo.singulo.registry.SinguloSounds.get("android.sweep"), net.minecraft.sounds.SoundSource.NEUTRAL, 0.35F,
                    0.9F + android.getRandom().nextFloat() * 0.25F);
        }
        if (left % 40 == 0) {
            android.setYRot(android.getYRot() + 25);       // 少しずつ向きを変えながら掃く
        }
    }

    /** 落ちている物を拾って、そばのチェストへしまう。 */
    private void tidy(ServerLevel level) {
        if (held.isEmpty()) {
            if (item == null || !item.isAlive()) {
                left = 0;
                return;
            }
            if (!near(item.blockPosition(), 2.0)) {             // 道のりの終わりは、ぴったり上でなく少し手前で止まる
                return;
            }
            held = item.getItem().copy();
            item.discard();
            item = null;
            travel = 0;
            android.gesture(RuinAndroid.GESTURE_NOD, null);
            android.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.3F);
            return;
        }
        if (chest == null || !near(chest, 1.9)) {
            return;
        }
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, chest, null);
        ItemStack rest = handler == null ? held : net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, held, false);
        held = ItemStack.EMPTY;
        if (!rest.isEmpty()) {
            android.spawnAtLocation(rest);                  // 入りきらない: 足元に置く
        }
        android.playSound(SoundEvents.CHEST_CLOSE, 0.4F, 1.4F);
        left = 0;
    }

    /** 仲間と向き合って話す（音符・うなずき・身ぶり）。 */
    private void chat(ServerLevel level) {
        if (partner == null || !partner.isAlive() || meet == null) {
            left = 0;
            return;
        }
        // 待ち合わせの所から、相手と1.5ブロックほど離れて立つ
        Vec3 away = android.position().subtract(partner.position()).multiply(1, 0, 1);
        Vec3 standAt = away.lengthSqr() < 1e-4 ? meet : meet.add(away.normalize().scale(0.8));
        if (android.position().subtract(standAt).horizontalDistance() > 1.0) {
            if (++travel > GIVE_UP) {
                left = 0;
                return;
            }
            if (android.getNavigation().isDone() || travel % 20 == 1) {
                android.getNavigation().moveTo(standAt.x, standAt.y, standAt.z, 0.7);
            }
            return;
        }
        android.getNavigation().stop();
        android.getLookControl().setLookAt(partner, 30, 30);
        int beat = (left + (partner.getId() & 15)) % 60;
        if (beat == 0) {
            android.gesture(android.getRandom().nextInt(3) == 0 ? RuinAndroid.GESTURE_NOD : RuinAndroid.GESTURE_TALK, partner);
            level.sendParticles(ParticleTypes.NOTE, android.getX(), android.getEyeY() + 0.6, android.getZ(), 1, 0, 0, 0,
                    android.getRandom().nextInt(24) / 24.0);
            android.playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("android.chatter"), 0.5F, 0.9F + android.getRandom().nextFloat() * 0.3F);
        }
    }

    // ------------------------------------------------------------------ 保存（拾った物だけ。失くさないように）

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (!held.isEmpty()) {
            t.put("held", held.save(android.registryAccess()));
        }
        return t;
    }

    void load(CompoundTag t) {
        held = t.contains("held") ? ItemStack.parseOptional(android.registryAccess(), t.getCompound("held")) : ItemStack.EMPTY;
        if (!held.isEmpty()) {
            android.spawnAtLocation(held);                  // 読み込み直したら、足元に置き直す
            held = ItemStack.EMPTY;
        }
    }
}
