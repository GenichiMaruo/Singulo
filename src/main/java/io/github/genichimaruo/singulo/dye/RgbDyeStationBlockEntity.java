package io.github.genichimaruo.singulo.dye;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * RGB染色台。赤・緑・青・白・黒の染料をそれぞれの枠に入れるとインクになり（染料1個 = {@link #PER_DYE} 目盛り、各色
 * {@link #MAX_INK} 目盛りまで）、画面の色の輪と明るさで選んだ好きな色に、染められる物（タグ minecraft:dyeable）を染める。
 * <p>
 * 1回に使うインクは、選んだ色を「白・黒・色み」に分けた量で決まる（各色 0〜{@link #MAX_COST} 目盛り）:
 * 白 = 3色の最小、黒 = 1 − 3色の最大、赤・緑・青 = それぞれから白を引いた分。純色なら色みだけ、灰色なら白と黒だけを使う。
 * 壊すと、中の染料と、インクを染料に戻せる分（目盛りの端数は失う）を落とす。
 */
public class RgbDyeStationBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener, AbstractMachineBlock.BreakListener {
    /** 枠: 赤・緑・青・白・黒の染料と、染める物。 */
    public static final int RED = 0, GREEN = 1, BLUE = 2, WHITE = 3, BLACK = 4, TARGET = 5, SLOTS = 6, INKS = 5;
    public static final int PER_DYE = 8, MAX_INK = 64, MAX_COST = 4;
    /** 枠ごとの染料のタグ（NeoForge の共通タグ）。 */
    @SuppressWarnings("unchecked")
    static final TagKey<Item>[] DYE_TAGS = new TagKey[] {Tags.Items.DYES_RED, Tags.Items.DYES_GREEN, Tags.Items.DYES_BLUE,
            Tags.Items.DYES_WHITE, Tags.Items.DYES_BLACK};
    /** 枠ごとの、インクを戻すときの染料。 */
    static final DyeColor[] DYES = {DyeColor.RED, DyeColor.GREEN, DyeColor.BLUE, DyeColor.WHITE, DyeColor.BLACK};

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            RgbDyeStationBlockEntity.this.setChanged();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return accepts(slot, stack);
        }
    };
    private final int[] ink = new int[INKS];
    /** 選んでいる色（RGB）。 */
    private int color = 0xE0607E;

    /** 画面に送る値: インク5色と、選んでいる色の R・G・B（どれも 0〜255 に収まる）。 */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return i < INKS ? ink[i] : (color >> (16 - 8 * (i - INKS))) & 0xFF;
        }

        @Override
        public void set(int i, int value) {
            if (i < INKS) {
                ink[i] = value;
            } else {
                int shift = 16 - 8 * (i - INKS);
                color = (color & ~(0xFF << shift)) | ((value & 0xFF) << shift);
            }
        }

        @Override
        public int getCount() {
            return INKS + 3;
        }
    };

    public RgbDyeStationBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.RGB_DYE_STATION.get(), pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    public ContainerData data() {
        return data;
    }

    public int ink(int i) {
        return ink[i];
    }

    public int color() {
        return color;
    }

    /** 枠に入れられる物か（染料の枠はその色の染料だけ。染める物の枠は染められる物だけ）。 */
    public static boolean accepts(int slot, ItemStack stack) {
        if (slot < INKS) {
            return stack.is(DYE_TAGS[slot]);
        }
        return slot == TARGET && stack.is(ItemTags.DYEABLE);
    }

    /** 色 rgb に染めるのに使うインク（赤・緑・青・白・黒の目盛り）。 */
    public static int[] cost(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
        float white = Math.min(r, Math.min(g, b)), black = 1 - Math.max(r, Math.max(g, b));
        float[] parts = {r - white, g - white, b - white, white, black};
        int[] out = new int[INKS];
        for (int i = 0; i < INKS; i++) {
            out[i] = parts[i] < 0.02F ? 0 : (int) Math.ceil(parts[i] * MAX_COST - 0.01F);
        }
        return out;
    }

    /** 今の色で染められるか（染める物があって、インクが足りる）。 */
    public boolean canApply() {
        ItemStack target = items.getItem(TARGET);
        if (target.isEmpty() || !target.is(ItemTags.DYEABLE)) {
            return false;
        }
        int[] need = cost(color);
        for (int i = 0; i < INKS; i++) {
            if (ink[i] < need[i]) {
                return false;
            }
        }
        return true;
    }

    /** 選んでいる色に染める（インクを使う）。染めたら true。 */
    public boolean apply() {
        if (!canApply()) {
            return false;
        }
        int[] need = cost(color);
        for (int i = 0; i < INKS; i++) {
            ink[i] -= need[i];
        }
        ItemStack target = items.getItem(TARGET).copy();
        target.set(DataComponents.DYED_COLOR, new DyedItemColor(color, true));
        items.setItem(TARGET, target);
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1, 1);
        }
        setChanged();
        return true;
    }

    public void setColor(int rgb) {
        color = rgb & 0xFFFFFF;
        setChanged();
    }

    /** 染料の枠の染料を、インクに入る分だけインクにする（毎 tick）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RgbDyeStationBlockEntity be) {
        for (int i = 0; i < INKS; i++) {
            ItemStack dye = be.items.getItem(i);
            if (!dye.isEmpty() && be.ink[i] + PER_DYE <= MAX_INK) {
                dye.shrink(1);
                be.ink[i] += PER_DYE;
                be.items.setChanged();
            }
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RgbDyeStationMenu(id, inv, worldPosition, items, data),
                Component.translatable(getBlockState().getBlock().getDescriptionId())), buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public void onBroken(Level level) {
        for (int i = 0; i < SLOTS; i++) {
            Block.popResource(level, worldPosition, items.getItem(i));
        }
        // インクは染料に戻せる分だけ戻す（目盛りの端数は失う）
        for (int i = 0; i < INKS; i++) {
            int dyes = ink[i] / PER_DYE;
            if (dyes > 0) {
                Block.popResource(level, worldPosition, new ItemStack(net.minecraft.world.item.DyeItem.byColor(DYES[i]), dyes));
            }
        }
    }

    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) < 8 * 8;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) {
            list.set(i, items.getItem(i));
        }
        tag.put("items", ContainerHelper.saveAllItems(new CompoundTag(), list, registries));
        tag.putIntArray("ink", ink);
        tag.putInt("color", color);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("items"), list, registries);
        for (int i = 0; i < SLOTS; i++) {
            items.setItem(i, list.get(i));
        }
        int[] saved = tag.getIntArray("ink");
        for (int i = 0; i < INKS; i++) {
            ink[i] = i < saved.length ? Math.max(0, Math.min(MAX_INK, saved[i])) : 0;
        }
        if (tag.contains("color")) {
            color = tag.getInt("color") & 0xFFFFFF;
        }
    }
}
