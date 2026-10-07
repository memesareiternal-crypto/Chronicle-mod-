package dev.chronicle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.chronicle.Chronicle;
import dev.chronicle.entity.ResonantCrystal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public final class CrystalRenderer extends EntityRenderer<ResonantCrystal> {
    public CrystalRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(ResonantCrystal e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose(); pose.scale(2,2,2); pose.translate(-.5,0,-.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Chronicle.CRYSTAL_MODEL.get().defaultBlockState(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.popPose(); super.render(e,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(ResonantCrystal e) { return InventoryMenu.BLOCK_ATLAS; }
}
