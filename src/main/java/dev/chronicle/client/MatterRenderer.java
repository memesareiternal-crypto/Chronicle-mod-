package dev.chronicle.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.chronicle.entity.MatterBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.client.model.data.ModelData;
import java.util.*;

/** One cached GPU mesh per render layer, built from actual resource-pack block quads. */
public final class MatterRenderer extends EntityRenderer<MatterBody> {
    private static final Map<MatterBody,Mesh> CACHE=new IdentityHashMap<>();
    private static int bakeBudget=2048;
    public static void beginFrame(){bakeBudget=2048;}
    public MatterRenderer(EntityRendererProvider.Context context){super(context);}
    public static void clear(){if(!RenderSystem.isOnRenderThread()){RenderSystem.recordRenderCall(MatterRenderer::clear);return;}for(Mesh mesh:CACHE.values())mesh.close();CACHE.clear();}
    @Override public void render(MatterBody body,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(body.cells().isEmpty())return;
        if(body.tickCount%60==0){var iterator=CACHE.entrySet().iterator();while(iterator.hasNext()){var row=iterator.next();if(!row.getKey().isAlive()){row.getValue().close();iterator.remove();}}}
        Mesh mesh=CACHE.get(body);if(mesh==null||mesh.revision!=body.revision()){if(mesh!=null)mesh.close();mesh=new Mesh(body);CACHE.put(body,mesh);}
        int work=Math.min(1024,Math.min(bakeBudget,body.mass()-mesh.cursor));
        if(work>0){bakePage(body,light,mesh,work);bakeBudget-=work;}
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(-90*body.turn()));pose.translate(-body.extent().getX()/2.,0,-body.extent().getZ()/2.);
        float brightness=.35f+.65f*Math.max(LightTexture.block(light),LightTexture.sky(light))/15f;RenderSystem.setShaderColor(brightness,brightness,brightness,1);
        for(var row:mesh.layers.entrySet()){row.getKey().setupRenderState();for(var buffer:row.getValue()){buffer.bind();buffer.drawWithShader(pose.last().pose(),RenderSystem.getProjectionMatrix(),RenderSystem.getShader());VertexBuffer.unbind();}row.getKey().clearRenderState();}
        RenderSystem.setShaderColor(1,1,1,1);pose.popPose();super.render(body,yaw,partial,pose,buffers,light);
    }
    private static void bakePage(MatterBody body,int light,Mesh mesh,int count){
        Minecraft mc=Minecraft.getInstance();Map<RenderType,BufferBuilder> builders=new LinkedHashMap<>();Map<BlockPos,MatterBody.Cell> cells=mesh.cells;
        PoseStack pose=new PoseStack();RandomSource random=RandomSource.create();
        for(var cell:body.cells().subList(mesh.cursor,mesh.cursor+count)) {
            if(!cell.state().getFluidState().isEmpty() && cell.state().getRenderShape()!=RenderShape.MODEL){
                var fluid=net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(cell.state().getFluidState());
                var sprite=mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(fluid.getStillTexture());
                BufferBuilder builder=builders.computeIfAbsent(RenderType.translucent(),k->{var b=new BufferBuilder(65536);b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);return b;});
                box(cell.offset(),cells,builder,sprite,fluid.getTintColor(),.72f);continue;
            }
            if(cell.state().getRenderShape()==RenderShape.ENTITYBLOCK_ANIMATED){var sprite=mc.getBlockRenderer().getBlockModel(cell.state()).getParticleIcon(ModelData.EMPTY);BufferBuilder builder=builders.computeIfAbsent(RenderType.cutout(),k->{var b=new BufferBuilder(65536);b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);return b;});box(cell.offset(),cells,builder,sprite,0xFFFFFF,1);continue;}
            if(cell.state().getRenderShape()!=RenderShape.MODEL)continue;
            var model=mc.getBlockRenderer().getBlockModel(cell.state());
            random.setSeed(cell.state().getSeed(cell.offset()));
            for(RenderType layer:model.getRenderTypes(cell.state(),random,ModelData.EMPTY)) {
                BufferBuilder builder=builders.computeIfAbsent(layer,k->{var b=new BufferBuilder(65536);b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);return b;});
                pose.pushPose();pose.translate(cell.offset().getX(),cell.offset().getY(),cell.offset().getZ());
                for(Direction direction:Direction.values()) {
                    var neighbor=cells.get(cell.offset().relative(direction));if(neighbor!=null && neighbor.state().canOcclude() && cell.state().canOcclude())continue;
                    random.setSeed(cell.state().getSeed(cell.offset()));for(BakedQuad quad:model.getQuads(cell.state(),direction,random,ModelData.EMPTY,layer))put(mc,body,cell,pose,builder,quad,light);
                }
                random.setSeed(cell.state().getSeed(cell.offset()));for(BakedQuad quad:model.getQuads(cell.state(),null,random,ModelData.EMPTY,layer))put(mc,body,cell,pose,builder,quad,light);
                pose.popPose();
            }
        }
        mesh.cursor+=count;for(var row:builders.entrySet()){var data=row.getValue().end();VertexBuffer buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();buffer.upload(data);VertexBuffer.unbind();mesh.layers.computeIfAbsent(row.getKey(),k->new ArrayList<>()).add(buffer);}
    }
    private static void box(BlockPos pos,Map<BlockPos,MatterBody.Cell> cells,BufferBuilder builder,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,int color,float alpha){
        int[][][] faces={{{0,0,1},{1,0,1},{1,0,0},{0,0,0}},{{0,1,0},{1,1,0},{1,1,1},{0,1,1}},{{1,0,0},{1,1,0},{0,1,0},{0,0,0}},{{0,0,1},{0,1,1},{1,1,1},{1,0,1}},{{0,0,0},{0,1,0},{0,1,1},{0,0,1}},{{1,0,1},{1,1,1},{1,1,0},{1,0,0}}};
        for(Direction direction:Direction.values()){if(cells.containsKey(pos.relative(direction)))continue;int[][] face=faces[direction.ordinal()];for(int i=3;i>=0;i--){int[] v=face[i];builder.vertex(pos.getX()+v[0],pos.getY()+v[1],pos.getZ()+v[2]).color(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,alpha).uv(sprite.getU(i==0||i==3?0:16),sprite.getV(i<2?0:16)).uv2(LightTexture.FULL_BRIGHT).normal(direction.getStepX(),direction.getStepY(),direction.getStepZ()).endVertex();}}
    }
    private static void put(Minecraft mc,MatterBody body,MatterBody.Cell cell,PoseStack pose,BufferBuilder builder,BakedQuad quad,int light){
        int color=quad.isTinted()?mc.getBlockColors().getColor(cell.state(),body.level(),body.blockPosition().offset(cell.offset()),quad.getTintIndex()):0xFFFFFF;
        float shade=quad.isShade()?body.level().getShade(quad.getDirection(),true):1;
        builder.putBulkData(pose.last(),quad,((color>>16)&255)/255f*shade,((color>>8)&255)/255f*shade,(color&255)/255f*shade,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
    }
    private static final class Mesh implements AutoCloseable {
        final int revision;int cursor;
        final Map<RenderType,List<VertexBuffer>> layers=new LinkedHashMap<>();
        final Map<BlockPos,MatterBody.Cell> cells=new HashMap<>();
        Mesh(MatterBody body){revision=body.revision();for(var cell:body.cells())cells.put(cell.offset(),cell);}
        public void close(){for(var layer:layers.values())for(var buffer:layer)buffer.close();layers.clear();cells.clear();}
    }
    @Override public ResourceLocation getTextureLocation(MatterBody body){return InventoryMenu.BLOCK_ATLAS;}
}
