package io.github.genichimaruo.singulo.recipe;

import io.github.genichimaruo.singulo.Singulo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.AddPackFindersEvent;

/** Data packs distributed with an instance, selected and ordered per world by Minecraft. */
public final class ModpackRecipePacks {

    private ModpackRecipePacks() {}

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            Path folder = FMLPaths.CONFIGDIR.get().resolve("singulo/datapacks");
            event.addRepositorySource(packs -> discover(folder, packs));
        }
    }

    static void discover(Path folder, Consumer<Pack> packs) {
        if (!Files.isDirectory(folder)) return;
        try {
            FolderRepositorySource.discoverPacks(folder, false, (path, supplier) -> {
                String name = path.getFileName().toString();
                // World-local packs use file/<name>; keep both selectable when names coincide.
                var pack = Pack.readMetaAndCreate("singulo/" + name, Component.literal(name), false,
                        supplier, PackType.SERVER_DATA, Pack.Position.TOP, PackSource.SERVER);
                if (pack != null) packs.accept(pack);
            });
        } catch (IOException ex) {
            Singulo.LOGGER.warn("Could not list data packs in {}", folder, ex);
        }
    }
}
