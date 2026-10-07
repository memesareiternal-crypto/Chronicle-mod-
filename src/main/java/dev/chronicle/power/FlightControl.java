package dev.chronicle.power;

import dev.chronicle.Settings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import static dev.chronicle.power.Intent.*;

/** A self-applied force: no creative permissions, fixed speed, or flight potion. */
public final class FlightControl {
    private boolean active, oldGravity;
    public boolean active() { return active; }
    public void toggle(ServerPlayer p) {
        if(active) close(p); else { oldGravity=p.isNoGravity();active=true;p.getPersistentData().putBoolean("chronicle_self_flight",true);p.getPersistentData().putBoolean("chronicle_flight_old_gravity",oldGravity);p.setNoGravity(true); }
    }
    public static Vec3 desired(ServerPlayer p, Intent input) {
        Vec3 look=p.getLookAngle(), right=look.cross(new Vec3(0,1,0)).normalize();
        Vec3 direction=Vec3.ZERO;
        if(input.has(FORWARD)) direction=direction.add(look);
        if(input.has(BACK)) direction=direction.subtract(look);
        if(input.has(LEFT)) direction=direction.subtract(right);
        if(input.has(RIGHT)) direction=direction.add(right);
        if(input.has(JUMP)) direction=direction.add(0,1,0);
        if(input.has(SNEAK)) direction=direction.add(0,-1,0);
        double speed=(Settings.FLIGHT_SPEED.get()+(Potential.level(p)-1)*Settings.FLIGHT_SPEED_GROWTH.get())*Math.sqrt(Potential.strength()*Potential.output(p))*Potential.control(p);
        if(input.has(SPRINT)) speed*=1.8;
        return direction.lengthSqr()<1e-6?Vec3.ZERO:direction.normalize().scale(Math.min(5,speed));
    }
    public void tick(ServerPlayer p, Intent input) {
        if(!active) return;
        if(p.isPassenger() || !p.isAlive()) { close(p); return; }
        p.setNoGravity(true); p.fallDistance=0;
        FlightGuard.allowControlledFlight(p);
        Vec3 before=p.getDeltaMovement(), target=desired(p,input), delta=target.subtract(before);
        double acceleration=Settings.FLIGHT_ACCELERATION.get()*(1+Potential.level(p)*.12)*Math.sqrt(Potential.strength())*Potential.control(p)*Potential.output(p);
        Vec3 force=delta.scale(target.lengthSqr()<.001?Settings.FLIGHT_DRAG.get():.25);
        if(force.length()>acceleration) force=force.normalize().scale(acceleration);
        Vec3 next=before.add(force);
        if(next.lengthSqr()<.0004) next=Vec3.ZERO;
        Physics.velocity(p,next);
        Potential.spend(p,Settings.FLIGHT_COST.get()*(.2+force.length()*3+next.length()*.1));
    }
    public void close(ServerPlayer p) { if(active) { p.setNoGravity(oldGravity);active=false;p.getPersistentData().remove("chronicle_self_flight");p.getPersistentData().remove("chronicle_flight_old_gravity"); } }
}
