package dev.chronicle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.chronicle.entity.MatterBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import java.util.*;

/** Renders one carrier from real baked block models; cells remain visual data, never separate simulated blocks. */
public final class MatterRenderer extends EntityRenderer<MatterBody> {
    private final Map<MatterBody,List<MatterBody.Cell>> surface = new WeakHashMap<>();
    public MatterRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(MatterBody body,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(-90 * body.turn())); pose.translate(-body.extent().getX()/2.,0,-body.extent().getZ()/2.);
        for (MatterBody.Cell cell : surface.computeIfAbsent(body, MatterRenderer::surfaceCells)) {
            pose.pushPose(); pose.translate(cell.offset().getX(),cell.offset().getY(),cell.offset().getZ());
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(cell.state(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose(); super.render(body,yaw,partial,pose,buffers,light);
    }
    private static List<MatterBody.Cell> surfaceCells(MatterBody body) {
        Set<BlockPos> occupied=new HashSet<>(); for(var cell:body.cells()) occupied.add(cell.offset());
        return body.cells().stream().filter(cell->{ for(Direction d:Direction.values()) if(!occupied.contains(cell.offset().relative(d))) return true; return false; }).toList();
    }
    @Override public ResourceLocation getTextureLocation(MatterBody e) { return InventoryMenu.BLOCK_ATLAS; }
}
