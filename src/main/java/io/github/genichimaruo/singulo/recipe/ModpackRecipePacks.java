package io.github.genichimaruo.singulo.recipe;

import io.github.genichimaruo.singulo.Singulo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Data packs distributed with an instance, selected and ordered per world by Minecraft. */
public final class ModpackRecipePacks {
    private static final PackSelectionConfig SELECTION = new PackSelectionConfig(false, Pack.Position.TOP, false);

    private ModpackRecipePacks() {}

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA && !event.isTrusted()) {
            Path folder = FMLPaths.CONFIGDIR.get().resolve("singulo/datapacks");
            event.addRepositorySource(packs -> discover(folder, packs));
        }
    }

    static void discover(Path folder, Consumer<Pack> packs) {
        if (!Files.isDirectory(folder)) return;
        var validator = LevelStorageSource.parseValidator(FMLPaths.GAMEDIR.get().resolve("allowed_symlinks.txt"));
        try {
            FolderRepositorySource.discoverPacks(folder, validator, (path, supplier) -> {
                String name = path.getFileName().toString();
                // World-local packs use file/<name>; keep both selectable when names coincide.
                var info = new PackLocationInfo("singulo/" + name, Component.literal(name), PackSource.SERVER, Optional.empty());
                var pack = Pack.readMetaAndCreate(info, supplier, PackType.SERVER_DATA, SELECTION);
                if (pack != null) packs.accept(pack);
            });
        } catch (IOException ex) {
            Singulo.LOGGER.warn("Could not list data packs in {}", folder, ex);
        }
    }
}
