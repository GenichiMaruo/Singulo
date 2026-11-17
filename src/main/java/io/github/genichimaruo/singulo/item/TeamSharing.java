package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;

/**
 * 持ち主と同じバニラのチーム（/team）の人にも、アンドロイド・ドローン・作業指令台を使わせる（0.20.0。サーバー設定 shareWithTeam）。
 * 許可証で使える人を絞っていても、チームの人は登録しなくてよい。持ち主がオフラインでも、名前からチームを調べる。
 */
public final class TeamSharing {
    private TeamSharing() {}

    /** player が owner と同じチームにいるか（設定で切ってあるか、持ち主がいない・チームに入っていないなら false）。 */
    public static boolean sameTeam(@Nullable Level level, @Nullable UUID owner, Player player) {
        if (level == null || owner == null || !enabled()) {
            return false;
        }
        PlayerTeam team = player.getTeam();
        if (team == null) {
            return false;
        }
        String name = ownerName(level, owner);
        return name != null && team.getPlayers().contains(name);
    }

    private static boolean enabled() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.SHARE_WITH_TEAM.get();
    }

    /** 持ち主の名前（スコアボードのチームは名前で人を覚える）。いなければ、サーバーが覚えている名前。 */
    @Nullable
    private static String ownerName(Level level, UUID owner) {
        Player online = level.getPlayerByUUID(owner);
        if (online != null) {
            return online.getScoreboardName();
        }
        var server = level.getServer();
        var cache = server == null ? null : server.getProfileCache();
        return cache == null ? null : cache.get(owner).map(com.mojang.authlib.GameProfile::getName).orElse(null);
    }
}
