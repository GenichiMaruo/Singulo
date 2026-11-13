package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.food.FoodPaste;
import io.github.genichimaruo.singulo.food.FoodPrinterBlockEntity;
import io.github.genichimaruo.singulo.food.PrintedFood;
import io.github.genichimaruo.singulo.food.PrintedFoodItem;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * フードプリンターの中の動き（座標はモデルと同じドット単位。正面が北のときの向きで描いて、向きに合わせて回す）。
 * <ul>
 *   <li>天井の左右のレール（x 3〜4・12〜13、y 11〜12）に掛かった橋が前後（z）に走り、橋の上の台車が左右（x）に走る。
 *       台車から下へノズルがのび、先が印刷する層の上に来る。</li>
 *   <li>印刷台（x 4〜12、z 3〜9、上面 y 4）に、食品の形（{@link PrintedFood}）を1ドットずつ積む。ノズルが次の場所へ動いてから、
 *       その1ドットが下から盛り上がる。印刷中はノズルの先がペーストの色に光る。</li>
 *   <li>印刷していないときは、ノズルを奥の上に寄せる。出口に印刷した食品があれば、できあがった形を台に置いて見せる。</li>
 *   <li>背面の3つの窓（x 3〜5・7〜9・11〜13、y 4〜10）に、カートリッジのペーストの量だけ光る柱。</li>
 * </ul>
 */
public class FoodPrinterRenderer implements BlockEntityRenderer<FoodPrinterBlockEntity> {
    private static final float PX = 1 / 16F;
    private static final int STEEL = 0xAEB4BE, WHITE = 0xE2E6EA, COPPER = 0xCE7E4E, DARK = 0x2A2E36;
    private static final float BED_TOP = 4, BED_CX = 8, BED_CZ = 6;
    /** 休むときのノズルの位置（奥の真ん中）と、先の高さ。 */
    private static final float PARK_X = 8, PARK_Z = 9.5F, PARK_TIP = 8.5F;
    private static final int[] WINDOW_X = {3, 7, 11};

