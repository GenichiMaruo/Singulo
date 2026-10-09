package io.github.genichimaruo.singulo.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * シールド許可証による守り: 許可証の入ったシールドが動いている間、その半径の中で、登録されていない人は
 * ブロックの設置・破壊、入れ物（チェスト・装置など）からの取り出し、額縁や防具立てへの手出しができない。
 * 放射冠だけはだれでも壊せ、壊すとシールドが止まる（締め出されたときの最後の手段）。
 */
public final class ShieldPermits {
    private ShieldPermits() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ShieldPermits::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ShieldPermits::onPlace);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ShieldPermits::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ShieldPermits::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ShieldPermits::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ShieldPermits::onAttackEntity);
    }

    private static boolean deny(Level level, BlockPos pos, Player player) {
        if (ShieldTowerBlockEntity.locked(level, pos, player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("gui.singulo.shield.locked"), true);
            }
            return true;
        }
        return false;
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (ShieldTowerBlockEntity.isActiveCrown(level, pos)) {
            ShieldTowerBlockEntity.onCrownBroken(level, pos);       // 放射冠はだれでも壊せる。壊すとシールドが止まる
            return;
        }
        if (deny(level, pos, event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && event.getLevel() instanceof Level level
                && deny(level, event.getPos(), player)) {
            event.setCanceled(true);
        }
    }

    /** 入れ物を開く・バケツで汲む・流す、を止める（ブロックを置くのは onPlace で止める）。 */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        boolean container = level.getBlockEntity(pos) != null;
        boolean bucket = event.getItemStack().getItem() instanceof BucketItem;
        if ((container || bucket) && deny(level, pos, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean guarded(Entity target) {
        return target instanceof HangingEntity || target instanceof ArmorStand || target instanceof ContainerEntity;
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (guarded(event.getTarget()) && deny(event.getLevel(), event.getTarget().blockPosition(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (guarded(event.getTarget()) && deny(event.getLevel(), event.getTarget().blockPosition(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (guarded(event.getTarget()) && deny(player.level(), event.getTarget().blockPosition(), player)) {
            event.setCanceled(true);
        }
    }
}
