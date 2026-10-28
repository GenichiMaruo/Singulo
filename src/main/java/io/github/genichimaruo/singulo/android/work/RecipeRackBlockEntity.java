package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * アンドロイドレシピ棚。6行あり、各行の左に作業位置カード1枚、右にレシピカード8枚を置く。
 * 左のカードの場所（作業台など）で、その行のレシピを作る。作業指令台につなぐと、並べたレシピを注文できる。
 */
public class RecipeRackBlockEntity extends WorkLinkedBlockEntity {
    public static final int ROWS = 6, COLS = 9, SLOTS = ROWS * COLS;
    private final ItemStack[] decodedCards = new ItemStack[SLOTS];
    private final RecipeData[] decodedRecipes = new RecipeData[SLOTS];

    /** Decode unchanged card NBT once. Compare components too, so in-place card edits are observed. */
    private RecipeData recipe(int slot, HolderLookup.Provider registries) {
        ItemStack card = items.getStackInSlot(slot);
        if (decodedCards[slot] == null || !ItemStack.isSameItemSameComponents(decodedCards[slot], card)) {
            decodedCards[slot] = card.copy();
            decodedRecipes[slot] = RecipeData.read(card, registries);
        }
        return decodedRecipes[slot];
    }

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            decodedCards[slot] = null;
            decodedRecipes[slot] = null;
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot % COLS == 0 ? WorkLocationCardItem.isCard(stack) : RecipeCardItem.isCard(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public RecipeRackBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANDROID_RECIPE_RACK.get(), pos, state);
    }

    @Override
    public WorkConsoleBlockEntity.LinkKind linkKind() {
        return WorkConsoleBlockEntity.LinkKind.RACK;
    }

    public ItemStackHandler items() {
        return items;
    }

    /** 1つのレシピと、それを作る場所。 */
    public record Entry(RecipeData recipe, BlockPos location, @Nullable net.minecraft.world.level.block.Block block) {}

    /**
     * 並べたレシピ（上の行から順に）。場所のカードがない行、別の次元の場所の行のレシピカードは飛ばす。
     * 手順カードは場所を自分の中に持つので、行の左のカードによらず並ぶ（場所は最初の手順の所）。
     */
    public void entries(Level level, List<Entry> out) {
        HolderLookup.Provider registries = level.registryAccess();
        for (int row = 0; row < ROWS; row++) {
            ItemStack loc = items.getStackInSlot(row * COLS);
            BlockPos pos = WorkLocationCardItem.pos(loc);
            boolean here = pos != null && WorkLocationCardItem.sameDimension(loc, level);
            for (int col = 1; col < COLS; col++) {
                RecipeData data = recipe(row * COLS + col, registries);
                if (data == null) {
                    continue;
                }
                if (data.procedure()) {
                    if (!data.steps().isEmpty()) {
                        BlockPos first = data.steps().get(0).pos();
                        // Reading the catalogue must never load a remote procedure's chunk.
                        out.add(new Entry(data, first, level.isLoaded(first) ? level.getBlockState(first).getBlock()
                                : net.minecraft.world.level.block.Blocks.AIR));
                    }
                } else if (here) {
                    out.add(new Entry(data, pos, WorkLocationCardItem.block(loc)));
                }
            }
        }
    }

    @Override
    protected boolean openScreen(ServerPlayer player) {
        if (!mayOperate(player)) {
            player.displayClientMessage(Component.translatable("gui.singulo.work_console.locked"), true);
            return true;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RecipeRackMenu(id, inv, worldPosition, items),
                Component.translatable(getBlockState().getBlock().getDescriptionId())), buf -> buf.writeBlockPos(worldPosition));
        return true;
    }

    @Override
    public void onBroken(Level level) {
        super.onBroken(level);
        for (int i = 0; i < SLOTS; i++) {
            Block.popResource(level, worldPosition, items.getStackInSlot(i));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag inv = tag.getCompound("items");
        inv.putInt("Size", SLOTS);
        items.deserializeNBT(registries, inv);
        java.util.Arrays.fill(decodedCards, null);
        java.util.Arrays.fill(decodedRecipes, null);
    }
}
