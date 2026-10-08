package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.multiblock.Structures;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

/**
 * ティプラー・シリンダー（ティア5、3×3×7 のマルチブロック。コアは底の中央）。中央で回る円柱が周りの時間を速め、
 * 半径 tiplerRadius（既定8）の中の装置・作物・かまど・醸造台の処理を ×tiplerSpeedMultiplier（既定2）にする。
 * 範囲内の触媒装置は触媒の減りも同じ倍率で速くなる。重力時間膨張ゾーンと重なった場所では打ち消し合う。
 * <p>
 * 時間結晶触媒（ティア4）で動き、エキゾチック物質を FUEL_TICKS ごとに1個と、大電力（FE_PER_TICK）を使う。
 * 名前は理論上の時間機械「ティプラーの円柱」から取った。実在の物理では作れない装置。
 */
public class TiplerCylinderBlockEntity extends CatalystDeviceBlockEntity {
    public static final int MACHINE_TIER = 4;
    public static final int FE_PER_TICK = 100_000;
    public static final int FUEL_TICKS = 1200;
    /** 円柱の中心（コアからの高さ）。 */
    public static final int CENTER_HEIGHT = 3;
    static final int CHECK_INTERVAL = 40;

    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    private final IItemHandler automation = new CombinedInvWrapper(slot, fuel);
    private int burn;
    private boolean formed;
    private boolean firstCheck = true;
    private boolean running;
    private long nextCheck;
    private double extraTickCarry;
    private double randomTickCarry;
    /** 回転の速さ（0〜1、見た目用）。 */
    private float spin;

