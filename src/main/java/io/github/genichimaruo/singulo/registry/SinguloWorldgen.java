package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.nature.MeteorCraterPiece;
import io.github.genichimaruo.singulo.nature.MeteorCraterStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/** 地形に描き込む構造物（隕石クレーター）の種類と部品。 */
public final class SinguloWorldgen {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Singulo.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Singulo.MODID);

    public static final RegistryObject<StructureType<MeteorCraterStructure>> METEOR_CRATER =
            STRUCTURE_TYPES.register("meteor_crater", () -> () -> MeteorCraterStructure.CODEC);
    public static final RegistryObject<StructurePieceType> METEOR_CRATER_PIECE =
            PIECES.register("meteor_crater", () -> MeteorCraterPiece::new);

    private SinguloWorldgen() {}
}
