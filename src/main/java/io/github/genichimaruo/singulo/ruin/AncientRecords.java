package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.Codec;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.network.RecordsPayload;
import io.github.genichimaruo.singulo.registry.SinguloTriggers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 旧文明の記録。プレイヤーごとに解読した記録の ID を持ち、解読した記録を読むたびに順番に1つずつ増える。
 * ハンドブックの「旧文明の記録」の章に出すため、クライアントへ送る。
 */
public final class AncientRecords {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Singulo.MODID);

    public static final Supplier<AttachmentType<List<String>>> DECODED = REGISTER.register("records",
            () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf()).copyOnDeath().build());

    private AncientRecords() {}

    public static List<String> decoded(ServerPlayer player) {
        return player.getData(DECODED);
    }

    /** 次の記録を読めるようにする。全部読んでいれば null。 */
    @Nullable
    public static String learnNext(ServerPlayer player) {
        List<String> have = decoded(player);
        for (String id : GeneratedContent.RECORDS) {
            if (!have.contains(id)) {
                List<String> copy = new ArrayList<>(have);
                copy.add(id);
                player.setData(DECODED, copy);
                sync(player);
                if (copy.size() >= GeneratedContent.RECORDS.size()) {
                    SinguloTriggers.milestone(player, "records_all");
                }
                return id;
            }
        }
        return null;
    }

    public static void sync(ServerPlayer player) {
        if (player.connection != null && player.connection.hasChannel(RecordsPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new RecordsPayload(decoded(player)));
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
