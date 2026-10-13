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
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * ボス戦の記録（調整用。設定 bossCombatLog）。挑戦者がボスから受けたダメージ（どの技か、防具でどれだけ減ったか）と、
 * ボスが挑戦者から受けたダメージ、倒れたときを、ゲームのフォルダの logs/singulo-combat.log に1行ずつ書く（タブ区切り）。
 */
public final class BossCombatLog {
    private static final String FILE = "singulo-combat.log";
    private static final String HEADER = "time\tevent\tboss\tboss_hp\tboss_state\tattack\traw\tdealt\tblocked\tplayer\tplayer_hp\tarmor\ttoughness"
            + "\tblocking\tdistance\tweapon\tparticipants";

    /** 叩きつけられた相手と、叩きつけたボス・技・その時刻（落下のダメージは攻撃した者を持たないので、ここで結びつける）。 */
    private record Slam(Mob boss, String attack, long until) {}

    private static final java.util.Map<java.util.UUID, Slam> SLAMS = new java.util.concurrent.ConcurrentHashMap<>();

    private BossCombatLog() {}

    /** ボスが相手を叩きつけた（このあと数秒の落下ダメージを、その技で受けたものとして記録する）。 */
    public static void slammed(LivingEntity target, Mob boss, String attack) {
        if (target instanceof Player && enabled()) {
            SLAMS.put(target.getUUID(), new Slam(boss, attack, target.level().getGameTime() + 100));
        }
    }

    /** 落下のダメージなら、直前に叩きつけたボス。 */
    private static Slam slamFor(LivingEntity victim, DamageSource source) {
        Slam s = SLAMS.get(victim.getUUID());
        if (s == null || !source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            return null;
        }
        if (victim.level().getGameTime() > s.until() || s.boss().isRemoved()) {
            SLAMS.remove(victim.getUUID());
            return null;
        }
        return s;
    }

    static boolean enabled() {
        return ServerConfig.SPEC.isLoaded() && ServerConfig.BOSS_COMBAT_LOG.get();
    }

    /** 攻撃した側（飛び道具なら撃った者）がボスなら、そのボス。 */
    private static Mob boss(Entity e) {
        return e instanceof RuinBoss || e instanceof HorizonWarden ? (Mob) e : null;
    }

    private static final java.util.Map<LivingEntity, Float> RAW = new java.util.WeakHashMap<>();
    private static final java.util.Map<LivingEntity, Float> BLOCKED = new java.util.WeakHashMap<>();
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide && enabled()) RAW.put(event.getEntity(), event.getAmount());
    }
    public static void onShield(net.minecraftforge.event.entity.living.ShieldBlockEvent event) {
        if (!event.getEntity().level().isClientSide && enabled()) BLOCKED.put(event.getEntity(), event.getBlockedDamage());
    }
    public static void onDamage(LivingDamageEvent event) {
        float rawDamage = RAW.getOrDefault(event.getEntity(), event.getAmount()); RAW.remove(event.getEntity());
        float blockedDamage = BLOCKED.getOrDefault(event.getEntity(), 0F); BLOCKED.remove(event.getEntity());
        if (event.getEntity().level().isClientSide() || !enabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Mob attacker = boss(source.getEntity());
        Slam slam = attacker == null ? slamFor(victim, source) : null;
        if (victim instanceof Player player && slam != null) {
            if (player.getHealth() > 0) {
                // 倒れなかった: この叩きつけの記録はここまで（倒れたときは、死亡の記録で使ってから消す）
                SLAMS.remove(player.getUUID());
            }
            write("hit_player", slam.boss(), slam.attack(), rawDamage, event.getAmount(), blockedDamage, player);
        } else if (victim instanceof Player player && attacker != null) {
            write("hit_player", attacker, attackName(attacker, source), rawDamage, event.getAmount(),
                    blockedDamage, player);
        } else if (boss(victim) != null && source.getEntity() instanceof Player player) {
            write("hit_boss", (Mob) victim, source.getMsgId(), rawDamage, event.getAmount(), blockedDamage, player);
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !enabled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        Mob attacker = boss(event.getSource().getEntity());
        Slam slam = attacker == null ? slamFor(dead, event.getSource()) : null;
        if (dead instanceof Player player && slam != null) {
            SLAMS.remove(player.getUUID());
            write("player_died", slam.boss(), slam.attack(), 0, 0, 0, player);
        } else if (dead instanceof Player player && attacker != null) {
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
        if (boss instanceof HorizonWarden w && w.combatAttack() != null) {
            return w.combatAttack();
        }
        return source.getMsgId();
    }

    private static void write(String event, Mob boss, String attack, float raw, float dealt, float blocked, Player player) {
        String state = boss instanceof RuinBoss r ? r.combatState() : boss instanceof HorizonWarden w ? w.combatState() : "";
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
