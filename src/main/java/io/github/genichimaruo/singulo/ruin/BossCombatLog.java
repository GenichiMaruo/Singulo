package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * ボス戦の記録（調整用。設定 bossCombatLog）。挑戦者がボスから受けたダメージ（どの技か、防具でどれだけ減ったか）と、
 * ボスが挑戦者から受けたダメージ、倒れたときを、ゲームのフォルダの logs/singulo-combat.log に1行ずつ書く（タブ区切り）。
 */
public final class BossCombatLog {
    private static final String FILE = "singulo-combat.log";
    private static final String HEADER = "time\tevent\tboss\tboss_hp\tboss_state\tattack\traw\tdealt\tblocked\tplayer\tplayer_hp\tarmor\ttoughness"
            + "\tblocking\tdistance\tweapon\tparticipants";

    private BossCombatLog() {}

    static boolean enabled() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.BOSS_COMBAT_LOG.get();
    }

    /** 攻撃した側（飛び道具なら撃った者）がボスなら、そのボス。 */
    private static Mob boss(Entity e) {
        return e instanceof RuinBoss || e instanceof HorizonWarden ? (Mob) e : null;
    }

    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !enabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Mob attacker = boss(source.getEntity());
        if (victim instanceof Player player && attacker != null) {
            write("hit_player", attacker, attackName(attacker, source), event.getOriginalDamage(), event.getNewDamage(),
                    event.getBlockedDamage(), player);
        } else if (boss(victim) != null && source.getEntity() instanceof Player player) {
            write("hit_boss", (Mob) victim, source.getMsgId(), event.getOriginalDamage(), event.getNewDamage(), event.getBlockedDamage(), player);
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !enabled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        Mob attacker = boss(event.getSource().getEntity());
        if (dead instanceof Player player && attacker != null) {
            write("player_died", attacker, attackName(attacker, event.getSource()), 0, 0, 0, player);
        } else if (boss(dead) != null) {
            Player killer = event.getSource().getEntity() instanceof Player p ? p : null;
            write("boss_died", (Mob) dead, event.getSource().getMsgId(), 0, 0, 0, killer);
        }
    }

    /** 技の名前: 飛び道具はその種類、ほかはボスが今使っている技（なければダメージの種類）。 */
    private static String attackName(Mob boss, DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct != null && direct != boss) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getPath();
        }
        if (boss instanceof RuinBoss r && r.combatAttack() != null) {
            return r.combatAttack();
        }
        return source.getMsgId();
    }

    private static void write(String event, Mob boss, String attack, float raw, float dealt, float blocked, Player player) {
        String state = boss instanceof RuinBoss r ? r.combatState() : "";
        int participants = boss instanceof RuinBoss r ? r.participants() : boss instanceof HorizonWarden w ? w.participants() : 1;
        String line = String.join("\t",
                String.format(Locale.ROOT, "%.2f", boss.tickCount / 20.0),
                event,
                BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).getPath() + (boss instanceof RuinBoss r && r.isMinion() ? "(echo)" : ""),
                String.format(Locale.ROOT, "%.1f/%.1f", boss.getHealth(), boss.getMaxHealth()),
                state,
                attack,
                String.format(Locale.ROOT, "%.2f", raw),
                String.format(Locale.ROOT, "%.2f", dealt),
                String.format(Locale.ROOT, "%.2f", blocked),
                player == null ? "-" : player.getGameProfile().getName(),
                player == null ? "-" : String.format(Locale.ROOT, "%.1f/%.1f", player.getHealth(), player.getMaxHealth()),
                player == null ? "-" : String.format(Locale.ROOT, "%.0f", player.getAttributeValue(Attributes.ARMOR)),
                player == null ? "-" : String.format(Locale.ROOT, "%.0f", player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),
                player == null ? "-" : String.valueOf(player.isBlocking()),
                player == null ? "-" : String.format(Locale.ROOT, "%.1f", player.distanceTo(boss)),
                player == null ? "-" : BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath(),
                String.valueOf(participants));
        Singulo.LOGGER.info("[SinguloCombat] {}", line.replace('\t', ' '));
        try {
            Path path = FMLPaths.GAMEDIR.get().resolve("logs").resolve(FILE);
            Files.createDirectories(path.getParent());
            boolean fresh = !Files.exists(path);
            Files.writeString(path, (fresh ? HEADER + System.lineSeparator() : "") + line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Singulo.LOGGER.warn("Could not write the boss combat log", e);
        }
    }
}
