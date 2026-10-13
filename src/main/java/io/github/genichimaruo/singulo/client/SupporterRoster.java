package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.supporter.SupporterCredits;
import java.io.Reader;
import net.minecraft.client.Minecraft;

/** Read when a screen is opened so resource-pack changes and reloads are respected. */
final class SupporterRoster {
    private SupporterRoster() {}

    static SupporterCredits.Catalog load() {
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(Singulo.id("supporters.json"))) {
            return SupporterCredits.read(reader);
        } catch (Exception ex) {
            Singulo.LOGGER.error("Could not read the supporter credit roster", ex);
            return SupporterCredits.EMPTY;
        }
    }
}
