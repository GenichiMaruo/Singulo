package io.github.genichimaruo.singulo.compat.jei;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.client.AssemblySelectorReview;
import io.github.genichimaruo.singulo.client.MachineScreen;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class JeiReviewHarness {
    private static final String[] LANGUAGES = {"ja_jp", "en_us", "zh_cn", "zh_tw", "ko_kr"};
    private static int ticks, language, frames;
    private static ReviewScreen screen;
    private static int transferPhase, transferTicks;
    private static final List<String> ASSEMBLIES = List.of("android_tank_upgrade", "android_work_console",
            "android_station", "wormhole_generator_core", "casimir_cavity_frame");
    private static int assembly;
    private static int transferSets;
    private static volatile boolean transferVerified;
    private static MachineRecipe transferRecipe;
    private static net.minecraft.resources.ResourceLocation transferId;
    private static final net.minecraft.core.BlockPos STATION = new net.minecraft.core.BlockPos(0, 80, 0);

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) {
        if (Minecraft.getInstance().screen == screen) frames++;
        AssemblySelectorReview.rendered();
        io.github.genichimaruo.singulo.client.CapacityUpgradeReview.rendered();
    }

    @SubscribeEvent
    public static void tooltip(RenderTooltipEvent.Color event) {
        AssemblySelectorReview.tooltip(event);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 3600) throw new IllegalStateException("JEI review timed out");
        if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
            for (var child : mc.screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents label
                        && label.getKey().equals("selectWorld.backupJoinSkipButton")) { button.onPress(); return; }
            }
        }
        if (mc.level == null || mc.player == null || mc.getOverlay() != null || JeiSearch.runtime == null) return;
        if (transferPhase > 0) { checkTransfer(mc); return; }
        if (screen == null) {
            mc.getLanguageManager().setSelected(LANGUAGES[language]);
            mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
            screen = new ReviewScreen();
            mc.setScreen(screen);
            frames = 0;
        } else if (mc.screen == screen && frames >= 3) {
            Path output = Path.of(System.getProperty("singulo.jeiReviewOutput"));
            Files.createDirectories(output);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(output.resolve("jei-" + LANGUAGES[language] + ".png"));
            }
            Singulo.LOGGER.info("SINGULO_JEI_LAYOUT_COMPLETE {}", LANGUAGES[language]);
            if (++language == LANGUAGES.length) { mc.setScreen(null); screen = null; transferPhase = 1; return; }
            screen = null;
        }
    }

    @SuppressWarnings("removal")
    private static void checkTransfer(Minecraft mc) throws Exception {
        if (transferPhase == 1) {
            transferId = Singulo.id("precision_assembler/" + ASSEMBLIES.get(assembly));
            transferSets = ASSEMBLIES.get(assembly).equals("android_tank_upgrade") ? 8 : 1;
            AssemblySelectorReview.expect(transferId, transferSets);
            transferRecipe = SinguloJeiPlugin.RECIPE_IDS.entrySet().stream().filter(e -> e.getValue().equals(transferId))
                    .findFirst().orElseThrow().getKey();
            mc.getLanguageManager().setSelected("ja_jp");
            mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
            transferTicks = 0;
            transferVerified = false;
            var playerId = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                player.getInventory().clearContent();
                for (int i = 0; i < transferRecipe.ingredients().size(); i++) {
                    var ingredient = transferRecipe.ingredients().get(i);
                    var stack = ingredient.ingredient().getItems()[0].copyWithCount(ingredient.count() * transferSets);
                    if (ingredient.uses() > 0) {
                        if (ingredient.usesFrom(stack) != 750) throw new IllegalStateException("Expected one 750-use cavity frame batch");
                        stack = UsesHelper.consume(stack, UsesHelper.remaining(stack) - 750);
                    }
                    player.getInventory().setItem(i, stack);
                }
                player.teleportTo(1.5, 80, 1.5);
                for (var floor : net.minecraft.core.BlockPos.betweenClosed(-2, 79, -2, 3, 79, 3))
                    player.serverLevel().setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                player.serverLevel().setBlockAndUpdate(STATION, io.github.genichimaruo.singulo.registry.SinguloBlocks.MACHINES
                        .get(MachineType.PRECISION_ASSEMBLER).get().defaultBlockState());
                var machine = (io.github.genichimaruo.singulo.machine.MachineBlockEntity) player.serverLevel().getBlockEntity(STATION);
                machine.magazine().load(new net.minecraft.nbt.CompoundTag(), player.registryAccess());
                for (int i = 0; i < machine.items().getSlots(); i++)
                    machine.items().setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
                for (int i = 0; i < MachineRecipe.ASSEMBLY_CELLS; i++)
                    machine.assemblyGrid().setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
                machine.energy().setEnergy(0);
                machine.openMenu(player);
            });
            transferPhase = 2;
        } else if (transferPhase == 2 && mc.player.containerMenu instanceof io.github.genichimaruo.singulo.machine.MachineMenu menu
                && io.github.genichimaruo.singulo.machine.MachineRecipeTransfer.plan(menu, transferRecipe, false) != null) {
            var runtime = JeiSearch.runtime;
            var category = runtime.getRecipeManager().getRecipeCategory(SinguloJeiPlugin.TYPES.get(MachineType.PRECISION_ASSEMBLER));
            var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
            var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, transferRecipe,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            boolean bulk = transferSets > 1;
            if (handler.transferRecipe(menu, transferRecipe, layout.getRecipeSlotsView(), mc.player, bulk, false) != null
                    || handler.transferRecipe(menu, transferRecipe, layout.getRecipeSlotsView(), mc.player, bulk, true) != null)
                throw new IllegalStateException("JEI registered transfer handler rejected fixture");
            transferPhase = 3;
        } else if (transferPhase == 3 && ++transferTicks > 10) {
            var playerId = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                var machine = (io.github.genichimaruo.singulo.machine.MachineBlockEntity) player.serverLevel().getBlockEntity(STATION);
                for (int i = 0; i < MachineRecipe.ASSEMBLY_CELLS; i++) {
                    var ingredient = transferRecipe.assemblyInput(i);
                    var stack = machine.assemblyGrid().getStackInSlot(i);
                    if (ingredient == null ? !stack.isEmpty() : stack.getCount() != transferSets || !ingredient.ingredient().test(stack))
                        throw new IllegalStateException("JEI packet placed the wrong stack at cell " + i);
                }
                if (ASSEMBLIES.get(assembly).equals("casimir_cavity_frame")) {
                    var catalyst = machine.magazine().loaded();
                    if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(catalyst.getItem()).equals(Singulo.id("bose_condensate_catalyst"))
                            || UsesHelper.remaining(catalyst) != 750 || !machine.items().getStackInSlot(machine.type().catalystSlot()).isEmpty())
                        throw new IllegalStateException("JEI transfer lost, duplicated or refreshed the catalyst");
                }
                for (int i = 0; i < 36; i++) if (!player.getInventory().getItem(i).isEmpty())
                    throw new IllegalStateException("JEI transfer duplicated fixture ingredients");
                transferVerified = true;
            });
            transferPhase = 4;
        } else if (transferPhase == 4 && transferVerified && AssemblySelectorReview.previewReady()) {
            Singulo.LOGGER.info("SINGULO_JEI_TRANSFER_COMPLETE {} ({} sets)", transferId, transferSets);
            Path output = Path.of(System.getProperty("singulo.jeiReviewOutput"));
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(output.resolve("assembly-" + ASSEMBLIES.get(assembly) + ".png"));
                AssemblySelectorReview.verifyGhost(image, (MachineScreen) mc.screen);
            }
            AssemblySelectorReview.openAndHover((MachineScreen) mc.screen);
            transferPhase = 5;
        } else if (transferPhase == 5 && AssemblySelectorReview.captureSelector(mc, ASSEMBLIES.get(assembly), transferRecipe,
                Path.of(System.getProperty("singulo.jeiReviewOutput")))) {
            if (++assembly < ASSEMBLIES.size()) {
                mc.player.closeContainer();
                transferPhase = 1;
                return;
            }
            AssemblySelectorReview.awaitSpent((MachineScreen) mc.screen);
            var server = mc.getSingleplayerServer();
            var playerId = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var machine = (io.github.genichimaruo.singulo.machine.MachineBlockEntity) player.serverLevel().getBlockEntity(STATION);
                machine.energy().setEnergy(machine.type().energyCapacity());
            });
            transferPhase = 6;
        } else if (transferPhase == 6 && AssemblySelectorReview.captureSpent(mc, transferRecipe,
                Path.of(System.getProperty("singulo.jeiReviewOutput")))) {
            io.github.genichimaruo.singulo.client.PopupInputReview.verify((MachineScreen) mc.screen, JeiSearch.runtime);
            transferPhase = 7;
        } else if (transferPhase == 7 && io.github.genichimaruo.singulo.client.CapacityUpgradeReview.tick(mc)) {
            transferPhase = 8;
            mc.setScreen(null);
            mc.stop();
        }
    }

    private static final class ReviewScreen extends Screen {
        private final List<IRecipeLayoutDrawable<MachineRecipe>> layouts = new ArrayList<>();

        ReviewScreen() {
            super(Component.literal("JEI layout review"));
            add(MachineType.COMPRESSOR, false);
            add(MachineType.COMPRESSOR, true);
            add(MachineType.PELTIER_COOLER, false);
            add(MachineType.PRECISION_ASSEMBLER, false);
            int verified = 0;
            for (MachineType type : MachineType.values()) {
                var runtime = JeiSearch.runtime;
                var category = runtime.getRecipeManager().getRecipeCategory(SinguloJeiPlugin.TYPES.get(type));
                for (var recipe : SinguloJeiPlugin.RECIPE_IDS.keySet()) {
                    if (!recipe.station().equals(type.id())) continue;
                    var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
                            runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
                    verifySlots(layout);
                    verified++;
                }
            }
            Singulo.LOGGER.info("SINGULO_JEI_ALL_LAYOUTS_COMPLETE {}", verified);
        }

        @SuppressWarnings("removal")
        private void add(MachineType type, boolean mass) {
            var runtime = JeiSearch.runtime;
            var recipe = SinguloJeiPlugin.RECIPE_IDS.keySet().stream()
                    .filter(r -> r.station().equals(type.id()) && r.mass().isPresent() == mass)
                    .max(java.util.Comparator.comparingInt(r -> r.ingredients().size())).orElseThrow();
            var category = runtime.getRecipeManager().getRecipeCategory(SinguloJeiPlugin.TYPES.get(type));
            var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            verifySlots(layout);
            layouts.add(layout);
        }

        @SuppressWarnings("removal")
        private void verifySlots(IRecipeLayoutDrawable<MachineRecipe> layout) {
            var category = layout.getRecipeCategory();
            var slots = layout.getRecipeSlotsView().getSlotViews();
            for (int i = 0; i < slots.size(); i++) {
                // JEI 19's getAreaIncludingBackground mixes slot and background coordinate spaces.
                var rect = ((IRecipeSlotDrawable) slots.get(i)).getRect();
                var a = new net.minecraft.client.renderer.Rect2i(rect.getX() - 1, rect.getY() - 1, 18, 18);
                if (a.getX() < 0 || a.getY() < 0 || a.getX() + a.getWidth() > category.getWidth()
                        || a.getY() + a.getHeight() > category.getHeight()) throw new IllegalStateException("JEI slot outside recipe area");
                for (int j = 0; j < i; j++) {
                    var other = ((IRecipeSlotDrawable) slots.get(j)).getRect();
                    var b = new net.minecraft.client.renderer.Rect2i(other.getX() - 1, other.getY() - 1, 18, 18);
                    if (a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
                            && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight())
                        throw new IllegalStateException("Overlapping JEI slots");
                }
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFFE8E8E8);
            for (int i = 0; i < layouts.size(); i++) {
                var layout = layouts.get(i);
                int x = width * (1 + 2 * (i % 2)) / 4 - layout.getRect().getWidth() / 2;
                int y = 25 + i / 2 * (height / 2);
                graphics.drawString(font, layout.getRecipeCategory().getTitle(), x, y - 12, 0xFF202020, false);
                layout.setPosition(x, y);
                layout.drawRecipe(graphics, -1, -1);
            }
        }
    }
}
