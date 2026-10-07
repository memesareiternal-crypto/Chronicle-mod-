package dev.chronicle.test;

import dev.chronicle.Chronicle;
import dev.chronicle.power.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Opt-in, isolated client test. Presses actual movement keys, receives real network state,
 * and measures vanilla client displacement. Never locks or drives the camera each frame. */
@Mod.EventBusSubscriber(modid=Chronicle.ID,value=Dist.CLIENT)
public final class FlightClientAudit {
    private static boolean arranged,started,oldPause;
    private static volatile boolean ready;
    private static int tick,wait;
    private static Vec3 start,forwardEnd,strafeEnd;
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("chronicle.flightAudit")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(!arranged){
            arranged=true;oldPause=mc.options.pauseOnLostFocus;mc.options.pauseOnLostFocus=false;
            UUID id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);if(p==null)return;
                var level=p.serverLevel();int x=p.blockPosition().getX(),z=p.blockPosition().getZ();
                for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(x-12,239,z-12),new BlockPos(x+12,239,z+80)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
                p.setGameMode(GameType.SURVIVAL);p.teleportTo(level,x+.5,240,z+.5,0,0);
                Potential.maximize(p);Potential.active(p,true);Concentration.synchronize(p);
                var crystal=Chronicle.CRYSTAL.get().create(level);if(crystal!=null){crystal.setPos(x+5,240,z+12);level.addFreshEntity(crystal);}
                ready=true;
            });return;
        }
        if(!ready||mc.player.getY()<235)return;
        if(mc.screen!=null||!mc.isWindowActive()){
            if(++wait>600)finish(mc,"FAIL: isolated test window never became active");return;
        }
        if(!started){started=true;start=mc.player.position();}
        tick++;
        // Set keys after the ordinary Presentation sampler. Each edge enters next tick
        // through the exact same packets as a human's keyboard input.
        mc.options.keyJump.setDown(tick==2||tick==5||tick>=40&&tick<52||tick==82||tick==85);
        mc.options.keyUp.setDown(tick>=7&&tick<37);
        mc.options.keyLeft.setDown(tick>=40&&tick<52);
        mc.options.keyShift.setDown(false);
        if(tick==36){
            forwardEnd=mc.player.position();
            if(forwardEnd.distanceTo(start)<30||!mc.player.isNoGravity()){finish(mc,"FAIL: real client forward flight did not move at speed");return;}
        }
        if(tick==55){
            strafeEnd=mc.player.position();
            if(strafeEnd.x-forwardEnd.x<6||strafeEnd.y-forwardEnd.y<5){finish(mc,"FAIL: client strafe/ascent did not respond");return;}
        }
        if(tick==78&&mc.player.getDeltaMovement().length()>.06){finish(mc,"FAIL: flight failed to brake into hover");return;}
        if(tick==98){
            if(mc.player.isNoGravity()||mc.player.getAbilities().mayfly){finish(mc,"FAIL: double Space failed to restore survival gravity");return;}
            final Vec3 finalPosition=mc.player.position();var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                String result=p!=null&&p.position().distanceTo(finalPosition)<5&&!p.getAbilities().mayfly
                    ?"PASS: actual keyboard/network/client flight; forward="+forwardEnd.distanceTo(start)+", strafe="+(strafeEnd.x-forwardEnd.x)+", rise="+(strafeEnd.y-forwardEnd.y)+", authoritative server displacement confirmed"
                    :"FAIL: server/client flight positions diverged";
                mc.execute(()->finish(mc,result));
            });
        }
    }
    private static void finish(Minecraft mc,String result){
        System.clearProperty("chronicle.flightAudit");
        mc.options.keyJump.setDown(false);mc.options.keyUp.setDown(false);mc.options.keyLeft.setDown(false);mc.options.pauseOnLostFocus=oldPause;
        com.mojang.logging.LogUtils.getLogger().info("CHRONICLE_FLIGHT_AUDIT {}",result);
        try{
            java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("flight-audit-result.txt"),result);
        }catch(java.io.IOException ex){throw new RuntimeException(ex);}
        Screenshot.grab(mc.gameDirectory,"flight-audit.png",mc.getMainRenderTarget(),message->{});
        mc.stop();
    }
}
