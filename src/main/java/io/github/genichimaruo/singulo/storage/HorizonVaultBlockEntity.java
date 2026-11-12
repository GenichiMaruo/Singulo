package io.github.genichimaruo.singulo.storage;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 事象の地平線倉庫。1種類の物を、中の小さなブラックホールに {@link #CAPACITY} 個まで「質量」としてしまう。
 * <ul>
 *   <li>物は見本（数1の写し）と個数（long）で持つ。種類は空になるまで変わらない。入りきらない分は受け取らない（捨てない）。</li>
 *   <li>ホッパー・パイプ・アンドロイド・ドローンの発着ポートなどからは、1枠の入れ物として見える（{@link #items()}）。</li>
 *   <li>手で: 物を持って使うと手の物を入れ、続けてすばやくもう一度使うと持ち物の同じ物を全部入れる。素手で使うと1スタック、
 *   素手でスニークして使うと1個取り出す。</li>
 *   <li>壊すと中身ごとアイテムになり（見本と個数の部品）、置き直すと戻る。中身入りの倉庫は、ほかの倉庫や入れ物に入れられない。</li>
 * </ul>
 * 見た目（中のブラックホールの大きさ・回る見本・正面の個数）はクライアントへ、変わったときだけ（10 tick に1回まで）送る。
 */
public class HorizonVaultBlockEntity extends BlockEntity {
    /** しまえる個数（long の上限、約922京。事実上無制限）。 */
    public static final long CAPACITY = Long.MAX_VALUE;
    /** 見た目（中のブラックホールの大きさ・柱の光の帯）の目盛りの上端（1兆個でいっぱいに見える）。 */
    public static final long VISUAL_FULL = 1_000_000_000_000L;
    /** 続けてもう一度使うと持ち物の同じ物を全部入れる、その間隔（tick）。 */
    static final int DOUBLE_USE_TICKS = 10;

    private ItemStack type = ItemStack.EMPTY;
    private long count;
    /** 見た目を送り直す必要があるか（serverTick が 10 tick ごとに送る）。 */
    private boolean visualDirty;
    @Nullable
    private UUID lastUser;
    private long lastUseAt = Long.MIN_VALUE;

    private final IItemHandler handler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return type.isEmpty() || count <= 0 ? ItemStack.EMPTY : type.copyWithCount((int) Math.min(Integer.MAX_VALUE, count));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return insert(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return extract(amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return (int) Math.min(Integer.MAX_VALUE, CAPACITY);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(stack);
        }
    };

    public HorizonVaultBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.HORIZON_VAULT.get(), pos, state);
    }

    public IItemHandler items() {
        return handler;
    }

    /** しまっている物の見本（空なら EMPTY）。 */
    public ItemStack type() {
        return type;
    }

    public long count() {
        return count;
    }

    /** しまえる物か: 空か同じ物。中身を持つ入れ物（梱包済みの箱・中身入りのシュルカーボックスや倉庫）は入れ子にしない。 */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty() || !stack.canFitInsideContainerItems() || stack.has(SinguloComponents.VAULT_COUNT.get())) {
            return false;
        }
        return type.isEmpty() || ItemStack.isSameItemSameComponents(type, stack);
    }

    /** 入るだけ入れて、残りを返す。 */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (!accepts(stack)) {
            return stack;
        }
        long room = CAPACITY - count;
        int moved = (int) Math.min(room, stack.getCount());
        if (moved <= 0) {
            return stack;
        }
        if (!simulate) {
            if (type.isEmpty()) {
                type = stack.copyWithCount(1);
            }
            count += moved;
            changed();
        }
        return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    /** 最大 amount 個（1スタックまで）取り出す。 */
    public ItemStack extract(int amount, boolean simulate) {
        if (type.isEmpty() || count <= 0 || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int n = (int) Math.min(Math.min(amount, type.getMaxStackSize()), count);
        ItemStack out = type.copyWithCount(n);
        if (!simulate) {
            count -= n;
            if (count <= 0) {
                count = 0;
                type = ItemStack.EMPTY;
            }
            changed();
        }
        return out;
    }

    // ------------------------------------------------------------------ 手で使う

    /** 最初の投入で手が空になった場合も、2回目のクリックで残りをまとめて入れる。 */
    boolean insertRemainingOnDoubleUse(Player player) {
        if (!player.getUUID().equals(lastUser) || level.getGameTime() - lastUseAt > DOUBLE_USE_TICKS || type.isEmpty()) {
            return false;
        }
        insertFromPlayer(player, ItemStack.EMPTY);
        lastUser = null;
        return true;
    }

    /** 手の物を入れる。続けてすばやく使ったら、持ち物の同じ物も全部入れる。入れたら true。 */
    boolean insertFromPlayer(Player player, ItemStack held) {
        long now = level.getGameTime();
        boolean again = player.getUUID().equals(lastUser) && now - lastUseAt <= DOUBLE_USE_TICKS;
        lastUser = player.getUUID();
        lastUseAt = now;
        boolean any = false;
        if (!held.isEmpty() && accepts(held)) {
            ItemStack rest = insert(held, false);
            any = rest.getCount() != held.getCount();
            held.setCount(rest.getCount());
        }
        if (again && !type.isEmpty()) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && accepts(s)) {
                    ItemStack rest = insert(s, false);
                    any |= rest.getCount() != s.getCount();
                    s.setCount(rest.getCount());
                }
            }
            inv.setChanged();
        }
        if (any) {
            level.playSound(null, worldPosition, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 0.4F, 0.6F);
        }
        return any;
    }

    /** 1スタック（スニークなら1個）を持ち物へ取り出す。入りきらなければ足もとに落とす（倉庫からは減らした分だけ）。 */
    boolean takeForPlayer(Player player, boolean single) {
        ItemStack out = extract(single ? 1 : Integer.MAX_VALUE, false);
        if (out.isEmpty()) {
            return false;
        }
        if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
        level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 0.8F);
        return true;
    }

    /** コンパレーターの強さ（入っていれば1以上）。 */
    int comparator() {
        if (count <= 0) {
            return 0;
        }
        // 量の桁で（1個で1、1兆個以上で15）。個数そのものの割合では、上限が大きすぎてほとんど動かない
        return 1 + (int) Math.min(14, Math.floor(14 * Math.log10(count) / Math.log10(VISUAL_FULL)));
    }

    // ------------------------------------------------------------------ 同期

    private void changed() {
        setChanged();
        visualDirty = true;
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, HorizonVaultBlockEntity be) {
        if (be.visualDirty && level.getGameTime() % 10 == 0) {
            be.visualDirty = false;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ アイテムとの受け渡し（中身ごと運ぶ）

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!type.isEmpty() && count > 0) {
            components.set(SinguloComponents.VAULT_ITEM.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(type.copyWithCount(1))));
            components.set(SinguloComponents.VAULT_COUNT.get(), count);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        var held = input.get(SinguloComponents.VAULT_ITEM.get());
        ItemStack t = held == null ? ItemStack.EMPTY : held.copyOne();
        long n = input.getOrDefault(SinguloComponents.VAULT_COUNT.get(), 0L);
        if (!t.isEmpty() && n > 0) {
            type = t.copyWithCount(1);
            count = Math.min(CAPACITY, n);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("type");
        tag.remove("count");
    }

    // ------------------------------------------------------------------ 保存

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag, registries);
    }

    private void write(CompoundTag tag, HolderLookup.Provider registries) {
        if (!type.isEmpty() && count > 0) {
            tag.put("type", type.save(registries));
            tag.putLong("count", count);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        type = tag.contains("type") ? ItemStack.parseOptional(registries, tag.getCompound("type")).copyWithCount(1) : ItemStack.EMPTY;
        count = Math.max(0, Math.min(CAPACITY, tag.getLong("count")));
        if (type.isEmpty() || count <= 0) {
            type = ItemStack.EMPTY;
            count = 0;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag, registries);
        // 空になったときのデータが空だと、クライアントで読まれずに前の個数（最後の1個など）が残るので、個数はいつも送る
        tag.putLong("count", count);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
