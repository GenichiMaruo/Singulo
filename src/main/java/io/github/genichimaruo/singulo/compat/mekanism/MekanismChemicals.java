package io.github.genichimaruo.singulo.compat.mekanism;

import io.github.genichimaruo.singulo.android.work.Chemicals;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;

/**
 * Mekanism の化学物質の受け持ち（Mekanism が入っているときだけ、{@link Chemicals} が読み込む）。
 * 化学物質の入れ口は、Mekanism と同じ名前（{@code mekanism:chemical_handler}）の能力で取る（同じ名前・型なら同じ能力になる）。
 */
public final class MekanismChemicals implements Chemicals.Access {
    private static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler");
    private static final BlockCapability<IChemicalHandler, Direction> BLOCK = BlockCapability.createSided(NAME, IChemicalHandler.class);
    private static final ItemCapability<IChemicalHandler, Void> ITEM = ItemCapability.createVoid(NAME, IChemicalHandler.class);

    public MekanismChemicals() {}

    @Nullable
    private static Chemical chemical(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return null;
        }
        Chemical c = MekanismAPI.CHEMICAL_REGISTRY.get(rl);
        return c == null || MekanismAPI.EMPTY_CHEMICAL_KEY.location().equals(rl) ? null : c;
    }

    private static String id(Chemical c) {
        ResourceLocation rl = MekanismAPI.CHEMICAL_REGISTRY.getKey(c);
        return rl == null ? "" : rl.toString();
    }

    private static Chemicals.Store wrap(IChemicalHandler h) {
        return new Chemicals.Store() {
            @Override
            public long amount(String id) {
                Chemical c = chemical(id);
                long n = 0;
                for (int t = 0; c != null && t < h.getChemicalTanks(); t++) {
                    ChemicalStack s = h.getChemicalInTank(t);
                    if (!s.isEmpty() && s.getChemical() == c) {
                        n += s.getAmount();
                    }
                }
                return n;
            }

            @Override
            public long insert(String id, long amount, boolean simulate) {
                Chemical c = chemical(id);
                if (c == null || amount <= 0) {
                    return 0;
                }
                ChemicalStack rest = h.insertChemical(new ChemicalStack(c, amount), simulate ? Action.SIMULATE : Action.EXECUTE);
                return amount - rest.getAmount();
            }

            @Override
            public long extract(String id, long amount, boolean simulate) {
                Chemical c = chemical(id);
                if (c == null || amount <= 0) {
                    return 0;
                }
                return h.extractChemical(new ChemicalStack(c, amount), simulate ? Action.SIMULATE : Action.EXECUTE).getAmount();
            }

            @Override
            public Map<String, Long> contents() {
                Map<String, Long> out = new LinkedHashMap<>();
                for (int t = 0; t < h.getChemicalTanks(); t++) {
                    ChemicalStack s = h.getChemicalInTank(t);
                    if (!s.isEmpty()) {
                        out.merge(id(s.getChemical()), s.getAmount(), Long::sum);
                    }
                }
                return out;
            }
        };
    }

    @Override
    @Nullable
    public Chemicals.Store store(Level level, BlockPos pos, @Nullable Direction side) {
        IChemicalHandler h = level.getCapability(BLOCK, pos, side);
        return h == null ? null : wrap(h);
    }

    @Override
    @Nullable
    public Map.Entry<String, Long> contained(ItemStack stack) {
        IChemicalHandler h = stack.getCapability(ITEM);
        if (h == null) {
            return null;
        }
        for (int t = 0; t < h.getChemicalTanks(); t++) {
            ChemicalStack s = h.getChemicalInTank(t);
            if (!s.isEmpty()) {
                return Map.entry(id(s.getChemical()), s.getAmount());
            }
        }
        return null;
    }

    @Override
    @Nullable
    public Chemicals.Store itemStore(ItemStack stack) {
        IChemicalHandler h = stack.getCapability(ITEM);
        return h == null ? null : wrap(h);
    }

    @Override
    public boolean exists(String id) {
        return chemical(id) != null;
    }

    @Override
    public Component name(String id) {
        Chemical c = chemical(id);
        return c == null ? Component.literal(id) : c.getTextComponent();
    }

    @Override
    public int tint(String id) {
        Chemical c = chemical(id);
        return c == null ? 0xB0B8C0 : c.getTint() & 0xFFFFFF;
    }

    @Override
    @Nullable
    public ResourceLocation icon(String id) {
        Chemical c = chemical(id);
        return c == null ? null : c.getIcon();
    }

    @Override
    public boolean gaseous(String id) {
        Chemical c = chemical(id);
        return c == null || c.isGaseous();
    }
}
