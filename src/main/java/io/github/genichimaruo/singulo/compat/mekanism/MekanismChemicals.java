package io.github.genichimaruo.singulo.compat.mekanism;

import io.github.genichimaruo.singulo.android.work.Chemicals;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.slurry.ISlurryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.registries.IForgeRegistry;

/** Mekanism 10.4 has separate registries and capabilities for its four chemical families. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class MekanismChemicals implements Chemicals.Access {
    private record Family(String prefix, Supplier<IForgeRegistry<?>> registry, Capability<?> capability) {}
    private static final List<Family> FAMILIES = List.of(
            new Family("gas", MekanismAPI::gasRegistry, CapabilityManager.get(new CapabilityToken<IGasHandler>() {})),
            new Family("infusion", MekanismAPI::infuseTypeRegistry, CapabilityManager.get(new CapabilityToken<IInfusionHandler>() {})),
            new Family("pigment", MekanismAPI::pigmentRegistry, CapabilityManager.get(new CapabilityToken<IPigmentHandler>() {})),
            new Family("slurry", MekanismAPI::slurryRegistry, CapabilityManager.get(new CapabilityToken<ISlurryHandler>() {})));
    private record Type(Family family, Chemical chemical) {}
    private record Handler(Family family, IChemicalHandler handler) {}

    @Nullable
    private static Type type(String key) {
        int split = key.indexOf('|');
        String family = split < 0 ? "gas" : key.substring(0, split);
        ResourceLocation id = ResourceLocation.tryParse(split < 0 ? key : key.substring(split + 1));
        if (id == null) return null;
        for (Family f : FAMILIES) {
            if (!f.prefix.equals(family)) continue;
            Object value = f.registry.get().getValue(id);
            if (value instanceof Chemical c && !c.isEmptyType()) return new Type(f, c);
        }
        return null;
    }

    private static String key(Family family, ChemicalStack stack) {
        String id = stack.getTypeRegistryName().toString();
        return family.prefix.equals("gas") ? id : family.prefix + "|" + id;
    }

    @Nullable
    private static Chemicals.Store store(ICapabilityProvider provider, @Nullable Direction side) {
        List<Handler> handlers = new ArrayList<>();
        for (Family f : FAMILIES) {
            IChemicalHandler h = (IChemicalHandler) provider.getCapability((Capability) f.capability, side).orElse(null);
            if (h != null) handlers.add(new Handler(f, h));
        }
        if (handlers.isEmpty()) return null;
        return new Chemicals.Store() {
            public long amount(String id) {
                Type type = type(id);
                if (type == null) return 0;
                long count = 0;
                for (Handler h : handlers) if (h.family == type.family) {
                    for (int i = 0; i < h.handler.getTanks(); i++) {
                        ChemicalStack stack = h.handler.getChemicalInTank(i);
                        if (!stack.isEmpty() && stack.getType() == type.chemical) count += stack.getAmount();
                    }
                }
                return count;
            }
            public long insert(String id, long count, boolean simulate) {
                Type type = type(id);
                if (type == null || count <= 0) return 0;
                long inserted = 0;
                for (Handler h : handlers) if (h.family == type.family && inserted < count) {
                    ChemicalStack rest = h.handler.insertChemical(type.chemical.getStack(count - inserted), simulate ? Action.SIMULATE : Action.EXECUTE);
                    inserted += count - inserted - rest.getAmount();
                }
                return inserted;
            }
            public long extract(String id, long count, boolean simulate) {
                Type type = type(id);
                if (type == null || count <= 0) return 0;
                long extracted = 0;
                for (Handler h : handlers) if (h.family == type.family && extracted < count) {
                    extracted += h.handler.extractChemical(type.chemical.getStack(count - extracted), simulate ? Action.SIMULATE : Action.EXECUTE).getAmount();
                }
                return extracted;
            }
            public Map<String, Long> contents() {
                Map<String, Long> contents = new LinkedHashMap<>();
                for (Handler h : handlers) for (int i = 0; i < h.handler.getTanks(); i++) {
                    ChemicalStack stack = h.handler.getChemicalInTank(i);
                    if (!stack.isEmpty()) contents.merge(key(h.family, stack), stack.getAmount(), Long::sum);
                }
                return contents;
            }
        };
    }

    @Override @Nullable
    public Chemicals.Store store(Level level, BlockPos pos, @Nullable Direction side) {
        var entity = level.getBlockEntity(pos);
        return entity == null ? null : store(entity, side);
    }
    @Override @Nullable
    public Chemicals.Store itemStore(ItemStack stack) { return store(stack, null); }
    @Override @Nullable
    public Map.Entry<String, Long> contained(ItemStack stack) {
        Chemicals.Store store = itemStore(stack);
        return store == null ? null : store.contents().entrySet().stream().findFirst().orElse(null);
    }
    @Override public boolean exists(String id) { return type(id) != null; }
    @Override public Component name(String id) {
        Type type = type(id); return type == null ? Component.literal(id) : type.chemical.getTextComponent();
    }
    @Override public int tint(String id) { Type type = type(id); return type == null ? 0xB0B8C0 : type.chemical.getTint() & 0xFFFFFF; }
    @Override @Nullable public ResourceLocation icon(String id) { Type type = type(id); return type == null ? null : type.chemical.getIcon(); }
    @Override public boolean gaseous(String id) { Type type = type(id); return type == null || type.family.prefix.equals("gas"); }
}
