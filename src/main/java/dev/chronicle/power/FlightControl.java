package dev.chronicle.power;

import dev.chronicle.Settings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import static dev.chronicle.power.Intent.*;

/** Shared client/server flight integrator. Survival permissions and camera are untouched. */
public final class FlightControl {
    private boolean active, oldGravity;
    private int lastJump=-1000;
    public boolean active() { return active; }
    public boolean originalGravity() { return oldGravity; }
    public boolean gesture(ServerPlayer p, Intent input, int previous) {
        if(!input.has(JUMP)||(previous&JUMP)!=0)return false;
        if(p.tickCount-lastJump<=7) {
            lastJump=-1000;
            if(Potential.level(p)>=Settings.FLIGHT_LEVEL.get()&&!p.isPassenger()){toggle(p);return true;}
        } else lastJump=p.tickCount;
        return false;
    }
    public void toggle(ServerPlayer p) {
        if(active) close(p);
        else {
            oldGravity=p.isNoGravity(); active=true;
            p.getPersistentData().putBoolean("chronicle_self_flight",true);
            p.getPersistentData().putBoolean("chronicle_flight_old_gravity",oldGravity);
            p.setNoGravity(true);p.fallDistance=0;
        }
    }
    public static double speed(ServerPlayer p) {
        return Math.min(5,(Settings.FLIGHT_SPEED.get()+(Potential.level(p)-1)*Settings.FLIGHT_SPEED_GROWTH.get())*Math.sqrt(Potential.strength()*Potential.output(p)));
    }
    public static double acceleration(ServerPlayer p) {
        return Math.min(2,Settings.FLIGHT_ACCELERATION.get()*(1+Potential.level(p)*.12)*Math.sqrt(Potential.strength()*Potential.output(p)));
    }
    public static Vec3 desired(Vec3 look,Intent input,double speed) {
        Vec3 right=look.cross(new Vec3(0,1,0));
        if(right.lengthSqr()<.001)right=new Vec3(1,0,0);else right=right.normalize();
        Vec3 direction=Vec3.ZERO;
        if(input.has(FORWARD))direction=direction.add(look);
        if(input.has(BACK))direction=direction.subtract(look);
        if(input.has(LEFT))direction=direction.subtract(right);
        if(input.has(RIGHT))direction=direction.add(right);
        if(input.has(JUMP))direction=direction.add(0,1,0);
        if(input.has(SNEAK))direction=direction.add(0,-1,0);
        if(input.has(SPRINT))speed=Math.min(5,speed*1.8);
        return direction.lengthSqr()<1e-6?Vec3.ZERO:direction.normalize().scale(speed);
    }
    public static Vec3 desired(ServerPlayer p, Intent input) {return desired(p.getLookAngle(),input,speed(p));}
    public static Vec3 integrate(Vec3 before,Vec3 look,Intent input,double speed,double acceleration,double braking) {
        Vec3 target=desired(look,input,speed);
        Vec3 force=target.subtract(before).scale(target.lengthSqr()<.001?braking:.55);
        if(force.length()>acceleration)force=force.normalize().scale(acceleration);
        Vec3 next=before.add(force);
        if(next.length()>5)next=next.normalize().scale(5);
        return next.lengthSqr()<.0004?Vec3.ZERO:next;
    }
    public void tick(ServerPlayer p, Intent input) {
        if(!active)return;
        if(p.isPassenger()||!p.isAlive()){close(p);return;}
        p.setNoGravity(true);p.fallDistance=0;FlightGuard.allowControlledFlight(p);
        // Local flight uses this same integrator before the next vanilla movement step.
        // Repeated server motion packets would overwrite that prediction and cause hovering.
        p.setDeltaMovement(integrate(p.getDeltaMovement(),p.getLookAngle(),input,speed(p),acceleration(p),Settings.FLIGHT_DRAG.get()));
        Potential.practice(p,.2);
    }
    public void close(ServerPlayer p) {
        if(!active)return;
        p.setNoGravity(oldGravity);p.fallDistance=0;active=false;
        p.getPersistentData().remove("chronicle_self_flight");
        p.getPersistentData().remove("chronicle_flight_old_gravity");
    }
}
