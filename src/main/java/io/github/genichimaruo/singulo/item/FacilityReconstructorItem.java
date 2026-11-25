package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.ruin.RuinReconstruction;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public final class FacilityReconstructorItem extends Item {
    private static final long CONFIRM_TICKS = 20 * 30;
    private final Map<ServerPlayer, Target> confirmations = new WeakHashMap<>();
    private record Target(ServerLevel level, BlockPos anchor, ItemStack stack, long expiration, String facility,
                          net.minecraft.world.level.levelgen.structure.BoundingBox bounds) {
        boolean matches(ServerLevel actual, RuinReconstruction.Plan plan, ItemStack item) {
            var b = plan.bounds();
            return level == actual && anchor.equals(plan.anchor()) && stack == item && expiration >= actual.getGameTime()
                    && facility.equals(plan.facility()) && bounds.minX() == b.minX() && bounds.minY() == b.minY()
                    && bounds.minZ() == b.minZ() && bounds.maxX() == b.maxX() && bounds.maxY() == b.maxY()
                    && bounds.maxZ() == b.maxZ();
        }
    }

    public FacilityReconstructorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }
        if (player.isSpectator()) {
            confirmations.remove(player);
            return InteractionResult.FAIL;
        }
        var server = level.getServer();
        boolean owner = server.isSingleplayerOwner(player.getGameProfile());
        if (!server.isSingleplayer() || server.isPublished()) {
            var policy = ServerConfig.MULTIPLAYER_ACCESS;
            switch (ServerConfig.SPEC.isLoaded() ? policy.get() : policy.getDefault()) {
                case "everyone" -> {}
                case "admins_only" -> {
                    if (!owner && !player.hasPermissions(2)) return deny(player, "permission");
                }
                default -> { return deny(player, "disabled"); }
            }
        } else if (!owner) {
            return deny(player, "permission");
        }
        BlockPos pos = context.getClickedPos();
        var plan = RuinReconstruction.plan(level, pos, player.isCreative());
        if (plan == null) {
            confirmations.remove(player);
            return notify(player, "unavailable");
        }
        var stack = context.getItemInHand();
        var pending = confirmations.remove(player);
        if (!player.isSecondaryUseActive() || pending == null || !pending.matches(level, plan, stack)) {
            var b = plan.bounds();
            int n = level.getPlayers(p -> p != player && !p.isSpectator() && p.getBoundingBox().intersects(plan.area())).size();
            if (n > 0) return notify(player, "occupied");
            player.sendSystemMessage(Component.translatable("item.singulo.facility_reconstructor.warning",
                    Component.translatable("ruin.singulo." + plan.facility()),
                    b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()).withStyle(ChatFormatting.RED));
            confirmations.put(player, new Target(level, pos.immutable(), stack, level.getGameTime() + CONFIRM_TICKS,
                    plan.facility(), b));
            return InteractionResult.CONSUME;
        }
        if (!level.getPlayers(p -> p != player && !p.isSpectator() && p.getBoundingBox().intersects(plan.area())).isEmpty()) {
            return notify(player, "occupied");
        }
        if (!RuinReconstruction.apply(level, plan)) return notify(player, "failed");
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return notify(player, "complete");
    }

    private InteractionResult deny(ServerPlayer player, String reason) {
        confirmations.remove(player);
        return notify(player, reason);
    }

    private static InteractionResult notify(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("item.singulo.facility_reconstructor." + key));
        return InteractionResult.CONSUME;
    }
}
