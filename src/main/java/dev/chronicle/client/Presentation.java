package dev.chronicle.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.chronicle.Chronicle;
import dev.chronicle.network.Wire;
import dev.chronicle.power.Intent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@Mod.EventBusSubscriber(modid=Chronicle.ID, bus=Mod.EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class Presentation {
    private static final KeyMapping GRIP = new KeyMapping("key.psychokinesis.focus", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.psychokinesis");
    private static final KeyMapping BARRIER = new KeyMapping("key.psychokinesis.barrier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.psychokinesis");
    private static final Map<Integer, Wire.View> VIEWS = new HashMap<>();
    private static final List<Visual> EFFECTS = new ArrayList<>();
    private static int lastButtons = -1;
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(GRIP); e.register(BARRIER); MinecraftForge.EVENT_BUS.register(new Runtime()); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(Chronicle.MATTER.get(), MatterRenderer::new); e.registerEntityRenderer(Chronicle.CRYSTAL.get(), CrystalRenderer::new); }
    @SubscribeEvent public static void items(BuildCreativeModeTabContentsEvent e) { if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) e.accept(Chronicle.CRYSTAL_EGG.get()); }
    public static void receive(Wire.View view) { VIEWS.put(view.entity(), view); }
    public static void receive(Wire.Effect effect) { Minecraft mc=Minecraft.getInstance(); if(mc.level!=null) EFFECTS.add(new Visual(effect,mc.level.getGameTime())); }
    private static int buttons() {
        Minecraft mc = Minecraft.getInstance(); if (mc.screen != null || !mc.isWindowActive()) return 0;
        return (GRIP.isDown()?Intent.GRIP:0) | (mc.options.keyUse.isDown()?Intent.ACT:0) | (mc.options.keyShift.isDown()?Intent.SNEAK:0) | (mc.options.keyJump.isDown()?Intent.JUMP:0) | (mc.options.keySprint.isDown()?Intent.SPRINT:0) | (BARRIER.isDown()?Intent.BARRIER:0);
    }
    public static final class Runtime {
        @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance(); if (mc.player == null) { lastButtons = -1; return; }
            var state = VIEWS.get(mc.player.getId()); if (state == null || !state.acquired()) return;
            int bits = buttons(); if (bits != lastButtons || bits != 0) { Wire.CHANNEL.sendToServer(new Wire.Input(bits, 0)); lastButtons = bits; }
        }
        @SubscribeEvent public void scroll(InputEvent.MouseScrollingEvent e) {
            Minecraft mc = Minecraft.getInstance(); if (mc.player == null || mc.screen != null) return;
            var state = VIEWS.get(mc.player.getId()); if (state == null || !state.active() || (!GRIP.isDown() && !BARRIER.isDown())) return;
            Wire.CHANNEL.sendToServer(new Wire.Input(buttons(), e.getScrollDelta()>0?1:-1)); e.setCanceled(true);
        }
        @SubscribeEvent public void interaction(InputEvent.InteractionKeyMappingTriggered e) {
            Minecraft mc=Minecraft.getInstance(); if(mc.player==null || !e.isUseItem()) return;
            var state=VIEWS.get(mc.player.getId());
            if(state!=null && state.active()) { e.setSwingHand(false); e.setCanceled(true); }
        }
        @SubscribeEvent public void logout(ClientPlayerNetworkEvent.LoggingOut e) { VIEWS.clear(); EFFECTS.clear(); lastButtons = -1; }
        @SubscribeEvent public void world(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            Minecraft mc = Minecraft.getInstance(); if (mc.level == null || mc.player == null) return;
            var pose = e.getPoseStack(); Vec3 eye = e.getCamera().getPosition(); pose.pushPose(); pose.translate(-eye.x,-eye.y,-eye.z);
            var lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            for (var player : mc.level.players()) {
                var state = VIEWS.get(player.getId()); if (state == null || !state.active()) continue;
                if (state.color() >= 0 && (player != mc.player || !mc.options.getCameraType().isFirstPerson())) {
                    float r=((state.color()>>16)&255)/255f, g=((state.color()>>8)&255)/255f, b=(state.color()&255)/255f;
                    var box=player.getBoundingBox().move(player.getPosition(e.getPartialTick()).subtract(player.position())).inflate(.035);
                    LevelRenderer.renderLineBox(pose,lines,box,r,g,b,.25f);
                }
                if (state.effects() && state.ward()!=null && state.radius()>0) {
                    float r=((state.color()>>16)&255)/255f, g=((state.color()>>8)&255)/255f, b=(state.color()&255)/255f;
                    double q=state.radius(), thin=state.plane()?.12:q;
                    AABB box=new AABB(state.ward().x-q,state.ward().y-(state.plane()?q:thin),state.ward().z-thin,state.ward().x+q,state.ward().y+(state.plane()?q:thin),state.ward().z+thin);
                    LevelRenderer.renderLineBox(pose,lines,box,r,g,b,.35f+.55f*state.integrity());
                    if(state.integrity()<.7f) LevelRenderer.renderLineBox(pose,lines,box.inflate(-q*.14),1f,.35f,.5f,.6f);
                }
            }
            long now=mc.level.getGameTime(); EFFECTS.removeIf(v->now-v.started>18);
            for(Visual v:EFFECTS) {
                double t=Math.min(1,(now-v.started+e.getPartialTick())/18.), q=v.effect.radius()*t;
                float r=((v.effect.color()>>16)&255)/255f,g=((v.effect.color()>>8)&255)/255f,b=(v.effect.color()&255)/255f,a=(float)(1-t);
                Vec3 c=v.effect.center();
                LevelRenderer.renderLineBox(pose,lines,new AABB(c.x-q,c.y-q*(v.effect.kind()==Wire.Effect.WAVE?.15:1),c.z-q,c.x+q,c.y+q*(v.effect.kind()==Wire.Effect.WAVE?.15:1),c.z+q),r,g,b,a);
                if(v.effect.kind()==Wire.Effect.EXPLOSION && q>1) LevelRenderer.renderLineBox(pose,lines,new AABB(c.x-q*.7,c.y-q*.7,c.z-q*.7,c.x+q*.7,c.y+q*.7,c.z+q*.7),.8f,.9f,1f,a*.7f);
            }
            // Snapped placement preview uses the actual carrier footprint and the existing crosshair.
            if (GRIP.isDown() && mc.options.keySprint.isDown() && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
                var bodies=mc.level.getEntitiesOfClass(dev.chronicle.entity.MatterBody.class,mc.player.getBoundingBox().inflate(32));
                if (!bodies.isEmpty()) { var body=bodies.stream().min(Comparator.comparingDouble(x->x.distanceToSqr(mc.player))).orElseThrow();
                    BlockPos base=hit.getBlockPos().relative(hit.getDirection()).offset(-body.width()/2,0,-body.depth()/2);
                    LevelRenderer.renderLineBox(pose,lines,new AABB(base,base.offset(body.width(),body.extent().getY(),body.depth())),.7f,1f,.8f,.45f);
                }
            }
            pose.popPose(); mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }
    }
    private record Visual(Wire.Effect effect,long started) {}
    private Presentation() {}
}
