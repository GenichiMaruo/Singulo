package io.github.genichimaruo.singulo.client;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ClientConfig;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Serialize our legacy config reloads with the UI that changes its values. */
final class LegacyClientConfig {
    private static WatchService watcher;
    private LegacyClientConfig() {}

    static void register(IEventBus modBus) {
        modBus.addListener((ModConfigEvent.Loading event) -> {
            var config = event.getConfig();
            if (config.getSpec() != ClientConfig.SPEC || !(config.getConfigData() instanceof CommentedFileConfig file)) return;
            var queued = new AtomicBoolean();
            try {
                // Forge 47's watcher parses into the live config on its own thread.
                // UI autosaves can otherwise modify that same TOML table mid-parse.
                // NightConfig 3.6 drops coalesced modify events (count > 1), which
                // Windows commonly emits. Keep other mods' watches and replace only
                // our callback, then watch our file with the JDK service directly.
                FileWatcher.defaultInstance().setWatch(file.getNioPath(), () -> {});
                if (watcher != null) watcher.close();
                var path = file.getNioPath().toAbsolutePath();
                var service = path.getFileSystem().newWatchService();
                watcher = service;
                path.getParent().register(service, StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE);
                Runnable reload = () -> {
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
                };
                Thread thread = new Thread(() -> {
                    try (service) {
                        while (true) {
                            var key = service.take();
                            boolean changed = false;
                            for (var change : key.pollEvents()) {
                                if (change.kind() == StandardWatchEventKinds.OVERFLOW
                                        || path.getFileName().equals(change.context())) changed = true;
                            }
                            if (!key.reset()) return;
                            if (changed && Files.isRegularFile(path)) reload.run();
                        }
                    } catch (ClosedWatchServiceException ignored) {
                        // A later config load replaced this watch.
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } catch (IOException error) {
                        Singulo.LOGGER.error("Cannot watch Singulo client configuration", error);
                    }
                }, "singulo-client-config-watch");
                thread.setDaemon(true);
                thread.start();
            } catch (IOException error) {
                throw new IllegalStateException("Cannot watch Singulo client configuration", error);
            }
        });
    }
}
