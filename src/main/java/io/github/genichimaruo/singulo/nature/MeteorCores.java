package io.github.genichimaruo.singulo.nature;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** 掘り出された隕石の核の場所（ワールドごと）。隕石コンパスは、ここにある核をもう指さない。 */
public class MeteorCores extends SavedData {
    static final String NAME = "singulo_meteor_cores";
    private final LongOpenHashSet mined = new LongOpenHashSet();

    public static MeteorCores get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MeteorCores::load, MeteorCores::new, NAME);
    }

    /** その核がもう掘り出されたか。読み込まれている場所なら、置かれているブロックも確かめる。 */
    public static boolean mined(ServerLevel level, BlockPos core) {
        if (get(level).mined.contains(core.asLong())) {
            return true;
        }
        return level.isLoaded(core) && !level.getBlockState(core).is(io.github.genichimaruo.singulo.registry.SinguloBlocks.METEORITE_CORE.get());
    }

    public void markMined(BlockPos core) {
        if (mined.add(core.asLong())) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("mined", mined.toLongArray());
        return tag;
    }

    static MeteorCores load(CompoundTag tag) {
        MeteorCores cores = new MeteorCores();
        for (long p : tag.getLongArray("mined")) {
            cores.mined.add(p);
        }
        return cores;
    }
}
