package io.github.genichimaruo.singulo.client;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ClientConfig;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Serialize our legacy config reloads with the UI that changes its values. */
final class LegacyClientConfig {
    private LegacyClientConfig() {}

    static void register(IEventBus modBus) {
        modBus.addListener((ModConfigEvent.Loading event) -> {
            var config = event.getConfig();
            if (config.getSpec() != ClientConfig.SPEC || !(config.getConfigData() instanceof CommentedFileConfig file)) return;
            var queued = new AtomicBoolean();
            try {
                // Forge 47's watcher parses into the live config on its own thread.
                // UI autosaves can otherwise modify that same TOML table mid-parse.
                FileWatcher.defaultInstance().setWatch(file.getNioPath(), () -> {
                    if (queued.compareAndSet(false, true)) Minecraft.getInstance().execute(() -> {
                        queued.set(false);
                        try {
                            file.load();
                            ClientConfig.SPEC.setConfig(file);
                            net.minecraftforge.fml.ModList.get().getModContainerById(Singulo.MODID).orElseThrow()
                                    .dispatchConfigEvent(new ModConfigEvent.Reloading(config));
                        } catch (RuntimeException error) {
                            Singulo.LOGGER.error("Cannot reload Singulo client configuration", error);
                        }
                    });
                });
            } catch (IOException error) {
                throw new IllegalStateException("Cannot watch Singulo client configuration", error);
            }
        });
    }
}
