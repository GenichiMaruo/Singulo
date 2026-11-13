package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.food.FoodPaste;
import io.github.genichimaruo.singulo.food.FoodPrinterBlockEntity;
import io.github.genichimaruo.singulo.food.PrintedFood;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class FoodPrinterGameTests {
    private static final BlockPos AT = new BlockPos(2, 1, 2);

    private static ItemStack cartridge(int kind, int amount) {
        ItemStack c = new ItemStack(SinguloItems.FOOD_CARTRIDGE.get());
        FoodPaste.set(c, kind, amount);
        return c;
    }

    @GameTest(template = "empty")
    public static void foodCartridgeRejectsMalformedContent(GameTestHelper h) {
        var component = io.github.genichimaruo.singulo.registry.SinguloComponents.FOOD_PASTE.get();
        for (int[] invalid : new int[][] {{-1, 1}, {FoodPaste.KINDS, 1}, {Integer.MAX_VALUE, 1},
                {0, -1}, {0, 0}, {0, FoodPaste.CAPACITY + 1}, {0, Integer.MAX_VALUE}}) {
            ItemStack stack = new ItemStack(SinguloItems.FOOD_CARTRIDGE.get());
            CompoundTag malformed = new CompoundTag();
            malformed.putInt("kind", invalid[0]);
            malformed.putInt("amount", invalid[1]);
            stack.getOrCreateTag().put(component.name(), malformed);
            stack = ItemStack.of(stack.save(new CompoundTag()));
            h.assertTrue(FoodPaste.kind(stack) == -1 && FoodPaste.amount(stack) == 0, "Malformed paste is not empty");
            h.assertFalse(stack.getItem().isBarVisible(stack), "Malformed paste shows a bar");
            h.assertTrue(stack.getItem().getBarWidth(stack) == 0 && stack.getItem().getBarColor(stack) == 0xFFFFFF,
                    "Malformed paste has unsafe bar values");
            stack.getHoverName();
        }
        ItemStack stack = new ItemStack(SinguloItems.FOOD_CARTRIDGE.get());
        io.github.genichimaruo.singulo.registry.SinguloComponents.set(stack, component,
                new FoodPaste.Content(FoodPaste.PROTEIN, Integer.MAX_VALUE));
        h.assertTrue(FoodPaste.kind(stack) == -1 && FoodPaste.amount(stack) == 0 && stack.getItem().getBarWidth(stack) == 0,
                "Oversized paste was not rejected");
        FoodPaste.set(stack, FoodPaste.KINDS, 1);
        h.assertTrue(FoodPaste.kind(stack) == -1 && FoodPaste.amount(stack) == 0, "Setter retained invalid kind");
        h.succeed();
    }

    /**
     * 食材は種類ごとのカートリッジにペーストとして入る（満腹度の分。小麦は2）。カートリッジがない・入りきらない食材は待つ。
     * 容器が残る物・毒のある物・印刷した食品は食材にならない。満タンのカートリッジに別の種類は入らない。
     * 電源を切っても食材はペーストにするが、印刷は始めない（自動でも）。
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void foodPrinterTurnsIngredientsIntoPaste(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.FOOD_PRINTER.get());
        FoodPrinterBlockEntity printer = (FoodPrinterBlockEntity) h.getBlockEntity(AT);
        var items = printer.items();
        printer.togglePower();
        h.assertTrue(FoodPaste.kindOf(new ItemStack(Items.COOKED_BEEF)) == FoodPaste.PROTEIN, "Beef is not protein");
        h.assertTrue(FoodPaste.kindOf(new ItemStack(Items.WHEAT)) == FoodPaste.CARB && FoodPaste.unitsOf(new ItemStack(Items.WHEAT)) == 2,
                "Wheat is not 2 carbohydrate");
        h.assertTrue(FoodPaste.kindOf(new ItemStack(Items.SWEET_BERRIES)) == FoodPaste.SWEET, "Berries are not sweet");
        for (ItemStack bad : new ItemStack[] {new ItemStack(Items.HONEY_BOTTLE), new ItemStack(Items.ROTTEN_FLESH),
                new ItemStack(Items.MUSHROOM_STEW), new ItemStack(PrintedFood.NUTRIENT_BAR.item()), new ItemStack(Items.STONE)}) {
            for (int k = 0; k < FoodPaste.KINDS; k++) {
                h.assertFalse(FoodPrinterBlockEntity.accepts(FoodPrinterBlockEntity.INGREDIENT + k, bad), bad + " accepted as an ingredient");
            }
        }
        h.assertFalse(FoodPrinterBlockEntity.accepts(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.CARB, new ItemStack(Items.COOKED_BEEF)),
                "Beef accepted in the carbohydrate ingredient slot");
        h.assertFalse(FoodPrinterBlockEntity.accepts(FoodPrinterBlockEntity.CARTRIDGE + FoodPaste.CARB, cartridge(FoodPaste.PROTEIN, 8)),
                "Protein cartridge accepted in the carbohydrate slot");
        items.setStackInSlot(FoodPrinterBlockEntity.CARTRIDGE + FoodPaste.PROTEIN, cartridge(FoodPaste.PROTEIN, 50));
        items.setStackInSlot(FoodPrinterBlockEntity.CARTRIDGE + FoodPaste.CARB, new ItemStack(SinguloItems.FOOD_CARTRIDGE.get()));
        items.setStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.PROTEIN, new ItemStack(Items.COOKED_BEEF, 3));
        h.runAtTickTime(30, () -> {
            // 50 + 8 = 58 までは入り、もう1個（66）は入らずに待つ
            h.assertTrue(printer.paste(FoodPaste.PROTEIN) == 58, "Protein paste: " + printer.paste(FoodPaste.PROTEIN));
            h.assertTrue(items.getStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.PROTEIN).getCount() == 2, "Overflowing beef was consumed");
            h.assertTrue(printer.printing() < 0 && printer.state() == FoodPrinterBlockEntity.S_OFF, "Printing started while switched off");
            items.setStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.CARB, new ItemStack(Items.WHEAT, 3));
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(printer.paste(FoodPaste.CARB) == 6 && items.getStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.CARB).isEmpty(),
                    "Wheat did not become 6 carbohydrate: " + printer.paste(FoodPaste.CARB));
            h.assertTrue(FoodPaste.kind(items.getStackInSlot(FoodPrinterBlockEntity.CARTRIDGE + FoodPaste.CARB)) == FoodPaste.CARB,
                    "Empty cartridge did not take the carbohydrate kind");
            // 甘味のカートリッジがないので、果物は待つ
            items.setStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.SWEET, new ItemStack(Items.APPLE));
        });
        h.runAtTickTime(90, () -> {
            h.assertTrue(items.getStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.SWEET).getCount() == 1, "Apple vanished without a cartridge");
            h.succeed();
        });
    }

    /**
     * 選んだ食品を、始めにペーストを払って印刷し、印刷の間だけ電力を使う（時間 × 20 FE ちょうど）。ペーストが尽きたら止まる。
     * 電力が切れると止まって待ち、保存し直しても進みを失わず、電力が戻ると続きから仕上げる（ペーストを二重に払わない）。
     */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void foodPrinterPrintsWithPasteAndPower(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.FOOD_PRINTER.get());
        FoodPrinterBlockEntity printer = (FoodPrinterBlockEntity) h.getBlockEntity(AT);
        var items = printer.items();
        PrintedFood steak = PrintedFood.PRINTED_STEAK;
        items.setStackInSlot(FoodPrinterBlockEntity.CARTRIDGE + FoodPaste.PROTEIN, cartridge(FoodPaste.PROTEIN, 2 * steak.cost(FoodPaste.PROTEIN)));
        // 1個目の途中で電力が切れる量
        int half = steak.energy() / 2;
        printer.energy().setEnergy(half);
        printer.select(steak.ordinal());
        h.runAtTickTime(steak.ticks() + 20, () -> {
            h.assertTrue(printer.printing() == steak.ordinal() && printer.progress() == steak.ticks() / 2,
                    "Printing did not pause without power: " + printer.printing() + " / " + printer.progress());
            h.assertTrue(printer.paste(FoodPaste.PROTEIN) == steak.cost(FoodPaste.PROTEIN), "Paste not paid once at the start");
            // 保存し直す
            CompoundTag tag = printer.saveWithFullMetadata();
            printer.load(tag);
            h.assertTrue(printer.printing() == steak.ordinal() && printer.progress() == steak.ticks() / 2, "Reload lost the progress");
            printer.energy().setEnergy(FoodPrinterBlockEntity.ENERGY_CAPACITY);
        });
        // 再開（steak.ticks() + 20）から、残りの半分と2個目まで
        h.runAtTickTime(3 * steak.ticks() + 80, () -> {
            ItemStack out = items.getStackInSlot(FoodPrinterBlockEntity.OUTPUT);
            h.assertTrue(out.is(steak.item()) && out.getCount() == 2, "Expected 2 steaks, got " + out);
            h.assertTrue(printer.paste(FoodPaste.PROTEIN) == 0 && printer.printing() < 0, "Printer kept going without paste");
            int used = half + FoodPrinterBlockEntity.ENERGY_CAPACITY - printer.energy().getEnergyStored();
            h.assertTrue(used == 2 * steak.energy(), "Energy used " + used + " instead of " + 2 * steak.energy());
            h.assertTrue(printer.selected() == steak.ordinal(), "Selection was cleared");
            printer.select(FoodPrinterBlockEntity.AUTO);
            h.assertTrue(printer.selected() == FoodPrinterBlockEntity.AUTO, "Could not go back to automatic");
            h.succeed();
        });
    }

    /**
     * 種類ごとの食材の枠に食材をまとめて入れておくと、選んだ食品に足りないペーストから補い（ほかに少ない種類があっても）、
     * あとは少ない種類から、入る分だけ補う。入りきらない食材は枠に残る。
     * 自動では、出口にある食品を優先して、ペーストが足りるものを印刷する。
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void foodPrinterRefillsWhatIsShort(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.FOOD_PRINTER.get());
        FoodPrinterBlockEntity printer = (FoodPrinterBlockEntity) h.getBlockEntity(AT);
        var items = printer.items();
        printer.togglePower();
        PrintedFood cake = PrintedFood.HONEYCOMB_CAKE;
        printer.select(cake.ordinal());
        // タンパク質は空で一番少ないが、ケーキに足りないのは甘味（2 < 4）
        items.setStackInSlot(FoodPrinterBlockEntity.CARTRIDGE + FoodPaste.PROTEIN, new ItemStack(SinguloItems.FOOD_CARTRIDGE.get()));
        items.setStackInSlot(FoodPrinterBlockEntity.CARTRIDGE + FoodPaste.CARB, cartridge(FoodPaste.CARB, 60));
        items.setStackInSlot(FoodPrinterBlockEntity.CARTRIDGE + FoodPaste.SWEET, cartridge(FoodPaste.SWEET, 2));
        items.setStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.PROTEIN, new ItemStack(Items.COOKED_BEEF, 10));
        items.setStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.CARB, new ItemStack(Items.WHEAT, 5));
        items.setStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.SWEET, new ItemStack(Items.APPLE, 5));
        boolean[] first = {false};
        h.onEachTick(() -> {
            if (!first[0] && (printer.paste(FoodPaste.SWEET) != 2 || printer.paste(FoodPaste.PROTEIN) != 0)) {
                first[0] = true;
                h.assertTrue(printer.paste(FoodPaste.SWEET) == 6 && printer.paste(FoodPaste.PROTEIN) == 0,
                        "Short sweet paste was not filled first: protein " + printer.paste(FoodPaste.PROTEIN) + ", sweet " + printer.paste(FoodPaste.SWEET));
            }
        });
        h.runAtTickTime(120, () -> {
            // タンパク質 8×8 = 64（牛肉2個が残る）、炭水化物 60 + 2×2 = 64（小麦3個が残る）、甘味 2 + 4×5 = 22
            h.assertTrue(printer.paste(FoodPaste.PROTEIN) == 64 && printer.paste(FoodPaste.CARB) == 64 && printer.paste(FoodPaste.SWEET) == 22,
                    "Paste not topped up: " + printer.paste(FoodPaste.PROTEIN) + " / " + printer.paste(FoodPaste.CARB) + " / " + printer.paste(FoodPaste.SWEET));
            h.assertTrue(items.getStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.PROTEIN).getCount() == 2
                    && items.getStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.CARB).getCount() == 3
                    && items.getStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.SWEET).isEmpty(), "Ingredients that did not fit were not kept");
            // 自動: 並びの先頭（栄養バー）も作れるが、出口にあるゼリーを続ける
            items.setStackInSlot(FoodPrinterBlockEntity.OUTPUT, new ItemStack(PrintedFood.FRUIT_GEL_CUBE.item()));
            printer.select(FoodPrinterBlockEntity.AUTO);
            printer.togglePower();
        });
        h.runAtTickTime(130, () -> {
            h.assertTrue(printer.printing() == PrintedFood.FRUIT_GEL_CUBE.ordinal(), "Automatic did not continue the food in the output: " + printer.printing());
            h.assertTrue(printer.state() == FoodPrinterBlockEntity.S_NO_POWER, "Printing without power was not reported: " + printer.state());
            h.succeed();
        });
    }

    /** ホッパーなどは食材とカートリッジを入れ、印刷した食品だけを取り出す。壊すと中身を落とし、カートリッジはペーストを持ったまま。 */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void foodPrinterAutomationAndBreakingKeepPaste(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.FOOD_PRINTER.get());
        FoodPrinterBlockEntity printer = (FoodPrinterBlockEntity) h.getBlockEntity(AT);
        var automation = printer.automation();
        h.assertTrue(automation.insertItem(FoodPrinterBlockEntity.CARTRIDGE + FoodPaste.SWEET, cartridge(FoodPaste.SWEET, 20), false).isEmpty(),
                "Automation could not insert a cartridge");
        h.assertFalse(automation.insertItem(FoodPrinterBlockEntity.OUTPUT, new ItemStack(PrintedFood.FRUIT_GEL_CUBE.item()), false).isEmpty(),
                "Automation inserted into the output");
        h.assertTrue(automation.extractItem(FoodPrinterBlockEntity.CARTRIDGE + FoodPaste.SWEET, 1, true).isEmpty(),
                "Automation took a cartridge");
        printer.items().setStackInSlot(FoodPrinterBlockEntity.OUTPUT, new ItemStack(PrintedFood.FRUIT_GEL_CUBE.item(), 3));
        // 隣の作業指令台は、取り出せる印刷した食品だけを在庫に数える（取り出せないカートリッジ・食材は数えない）
        printer.items().setStackInSlot(FoodPrinterBlockEntity.INGREDIENT + FoodPaste.SWEET, new ItemStack(Items.APPLE, 5));
        h.setBlock(AT.west(), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity console = (io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity) h.getBlockEntity(AT.west());
        var stock = console.stock();
        h.assertTrue(stock.size() == 1 && stock.get(0).item.is(PrintedFood.FRUIT_GEL_CUBE.item()) && stock.get(0).count == 3,
                "Work console counted items it cannot take out: " + stock.stream().map(c -> c.item + " x" + c.count).toList());
        h.assertTrue(automation.extractItem(FoodPrinterBlockEntity.OUTPUT, 3, false).getCount() == 3, "Automation could not take food");
        h.getLevel().destroyBlock(h.absolutePos(AT), false);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(AT)).inflate(2));
        boolean kept = drops.stream().anyMatch(e -> e.getItem().is(SinguloItems.FOOD_CARTRIDGE.get())
                && FoodPaste.kind(e.getItem()) == FoodPaste.SWEET && FoodPaste.amount(e.getItem()) == 20);
        drops.forEach(net.minecraft.world.entity.Entity::discard);
        h.assertTrue(kept, "Cartridge lost its paste when the printer broke");
        h.succeed();
    }
}
