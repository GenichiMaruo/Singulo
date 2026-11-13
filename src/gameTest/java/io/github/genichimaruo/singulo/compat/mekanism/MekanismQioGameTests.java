package io.github.genichimaruo.singulo.compat.mekanism;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ObjLongConsumer;
import mekanism.api.Action;
import mekanism.api.inventory.qio.IQIOFrequency;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo_mekanism")
@PrefixGameTestTemplate(false)
public final class MekanismQioGameTests {
    private MekanismQioGameTests() {}

    @SuppressWarnings("unchecked")
    private static Object frequency(AtomicLong count) {
        return (IQIOFrequency) Proxy.newProxyInstance(IQIOFrequency.class.getClassLoader(),
                new Class<?>[] { IQIOFrequency.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "test QIO frequency";
                        case "forAllStored" -> {
                            if (count.get() > 0) ((ObjLongConsumer<ItemStack>) args[0]).accept(new ItemStack(Items.STONE), count.get());
                            yield null;
                        }
                        case "massInsert" -> {
                            long amount = (long) args[1];
                            if (args[2] == Action.EXECUTE) count.addAndGet(amount);
                            yield amount;
                        }
                        case "massExtract" -> {
                            long amount = Math.min(count.get(), (long) args[1]);
                            if (args[2] == Action.EXECUTE) count.addAndGet(-amount);
                            yield amount;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }

    @GameTest(template = "empty")
    public static void firstInsertionSnapshot(GameTestHelper helper) {
        AtomicLong count = new AtomicLong(10);
        var handler = MekanismQio.handler(helper.getLevel(), (IQIOFrequency) frequency(count));
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.STONE, 5), false).isEmpty(), "Insertion failed");
        helper.assertTrue(count.get() == 15 && handler.getStackInSlot(0).getCount() == 15,
                "First insertion must not double-count the updated frequency");
        helper.runAfterDelay(2, () -> {
            handler.insertItem(0, new ItemStack(Items.STONE, 3), false);
            helper.assertTrue(count.get() == 18 && handler.getStackInSlot(0).getCount() == 18,
                    "First insertion of a later tick must not double-count");
            handler.insertItem(0, new ItemStack(Items.STONE, 4), true);
            helper.assertTrue(count.get() == 18 && handler.getStackInSlot(0).getCount() == 18,
                    "Simulation must not change the snapshot");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void weakHandlerCache(GameTestHelper helper) throws ReflectiveOperationException {
        var frequency = (IQIOFrequency) frequency(new AtomicLong(1));
        var first = MekanismQio.handler(helper.getLevel(), frequency);
        helper.assertTrue(first == MekanismQio.handler(helper.getLevel(), frequency), "Live handlers must retain identity");
        var field = MekanismQio.class.getDeclaredField("HANDLERS");
        field.setAccessible(true);
        Object value = ((java.util.Map<?, ?>) field.get(null)).get(frequency);
        helper.assertTrue(value instanceof java.lang.ref.WeakReference<?>, "Cache must not strongly retain a world through its handler");
        ((java.lang.ref.WeakReference<?>) value).clear();
        helper.assertTrue(first != MekanismQio.handler(helper.getLevel(), frequency), "Cleared weak values must be recreated");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void dashboardSecurity(GameTestHelper helper) {
        var position = new net.minecraft.core.BlockPos(1, 1, 1);
        var absolute = helper.absolutePos(position);
        helper.assertTrue(!MekanismQio.publicAccess(helper.getLevel(), absolute),
                "Missing security capability must fail closed");
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                new net.minecraft.resources.ResourceLocation("mekanism:qio_dashboard"));
        helper.setBlock(position, block);
        var security = MekanismQio.securityCapability(helper.getLevel(), absolute);
        helper.assertTrue(security != null, "Dashboard must expose official security capability");
        // GameTestServer has no profile cache: test block modes without an owner override.
        security.setSecurityMode(mekanism.api.security.SecurityMode.PUBLIC);
        helper.assertTrue(MekanismQio.publicAccess(helper.getLevel(), absolute), "Public dashboard should be usable");
        security.setSecurityMode(mekanism.api.security.SecurityMode.PRIVATE);
        helper.assertTrue(!MekanismQio.publicAccess(helper.getLevel(), absolute), "Private dashboard must reject anonymous automation");
        security.setSecurityMode(mekanism.api.security.SecurityMode.TRUSTED);
        helper.assertTrue(!MekanismQio.publicAccess(helper.getLevel(), absolute), "Trusted dashboard must reject anonymous automation");
        helper.succeed();
    }
}