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
import net.minecraftforge.client.model.data.ModelData;
import java.util.ArrayList;
import java.util.List;

public final class CrystalRenderer extends EntityRenderer<ResonantCrystal> {
    private final RandomSource random = RandomSource.create(0);
    private BakedModel cachedModel;
    private List<BakedQuad> quads = List.of();
    public CrystalRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(ResonantCrystal e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose(); pose.scale(3,3,3); pose.translate(0,.09,0);
        var renderer = Minecraft.getInstance().getBlockRenderer();
        BlockState state = Chronicle.CRYSTAL_MODEL.get().defaultBlockState();
        BakedModel model = renderer.getBlockModel(state);
        if(model!=cachedModel)cacheGlow(state,model);
        var surface=buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        double time=e.level().getGameTime()+partial;
        for(BakedQuad quad:quads){
            boolean glow=quad.getSprite().contents().name().getPath().equals("block/crystal_glow");
            float pulse=glow?(float)(.8+.12*Math.sin(time*.055+e.getId()*.17)+.08*Math.sin(averageHeight(quad)*9-time*.13)):1;
            surface.putBulkData(pose.last(),quad,pulse,pulse,pulse,glow?LightTexture.FULL_BRIGHT:light,OverlayTexture.NO_OVERLAY);
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
        // Deduplicate identical faces and draw each surface exactly once. The old additive
        // overlay used almost coplanar geometry and flickered as the camera moved.
        var unique=new java.util.LinkedHashMap<String,BakedQuad>();
        for(BakedQuad quad:allQuads)unique.putIfAbsent(quad.getSprite().contents().name()+":"+java.util.Arrays.toString(quad.getVertices()),quad);
        quads=List.copyOf(unique.values());
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
