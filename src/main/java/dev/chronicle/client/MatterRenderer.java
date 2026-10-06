package dev.chronicle.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.chronicle.entity.MatterBody;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;
import java.util.*;

/** Greedy exterior meshing: identical adjacent faces become one rectangle, baked once per body. */
public final class MatterRenderer extends EntityRenderer<MatterBody> {
    private final Map<MatterBody,List<Face>> cache = new WeakHashMap<>();
    public MatterRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(MatterBody body,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        var faces=cache.computeIfAbsent(body,this::bake);
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(-90*body.turn())); pose.translate(-body.extent().getX()/2.,0,-body.extent().getZ()/2.);
        VertexConsumer out=buffers.getBuffer(RenderType.debugQuads());
        for (Face face:faces) face.draw(pose.last().pose(),out);
        pose.popPose(); super.render(body,yaw,partial,pose,buffers,light);
    }
    private record Square(int u,int v) {}
    private record Slice(Direction direction,int plane,int color) {}
    private List<Face> bake(MatterBody body) {
        Set<BlockPos> occupied=new HashSet<>(); for(var c:body.cells()) occupied.add(c.offset());
        Map<Slice,TreeSet<Square>> slices=new HashMap<>();
        for(var c:body.cells()) {
            BlockPos p=c.offset(); int color=c.state().getMapColor(body.level(),body.blockPosition()).col; if(color==0) color=0x8998A5;
            for(Direction d:Direction.values()) {
                if(occupied.contains(p.relative(d))) continue;
                int plane,u,v;
                switch(d.getAxis()) { case X -> { plane=p.getX();u=p.getZ();v=p.getY(); } case Y -> {plane=p.getY();u=p.getX();v=p.getZ();} default -> {plane=p.getZ();u=p.getX();v=p.getY();} }
                if(d.getAxisDirection()==Direction.AxisDirection.POSITIVE) plane++;
                slices.computeIfAbsent(new Slice(d,plane,color),unused->new TreeSet<>(Comparator.comparingInt(Square::v).thenComparingInt(Square::u))).add(new Square(u,v));
            }
        }
        List<Face> faces=new ArrayList<>();
        slices.forEach((slice,squares)->{
            while(!squares.isEmpty()) {
                var start=squares.first(); int w=1,h=1;
                while(squares.contains(new Square(start.u+w,start.v))) w++;
                outer:while(true) {for(int x=0;x<w;x++) if(!squares.contains(new Square(start.u+x,start.v+h))) break outer;h++;}
                for(int x=0;x<w;x++) for(int y=0;y<h;y++) squares.remove(new Square(start.u+x,start.v+y));
                faces.add(new Face(slice,start.u,start.v,w,h));
            }
        }); return faces;
    }
    private record Face(Slice slice,int u,int v,int width,int height) {
        void draw(Matrix4f m,VertexConsumer out) {
            float shade=slice.direction.getAxis()==Direction.Axis.Y?1:slice.direction.getAxis()==Direction.Axis.X?.72f:.86f;
            float r=((slice.color>>16)&255)/255f*shade,g=((slice.color>>8)&255)/255f*shade,b=(slice.color&255)/255f*shade;
            point(m,out,u,v,r,g,b);point(m,out,u+width,v,r,g,b);point(m,out,u+width,v+height,r,g,b);point(m,out,u,v+height,r,g,b);
        }
        void point(Matrix4f m,VertexConsumer out,float a,float b,float r,float g,float blue) {
            switch(slice.direction.getAxis()) {
                case X -> out.vertex(m,slice.plane,b,a).color(r,g,blue,1).endVertex();
                case Y -> out.vertex(m,a,slice.plane,b).color(r,g,blue,1).endVertex();
                case Z -> out.vertex(m,a,b,slice.plane).color(r,g,blue,1).endVertex();
            }
        }
    }
    @Override public ResourceLocation getTextureLocation(MatterBody e) { return InventoryMenu.BLOCK_ATLAS; }
}
