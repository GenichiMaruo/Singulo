package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.CapacityUpgrades;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineMenu;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Real, synchronized machine screens for all upgrade tiers in every shipped language. */
public final class CapacityUpgradeReview {
    private static final String[] LANGUAGES = {"ja_jp", "en_us", "zh_cn", "zh_tw", "ko_kr"};
    private static int language, tier = 1, phase, frames;
    private static boolean active;

    public static void rendered() {
        if (active && Minecraft.getInstance().screen instanceof MachineScreen) frames++;
    }

    public static boolean tick(Minecraft mc) throws Exception {
        if (!(mc.screen instanceof MachineScreen gui)) throw new IllegalStateException("Missing upgrade review screen");
        MachineMenu menu = gui.getMenu();
        active = true;
        if (phase == 0) {
            mc.getLanguageManager().setSelected(LANGUAGES[language]);
            mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
            int requested = tier;
            var playerId = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                var machine = (MachineBlockEntity) player.serverLevel().getBlockEntity(menu.pos());
                machine.items().setStackInSlot(machine.type().capacitySlot(), CapacityUpgrades.item(requested));
                machine.items().setStackInSlot(machine.type().upgradeSlot(),
                        new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("overclock_chip"))));
                machine.energy().setEnergy(CapacityUpgrades.capacity(requested) / 2);
            });
            frames = 0;
            phase = 1;
        } else if (phase == 1 && CapacityUpgrades.tier(menu.getSlot(menu.type().capacitySlot()).getItem()) == tier
                && menu.value(MachineBlockEntity.D_CAPACITY) == CapacityUpgrades.capacity(tier) && frames >= 4) {
            if (menu.getSlot(menu.type().upgradeSlot()).getItem().isEmpty())
                throw new IllegalStateException("Capacity/speed upgrade slots overlap");
            Path output = Path.of(System.getProperty("singulo.jeiReviewOutput"));
            Files.createDirectories(output);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(output.resolve("capacity-mk" + tier + "-" + LANGUAGES[language] + ".png"));
            }
            Singulo.LOGGER.info("SINGULO_CAPACITY_GUI_COMPLETE {} Mk{}", LANGUAGES[language], tier);
            phase = 0;
            if (++tier > 3) { tier = 1; if (++language == LANGUAGES.length) { active = false; return true; } }
        }
        return false;
    }
}
