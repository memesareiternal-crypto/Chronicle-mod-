package dev.chronicle.test;

import dev.chronicle.Chronicle;
import dev.chronicle.power.Concentration;
import dev.chronicle.power.Potential;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in local playtest grants real power once; it never drives the camera or supplies fake presentation state. */
@Mod.EventBusSubscriber(modid=Chronicle.ID,value=Dist.CLIENT)
public final class PlaytestSetup {
    private static boolean ready;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("chronicle.playtest")||ready||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        ready=true;var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);if(p!=null){Potential.maximize(p);Potential.active(p,true);Concentration.synchronize(p);}});
    }
}
