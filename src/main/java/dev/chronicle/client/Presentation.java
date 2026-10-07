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
    private static final KeyMapping FORCE = new KeyMapping("key.psychokinesis.force", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.psychokinesis");
    private static Wire.FlightState flightState=new Wire.FlightState(false,0,0,0,false);
    private static boolean predictedFlight, oldGravity;
    private static final Map<Integer, Wire.View> VIEWS = new HashMap<>();
    private static final List<Visual> EFFECTS = new ArrayList<>();
    private static int lastButtons = -1;
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(GRIP); e.register(BARRIER); e.register(FORCE); MinecraftForge.EVENT_BUS.register(new Runtime()); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(Chronicle.MATTER.get(), MatterRenderer::new); e.registerEntityRenderer(Chronicle.CRYSTAL.get(), CrystalRenderer::new); }
    @SubscribeEvent public static void reload(ModelEvent.BakingCompleted e){MatterRenderer.clear();}
    @SubscribeEvent public static void items(BuildCreativeModeTabContentsEvent e) { if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) e.accept(Chronicle.CRYSTAL_EGG.get()); }
    @SubscribeEvent public static void eggColors(RegisterColorHandlersEvent.Item e){e.register((stack,index)->index==0?0x171B26:0xEDEBF8,Chronicle.CRYSTAL_EGG.get());}
    public static void receive(Wire.FlightState state) {flightState=state;if(!state.active())predictFlight(0);}
    private static void predictFlight(int bits) {
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(flightState.active()&&!mc.player.isPassenger()){
            if(!predictedFlight){oldGravity=flightState.originalGravity();predictedFlight=true;}
            mc.player.setNoGravity(true);mc.player.fallDistance=0;
            mc.player.setDeltaMovement(dev.chronicle.power.FlightControl.integrate(mc.player.getDeltaMovement(),mc.player.getLookAngle(),new Intent(bits,0),flightState.speed(),flightState.acceleration(),flightState.braking()));
        }else if(predictedFlight){mc.player.setNoGravity(oldGravity);mc.player.fallDistance=0;predictedFlight=false;}
    }
    public static void receive(Wire.View view) { VIEWS.put(view.entity(),view); }
    public static void receive(Wire.Snapshot snapshot){Minecraft mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.getEntity(snapshot.entity()) instanceof dev.chronicle.entity.MatterBody body){var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(snapshot.cells()));try{body.readSpawnData(buffer);}finally{buffer.release();}}}
    public static void receive(Wire.Effect effect) { Minecraft mc=Minecraft.getInstance(); if(mc.level!=null) EFFECTS.add(new Visual(effect,mc.level.getGameTime())); }
    private static int buttons() {
        Minecraft mc = Minecraft.getInstance(); if (mc.screen != null || !mc.isWindowActive()) return 0;
        return (GRIP.isDown()?Intent.GRIP:0) | (mc.options.keyUse.isDown()?Intent.ACT:0) | (mc.options.keyShift.isDown()?Intent.SNEAK:0) | (mc.options.keyJump.isDown()?Intent.JUMP:0) | (mc.options.keySprint.isDown()?Intent.SPRINT:0) | (BARRIER.isDown()?Intent.BARRIER:0) | (mc.options.keyUp.isDown()?Intent.FORWARD:0) | (mc.options.keyDown.isDown()?Intent.BACK:0) | (mc.options.keyLeft.isDown()?Intent.LEFT:0) | (mc.options.keyRight.isDown()?Intent.RIGHT:0) | (mc.options.keyAttack.isDown()?Intent.ATTACK:0) | (FORCE.isDown()?Intent.FORCE:0);
    }
    public static final class Runtime {
        @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance(); if (mc.player == null) { lastButtons = -1; return; }
            var state = VIEWS.get(mc.player.getId()); if (state == null || !state.acquired()) return;
            int bits = buttons(); predictFlight(bits); if (bits != lastButtons || bits != 0) { Wire.CHANNEL.sendToServer(new Wire.Input(bits, 0)); lastButtons = bits; }
        }
        @SubscribeEvent public void scroll(InputEvent.MouseScrollingEvent e) {
            Minecraft mc = Minecraft.getInstance(); if (mc.player == null || mc.screen != null) return;
            var state = VIEWS.get(mc.player.getId()); if (state == null || !state.active() || (!mc.options.keyShift.isDown() && !GRIP.isDown() && !mc.options.keyUse.isDown() && !BARRIER.isDown())) return;
            Wire.CHANNEL.sendToServer(new Wire.Input(buttons(),e.getScrollDelta()>0?1:-1));
        }
        @SubscribeEvent public void interaction(InputEvent.InteractionKeyMappingTriggered e) {
            Minecraft mc=Minecraft.getInstance(); if(mc.player==null) return;
            var state=VIEWS.get(mc.player.getId());
            if(state!=null && state.active() && (e.isUseItem() || (e.isAttack()&&(state.holding()||mc.options.keyUse.isDown()||FORCE.isDown())))) { e.setSwingHand(false); e.setCanceled(true); }
        }
        @SubscribeEvent public void logout(ClientPlayerNetworkEvent.LoggingOut e) { VIEWS.clear(); EFFECTS.clear(); flightState=new Wire.FlightState(false,0,0,0,false); predictedFlight=false; MatterRenderer.clear(); lastButtons = -1; }
        @SubscribeEvent public void output(RenderGuiEvent.Post e){Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;var state=VIEWS.get(mc.player.getId());if(state==null||!state.active())return;int width=182,x=mc.getWindow().getGuiScaledWidth()/2-width/2;boolean survival=mc.gameMode!=null&&mc.gameMode.canHurtPlayer();int extra=survival?Math.max(0,(int)Math.ceil((mc.player.getMaxHealth()+mc.player.getAbsorptionAmount())/20.)-1)*10:0;int y=mc.getWindow().getGuiScaledHeight()-(survival?52+extra:35);e.getGuiGraphics().fill(x-1,y-1,x+width+1,y+4,0x66000000);int filled=Math.round(width*state.output());for(int i=0;i<filled;i++){int color=net.minecraft.util.Mth.hsvToRgb((float)((i/(double)width+mc.player.tickCount*.002)%1),.4f,1f);e.getGuiGraphics().fill(x+i,y,x+i+1,y+3,0xBB000000|color);}}
        @SubscribeEvent public void world(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
            MatterRenderer.beginFrame();
            Minecraft mc = Minecraft.getInstance(); if (mc.level == null || mc.player == null) return;
            var pose = e.getPoseStack(); Vec3 eye = e.getCamera().getPosition(); pose.pushPose(); pose.translate(-eye.x,-eye.y,-eye.z);
            var lines = mc.renderBuffers().bufferSource().getBuffer(PsychicRenderType.FILM);
            for (var player : mc.level.players()) {
                var state = VIEWS.get(player.getId()); if (state == null || !state.active()) continue;
                if (state.color() >= 0 && (player != mc.player || !mc.options.getCameraType().isFirstPerson())) {
                    var box=player.getBoundingBox().move(player.getPosition(e.getPartialTick()).subtract(player.position())).inflate(.035);
                    PsychicGeometry.aura(pose,lines,box.getCenter(),player.getBbWidth()*.57,player.getBbHeight()*.55,state.color());
                }
                if (state.effects() && state.ward()!=null && state.radius()>0) {
                    int color=state.color()<0?0xA8D7FF:state.color();float alpha=.16f+.18f*state.integrity();
                    if(state.plane()){PsychicGeometry.disc(pose,lines,state.ward(),state.normal(),state.radius(),color,alpha);}
                    else PsychicGeometry.shell(pose,lines,state.ward(),state.radius(),state.shape()==1,color,alpha);
                    if(state.integrity()<.6f)PsychicGeometry.cracks(pose,lines,state.ward().add(state.normal().scale(state.plane()?0:state.radius())),state.normal(),state.radius()*.6,color,1-state.integrity());
                    if(state.impact()!=null&&state.impactAge()<15){Vec3 normal=state.plane()?state.normal():state.impact().subtract(state.ward()).normalize();PsychicGeometry.ring(pose,lines,state.impact().add(normal.scale(.03)),normal,.15+state.impactAge()*.07,color,(1-state.impactAge()/15f)*.8f);}
                }
            }
            long now=mc.level.getGameTime(); EFFECTS.removeIf(v->now-v.started>18);
            for(Visual v:EFFECTS) {
                double t=Math.min(1,(now-v.started+e.getPartialTick())/18.), q=v.effect.radius()*t;
                float a=(float)(1-t);
                Vec3 c=v.effect.center();
                if(v.effect.kind()==Wire.Effect.EXPLOSION){PsychicGeometry.shell(pose,lines,c,q,false,v.effect.color(),a*.55f);PsychicGeometry.ring(pose,lines,c,new Vec3(0,1,0),q*1.25,v.effect.color(),a*.8f);}
                else PsychicGeometry.ring(pose,lines,c,new Vec3(0,1,0),q,v.effect.color(),a*.5f);
            }
            // Snapped placement preview uses the actual carrier footprint and the existing crosshair.
            if ((GRIP.isDown()||mc.options.keyUse.isDown()) && mc.options.keySprint.isDown() && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
                var bodies=mc.level.getEntitiesOfClass(dev.chronicle.entity.MatterBody.class,mc.player.getBoundingBox().inflate(32));
                if (!bodies.isEmpty()) { var body=bodies.stream().min(Comparator.comparingDouble(x->x.distanceToSqr(mc.player))).orElseThrow();
                    BlockPos base=hit.getBlockPos().relative(hit.getDirection()).offset(-body.width()/2,0,-body.depth()/2);
                    LevelRenderer.renderLineBox(pose,mc.renderBuffers().bufferSource().getBuffer(RenderType.lines()),new AABB(base,base.offset(body.width(),body.extent().getY(),body.depth())),.7f,1f,.8f,.45f);
                }
            }
            pose.popPose();mc.renderBuffers().bufferSource().endBatch(PsychicRenderType.FILM);mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }
    }
    private record Visual(Wire.Effect effect,long started) {}
    private Presentation() {}
}
