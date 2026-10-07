package dev.chronicle.power;

import dev.chronicle.Settings;
import dev.chronicle.entity.MatterBody;
import dev.chronicle.network.Wire;
import dev.chronicle.world.MassJobs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Momentum opens real cavities and ejects preserved terrain rather than deleting blast spheres. */
public final class Impacts {
    private static final Map<UUID,Long> RECENT=new HashMap<>();
    public static void resolve(ServerPlayer owner,Entity body,Vec3 previous,Vec3 velocity){
        double speed=velocity.length(),energy=Physics.impactDamage(body,speed);
        if(speed<1.2||energy<8)return;
        long now=owner.level().getGameTime();
        if(RECENT.getOrDefault(owner.getUUID(),-100L)+4>now)return;
        RECENT.put(owner.getUUID(),now);
        Vec3 direction=velocity.normalize();
        Vec3 from=body.getBoundingBox().getCenter().subtract(direction.scale(Math.max(2,body.getBbWidth()*.6)));
        Vec3 to=body.getBoundingBox().getCenter().add(direction.scale(Math.max(3,body.getBbWidth()*.6)));
        var hit=owner.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        Vec3 center=hit.getType()==HitResult.Type.BLOCK?hit.getLocation():body.getBoundingBox().getCenter();
        double radius=Math.min(16,1+Math.sqrt(energy)*.7)*Math.sqrt(Potential.output(owner));
        for(LivingEntity victim:owner.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius))){
            if(victim==body||!Physics.allowed(owner,victim)||Physics.controlled(victim))continue;
            double falloff=Math.max(0,1-victim.distanceToSqr(center)/(radius*radius));
            if(falloff<=0)continue;
            victim.hurt(owner.damageSources().thrown(body,owner),(float)(energy*.35*falloff));
            Physics.impulse(owner,victim,victim.position().subtract(center).normalize().add(0,.25,0).scale(speed*falloff));
        }
        if(Settings.GEOMETRY_EFFECTS.get())Wire.CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(center.x,center.y,center.z,128,owner.level().dimension())),new Wire.Effect(Wire.Effect.EXPLOSION,center,radius,Potential.color(owner)));
        if(!Settings.TERRAIN.get()||hit.getType()!=HitResult.Type.BLOCK)return;
        int r=Math.max(1,Math.min(8,(int)Math.floor(radius)));
        MassJobs.capture(owner,hit.getBlockPos(),r,Math.min(Potential.blocks(owner),4096),debris->{
            if(debris==null)return;
            debris.release();
            Physics.launch(owner,debris,direction.scale(Math.min(4,speed*.4)).add(0,1,0));
        });
    }
    public static void clear(){RECENT.clear();}
    private Impacts(){}
}
