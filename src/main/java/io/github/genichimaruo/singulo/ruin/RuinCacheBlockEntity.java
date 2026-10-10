package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * 遺構保管庫。中身は loot table（singulo:ruins/&lt;遺構ID&gt;）から入れ、空になってから
 * ゲーム内 N 日（観測拠点・研究棟は7日、培養施設・最終実験施設は14日 × regenDaysMultiplier）で再生する。
 * 初回だけ singulo:ruins/&lt;遺構ID&gt;_first も入れる（休眠した特異点の種など一回限りのもの）。
 * 再生の判定は開いたときに行うので、毎tickの負荷はない。
 */
public class RuinCacheBlockEntity extends BaseContainerBlockEntity implements net.minecraft.world.WorldlyContainer {
    private static final int[] NO_SLOTS = new int[0];

    /** ホッパーなどからは、どの面からも出し入れできない（開けて手で取り出す）。 */
    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @javax.annotation.Nullable net.minecraft.core.Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) {
        return false;
    }

    public static final int SIZE = 27;
    public static final long TICKS_PER_DAY = 24_000;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private String ruin = "";
    private boolean filledOnce;
    /** 空になったのに気づいたゲーム時刻。-1 なら中身がある（または未確認）。 */
    private long emptiedAt = -1;
    /** プレイヤーが置いた（壊して置き直した）保管庫。中身を入れない・再生しない。 */
    private boolean placedByPlayer;

    public RuinCacheBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.RUIN_CACHE.get(), pos, state);
    }

    public String ruin() {
        return ruin;
    }

    public void setRuin(String ruin) {
        this.ruin = ruin;
        setChanged();
    }

    /** プレイヤーが置いた印をつける（遺構の構造物として置かれたものには付かない）。 */
    public void markPlacedByPlayer() {
        placedByPlayer = true;
        setChanged();
    }

    public boolean placedByPlayer() {
        return placedByPlayer;
    }

    /** 力場で封鎖されているか（番人に守られた保管庫は、番人を倒すまで）。 */
    public boolean isSealed() {
        return getBlockState().hasProperty(RuinCacheBlock.SEALED) && getBlockState().getValue(RuinCacheBlock.SEALED);
    }

    public void setSealed(boolean sealed) {
        if (level != null && getBlockState().hasProperty(RuinCacheBlock.SEALED) && isSealed() != sealed) {
            level.setBlock(worldPosition, getBlockState().setValue(RuinCacheBlock.SEALED, sealed), Block.UPDATE_ALL);
        }
    }

    /** 番人に守られた遺構（研究棟・封鎖培養施設はボス部屋の番人、最終実験施設は守護機）。 */
    public static final java.util.Set<String> GUARDED = java.util.Set.of("research_building", "culture_facility", "final_lab");

    /** 番人に守られた保管庫か。倒すまで封鎖され、再生したら封鎖し直す。 */
    public boolean guarded() {
        return GUARDED.contains(ruin);
    }

    /** 再生までの tick 数。0 以下なら再生しない。 */
    public long regenTicks() {
        return regenTicks(ruin);
    }

    /** その遺構の中身が再生するまでの tick 数。0 以下なら再生しない。 */
    public static long regenTicks(String ruin) {
        double mult = ServerConfig.SPEC.isLoaded() ? ServerConfig.REGEN_DAYS_MULTIPLIER.get() : 1.0;
        int days = GeneratedContent.RUIN_REGEN_DAYS.getOrDefault(ruin, 7);
        return mult <= 0 ? -1 : Math.round(days * TICKS_PER_DAY * mult);
    }

    /** その遺構の1回の遠征ぶんの中身（loot table singulo:ruins/<遺構ID>）。 */
    public static List<ItemStack> rollLoot(ServerLevel level, String ruin, BlockPos at) {
        return roll(level, "ruins/" + ruin, at);
    }

    /** 初回なら中身を入れ、空で再生の時期を過ぎていれば入れ直す。now はゲーム時刻。 */
    public void refillIfDue(ServerLevel level, long now) {
        if (ruin.isEmpty() || placedByPlayer) {
            return;
        }
        if (!filledOnce) {
            fill(level, true);
            return;
        }
        if (!isEmpty()) {
            emptiedAt = -1;
            return;
        }
        if (emptiedAt < 0) {
            emptiedAt = now;
            setChanged();
            return;
        }
        long regen = regenTicks();
        if (regen > 0 && now - emptiedAt >= regen) {
            fill(level, false);
        }
    }

    private void fill(ServerLevel level, boolean first) {
        List<ItemStack> loot = new ArrayList<>(roll(level, "ruins/" + ruin, worldPosition));
        if (first) {
            loot.addAll(roll(level, "ruins/" + ruin + "_first", worldPosition));
        }
        double mult = ServerConfig.SPEC.isLoaded() ? ServerConfig.LOOT_COUNT_MULTIPLIER.get() : 1.0;
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            if (items.get(i).isEmpty()) {
                free.add(i);
            }
        }
        java.util.Collections.shuffle(free, new java.util.Random(level.random.nextLong()));
        int next = 0;
        for (ItemStack stack : loot) {
            int count = Math.max(1, (int) Math.round(stack.getCount() * mult));
            while (count > 0 && next < free.size()) {
                int n = Math.min(count, stack.getMaxStackSize());
                items.set(free.get(next++), stack.copyWithCount(n));
                count -= n;
            }
        }
        // 再生した中身は、番人を倒し直すまで取り出せない
        if (!first && guarded()) {
            setSealed(true);
        }
        filledOnce = true;
        emptiedAt = -1;
        setChanged();
    }

    private static List<ItemStack> roll(ServerLevel level, String path, BlockPos at) {
        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Singulo.id(path)));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
                .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
    }

    @Override
    public boolean canTakeItem(net.minecraft.world.Container target, int slot, ItemStack stack) {
        return !isSealed();
    }

    @Override
    public void stopOpen(Player player) {
        super.stopOpen(player);
        if (filledOnce && isEmpty() && emptiedAt < 0 && level != null) {
            emptiedAt = level.getGameTime();
            setChanged();
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putString("ruin", ruin);
        tag.putBoolean("filled", filledOnce);
        tag.putLong("emptied_at", emptiedAt);
        tag.putBoolean("placed", placedByPlayer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        ruin = tag.getString("ruin");
        filledOnce = tag.getBoolean("filled");
        emptiedAt = tag.contains("emptied_at") ? tag.getLong("emptied_at") : -1;
        placedByPlayer = tag.getBoolean("placed");
    }
}
