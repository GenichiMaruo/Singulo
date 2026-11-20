package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidStationBlockEntity;
import io.github.genichimaruo.singulo.android.PortableAndroidPrinterItem;
import io.github.genichimaruo.singulo.android.PortablePrinterEntity;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

/**
 * 携帯型アンドロイド印刷機（0.21.0）: メモリ・キット・電力がそろえば地面に置いて印刷し、キット1つと組立ステーションの2倍の電力を使う。
 * 印刷したアンドロイドは使った人の護衛で、ステーションのない（身寄りのない）追従モード。自己修理キットのストックを持たせる。
 * 終わると印刷機は持ち物へ戻る。
 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class PortablePrinterGameTests {
    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Singulo.id(id)), count);
    }

    @GameTest(template = "empty", timeoutTicks = PortablePrinterEntity.DONE + 60)
    public static void portablePrinterPrintsAFollowingOrphan(GameTestHelper h) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        ItemStack printer = new ItemStack(SinguloItems.PORTABLE_ANDROID_PRINTER.get());
        PortableAndroidPrinterItem.energy(printer).receiveEnergy(PortableAndroidPrinterItem.CAPACITY, false);
        // 容量は、入れたメモリの型の1体分（倍率3の型なら3倍）
        java.util.List<ItemStack> heavy = java.util.List.of(item("android_memory_gunner", 1), item("android_kit", 1));
        ItemStack probe = new ItemStack(SinguloItems.PORTABLE_ANDROID_PRINTER.get());
        PortableAndroidPrinterItem.setContents(probe, heavy);
        h.assertTrue(PortableAndroidPrinterItem.capacity(probe) == 3 * PortableAndroidPrinterItem.CAPACITY,
                "The capacity does not follow the memory: " + PortableAndroidPrinterItem.capacity(probe));
        PortableAndroidPrinterItem.energy(probe).receiveEnergy(Integer.MAX_VALUE, false);
        h.assertTrue(PortableAndroidPrinterItem.problem(probe) == null, "Mk1 cannot print a costly type");
        // メモリがなければ印刷できない
        h.assertTrue("gui.singulo.portable_printer.no_memory".equals(PortableAndroidPrinterItem.problem(printer)), "Prints without a memory");
        PortableAndroidPrinterItem.setContents(printer, List.of(item("android_memory_blade", 1), item("android_kit", 2),
                item("android_repair_kit", 10), item("android_armor_plate", 1)));
        h.assertTrue(PortableAndroidPrinterItem.problem(printer) == null, "Not ready: " + PortableAndroidPrinterItem.problem(printer));
        player.setItemInHand(InteractionHand.MAIN_HAND, printer);
        h.setBlock(new BlockPos(3, 0, 3), net.minecraft.world.level.block.Blocks.STONE);   // 置く地面
        BlockPos at = h.absolutePos(new BlockPos(3, 1, 3));
        BlockPos stand = h.absolutePos(new BlockPos(0, 1, 0));
        player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        h.assertTrue(PortableAndroidPrinterItem.deploy(player, InteractionHand.MAIN_HAND, at), "Could not set the printer down at " + at
                + ": floor " + h.getLevel().getBlockState(at.below()) + ", entities "
                + h.getLevel().getEntities(null, new AABB(at).expandTowards(0, 1, 0)));
        h.assertTrue(player.getMainHandItem().isEmpty(), "The printer stayed in the hand");
        AABB area = new AABB(at).inflate(3);
        h.assertTrue(h.getLevel().getEntitiesOfClass(PortablePrinterEntity.class, area).size() == 1, "No briefcase on the ground");
        h.runAfterDelay(PortablePrinterEntity.DONE + 5, () -> {
            List<RuinAndroid> androids = h.getLevel().getEntitiesOfClass(RuinAndroid.class, area.inflate(4));
            h.assertTrue(androids.size() == 1, "Expected one printed android, found " + androids.size());
            RuinAndroid a = androids.get(0);
            h.assertTrue(a.isGuard() && a.orphan() && player.getUUID().equals(a.owner()), "The android is not an orphan guard of the user");
            h.assertTrue(a.mode() == RuinAndroid.MODE_FOLLOW && a.printed(), "The android does not follow: mode " + a.mode());
            h.assertTrue(h.getLevel().getEntitiesOfClass(PortablePrinterEntity.class, area).isEmpty(), "The briefcase did not close");
            ItemStack back = ItemStack.EMPTY;
            for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
                if (s.getItem() instanceof PortableAndroidPrinterItem) {
                    back = s;
                }
            }
            h.assertTrue(!back.isEmpty(), "The printer did not come back to the inventory");
            h.assertTrue(PortableAndroidPrinterItem.kits(back) == 1, "One kit should be used: " + PortableAndroidPrinterItem.kits(back));
            h.assertTrue(PortableAndroidPrinterItem.energy(back).getEnergyStored()
                    == PortableAndroidPrinterItem.CAPACITY - AndroidStationBlockEntity.PRINT_FE * PortableAndroidPrinterItem.COST_FACTOR,
                    "The print did not use twice the station's power");
            h.assertTrue(PortableAndroidPrinterItem.kind(back) != null, "The memory was used up");
            // 自己修理キットのストックは、印刷したアンドロイドに持たせる
            h.assertTrue(a.inventory().getItem(RuinAndroid.SLOT_REPAIR).getCount() == 10 && PortableAndroidPrinterItem.repairKits(back) == 0,
                    "The self-repair kits were not handed over: " + a.inventory().getItem(RuinAndroid.SLOT_REPAIR));
            // 装甲板は積む（胸当てとして装備される）
            h.assertTrue(RuinAndroid.isArmorPlate(a.inventory().getItem(RuinAndroid.SLOT_PLATE))
                    && PortableAndroidPrinterItem.contents(back).get(PortableAndroidPrinterItem.SLOT_PLATE).isEmpty(), "The armor plate was not fitted");
            a.discard();
            h.succeed();
        });
    }

    /** Mk2: 3組を積み、選んだ組（そろっていれば）で印刷し、次の組へ進む。 */
    @GameTest(template = "empty", timeoutTicks = PortablePrinterEntity.DONE + 60)
    public static void portablePrinterMk2PrintsTheChosenSet(GameTestHelper h) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        ItemStack printer = new ItemStack(SinguloItems.PORTABLE_ANDROID_PRINTER_2.get());
        h.assertTrue(PortableAndroidPrinterItem.units(printer) == 3
                && PortableAndroidPrinterItem.capacity(printer) == 3 * PortableAndroidPrinterItem.CAPACITY, "Mk2 does not hold three sets");
        PortableAndroidPrinterItem.energy(printer).receiveEnergy(PortableAndroidPrinterItem.capacity(printer), false);
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            items.add(ItemStack.EMPTY);
        }
        items.set(PortableAndroidPrinterItem.slot(0, PortableAndroidPrinterItem.SLOT_MEMORY), item("android_memory_blade", 1));
        items.set(PortableAndroidPrinterItem.slot(0, PortableAndroidPrinterItem.SLOT_KIT), item("android_kit", 1));
        items.set(PortableAndroidPrinterItem.slot(1, PortableAndroidPrinterItem.SLOT_MEMORY), item("android_memory_whip", 1));
        items.set(PortableAndroidPrinterItem.slot(1, PortableAndroidPrinterItem.SLOT_KIT), item("android_kit", 2));
        PortableAndroidPrinterItem.setContents(printer, items);
        printer.set(io.github.genichimaruo.singulo.registry.SinguloComponents.PRINTER_NEXT.get(), 1);   // 2つ目の組を選ぶ
        h.assertTrue(PortableAndroidPrinterItem.readyUnit(printer) == 1, "The chosen set is not printed next");
        h.setBlock(new BlockPos(3, 0, 3), net.minecraft.world.level.block.Blocks.STONE);
        BlockPos stand = h.absolutePos(new BlockPos(0, 1, 0));
        player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, printer);
        BlockPos at = h.absolutePos(new BlockPos(3, 1, 3));
        h.assertTrue(PortableAndroidPrinterItem.deploy(player, InteractionHand.MAIN_HAND, at), "Could not set the Mk2 down");
        AABB area = new AABB(at).inflate(7);
        h.runAfterDelay(PortablePrinterEntity.DONE + 5, () -> {
            List<RuinAndroid> androids = h.getLevel().getEntitiesOfClass(RuinAndroid.class, area);
            h.assertTrue(androids.size() == 1 && androids.get(0).getType() == io.github.genichimaruo.singulo.android.AndroidKind.WHIP.type(),
                    "The chosen whip set was not printed: " + androids);
            ItemStack back = ItemStack.EMPTY;
            for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
                if (s.getItem() instanceof PortableAndroidPrinterItem) {
                    back = s;
                }
            }
            h.assertTrue(PortableAndroidPrinterItem.kits(back, 1) == 1 && PortableAndroidPrinterItem.kits(back, 0) == 1,
                    "The wrong set used a kit");
            // 3つ目の組は空なので、次は1つ目の組（剣の型）に回る
            h.assertTrue(PortableAndroidPrinterItem.readyUnit(back) == 0, "The next set did not move on: " + PortableAndroidPrinterItem.readyUnit(back));
            androids.forEach(net.minecraft.world.entity.Entity::discard);
            h.succeed();
        });
    }
}
