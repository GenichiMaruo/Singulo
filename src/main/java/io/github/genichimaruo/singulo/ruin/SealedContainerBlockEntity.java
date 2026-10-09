package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * 封印コンテナの中身と、開け閉めの段取り。
 * <p>
 * 解錠（UNSEALING、{@link #UNSEAL_TICKS} tick）: 鍵を差す → 光の輪が上から下へ読み取る → 継ぎ目が順に光って錠が外れる →
 * 冷気が噴き出す → 蓋の4枚の板が回りながら外へ滑り出す → 中の台がせり上がり、中身が浮かんで見える。
 * 封印（SEALING、{@link #SEAL_TICKS} tick）はその逆。段取りの時刻は描画（SealedContainerRenderer）と共有する。
 * <p>
 * 遺構に置かれたコンテナは、初めて開いたときに loot table singulo:sealed/tier_N の中身が入る。
 */
public class SealedContainerBlockEntity extends BaseContainerBlockEntity {
    public enum Phase { SEALED, UNSEALING, OPEN, SEALING }

    public static final int SIZE = 9;
    public static final int UNSEAL_TICKS = 90;
    public static final int SEAL_TICKS = 50;
    /** 解錠の段取り（開始からの tick）。 */
    public static final int T_SCAN = 6, T_SCAN_END = 26, T_UNLOCK = 26, T_VENT = 38, T_OPEN = 44, T_OPEN_END = 68,
            T_RISE = 66, T_RISE_END = 86;
    /** 段階ごとの鍵。 */
    static final String[] KEYS = {"magnetic_key", "quantum_key", "temporal_key", "singularity_key"};

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private Phase phase = Phase.OPEN;
    private long phaseStart;
    /** 遺構に置かれたコンテナで、まだ中身を入れていない。 */
    private boolean lootPending;
    /** クライアント: 浮かべて見せる中身（いくつか）。 */
    private final List<ItemStack> shown = new ArrayList<>();

    public SealedContainerBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.SEALED_CONTAINER.get(), pos, state);
    }

    public int tier() {
        return getBlockState().getBlock() instanceof SealedContainerBlock b ? b.tier() : 1;
    }

    public Phase phase() {
        return phase;
    }

    /** 今の段取りが始まってからの tick（partialTick を足して描画に使う）。 */
    public float phaseTime(float partialTick) {
        return level == null ? 0 : level.getGameTime() - phaseStart + partialTick;
    }

    public List<ItemStack> shownItems() {
        return shown;
    }

    public static Item keyItem(int tier) {
        return BuiltInRegistries.ITEM.get(Singulo.id(KEYS[Math.max(1, Math.min(4, tier)) - 1]));
    }

    public static boolean isKey(ItemStack stack, int tier) {
        return !stack.isEmpty() && stack.is(keyItem(tier));
    }

    // ------------------------------------------------------------------ 操作

    /** 使われた（stack は手に持っているもの。空でもよい）。 */
    public void interact(Player player, ItemStack stack) {
        switch (phase) {
            case UNSEALING, SEALING -> player.displayClientMessage(Component.translatable("message.singulo.sealed.busy"), true);
            case SEALED -> {
                if (isKey(stack, tier())) {
                    useKey(player, stack);
                    start(Phase.UNSEALING);
                } else {
                    play("sealed_container.denied", 1.0F);
                    player.displayClientMessage(Component.translatable("message.singulo.sealed.need_key",
                            keyItem(tier()).getDescription()), true);
                }
            }
            case OPEN -> {
                if (isKey(stack, tier()) && player.isSecondaryUseActive()) {
                    useKey(player, stack);
                    start(Phase.SEALING);
                } else {
                    player.openMenu(this);
                }
            }
        }
    }

    private static void useKey(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private void start(Phase next) {
        phase = next;
        phaseStart = level.getGameTime();
        play("sealed_container.insert", 1.0F);
        sync();
    }

    private void play(String sound, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, SinguloSounds.get(sound), SoundSource.BLOCKS, 1.0F, pitch);
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SealedContainerBlockEntity be) {
        if (be.phase != Phase.UNSEALING && be.phase != Phase.SEALING) {
            return;
        }
        long t = level.getGameTime() - be.phaseStart;
        if (be.phase == Phase.UNSEALING) {
            if (t == T_SCAN) {
                be.play("sealed_container.scan", 1.0F);
            } else if (t == T_UNLOCK) {
                be.play("sealed_container.unlock", 1.0F);
            } else if (t == T_VENT) {
                be.play("sealed_container.vent", 1.0F);
            } else if (t == T_OPEN) {
                be.play("sealed_container.open", 1.0F);
            } else if (t == T_RISE) {
                be.play("sealed_container.rise", 1.0F);
            }
            if (t >= UNSEAL_TICKS) {
                if (be.lootPending && level instanceof ServerLevel server) {
                    be.fillLoot(server);
                }
                be.phase = Phase.OPEN;
                be.sync();
            }
        } else {
            if (t == 4) {
                be.play("sealed_container.lock", 1.0F);
            } else if (t == SEAL_TICKS - 10) {
                be.play("sealed_container.unlock", 0.7F);
            }
            if (t >= SEAL_TICKS) {
                be.phase = Phase.SEALED;
                be.sync();
            }
        }
    }

    /** 冷気の噴き出し（解錠）と、閉じたあとの小さな光（封印）。 */
    public static void clientTick(Level level, BlockPos pos, BlockState state, SealedContainerBlockEntity be) {
        if (be.phase != Phase.UNSEALING) {
            return;
        }
        long t = level.getGameTime() - be.phaseStart;
        var rnd = level.random;
        if (t >= T_VENT && t < T_VENT + 12) {
            for (int i = 0; i < 4; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double vx = Math.cos(a) * 0.08;
                double vz = Math.sin(a) * 0.08;
                level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5 + Math.cos(a) * 0.45, pos.getY() + 0.85,
                        pos.getZ() + 0.5 + Math.sin(a) * 0.45, vx, 0.01, vz);
                if (rnd.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5 + Math.cos(a) * 0.4, pos.getY() + 0.8,
                            pos.getZ() + 0.5 + Math.sin(a) * 0.4, vx * 1.5, 0.02, vz * 1.5);
                }
            }
        }
        if (t >= T_RISE && t < T_RISE_END && rnd.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.3, pos.getY() + 0.9,
                    pos.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.3, 0, 0.03, 0);
        }
    }

    private void fillLoot(ServerLevel level) {
        lootPending = false;
        var table = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Singulo.id("sealed/tier_" + tier())));
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .create(LootContextParamSets.CHEST);
        int slot = 0;
        for (ItemStack stack : table.getRandomItems(params)) {
            while (!stack.isEmpty() && slot < SIZE) {
                items.set(slot++, stack.split(stack.getMaxStackSize()));
            }
        }
    }

    /** 封印中に壊された: 中身は失われる。 */
    void shatter(ServerLevel level) {
        items.clear();
        Vec3 c = Vec3.atCenterOf(worldPosition);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, worldPosition, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    /** テスト用: 遺構に置かれた、まだ開けていない状態にする。 */
    public void sealWithLoot() {
        phase = Phase.SEALED;
        lootPending = true;
        sync();
    }

    // ------------------------------------------------------------------ 入れ物（開いている間だけ出し入れできる）

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return phase == Phase.OPEN;
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return phase == Phase.OPEN;
    }

    @Override
    public boolean stillValid(Player player) {
        return phase == Phase.OPEN && super.stillValid(player);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide && phase == Phase.OPEN) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
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
        return new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    // ------------------------------------------------------------------ 保存・同期

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putString("Phase", phase.name());
        tag.putLong("PhaseStart", phaseStart);
        tag.putBoolean("Loot", lootPending);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        lootPending = tag.getBoolean("Loot");
        if (tag.contains("Phase")) {
            try {
                phase = Phase.valueOf(tag.getString("Phase"));
            } catch (IllegalArgumentException e) {
                phase = Phase.SEALED;
            }
        } else if (lootPending) {
            phase = Phase.SEALED;                    // 遺構の構造物から置かれたばかり
        }
        phaseStart = tag.getLong("PhaseStart");
        shown.clear();
        ListTag list = tag.getList("Shown", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(registries, list.getCompound(i)).ifPresent(shown::add);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Phase", phase.name());
        tag.putLong("PhaseStart", phaseStart);
        // 浮かべて見せる中身（開いているときだけ、3つまで）
        ListTag list = new ListTag();
        if (phase == Phase.OPEN || phase == Phase.SEALING) {
            for (ItemStack stack : items) {
                if (!stack.isEmpty() && list.size() < 3) {
                    list.add(stack.copyWithCount(1).save(registries));
                }
            }
        }
        tag.put("Shown", list);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
