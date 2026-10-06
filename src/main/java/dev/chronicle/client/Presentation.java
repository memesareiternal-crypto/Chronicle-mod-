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
    private static final KeyMapping ACT = new KeyMapping("key.psychokinesis.action", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.psychokinesis");
    private static final Map<Integer, Wire.View> VIEWS = new HashMap<>();
    private static int lastButtons = -1;
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(GRIP); e.register(ACT); MinecraftForge.EVENT_BUS.register(new Runtime()); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(Chronicle.MATTER.get(), MatterRenderer::new); e.registerEntityRenderer(Chronicle.CRYSTAL.get(), CrystalRenderer::new); }
    @SubscribeEvent public static void items(BuildCreativeModeTabContentsEvent e) { if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) e.accept(Chronicle.CRYSTAL_EGG.get()); }
    public static void receive(Wire.View view) { VIEWS.put(view.entity(), view); }
    private static int buttons() {
        Minecraft mc = Minecraft.getInstance(); if (mc.screen != null || !mc.isWindowActive()) return 0;
        return (GRIP.isDown()?Intent.GRIP:0) | (ACT.isDown()?Intent.ACT:0) | (mc.options.keyShift.isDown()?Intent.SNEAK:0) | (mc.options.keyJump.isDown()?Intent.JUMP:0) | (mc.options.keySprint.isDown()?Intent.SPRINT:0);
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
            var state = VIEWS.get(mc.player.getId()); if (state == null || !state.active() || (!GRIP.isDown() && !ACT.isDown())) return;
            Wire.CHANNEL.sendToServer(new Wire.Input(buttons(), e.getScrollDelta()>0?1:-1)); e.setCanceled(true);
        }
        @SubscribeEvent public void logout(ClientPlayerNetworkEvent.LoggingOut e) { VIEWS.clear(); lastButtons = -1; }
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
                if (player == mc.player && state.a()!=null && state.domain()==Intent.Domain.VOLUME.ordinal()) {
                    BlockPos a=state.a(), b=state.b()==null?a:state.b();
                    var box=new AABB(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1,Math.max(a.getY(),b.getY())+1,Math.max(a.getZ(),b.getZ())+1);
                    LevelRenderer.renderLineBox(pose,lines,box,.6f,.8f,1f,.65f);
                }
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
    private Presentation() {}
}
