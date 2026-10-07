package dev.chronicle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.chronicle.Chronicle;
import dev.chronicle.entity.ResonantCrystal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import java.util.ArrayList;
import java.util.List;

public final class CrystalRenderer extends EntityRenderer<ResonantCrystal> {
    private final RandomSource random = RandomSource.create(0);
    private BakedModel cachedModel;
    private List<BakedQuad> glowQuads = List.of();
    private Vec3 modelCenter = Vec3.ZERO;
    public CrystalRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(ResonantCrystal e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose(); pose.scale(3,3,3); pose.translate(0,.09,0);
        var renderer = Minecraft.getInstance().getBlockRenderer();
        BlockState state = Chronicle.CRYSTAL_MODEL.get().defaultBlockState();
        // Keep the supplied artwork and corrected alpha/UV conversion. Only its white vein
        // material receives emission; the surrounding stone follows ordinary scene lighting.
        renderer.renderSingleBlock(state,pose,buffers,light,OverlayTexture.NO_OVERLAY,ModelData.EMPTY,RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        BakedModel model = renderer.getBlockModel(state);
        if (model != cachedModel) cacheGlow(state, model);
        if (!glowQuads.isEmpty()) {
            pose.pushPose();
            pose.translate(modelCenter.x, modelCenter.y, modelCenter.z);
            pose.scale(1.0005f, 1.0005f, 1.0005f);
            pose.translate(-modelCenter.x, -modelCenter.y, -modelCenter.z);
            var emission = buffers.getBuffer(RenderType.eyes(InventoryMenu.BLOCK_ATLAS));
            double time = e.level().getGameTime() + partial;
            for (BakedQuad quad : glowQuads) {
                double height = averageHeight(quad);
                double breath = Math.sin(time * .055 + e.getId() * .17);
                double travelling = Math.sin(height * 9 - time * .13);
                float brightness = (float)(.64 + breath * .15 + travelling * .11);
                // An additive pass animates the original crack texture. Its black texels add
                // zero light, so they cannot turn the full crystal face into a glowing panel.
                emission.putBulkData(pose.last(), quad, brightness, brightness * .965f, brightness * .99f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
        }
        pose.popPose(); super.render(e,yaw,partial,pose,buffers,light);
    }

    private void cacheGlow(BlockState state, BakedModel model) {
        cachedModel = model;
        var allQuads = new ArrayList<BakedQuad>();
        random.setSeed(0);
        for (RenderType layer : model.getRenderTypes(state, random, ModelData.EMPTY)) {
            for (Direction side : Direction.values()) {
                random.setSeed(0);
                allQuads.addAll(model.getQuads(state, side, random, ModelData.EMPTY, layer));
            }
            random.setSeed(0);
            allQuads.addAll(model.getQuads(state, null, random, ModelData.EMPTY, layer));
        }
        glowQuads = allQuads.stream().filter(quad -> quad.getSprite().contents().name().getPath().equals("block/crystal_glow")).toList();
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (BakedQuad quad : allQuads) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) {
                double x = Float.intBitsToFloat(data[vertex * stride]);
                double y = Float.intBitsToFloat(data[vertex * stride + 1]);
                double z = Float.intBitsToFloat(data[vertex * stride + 2]);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
        }
        modelCenter = allQuads.isEmpty() ? Vec3.ZERO : new Vec3((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    private static double averageHeight(BakedQuad quad) {
        int[] data = quad.getVertices();
        int stride = data.length / 4;
        double sum = 0;
        for (int vertex = 0; vertex < 4; vertex++) sum += Float.intBitsToFloat(data[vertex * stride + 1]);
        return sum / 4;
    }
    @Override public ResourceLocation getTextureLocation(ResonantCrystal e) { return InventoryMenu.BLOCK_ATLAS; }
}
