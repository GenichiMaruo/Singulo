package io.github.genichimaruo.singulo.client;

import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.joml.Vector3fc;

/** World-aware glass geometry shared by the two connected-glass models. */
abstract class GlassModelBase implements BlockStateModel {
    protected final BlockStateModel original;
    GlassModelBase(BlockStateModel original) { this.original = original; }
    @Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) { original.collectParts(random, parts); }
    @Override public Material.Baked particleMaterial() { return original.particleMaterial(); }
    @Override public int materialFlags() { return original.materialFlags() | BakedQuad.FLAG_TRANSLUCENT; }
    protected BlockStateModelPart part(Function<Direction, List<BakedQuad>> faces) {
        return new BlockStateModelPart() {
            @Override public List<BakedQuad> getQuads(Direction direction) { return faces.apply(direction); }
            @Override public boolean useAmbientOcclusion() { return true; }
            @Override public Material.Baked particleMaterial() { return original.particleMaterial(); }
            @Override public int materialFlags() { return GlassModelBase.this.materialFlags(); }
        };
    }
    protected static TextureAtlasSprite sprite(net.minecraft.resources.Identifier id) {
        return net.minecraft.client.Minecraft.getInstance().getAtlasManager().get(
                new net.minecraft.client.resources.model.sprite.SpriteId(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, id));
    }
    private static final ModelBaker.Interner INTERNER = new ModelBaker.Interner() {
        @Override public Vector3fc vector(Vector3fc vector) { return vector; }
        @Override public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) { return material; }
    };
    protected static BakedQuad bakeQuad(Vector3fc from, Vector3fc to, float[] uv, TextureAtlasSprite sprite, Direction face) {
        var material = new BakedQuad.MaterialInfo(sprite, ChunkSectionLayer.TRANSLUCENT,
                RenderTypes.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS), -1, true, 0);
        return FaceBakery.bakeQuad(INTERNER, from, to, new CuboidFace.UVs(uv[0], uv[1], uv[2], uv[3]),
                com.mojang.math.Quadrant.R0, material, face, new ModelState() {}, null);
    }
}