    public FoodPrinterRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FoodPrinterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(AbstractMachineBlock.FACING) ? state.getValue(AbstractMachineBlock.FACING) : Direction.NORTH;
        boolean working = state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(PX, PX, PX);
        VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE));

        PrintedFood food = PrintedFood.byIndex(be.printing());
        float nx = PARK_X, nz = PARK_Z, tip = PARK_TIP;
        if (food != null) {
            float t = Mth.clamp((be.progress() + (working ? partialTick : 0)) / food.ticks(), 0, 1);
            int[][] voxels = food.voxels();
            float at = t * voxels.length;
            int i = Math.min(voxels.length - 1, (int) at);
            float frac = at - i;
            float ox = BED_CX - food.width() / 2F, oz = BED_CZ - food.depth() / 2F;
            // 置き終えた立方体と、いま盛り上がっている1つ
            for (int k = 0; k < i; k++) {
                voxel(pose, solid, voxels[k], ox, oz, 1, light);
            }
            float rise = Mth.clamp((frac - 0.4F) / 0.6F, 0, 1);
            if (rise > 0) {
                voxel(pose, solid, voxels[i], ox, oz, rise, light);
            }
            // ノズル: 前の立方体の上から、次の立方体の上へ動く
            int[] from = voxels[Math.max(0, i - 1)], to = voxels[i];
            float move = Mth.clamp(frac / 0.4F, 0, 1);
            move = move * move * (3 - 2 * move);
            nx = Mth.lerp(move, ox + from[0] + 0.5F, ox + to[0] + 0.5F);
            nz = Mth.lerp(move, oz + from[2] + 0.5F, oz + to[2] + 0.5F);
            tip = BED_TOP + Mth.lerp(move, from[1], to[1]) + 1.6F;
            if (working) {
                // 銅の先（±0.45）より少し大きく包む（同じ面に重ねると、ちらつく）
                FxDraw.box(pose, FxDraw.glow(buffers), nx - 0.55F, tip - 0.45F, nz - 0.55F, nx + 0.55F, tip + 0.2F, nz + 0.55F,
                        LightTexture.FULL_BRIGHT, pasteColor(food), 220);
            }
        } else {
            ItemStack out = be.items().getStackInSlot(FoodPrinterBlockEntity.OUTPUT);
            if (out.getItem() instanceof PrintedFoodItem printed) {
                PrintedFood done = printed.printedFood();
                float ox = BED_CX - done.width() / 2F, oz = BED_CZ - done.depth() / 2F;
                for (int[] v : done.voxels()) {
                    voxel(pose, solid, v, ox, oz, 1, light);
                }
            }
        }
        gantry(pose, solid, nx, nz, tip, light);
        windows(be, pose, solid, light);
        pose.popPose();
    }

    /** 立方体1つ（rise は下からの高さの割合）。 */
    private static void voxel(PoseStack pose, VertexConsumer vc, int[] v, float ox, float oz, float rise, int light) {
        float x = ox + v[0], y = BED_TOP + v[1], z = oz + v[2];
        FxDraw.box(pose, vc, x, y, z, x + 1, y + rise, z + 1, light, v[3]);
    }

    /**
     * 橋（レールの間を前後に）、レールに掛かる2つの滑り台、台車、ノズルの管と銅の先。
     * 接する部品は、面をちょうど重ねずに少し食い込ませる（同じ位置の面は、ちらつく）。滑り台と橋の上面は、モデルのレールの下面（y 11）から少し下げる。
     */
    private static void gantry(PoseStack pose, VertexConsumer vc, float nx, float nz, float tip, int light) {
        FxDraw.box(pose, vc, 3, 10, nz - 1, 4, 10.95F, nz + 1, light, STEEL);
        FxDraw.box(pose, vc, 12, 10, nz - 1, 13, 10.95F, nz + 1, light, STEEL);
        FxDraw.box(pose, vc, 3.5F, 10.25F, nz - 0.5F, 12.5F, 10.85F, nz + 0.5F, light, STEEL);
        FxDraw.box(pose, vc, nx - 1, 9.25F, nz - 1, nx + 1, 10.4F, nz + 1, light, WHITE);
        FxDraw.box(pose, vc, nx - 0.3F, tip + 0.5F, nz - 0.3F, nx + 0.3F, 9.4F, nz + 0.3F, light, STEEL);
        FxDraw.box(pose, vc, nx - 0.45F, tip, nz - 0.45F, nx + 0.45F, tip + 0.6F, nz + 0.45F, light, COPPER);
    }

    /** 背面の窓: カートリッジがあれば、暗いガラスの筒と、ペーストの量だけ光る柱。 */
    private static void windows(FoodPrinterBlockEntity be, PoseStack pose, VertexConsumer vc, int light) {
        for (int k = 0; k < FoodPaste.KINDS; k++) {
            int amount = be.paste(k);
            if (amount < 0) {
                continue;
            }
            float x0 = WINDOW_X[k], x1 = x0 + 2;
            FxDraw.box(pose, vc, x0, 4, 14, x1, 10, 14.9F, light, DARK);
            float fill = 6F * amount / FoodPaste.CAPACITY;
            if (fill > 0) {
                FxDraw.box(pose, vc, x0 + 0.25F, 4, 14.9F, x1 - 0.25F, 4 + fill, 14.95F, LightTexture.FULL_BRIGHT, FoodPaste.COLORS[k]);
            }
        }
    }

    /** 印刷の光の色: いちばん多く使うペーストの色。 */
    private static int pasteColor(PrintedFood food) {
        int best = 0;
        for (int k = 1; k < FoodPaste.KINDS; k++) {
            if (food.cost(k) > food.cost(best)) {
                best = k;
            }
        }
        return FoodPaste.COLORS[best];
    }
}