    public TiplerCylinderBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.TIPLER_CYLINDER.get(), pos, state, 10_000_000, 1_000_000, 0);
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(BuiltInRegistries.ITEM.get(Singulo.id("exotic_matter")));
    }

    public static int radius() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.TIPLER_RADIUS.get() : 8;
    }

    public ItemStackHandler fuel() {
        return fuel;
    }

    public boolean running() {
        return running;
    }

    public BlockPos fieldCenter() {
        return worldPosition.above(CENTER_HEIGHT);
    }

    @Override
    public Kind kind() {
        return Kind.TIPLER_CYLINDER;
    }

    @Override
    protected int machineTier() {
        return MACHINE_TIER;
    }

    @Override
    protected int baseUsage(ItemStack catalyst) {
        return FE_PER_TICK;
    }

    @Override
    protected Status readiness(ServerLevel level) {
        if (level.getGameTime() >= nextCheck) {
            nextCheck = level.getGameTime() + CHECK_INTERVAL;
            boolean was = formed;
            formed = Structures.casingShape(level, worldPosition, Structures.tiplerLayout(worldPosition), 6);
            io.github.genichimaruo.singulo.multiblock.FormationEffect.onChange(level, worldPosition, was, formed, firstCheck, 2, 0, 7);
            firstCheck = false;
        }
        if (!formed) {
            return Status.NOT_FORMED;
        }
        if (burn <= 0 && fuel.getStackInSlot(0).isEmpty()) {
            return Status.NO_FUEL;
        }
        return null;
    }

    @Override
    protected void apply(ServerLevel level, CatalystHelper.Effect effect, boolean active) {
        if (active != running) {
            running = active;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (!active) {
            TimeFields.remove(level, worldPosition);
            return;
        }
        if (burn <= 0) {
            fuel.extractItem(0, 1, false);
            burn = FUEL_TICKS;
        }
        burn--;
        int r = radius();
        TimeFields.set(level, worldPosition, fieldCenter(), r, true);
        double extra = TimeFields.accelerationSpeed() - 1;
        if (extra > 0) {
            accelerateVanilla(level, r, extra);
        }
    }

    /** かまど・醸造台を余分に動かし、作物に余分なランダムティックを与える（時間膨張と重なる場所は除く）。 */
    private void accelerateVanilla(ServerLevel level, int r, double extra) {
        BlockPos c = fieldCenter();
        extraTickCarry += extra;
        int ticks = (int) extraTickCarry;
        extraTickCarry -= ticks;
        if (ticks > 0) {
            List<BlockEntity> targets = new ArrayList<>();
            ChunkPos min = new ChunkPos(c.offset(-r, 0, -r));
            ChunkPos max = new ChunkPos(c.offset(r, 0, r));
            for (int cx = min.x; cx <= max.x; cx++) {
                for (int cz = min.z; cz <= max.z; cz++) {
                    if (!level.hasChunk(cx, cz)) {
                        continue;
                    }
                    for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                        if ((be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity)
                                && TimeFields.state(level, be.getBlockPos()) == 1) {
                            targets.add(be);
                        }
                    }
                }
            }
            for (int i = 0; i < ticks; i++) {
                for (BlockEntity be : targets) {
                    if (be.isRemoved()) {
                        continue;
                    }
                    if (be instanceof AbstractFurnaceBlockEntity furnace) {
                        AbstractFurnaceBlockEntity.serverTick(level, be.getBlockPos(), be.getBlockState(), furnace);
                    } else if (be instanceof BrewingStandBlockEntity stand) {
                        BrewingStandBlockEntity.serverTick(level, be.getBlockPos(), be.getBlockState(), stand);
                    }
                }
            }
        }
        // ランダムティック: 1区画（16³）あたり randomTickSpeed 回なので、範囲の立方体ぶんを extra 倍だけ足す
        int side = 2 * r + 1;
        randomTickCarry += level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING) * extra * side * side * side / 4096.0;
        int count = (int) randomTickCarry;
        randomTickCarry -= count;
        for (int i = 0; i < count; i++) {
            BlockPos p = c.offset(level.random.nextInt(side) - r, level.random.nextInt(side) - r, level.random.nextInt(side) - r);
            if (!level.isLoaded(p) || c.distSqr(p) > (double) r * r) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.isRandomlyTicking() && isGrowing(s) && TimeFields.state(level, p) == 1) {
                s.randomTick(level, p, level.random);
            }
        }
    }

    /** 時間を速める対象の植物。 */
    public static boolean isGrowing(BlockState s) {
        Block b = s.getBlock();
        return s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS) || b instanceof StemBlock || b instanceof SugarCaneBlock
                || b instanceof CactusBlock || b instanceof NetherWartBlock || b instanceof CocoaBlock
                || b instanceof SweetBerryBushBlock || b instanceof BambooStalkBlock || b instanceof BambooSaplingBlock;
    }

    @Override
    protected int displayValue() {
        return radius();
    }

    @Override
    protected int extraValue() {
        return fuel.getStackInSlot(0).getCount();
    }

    @Override
    public IItemHandler automationItems() {
        return automation;
    }

    /** 燃料（エキゾチック物質）は手に持って右クリックでも入れられる。 */
    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (isFuel(stack)) {
            ItemStack rest = fuel.insertItem(0, stack.copy(), false);
            stack.setCount(rest.getCount());
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 見た目（クライアント）

    public static void clientTick(Level level, BlockPos pos, BlockState state, TiplerCylinderBlockEntity be) {
        be.spin = be.running ? Math.min(1, be.spin + 0.01F) : Math.max(0, be.spin - 0.005F);
        be.angle += be.spin * 24;
        if (be.spin > 0.2F && level.random.nextFloat() < be.spin * 0.6F) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = 1.5 + level.random.nextDouble() * 4;
            double y = pos.getY() + 1 + level.random.nextDouble() * 5;
            double x = pos.getX() + 0.5 + Math.cos(a) * d;
            double z = pos.getZ() + 0.5 + Math.sin(a) * d;
            // 円柱の回りを速く流れる粒子
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, x, y, z,
                    -Math.sin(a) * 0.3 * be.spin, 0, Math.cos(a) * 0.3 * be.spin);
        }
    }

    private float angle;

    public float spin() {
        return spin;
    }

    public float angle(float partialTick) {
        return angle + spin * 24 * partialTick;
    }

    @Override
    public void onBroken(Level level) {
        super.onBroken(level);
        Block.popResource(level, worldPosition, fuel.getStackInSlot(0));
    }

    @Override
    public void setRemoved() {
        if (getLevel() != null) {
            TimeFields.remove(getLevel(), worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("running", running);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.serializeNBT(registries));
        tag.putInt("burn", burn);
        tag.putBoolean("running", running);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        burn = tag.getInt("burn");
        running = tag.getBoolean("running");
    }
}
