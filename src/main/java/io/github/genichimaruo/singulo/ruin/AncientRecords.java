package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.Codec;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.network.RecordsPayload;
import io.github.genichimaruo.singulo.registry.SinguloTriggers;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import io.github.genichimaruo.singulo.network.SinguloNetwork;

/**
 * 旧文明の記録。プレイヤーごとに解読した記録の ID を持ち、解読した記録を読むたびに順番に1つずつ増える。
 * ハンドブックの「旧文明の記録」の章に出すため、クライアントへ送る。
 */
public final class AncientRecords {
    public static final String DECODED = "singulo:records";

    private AncientRecords() {}

    public static List<String> decoded(ServerPlayer player) {
        return io.github.genichimaruo.singulo.compat.PlayerData.get(player, DECODED, Codec.STRING.listOf(), new ArrayList<>());
    }

    /** 次の記録を読めるようにする。全部読んでいれば null。 */
    @Nullable
    public static String learnNext(ServerPlayer player) {
        List<String> have = decoded(player);
        for (String id : GeneratedContent.RECORDS) {
            if (!have.contains(id)) {
                List<String> copy = new ArrayList<>(have);
                copy.add(id);
                io.github.genichimaruo.singulo.compat.PlayerData.set(player, DECODED, Codec.STRING.listOf(), copy);
                sync(player);
                if (copy.containsAll(GeneratedContent.RECORDS)) {
                    SinguloTriggers.milestone(player, "records_all");
                }
                return id;
            }
        }
        return null;
    }

    /** 次の封印された記録（封印記録から）を読めるようにする。全部読んでいれば null。 */
    @Nullable
    public static String learnNextHidden(ServerPlayer player) {
        List<String> have = decoded(player);
        for (String id : GeneratedContent.HIDDEN_RECORDS) {
            if (!have.contains(id)) {
                List<String> copy = new ArrayList<>(have);
                copy.add(id);
                io.github.genichimaruo.singulo.compat.PlayerData.set(player, DECODED, Codec.STRING.listOf(), copy);
                sync(player);
                return id;
            }
        }
        return null;
    }

    public static void sync(ServerPlayer player) {
        if (player.connection != null) {
            SinguloNetwork.sendToPlayer(player, new RecordsPayload(decoded(player)));
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
