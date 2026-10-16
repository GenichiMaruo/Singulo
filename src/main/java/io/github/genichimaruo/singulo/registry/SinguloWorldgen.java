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

    /** 地下アンドロイド研究所（部屋の型紙をランダムに並べる）と、その部品。 */
    public static final RegistryObject<StructureType<io.github.genichimaruo.singulo.ruin.lab.LabStructure>> ANDROID_LAB =
            STRUCTURE_TYPES.register("android_lab", () -> () -> io.github.genichimaruo.singulo.ruin.lab.LabStructure.CODEC);
    public static final RegistryObject<StructurePieceType> LAB_HULL =
            PIECES.register("lab_hull", () -> io.github.genichimaruo.singulo.ruin.lab.LabPieces.Hull::new);
    public static final RegistryObject<StructurePieceType> LAB_ROOM =
            PIECES.register("lab_room", () -> io.github.genichimaruo.singulo.ruin.lab.LabPieces.Room::new);
    public static final RegistryObject<StructurePieceType> LAB_CORRIDOR =
            PIECES.register("lab_corridor", () -> io.github.genichimaruo.singulo.ruin.lab.LabPieces.Corridor::new);
    public static final RegistryObject<StructurePieceType> LAB_DOOR =
            PIECES.register("lab_door", () -> io.github.genichimaruo.singulo.ruin.lab.LabPieces.Doors::new);
    public static final RegistryObject<StructurePieceType> LAB_SHAFT =
            PIECES.register("lab_shaft", () -> io.github.genichimaruo.singulo.ruin.lab.LabPieces.Shaft::new);

    private SinguloWorldgen() {}
}
