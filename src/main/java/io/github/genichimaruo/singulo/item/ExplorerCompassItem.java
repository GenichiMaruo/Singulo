package io.github.genichimaruo.singulo.item;

import com.mojang.datafixers.util.Pair;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * 探索コンパス（段階1）。探す遺構を選んで右クリックすると、いちばん近いその遺構を探し、針がそちらを向く（距離も出る）。
 * スニークして右クリックすると探す遺構が切り替わる。探すのは少し重いので、使ったあとは数秒待つ。
 * <p>
 * はじめは地表観測拠点しか探せない。攻略した（保管庫を自分で開けた）遺構の保管庫をスニークして右クリックすると、
 * そこに残る記録でコンパスを調整し、次の遺構（研究棟 → 封鎖培養施設 → 最終実験施設）も探せるようになる。
 */
public class ExplorerCompassItem extends SinguloItem {
    public static final String[] TARGETS = {"observation_post", "research_building", "culture_facility", "final_lab"};
    public static final int SEARCH_CHUNKS = 64;

    public ExplorerCompassItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    /** 探せる遺構の数（1〜4）。TARGETS の先頭からこの数だけ。 */
    public static int level(ItemStack stack) {
        return Math.max(1, Math.min(TARGETS.length, stack.getOrDefault(SinguloComponents.COMPASS_LEVEL.get(), 1)));
    }

    public static int target(ItemStack stack) {
        return Math.floorMod(stack.getOrDefault(SinguloComponents.HOLO_SIZE.get(), 0), level(stack));
    }

    /**
     * コンパスの調整: 攻略した遺構の保管庫をスニークして右クリックする。調整できるのは、いま探せるいちばん奥の遺構の
     * 保管庫で、その遺構の保管庫を自分で開けたことがある（RuinDiscovery）ときだけ。
     */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !player.isSecondaryUseActive()
                || !(level.getBlockEntity(context.getClickedPos()) instanceof io.github.genichimaruo.singulo.ruin.RuinCacheBlockEntity cache)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        int lv = level(stack);
        if (lv >= TARGETS.length) {
            player.displayClientMessage(Component.translatable("compass.singulo.max"), true);
            return net.minecraft.world.InteractionResult.FAIL;
        }
        String deepest = TARGETS[lv - 1];
        Component deepestName = Component.translatable("ruin.singulo." + deepest);
        if (!deepest.equals(cache.ruin())) {
            player.displayClientMessage(Component.translatable("compass.singulo.wrong_cache", deepestName), true);
            return net.minecraft.world.InteractionResult.FAIL;
        }
        boolean cleared = io.github.genichimaruo.singulo.ruin.RuinDiscovery.discovered(player).stream()
                .anyMatch(d -> d.ruin().equals(deepest));
        if (!cleared) {
            player.displayClientMessage(Component.translatable("compass.singulo.not_cleared", deepestName), true);
            return net.minecraft.world.InteractionResult.FAIL;
        }
        upgrade(stack);
        player.displayClientMessage(Component.translatable("compass.singulo.upgraded",
                Component.translatable("ruin.singulo." + TARGETS[lv])), true);
        level.playSound(null, context.getClickedPos(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
        if (level instanceof ServerLevel server) {
            BlockPos p = context.getClickedPos();
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5,
                    30, 0.4, 0.4, 0.4, 0.6);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** 調整段階を1つ上げ、新しく探せるようになった遺構を選ぶ。 */
    public static void upgrade(ItemStack stack) {
        int lv = Math.min(TARGETS.length, level(stack) + 1);
        stack.set(SinguloComponents.COMPASS_LEVEL.get(), lv);
        stack.set(SinguloComponents.HOLO_SIZE.get(), lv - 1);
        stack.remove(DataComponents.LODESTONE_TRACKER);
    }

    /** 今の針の向き先（なければ null）。クライアントの針の角度にも使う。 */
    public static GlobalPos pointing(ItemStack stack) {
        LodestoneTracker t = stack.get(DataComponents.LODESTONE_TRACKER);
        return t == null ? null : t.target().orElse(null);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown()) {
            stack.set(SinguloComponents.HOLO_SIZE.get(), target(stack) + 1);
            stack.remove(DataComponents.LODESTONE_TRACKER);
            player.displayClientMessage(Component.translatable("compass.singulo.target",
                    Component.translatable("ruin.singulo." + TARGETS[target(stack)])), true);
            return InteractionResultHolder.success(stack);
        }
        ServerLevel server = (ServerLevel) level;
        String rid = TARGETS[target(stack)];
        Optional<Holder.Reference<Structure>> holder = server.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, Singulo.id(rid)));
        Pair<BlockPos, Holder<Structure>> found = holder.isEmpty() ? null : server.getChunkSource().getGenerator()
                .findNearestMapStructure(server, HolderSet.direct(holder.get()), player.blockPosition(), SEARCH_CHUNKS, false);
        player.getCooldowns().addCooldown(this, 60);
        if (found == null) {
            stack.remove(DataComponents.LODESTONE_TRACKER);
            player.displayClientMessage(Component.translatable("compass.singulo.not_found",
                    Component.translatable("ruin.singulo." + rid)), true);
            return InteractionResultHolder.fail(stack);
        }
        BlockPos p = center(server, found.getFirst(), holder.get().value());
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), p)), false));
        int dist = (int) Math.sqrt(player.blockPosition().distSqr(new BlockPos(p.getX(), player.getBlockY(), p.getZ())));
        player.displayClientMessage(Component.translatable("compass.singulo.found",
                Component.translatable("ruin.singulo." + rid), dist), true);
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResultHolder.success(stack);
    }

    /**
     * 見つかった遺構の、建物全体の中心（地面の高さはそのまま）。見つけた位置は配置の基準のチャンクなので、
     * その構造物の始まりを読み、全体を囲む範囲の中心にする（読めなければ見つけた位置のまま）。
     */
    static BlockPos center(ServerLevel server, BlockPos found, Structure structure) {
        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(found);
        var start = server.getChunk(cp.x, cp.z, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS)
                .getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            return found;
        }
        BlockPos c = start.getBoundingBox().getCenter();
        return new BlockPos(c.getX(), found.getY(), c.getZ());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("compass.singulo.target", Component.translatable("ruin.singulo." + TARGETS[target(stack)]))
                .withStyle(ChatFormatting.GRAY));
        int lv = level(stack);
        tooltip.add(Component.translatable("compass.singulo.level", lv, TARGETS.length).withStyle(ChatFormatting.GRAY));
        if (lv < TARGETS.length) {
            tooltip.add(Component.translatable("compass.singulo.hint",
                    Component.translatable("ruin.singulo." + TARGETS[lv - 1])).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
