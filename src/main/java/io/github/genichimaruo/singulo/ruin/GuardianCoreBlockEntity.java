package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 番人の封印核。研究棟と封鎖培養施設のボス部屋の床に埋まっていて、壊せない。
 * <ul>
 *   <li>挑戦者が部屋に入ると、近くの封鎖された保管庫の遺構に応じた番人を起こす（研究棟は残響の番人、培養施設は重力の澱、
 *       沈んだ炉心区画はエルゴ球の大蛇）</li>
 *   <li>番人が倒されると保管庫の封鎖を解く。中身が再生すると保管庫はまた封鎖され、次に入ったときに番人が戻ってくる</li>
 *   <li>部屋の仕掛け（残響投影器・封じ込め槽・製造ライン・工場のコンベア）は最初に覚えておき、番人を起こすたびに元の形へ直す</li>
 *   <li>攻略後の再戦: その遺構の覚醒核（覚醒の残響核・覚醒の重力核）を手に持って核に使うと、保管庫が開いていても
 *       覚醒した番人を起こす（{@link #awakenAwakened}）</li>
 * </ul>
 */
public class GuardianCoreBlockEntity extends BlockEntity {
    /** 保管庫を探す広さ。 */
    public static final int VAULT_SEARCH_RADIUS = 16;
    /** 挑戦者に気づく広さ（核からの水平距離）と高さ。 */
    public static final int TRIGGER_RADIUS = 9;
    public static final int TRIGGER_HEIGHT = 8;
    /** 残響投影器を探す広さ。 */
    public static final int PROP_RADIUS = 16;
    /** 封じ込め槽（核の真上の円筒）の半径と高さ。 */
    static final double TANK_RADIUS = 2.6;
    static final int TANK_HEIGHT = 9;
    public static final int MESSAGE_RADIUS = 40;

    @Nullable
    private UUID boss;
    private boolean scanned;
    /** 残響投影器の位置。 */
    private final List<BlockPos> projectors = new ArrayList<>();
    /** 封じ込め槽のブロック（位置と、元の形）。 */
    private final List<BlockPos> tankPos = new ArrayList<>();
    private final List<BlockState> tankState = new ArrayList<>();
    /** 製造ラインと工場のコンベア（位置と、元の形）。 */
    private final List<BlockPos> linePos = new ArrayList<>();
    private final List<BlockState> lineState = new ArrayList<>();
    private final List<BlockPos> conveyorPos = new ArrayList<>();
    private final List<BlockState> conveyorState = new ArrayList<>();
    /** 地下アンドロイド研究所のボス部屋の核なら、施設の範囲（番人を倒すと、その中の耐圧殻の封じが解ける）。 */
    @Nullable
    private net.minecraft.world.level.levelgen.structure.BoundingBox facility;
    /** 研究所の搬入出の立坑の範囲（ボスを倒すと、ここの溶接も解く）。 */
    @Nullable
    private net.minecraft.world.level.levelgen.structure.BoundingBox access;
    /** 出現の演出で、槽をどこまで壊したか（0: 無傷、1: ひび、2: 砕けた）。 */
    private int tankStage;

    public GuardianCoreBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.GUARDIAN_CORE.get(), pos, state);
    }

    /** 遺構ID → 起こす番人。番人のいない遺構は null。 */
    @Nullable
    public static EntityType<? extends RuinBoss> bossFor(String ruin) {
        return switch (ruin) {
            case "research_building" -> SinguloEntities.ECHO_SENTINEL.get();
            case "culture_facility" -> SinguloEntities.GRAVITY_REMNANT.get();
            case "sunken_reactor" -> SinguloEntities.ERGOSPHERE_LEVIATHAN.get();
            case "android_lab" -> SinguloEntities.FOUNDRY_OVERSEER.get();
            default -> null;
        };
    }

    /** 遺構ID → 覚醒した番人を呼ぶ覚醒核のアイテムID。番人のいない遺構は null。 */
    @Nullable
    public static String awakeningCoreId(String ruin) {
        return switch (ruin) {
            case "research_building" -> "awakening_echo_core";
            case "culture_facility" -> "awakening_gravity_core";
            case "sunken_reactor" -> "awakening_ergosphere_core";
            case "android_lab" -> "awakening_foundry_core";
            default -> null;
        };
    }

    /** 番人の種類 → その覚醒核。 */
    @Nullable
    public static net.minecraft.world.item.Item awakeningCoreFor(EntityType<?> type) {
        String id = type == SinguloEntities.ECHO_SENTINEL.get() ? "awakening_echo_core"
                : type == SinguloEntities.GRAVITY_REMNANT.get() ? "awakening_gravity_core"
                : type == SinguloEntities.ERGOSPHERE_LEVIATHAN.get() ? "awakening_ergosphere_core"
                : type == SinguloEntities.FOUNDRY_OVERSEER.get() ? "awakening_foundry_core" : null;
        return id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(id));
    }

    /** どれかの番人の覚醒核か。 */
    public static boolean isAwakeningCore(net.minecraft.world.item.ItemStack stack) {
        return stack.is(awakeningCoreFor(SinguloEntities.ECHO_SENTINEL.get())) || stack.is(awakeningCoreFor(SinguloEntities.GRAVITY_REMNANT.get()))
                || stack.is(awakeningCoreFor(SinguloEntities.ERGOSPHERE_LEVIATHAN.get()))
                || stack.is(awakeningCoreFor(SinguloEntities.FOUNDRY_OVERSEER.get()));
    }

    public List<BlockPos> projectors() {
        return projectors;
    }

    public List<BlockPos> tank() {
        return tankPos;
    }

    /** 研究所の範囲を覚える（生成のとき）。 */
    public void setFacility(net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        facility = box;
        setChanged();
    }

    @Nullable
    public net.minecraft.world.level.levelgen.structure.BoundingBox facility() {
        return facility;
    }

    /** 研究所の搬入出の立坑の範囲を覚える（生成のとき。なければ null）。 */
    public void setAccess(@Nullable net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        access = box;
        setChanged();
    }

    @Nullable
    public net.minecraft.world.level.levelgen.structure.BoundingBox access() {
        return access;
    }

    public List<BlockPos> lines() {
        return linePos;
    }

    public List<BlockPos> conveyors() {
        return conveyorPos;
    }

    /** その位置の製造ラインの、元の形（覚えていなければ null）。 */
    @Nullable
    public BlockState lineStateAt(BlockPos p) {
        int i = linePos.indexOf(p);
        return i < 0 ? null : lineState.get(i);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GuardianCoreBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (!scanned) {
            scan(level);
        }
        RuinBoss active = activeBoss(level);
        if (active != null) {
            if (active instanceof GravityRemnant && active.emergeTicks() > 0) {
                shatterTank(level, active.emergeProgress(0));
            }
            return;
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        AABB room = new AABB(worldPosition).inflate(TRIGGER_RADIUS, 0, TRIGGER_RADIUS).expandTowards(0, TRIGGER_HEIGHT, 0);
        if (!level.getEntitiesOfClass(ServerPlayer.class, room, RuinBoss::challenger).isEmpty()) {
            awaken(level);
        }
    }

    /** 部屋の仕掛けを覚える（最初の1回だけ）。 */
    public void scan(ServerLevel level) {
        scanned = true;
        projectors.clear();
        tankPos.clear();
        tankState.clear();
        linePos.clear();
        lineState.clear();
        conveyorPos.clear();
        conveyorState.clear();
        int r = PROP_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-r, -2, -r), worldPosition.offset(r, 10, r))) {
            BlockState s = level.getBlockState(p);
            if (s.is(SinguloBlocks.ECHO_PROJECTOR.get())) {
                projectors.add(p.immutable());
            } else if (s.is(SinguloBlocks.FACTORY_LINE.get())) {
                linePos.add(p.immutable());
                lineState.add(s);
            } else if (s.is(SinguloBlocks.FACTORY_CONVEYOR.get())) {
                conveyorPos.add(p.immutable());
                // 覚えるのは止まった形（戦いの途中で読み込み直しても、動いたままにしない）
                conveyorState.add(s.setValue(FactoryConveyorBlock.RUNNING, false).setValue(FactoryConveyorBlock.WARN, false));
            }
        }
        int t = (int) Math.ceil(TANK_RADIUS);
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-t, 1, -t), worldPosition.offset(t, TANK_HEIGHT, t))) {
            double dx = p.getX() - worldPosition.getX();
            double dz = p.getZ() - worldPosition.getZ();
            BlockState s = level.getBlockState(p);
            if (dx * dx + dz * dz <= TANK_RADIUS * TANK_RADIUS && glassLike(s)) {
                tankPos.add(p.immutable());
                tankState.add(s);
            }
        }
        setChanged();
    }

    private static boolean glassLike(BlockState s) {
        return s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || s.is(SinguloBlocks.INTACT_RUIN_GLASS.get())
                || s.is(SinguloBlocks.RUIN_GLASS.get());
    }

    /** いちばん近い、番人に守られた保管庫。 */
    @Nullable
    public RuinCacheBlockEntity vault(ServerLevel level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = VAULT_SEARCH_RADIUS;
        RuinCacheBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -4; dy <= 8; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < bestDist && level.getBlockEntity(p) instanceof RuinCacheBlockEntity cache && cache.guarded()
                            && bossFor(cache.ruin()) != null) {
                        best = cache;
                        bestDist = d;
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    public RuinBoss activeBoss(ServerLevel level) {
        if (boss == null) {
            return null;
        }
        Entity e = level.getEntity(boss);
        return e instanceof RuinBoss b && b.isAlive() ? b : null;
    }

    /** 番人を起こす（保管庫が封鎖されていて、まだ起きていないときだけ）。起こしたらそれを返す。 */
    @Nullable
    public RuinBoss awaken(ServerLevel level) {
        if (activeBoss(level) != null) {
            return null;
        }
        RuinCacheBlockEntity vault = vault(level);
        if (vault == null) {
            return null;
        }
        vault.refillIfDue(level, level.getGameTime());
        if (!vault.isSealed()) {
            return null;
        }
        RuinBoss b = spawn(level, vault, false);
        if (b != null) {
            broadcast(level, "gui.singulo.guardian.awakened." + typeName(b.getType()));
        }
        return b;
    }

    /**
     * 覚醒核を使ったとき。覚醒した番人を起こせたらそれを返す（呼んだ側が覚醒核を1個減らす）。
     * 保管庫が封鎖されていてもいなくても呼べる（勝てば封鎖も解ける）。core は使われた覚醒核で、この遺構のものでなければ断る。
     */
    @Nullable
    public RuinBoss awakenAwakened(ServerLevel level, @Nullable ServerPlayer player, net.minecraft.world.item.ItemStack core) {
        if (activeBoss(level) != null) {
            tell(player, "gui.singulo.guardian.active");
            return null;
        }
        RuinCacheBlockEntity vault = vault(level);
        if (vault == null) {
            tell(player, "gui.singulo.guardian.no_vault");
            return null;
        }
        String id = awakeningCoreId(vault.ruin());
        if (id == null || !core.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(id)))) {
            tell(player, "gui.singulo.guardian.wrong_core");
            return null;
        }
        // 封印コンソールと同じく、中身の再生期限が過ぎていればここで入れ直して封鎖を確定させる（勝てばその封鎖が解ける）
        vault.refillIfDue(level, level.getGameTime());
        RuinBoss b = spawn(level, vault, true);
        if (b != null) {
            broadcast(level, "gui.singulo.guardian.awakened_rematch." + typeName(b.getType()));
        }
        return b;
    }

    @Nullable
    private RuinBoss spawn(ServerLevel level, RuinCacheBlockEntity vault, boolean awakened) {
        EntityType<? extends RuinBoss> type = bossFor(vault.ruin());
        RuinBoss b = type == null ? null : type.create(level);
        if (b == null) {
            return null;
        }
        if (!scanned) {
            scan(level);
        }
        restoreProps(level);
        tankStage = 0;
        b.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0 + b.spawnHeight(), worldPosition.getZ() + 0.5, 0, 0);
        b.awaken(worldPosition);
        if (awakened) {
            b.makeAwakened();
        }
        if (b instanceof EchoSentinel s) {
            s.setProjectors(projectors);
        }
        if (b instanceof FoundryOverseer f) {
            f.setLines(linePos);
            f.setConveyors(conveyorPos);
        }
        b.startEmerging();
        level.addFreshEntity(b);
        boss = b.getUUID();
        setChanged();
        level.playSound(null, worldPosition, io.github.genichimaruo.singulo.registry.SinguloSounds.get("guardian_core.awaken"), SoundSource.HOSTILE, 2.0F,
                awakened ? 0.8F : 1.0F);
        return b;
    }

    /** 部屋の仕掛けを元の形へ直す。 */
    public void restoreProps(ServerLevel level) {
        BlockState projector = SinguloBlocks.ECHO_PROJECTOR.get().defaultBlockState();
        for (BlockPos p : projectors) {
            if (!level.getBlockState(p).is(projector.getBlock())) {
                level.setBlock(p, projector, Block.UPDATE_ALL);
            }
        }
        for (int i = 0; i < tankPos.size(); i++) {
            if (level.getBlockState(tankPos.get(i)) != tankState.get(i)) {
                level.setBlock(tankPos.get(i), tankState.get(i), Block.UPDATE_ALL);
            }
        }
        restore(level, linePos, lineState);
        restore(level, conveyorPos, conveyorState);
    }

    private static void restore(ServerLevel level, List<BlockPos> pos, List<BlockState> states) {
        for (int i = 0; i < pos.size(); i++) {
            if (level.getBlockState(pos.get(i)) != states.get(i)) {
                level.setBlock(pos.get(i), states.get(i), Block.UPDATE_ALL);
            }
        }
    }

    /** 重力の澱の出現: 35% で槽のガラスにひびが走り、75% で砕け散る。 */
    private void shatterTank(ServerLevel level, float progress) {
        if (tankStage == 0 && progress >= 0.35F) {
            tankStage = 1;
            BlockState cracked = SinguloBlocks.RUIN_GLASS.get().defaultBlockState();
            for (BlockPos p : tankPos) {
                if (!level.getBlockState(p).isAir()) {
                    level.setBlock(p, cracked, Block.UPDATE_ALL);
                }
            }
            level.playSound(null, worldPosition.above(4), io.github.genichimaruo.singulo.registry.SinguloSounds.get("containment_tank.crack"), SoundSource.HOSTILE, 2.0F, 1.0F);
        } else if (tankStage == 1 && progress >= 0.75F) {
            tankStage = 2;
            for (BlockPos p : tankPos) {
                if (!level.getBlockState(p).isAir()) {
                    level.destroyBlock(p, false);
                }
            }
            level.playSound(null, worldPosition.above(4), io.github.genichimaruo.singulo.registry.SinguloSounds.get("containment_tank.shatter"), SoundSource.HOSTILE, 3.0F, 1.0F);
        }
    }

    void onBossDefeated(ServerLevel level) {
        EntityType<?> type = bossType(level);
        boss = null;
        setChanged();
        RuinCacheBlockEntity vault = vault(level);
        if (vault != null) {
            vault.setSealed(false);
            level.playSound(null, vault.getBlockPos(), io.github.genichimaruo.singulo.registry.SinguloSounds.get("vault.unseal"), SoundSource.BLOCKS, 1.5F, 1.0F);
        }
        if (facility != null) {
            // 研究所: 耐圧殻の封じが解ける（黒曜石より硬いが、掘れるようになる）
            io.github.genichimaruo.singulo.ruin.lab.LabPower.unseal(level, facility);
            if (access != null) {
                io.github.genichimaruo.singulo.ruin.lab.LabPower.unseal(level, access);   // 搬入出の立坑の溶接された蓋
            }
            broadcast(level, "lab.singulo.hull_unsealed");
        }
        broadcast(level, "gui.singulo.guardian.defeated." + typeName(type));
    }

    /** 挑戦者がいなくなって番人が戻ったとき。覚醒戦なら、核に手が届くように部屋の仕掛け（割れた槽）は直さない。 */
    void onBossReset(ServerLevel level, boolean awakened) {
        EntityType<?> type = bossType(level);
        boss = null;
        setChanged();
        if (!awakened) {
            restoreProps(level);
        }
        broadcast(level, "gui.singulo.guardian.reset." + typeName(type));
    }

    @Nullable
    private EntityType<?> bossType(ServerLevel level) {
        RuinBoss b = activeBoss(level);
        if (b != null) {
            return b.getType();
        }
        RuinCacheBlockEntity vault = vault(level);
        return vault == null ? null : bossFor(vault.ruin());
    }

    private static String typeName(@Nullable EntityType<?> type) {
        if (type == null) {
            return "echo_sentinel";
        }
        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key.getPath();
    }

    private static void tell(@Nullable ServerPlayer player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    private void broadcast(ServerLevel level, String key) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(MESSAGE_RADIUS))) {
            p.displayClientMessage(Component.translatable(key), false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boss != null) {
            tag.putUUID("boss", boss);
        }
        tag.putBoolean("scanned", scanned);
        ListTag props = new ListTag();
        for (BlockPos p : projectors) {
            props.add(NbtUtils.writeBlockPos(p));
        }
        tag.put("projectors", props);
        ListTag tank = new ListTag();
        for (int i = 0; i < tankPos.size(); i++) {
            CompoundTag t = new CompoundTag();
            t.put("pos", NbtUtils.writeBlockPos(tankPos.get(i)));
            t.put("state", NbtUtils.writeBlockState(tankState.get(i)));
            tank.add(t);
        }
        tag.put("tank", tank);
        tag.put("lines", writeStates(linePos, lineState));
        if (facility != null) {
            tag.putIntArray("facility", io.github.genichimaruo.singulo.ruin.lab.LabPower.write(facility));
        }
        if (access != null) {
            tag.putIntArray("access", io.github.genichimaruo.singulo.ruin.lab.LabPower.write(access));
        }
        tag.put("conveyors", writeStates(conveyorPos, conveyorState));
    }

    private static ListTag writeStates(List<BlockPos> pos, List<BlockState> states) {
        ListTag out = new ListTag();
        for (int i = 0; i < pos.size(); i++) {
            CompoundTag t = new CompoundTag();
            t.put("pos", NbtUtils.writeBlockPos(pos.get(i)));
            t.put("state", NbtUtils.writeBlockState(states.get(i)));
            out.add(t);
        }
        return out;
    }

    private static void readStates(ListTag in, HolderLookup.Provider registries, List<BlockPos> pos, List<BlockState> states) {
        pos.clear();
        states.clear();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag t : in) {
            CompoundTag c = (CompoundTag) t;
            BlockState s = NbtUtils.readBlockState(blocks, c.getCompound("state"));
            if (!s.isAir()) {
                pos.add(NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO));
                states.add(s);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boss = tag.hasUUID("boss") ? tag.getUUID("boss") : null;
        scanned = tag.getBoolean("scanned");
        projectors.clear();
        for (Tag t : tag.getList("projectors", Tag.TAG_INT_ARRAY)) {
            projectors.add(NbtUtils.readBlockPos(wrap(t), "p").orElse(BlockPos.ZERO));
        }
        tankPos.clear();
        tankState.clear();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag t : tag.getList("tank", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            BlockState s = NbtUtils.readBlockState(blocks, c.getCompound("state"));
            if (s.isAir()) {
                s = Blocks.GLASS.defaultBlockState();
            }
            tankPos.add(NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO));
            tankState.add(s);
        }
        readStates(tag.getList("lines", Tag.TAG_COMPOUND), registries, linePos, lineState);
        int[] f = tag.getIntArray("facility");
        facility = f.length == 6 ? io.github.genichimaruo.singulo.ruin.lab.LabPower.read(f) : null;
        int[] a = tag.getIntArray("access");
        access = a.length == 6 ? io.github.genichimaruo.singulo.ruin.lab.LabPower.read(a) : null;
        readStates(tag.getList("conveyors", Tag.TAG_COMPOUND), registries, conveyorPos, conveyorState);
    }

    private static CompoundTag wrap(Tag t) {
        CompoundTag c = new CompoundTag();
        c.put("p", t);
        return c;
    }
}
