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
 * スニークして右クリックすると探す遺構（地表観測拠点・研究棟・封鎖培養施設・最終実験施設）が切り替わる。
 * 探すのは少し重いので、使ったあとは数秒待つ。
 */
public class ExplorerCompassItem extends SinguloItem {
    public static final String[] TARGETS = {"observation_post", "research_building", "culture_facility", "final_lab"};
    public static final int SEARCH_CHUNKS = 64;

    public ExplorerCompassItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    public static int target(ItemStack stack) {
        return Math.floorMod(stack.getOrDefault(SinguloComponents.HOLO_SIZE.get(), 0), TARGETS.length);
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
        BlockPos p = found.getFirst();
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), p)), false));
        int dist = (int) Math.sqrt(player.blockPosition().distSqr(new BlockPos(p.getX(), player.getBlockY(), p.getZ())));
        player.displayClientMessage(Component.translatable("compass.singulo.found",
                Component.translatable("ruin.singulo." + rid), dist), true);
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResultHolder.success(stack);
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
    }
}
