package dev.chronicle.power;

import dev.chronicle.Settings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Damage-event reinforcement also covers modded hitscan, magic and explosive sources. */
public final class PersonalDefense {
    public static float reduce(ServerPlayer p,DamageSource source,float amount){
        if(!Potential.active(p)||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return amount;
        double resisted=Potential.level(p)*Settings.DEFENSE.get()*Potential.output(p)*Math.sqrt(Potential.strength());
        if(Concentration.protectedPersonally(p)){
            double shield=Settings.PERSONAL_PROTECTION.get()*Potential.level(p)/10.*Math.sqrt(Potential.output(p)*Potential.strength());
            double overload=Math.sqrt(Math.max(1,amount/Math.max(1,Ward.capacity(p)*8)));
            resisted=Math.max(resisted,shield/overload);
            Concentration.defensiveImpact(p,source.getDirectEntity());
        }
        return amount*(float)Math.max(.01,1-Math.min(.99,resisted));
    }
    public static void intercept(ServerPlayer p){
        if(!Settings.PASSIVE_DEFENSE.get()||!Potential.active(p)||p.tickCount%3!=0)return;
        int count=0;
        for(Projectile projectile:p.level().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(8))){
            if(count++>=Settings.PROJECTILE_BUDGET.get())break;
            if(projectile.getOwner()==p||Physics.controlled(projectile)||Physics.damageEstimate(projectile)>Ward.capacity(p))continue;
            Vec3 velocity=projectile.getDeltaMovement(),toward=p.getBoundingBox().getCenter().subtract(projectile.position());
            if(toward.dot(velocity)<=0||projectile.getBoundingBox().getCenter().distanceToSqr(p.getBoundingBox().getCenter())>64)continue;
            if(p.getBoundingBox().inflate(1).clip(projectile.position(),projectile.position().add(velocity.scale(5))).isEmpty())continue;
            Physics.launch(p,projectile,velocity.scale(-1));
            Concentration.defensiveImpact(p,projectile);
        }
    }
    private PersonalDefense(){}
}
