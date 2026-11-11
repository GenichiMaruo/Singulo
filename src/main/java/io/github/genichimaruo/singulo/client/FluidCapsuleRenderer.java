package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import io.github.genichimaruo.singulo.drone.FluidCapsuleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 液体カプセルの絵（手に持ったとき・持ち物の欄・ドローンに吊るしたとき・発着ポートの台の上）。
 * <p>
 * 殻は立体のモデル（tools/drones.py の capsule_models: 両端の鏡板・ガラスの胴・保護の枠と脚・上の弁・段階のバッテリー）で、
 * 胴の中に、詰めた液体をその液体の絵と色で量に応じた高さまで描く（気体は胴いっぱいにうすく）。少し揺れて見えるように、
 * 液面は時間でわずかに上下する。
 */
public final class FluidCapsuleRenderer extends BlockEntityWithoutLevelRenderer {
    /** ガラスの胴の内側（ドット。モデルの胴は x 3〜13、y 5〜11、z 5〜11）。 */
    private static final float X0 = 3.2F / 16, X1 = 12.8F / 16, Y0 = 5.2F / 16, Y1 = 10.8F / 16, Z0 = 5.2F / 16, Z1 = 10.8F / 16;

    private static FluidCapsuleRenderer instance;

    private FluidCapsuleRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static FluidCapsuleRenderer get() {
        if (instance == null) {
            instance = new FluidCapsuleRenderer();
        }
        return instance;
    }

    /** 段階ごとの殻のモデル（ModelEvent.RegisterAdditional で読む）。 */
    static ModelResourceLocation shell(int tier) {
        return ModelResourceLocation.standalone(Singulo.id("item/fluid_capsule_shell_" + Mth.clamp(tier, 1, 3)));
    }

    static void registerModels(ModelEvent.RegisterAdditional event) {
        for (int t = 1; t <= 3; t++) {
            event.register(shell(t));
        }
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        VertexConsumer vc = buffers.getBuffer(Sheets.translucentItemSheet());
        FluidStack fluid = FluidCapsuleItem.fluid(stack);
        if (!fluid.isEmpty()) {
            FluidLook.Look look = FluidLook.look(fluid);
            boolean gas = fluid.getFluidType().isLighterThanAir();
            float fill = Mth.clamp(fluid.getAmount() / (float) FluidCapsuleItem.CAPACITY, 0.08F, 1);
            float time = mc.level == null ? 0 : (mc.level.getGameTime() % 24000 + mc.getTimer().getGameTimeDeltaPartialTick(false)) / 20F;
            float wobble = fill < 0.98F ? Mth.sin(time * 2.4F) * 0.12F / 16 : 0;
            float top = gas ? Y1 : Math.min(Y1, Y0 + (Y1 - Y0) * fill + wobble);
            FluidLook.box(pose, vc, look, look.rgb(), look.alpha(), light, 0x3F, X0, Y0, Z0, X1, top, Z1);
        }
        var model = mc.getModelManager().getModel(shell(DroneCrateItem.tier(stack)));
        mc.getItemRenderer().renderModelLists(model, stack, light, overlay, pose, vc);
    }
}
