package dev.chronicle.test;

import dev.chronicle.Chronicle;
import dev.chronicle.client.Presentation;
import dev.chronicle.entity.MatterBody;
import dev.chronicle.network.Wire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in development-only renderer fixture, excluded from the production jar. Uses a copied test world. */
@Mod.EventBusSubscriber(modid=Chronicle.ID,value=Dist.CLIENT)
public final class VisualSmoke {
    private static boolean arranged,captured,oldGui,firstCapture,oldPause;
    private static volatile Vec3 cameraAnchor;
    private static int frames;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("chronicle.visualSmoke")||arranged||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        arranged=true;oldGui=mc.options.hideGui;oldPause=mc.options.pauseOnLostFocus;mc.options.hideGui=false;mc.options.pauseOnLostFocus=false;UUID id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayer(id);if(p==null)return;var level=p.serverLevel();int x=p.blockPosition().getX(),z=p.blockPosition().getZ();
            level.setDayTime(18000);level.setWeatherParameters(0,0,false,false);
            BlockPos floor=new BlockPos(x,160,z);for(BlockPos pos:BlockPos.betweenClosed(floor.offset(-12,0,-2),floor.offset(12,0,20)))level.setBlock(pos,Blocks.SMOOTH_STONE.defaultBlockState(),18);
            var fixtureBounds=new net.minecraft.world.phys.AABB(floor.offset(-15,0,-5),floor.offset(15,16,25));for(var body:level.getEntitiesOfClass(MatterBody.class,fixtureBounds))body.discard();for(var crystal:level.getEntitiesOfClass(dev.chronicle.entity.ResonantCrystal.class,fixtureBounds))crystal.discard();
            for(BlockPos pos:BlockPos.betweenClosed(floor.offset(-12,1,-2),floor.offset(12,12,20)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);
            p.setGameMode(GameType.CREATIVE);p.teleportTo(level,x+.5,161,z+.5,0,3);
            cameraAnchor=new Vec3(x+.5,162.62,z+.5);
            List<BlockPos> blocks=new ArrayList<>();for(BlockPos pos:BlockPos.betweenClosed(floor.offset(-4,3,9),floor.offset(-1,5,12))){level.setBlock(pos,(pos.getY()%2==0?Blocks.OAK_PLANKS:Blocks.STONE_BRICKS).defaultBlockState(),18);blocks.add(pos.immutable());}
            MatterBody body=MatterBody.capture(level,blocks,null);if(body!=null)body.transferring(true);
            var crystal=Chronicle.CRYSTAL.get().create(level);if(crystal!=null){crystal.setPos(x+4,161,z+10);level.addFreshEntity(crystal);}
        });
    }
    @SubscribeEvent public static void frame(RenderLevelStageEvent event){
        if(!Boolean.getBoolean("chronicle.visualSmoke")||!arranged)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY&&cameraAnchor!=null){Vec3 c=cameraAnchor.add(0,.4,9);Presentation.receive(new Wire.View(mc.player.getId(),true,true,0x83D8EE,true,c,4,false,.75f,new Vec3(0,0,-1),0,c.add(0,0,-4),8,false,false,.65f));}
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)public static void screenshot(RenderGuiEvent.Post event){if(!Boolean.getBoolean("chronicle.visualSmoke")||!arranged||captured)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;if(mc.player.tickCount>140&&!firstCapture){firstCapture=true;event.getGuiGraphics().flush();Screenshot.grab(mc.gameDirectory,"psychokinesis-visual-smoke.png",mc.getMainRenderTarget(),message->com.mojang.logging.LogUtils.getLogger().info("Chronicle visual smoke screenshot: {}",message.getString()));}if(mc.player.tickCount>180){captured=true;event.getGuiGraphics().flush();Screenshot.grab(mc.gameDirectory,"psychokinesis-glow-second-phase.png",mc.getMainRenderTarget(),message->{com.mojang.logging.LogUtils.getLogger().info("Chronicle glow phase screenshot: {}",message.getString());mc.execute(()->{mc.options.hideGui=oldGui;mc.options.pauseOnLostFocus=oldPause;mc.setCameraEntity(mc.player);mc.stop();});});}}
}
