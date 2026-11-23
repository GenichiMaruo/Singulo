package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.registry.SinguloWorldgen;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * 地下の遺構（singulo:avoiding）。中の構造物（structure。最終実験施設などの jigsaw、地下アンドロイド研究所）を、近くに
 * 別の大きな地下構造物（古代都市・試練の間・ほかの遺構）が生まれる場所を避けて置く。
 * <p>
 * 避ける相手は、散らばった配置（random_spread）の構造物の組（avoid）。始まりのチャンクから chunk_count 以内に相手の
 * 始まりの候補があり、その候補に相手が本当に生まれる（バイオームと高さが合う）なら、ここには生まない。生まれるかどうかの判定
 * （{@link #findGenerationPoint}）に入れるので、/locate や探索コンパスも避けた場所を指さない。相手もこの種類の構造物なら、
 * 相手の避ける条件は見ない（遺構どうしは、優先する側だけを avoid に書く。互いに避け合うと堂々巡りになるので）。
 * <p>
 * 要塞（同心円の配置）と廃坑（どこにでもある）は避けない。地下の遺構はいちばん最後の段階（top_layer_modification）で
 * 置くので、重なっても遺構の側は欠けず、先に生えた洞窟の植物も部屋の中からは消える。
 */
public class AvoidingStructure extends Structure {
    /** 避ける相手（構造物の組）と、始まりのチャンクからの距離（チャンク）。 */
    public record Avoid(Holder<StructureSet> set, int chunks) {
        public static final Codec<Avoid> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryFileCodec.create(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC, false).fieldOf("set").forGetter(Avoid::set),
                Codec.intRange(1, 32).fieldOf("chunk_count").forGetter(Avoid::chunks)
        ).apply(i, Avoid::new));
    }

    public static final MapCodec<AvoidingStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Structure.DIRECT_CODEC.fieldOf("structure").forGetter(AvoidingStructure::inner),
            Avoid.CODEC.listOf().optionalFieldOf("avoid", List.of()).forGetter(AvoidingStructure::avoid)
    ).apply(i, AvoidingStructure::new));

    private final Structure inner;
    private final List<Avoid> avoid;

    public AvoidingStructure(StructureSettings settings, Structure inner, List<Avoid> avoid) {
        super(settings);
        this.inner = inner;
        this.avoid = List.copyOf(avoid);
    }

    public Structure inner() {
        return inner;
    }

    public List<Avoid> avoid() {
        return avoid;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        for (Avoid a : avoid) {
            if (a.set().isBound() && generatesNear(a.set().value(), context, a.chunks())) {
                return Optional.empty();
            }
        }
        return inner.findValidGenerationPoint(context);
    }

    /** set の構造物が、context のチャンクから chunks 以内で生まれるか。 */
    static boolean generatesNear(StructureSet set, GenerationContext context, int chunks) {
        if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
            return false;
        }
        int x = context.chunkPos().x, z = context.chunkPos().z, spacing = placement.spacing();
        // 相手の区画（spacing×spacing チャンクに1つ）ごとに、始まりの候補のチャンクを1つだけ調べる
        for (int rx = Math.floorDiv(x - chunks, spacing); rx <= Math.floorDiv(x + chunks, spacing); rx++) {
            for (int rz = Math.floorDiv(z - chunks, spacing); rz <= Math.floorDiv(z + chunks, spacing); rz++) {
                ChunkPos c = placement.getPotentialStructureChunk(context.seed(), rx * spacing, rz * spacing);
                if (Math.abs(c.x - x) > chunks || Math.abs(c.z - z) > chunks
                        || !placement.applyAdditionalChunkRestrictions(c.x, c.z, context.seed())) {
                    continue;
                }
                for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                    Structure other = entry.structure().value();
                    Structure check = other instanceof AvoidingStructure wrapped ? wrapped.inner() : other;
                    GenerationContext there = new GenerationContext(context.registryAccess(), context.chunkGenerator(),
                            context.biomeSource(), context.randomState(), context.structureTemplateManager(), context.seed(), c,
                            context.heightAccessor(), other.biomes()::contains);
                    if (check.findValidGenerationPoint(there).isPresent()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public StructureType<?> type() {
        return SinguloWorldgen.AVOIDING.get();
    }
}
