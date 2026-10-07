package dev.chronicle.power;

import dev.chronicle.Settings;
import dev.chronicle.entity.MatterBody;
import dev.chronicle.entity.ResonantCrystal;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Ownership, reversible suspension, and swept collision accounting are shared by every application. */
public final class Physics {
    private static final Map<UUID, UUID> CLAIMS = new HashMap<>();
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    public static boolean controlled(Entity e) { return CLAIMS.containsKey(e.getUUID()); }
    public static boolean allowed(ServerPlayer p, Entity e) {
        if (!e.isAlive() || e == p || e instanceof ResonantCrystal || e.isSpectator()) return false;
        if (e instanceof ServerPlayer other && (!Settings.PVP.get() || other.isCreative() || !p.canHarmPlayer(other))) return false;
        for (Entity passenger : e.getPassengers()) if (!allowed(p, passenger)) return false;
        return true;
    }
    public static double mass(Entity e) {
        double value=e instanceof MatterBody b?Math.max(1,b.physicalMass()):e instanceof ItemEntity item?.15+item.getItem().getCount()*.015:Math.max(.2,e.getBbWidth()*e.getBbWidth()*e.getBbHeight()*4);
        for(Entity passenger:e.getPassengers()) value+=mass(passenger);
        return value;
    }
    public static void velocity(Entity e, Vec3 velocity) {
        e.setDeltaMovement(velocity); e.hurtMarked = true;
        if (e instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(e));
    }
    public static Hold take(ServerPlayer owner, Entity raw) {
        Entity e = raw.getRootVehicle();
        if (!allowed(owner, e) || CLAIMS.containsKey(e.getUUID()) || mass(e)>Potential.massLimit(owner) || (e instanceof MatterBody body && body.transferring())) return null;
        for(Entity rider:e.getIndirectPassengers())if(CLAIMS.containsKey(rider.getUUID()))return null;
        if(e instanceof MatterBody body)body.owner(owner.getUUID());
        CLAIMS.put(e.getUUID(), owner.getUUID());
        for(Entity passenger:e.getIndirectPassengers())CLAIMS.put(passenger.getUUID(),owner.getUUID());
        var data = e.getPersistentData();
        data.putBoolean("chronicle_suspended", true); data.putBoolean("chronicle_old_gravity", e.isNoGravity());
        data.putBoolean("chronicle_old_ai", e instanceof Mob mob && mob.isNoAi());
        if(e instanceof AbstractHurtingProjectile f){data.putDouble("chronicle_power_x",f.xPower);data.putDouble("chronicle_power_y",f.yPower);data.putDouble("chronicle_power_z",f.zPower);}
        return new Hold(e, e.isNoGravity(), e instanceof Mob mob && mob.isNoAi());
    }
    public static void recoverOrphan(Entity e) {
        if(e.getPersistentData().getBoolean("chronicle_self_flight")){e.setNoGravity(e.getPersistentData().getBoolean("chronicle_flight_old_gravity"));e.getPersistentData().remove("chronicle_self_flight");e.getPersistentData().remove("chronicle_flight_old_gravity");}
        var d = e.getPersistentData(); if (!d.getBoolean("chronicle_suspended")) return;
        e.setNoGravity(d.getBoolean("chronicle_old_gravity"));
        if (e instanceof Mob mob) mob.setNoAi(d.getBoolean("chronicle_old_ai"));
        if(e instanceof AbstractHurtingProjectile f && d.contains("chronicle_power_x")){f.xPower=d.getDouble("chronicle_power_x");f.yPower=d.getDouble("chronicle_power_y");f.zPower=d.getDouble("chronicle_power_z");}
        d.remove("chronicle_power_x");d.remove("chronicle_power_y");d.remove("chronicle_power_z");
        d.remove("chronicle_suspended"); d.remove("chronicle_old_gravity"); d.remove("chronicle_old_ai");
    }
    public static final class Hold {
        public final Entity entity;
        private final boolean gravity, ai;
        private final Vec3 acceleration;
        private double struggle;
        private double compressionWork;
        private Vec3 anchor;
        private boolean closed;
        private final Set<UUID> claimed=new HashSet<>();
        public int ticks;
        Hold(Entity entity, boolean gravity, boolean ai) {
            this.entity = entity; this.gravity = gravity; this.ai = ai;
            claimed.add(entity.getUUID());for(Entity passenger:entity.getIndirectPassengers())claimed.add(passenger.getUUID());
            acceleration = entity instanceof AbstractHurtingProjectile fireball ? new Vec3(fireball.xPower,fireball.yPower,fireball.zPower) : null;
        }
        public boolean steer(ServerPlayer p, Vec3 destination, boolean rigid) {
            if(closed)return false;
            Set<UUID> topology=new HashSet<>();topology.add(entity.getUUID());for(Entity passenger:entity.getIndirectPassengers())topology.add(passenger.getUUID());
            if(entity instanceof MatterBody&&entity.hasPassenger(p)&&topology.size()==claimed.size()+1&&topology.containsAll(claimed)&&topology.contains(p.getUUID())&&!CLAIMS.containsKey(p.getUUID())){claimed.add(p.getUUID());CLAIMS.put(p.getUUID(),p.getUUID());}
            if(!topology.equals(claimed))return false;
            ticks++;
            if (!entity.isAlive() || entity.level() != p.level() || entity.distanceToSqr(p) > Math.pow(Potential.reach(p) * 1.8, 2)) return false;
            double mass = mass(entity);
            List<LivingEntity> occupants=livingMembers(entity);
            occupants.removeIf(member->member==p);
            if (!occupants.isEmpty()) {
                double resistance=mass*.3;
                for(LivingEntity living:occupants){double boss=living instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||living instanceof net.minecraft.world.entity.boss.wither.WitherBoss||living instanceof net.minecraft.world.entity.monster.warden.Warden?Settings.BOSS_RESISTANCE.get():1;resistance+=(living.getHealth()*.08+living.getMaxHealth()*.02+living.getArmorValue()*.2+living.getDeltaMovement().length()*.2)*boss;}
                resistance*=Settings.RESISTANCE.get();
                double capacity = (5 + Potential.force(p) * 18)*Settings.GRIP_STRENGTH.get()/(1+entity.distanceTo(p)/Math.max(1,Potential.reach(p))*.3);
                struggle = Math.max(0, struggle + (resistance / capacity - 1) * .025 + p.getRandom().nextDouble() * .004);
                if (struggle > 1) return false;
            }
            if (entity instanceof MatterBody body) body.held(); else entity.setNoGravity(true);
            if (entity instanceof Mob mob) mob.setNoAi(true);
            if (entity instanceof AbstractHurtingProjectile fireball) { fireball.xPower=0;fireball.yPower=0;fireball.zPower=0; }
            Vec3 delta = (anchor==null?destination:anchor).subtract(entity.getBoundingBox().getCenter());
            double acceleration = Math.min(1.4, (.8 + Math.sqrt(Potential.force(p))) * Settings.GRIP_STRENGTH.get() / Math.pow(mass,.08));
            Vec3 v = entity.getDeltaMovement().scale(.18).add(delta.scale(acceleration*.65));
            double cap = Math.min(7,2+Math.sqrt(Potential.force(p))*1.5); if (v.length() > cap) v = v.normalize().scale(cap);
            Vec3 actual=rigid?v.scale(.3):v;track(p,entity,actual);velocity(entity,actual);entity.fallDistance=0;
            if(entity instanceof ServerPlayer controlledPlayer)FlightGuard.allowControlledFlight(controlledPlayer);
            return true;
        }
        public void anchor(Vec3 point){anchor=point;}
        public void unanchor(){anchor=null;}
        public boolean anchored(){return anchor!=null;}
        public boolean compress(ServerPlayer p,double output){
            if(closed||!entity.isAlive())return false;
            double work=Potential.force(p)*Math.max(.1,output)/Math.max(1,Math.cbrt(mass(entity)));
            compressionWork+=work;Potential.practice(p,.15+work*.3);
            List<LivingEntity> occupants=livingMembers(entity);
            occupants.removeIf(member->member==p);
            if(!occupants.isEmpty()) {
                if(ticks%8==0){for(LivingEntity living:occupants){double resistance=1+living.getArmorValue()*.15+living.getMaxHealth()*.025;if(compressionWork>=resistance){float amount=(float)Math.min(Settings.MAX_COLLISION_DAMAGE.get(),compressionWork/resistance*Settings.COMPRESSION_DAMAGE.get()/Math.sqrt(occupants.size()));living.hurt(p.damageSources().indirectMagic(p,p),amount);}}compressionWork=0;}
                velocity(entity,entity.getDeltaMovement().scale(.2));
            }else if(entity instanceof MatterBody body && compressionWork>body.physicalMass()*(1+body.hardness())*.25){body.shatter(p);compressionWork=0;}
            return entity.isAlive();
        }
        public void close() {
            if(closed)return;closed=true;
            if (entity instanceof MatterBody body) body.release(); else entity.setNoGravity(gravity);
            if (entity instanceof Mob mob) mob.setNoAi(ai);
            if (entity instanceof AbstractHurtingProjectile fireball && acceleration != null) { fireball.xPower=acceleration.x;fireball.yPower=acceleration.y;fireball.zPower=acceleration.z; }
            for(UUID id:claimed)CLAIMS.remove(id);var d = entity.getPersistentData();
            d.remove("chronicle_suspended"); d.remove("chronicle_old_gravity"); d.remove("chronicle_old_ai");
            d.remove("chronicle_power_x");d.remove("chronicle_power_y");d.remove("chronicle_power_z");
        }
    }
    private static List<LivingEntity> livingMembers(Entity entity){List<LivingEntity> result=new ArrayList<>();if(entity instanceof LivingEntity living)result.add(living);for(Entity passenger:entity.getIndirectPassengers())if(passenger instanceof LivingEntity living)result.add(living);return result;}
    private static void track(ServerPlayer owner,Entity entity,Vec3 velocity) {
        var flight=FLIGHTS.get(entity.getUUID());if(flight==null)FLIGHTS.put(entity.getUUID(),new Flight(owner.getUUID(),entity,velocity));else{if(!flight.owner.equals(owner.getUUID())){flight.owner=owner.getUUID();flight.hit.clear();}flight.life=120;flight.velocity=velocity;}
    }
    public static void impulse(ServerPlayer owner,Entity raw,Vec3 force){Entity entity=raw.getRootVehicle();if(!allowed(owner,entity)||(CLAIMS.containsKey(entity.getUUID())&&!owner.getUUID().equals(CLAIMS.get(entity.getUUID()))))return;Vec3 velocity=entity.getDeltaMovement().add(force.scale(1/Math.max(1,Math.pow(mass(entity),.18))));if(velocity.length()>7)velocity=velocity.normalize().scale(7);track(owner,entity,velocity);velocity(entity,velocity);}
    public static double damageEstimate(Projectile p) {
        if (p instanceof ThrownTrident) return 8;
        if (p instanceof AbstractArrow a) return Math.ceil(a.getBaseDamage() * a.getDeltaMovement().length()) * (a.isCritArrow() ? 1.5 : 1);
        if (p instanceof WitherSkull || p instanceof LargeFireball) return 12;
        if (p instanceof SmallFireball) return 5;
        if (p instanceof Snowball || p instanceof ThrownEgg) return 1;
        // Unknown bullet implementations require an explicit estimate or impact-event fallback.
        var tag = p.getPersistentData();
        if (tag.contains("chronicle_damage")) return Math.max(0, tag.getDouble("chronicle_damage"));
        return dev.chronicle.compat.GunBridge.estimate(p);
    }
    public static void launch(ServerPlayer owner, Entity entity, Vec3 velocity) {
        if (entity instanceof ItemEntity item) {
            var stack = item.getItem();
            Projectile ammo = null;
            if (stack.is(net.minecraft.world.item.Items.SNOWBALL)) ammo = new Snowball(owner.level(), owner);
            else if (stack.is(net.minecraft.world.item.Items.EGG)) ammo = new ThrownEgg(owner.level(), owner);
            else if (stack.is(net.minecraft.world.item.Items.ARROW) || stack.is(net.minecraft.world.item.Items.FLINT)
                || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_nugget")) {
                var arrow = new Arrow(owner.level(), owner); arrow.setBaseDamage(1 + Potential.force(owner));
                arrow.pickup = stack.is(net.minecraft.world.item.Items.ARROW) ? AbstractArrow.Pickup.ALLOWED : AbstractArrow.Pickup.DISALLOWED;
                ammo = arrow;
            }
            if (ammo != null) {
                ammo.setPos(entity.position());
                if (owner.level().addFreshEntity(ammo)) { stack.shrink(1); if (stack.isEmpty()) item.discard(); entity = ammo; }
            }
        }
        if (entity instanceof Projectile projectile) { projectile.setOwner(owner);if(projectile instanceof AbstractArrow arrow){var d=arrow.getPersistentData();if(!d.contains("chronicle_arrow_base"))d.putDouble("chronicle_arrow_base",arrow.getBaseDamage());arrow.setBaseDamage(d.getDouble("chronicle_arrow_base")+Potential.level(owner)*.025);} }
        if (entity instanceof AbstractHurtingProjectile fireball) { Vec3 direction=velocity.normalize().scale(.1);fireball.xPower=direction.x;fireball.yPower=direction.y;fireball.zPower=direction.z; }
        velocity(entity, velocity);
        FLIGHTS.remove(entity.getUUID());track(owner,entity,velocity);
    }
    public static void tick(ServerLevel level) {
        Iterator<Flight> iterator = FLIGHTS.values().iterator();
        while (iterator.hasNext()) {
            Flight flight = iterator.next(); Entity e = flight.entity;
            if (e.level() != level) continue;
            if (!e.isAlive() || --flight.life <= 0) { iterator.remove(); continue; }
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(flight.owner);
            double speed = Math.max(e.getDeltaMovement().length(), flight.velocity.length());
            float damage = impactDamage(e,speed);
            AABB swept = e.getBoundingBox().minmax(e.getBoundingBox().move(flight.previous.subtract(e.position()))).inflate(.3);
            if (speed>=.35&&(!(e instanceof LivingEntity) || Settings.MOB_COLLISIONS.get())) {
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, swept)) {
                    if (victim == e || victim == owner || victim.getRootVehicle()==e || speed<.35 || (victim instanceof ServerPlayer&&owner==null) || (owner != null && !allowed(owner, victim)) || flight.hit.getOrDefault(victim.getUUID(),0)>e.tickCount) continue;
                    if(CLAIMS.containsKey(e.getUUID())&&Objects.equals(CLAIMS.get(e.getUUID()),CLAIMS.get(victim.getRootVehicle().getUUID())))continue;
                    if (victim.getBoundingBox().inflate(e.getBbWidth() / 2. + .2).clip(flight.previous, e.position()).isEmpty() && !victim.getBoundingBox().intersects(e.getBoundingBox())) continue;
                    if (!(e instanceof Snowball) && !(e instanceof ThrownEgg) && !(e instanceof AbstractArrow)) victim.hurt(level.damageSources().thrown(e, owner), damage);
                    flight.hit.put(victim.getUUID(),e.tickCount+10);
                    if (e instanceof ThrownEgg && victim instanceof Mob mob) { mob.setTarget(null); mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false)); }
                    velocity(victim, victim.getDeltaMovement().add(flight.velocity.scale(Math.min(.8,mass(e)/Math.max(1,mass(victim))))));
                    if (e instanceof LivingEntity) e.hurt(level.damageSources().flyIntoWall(), damage * .45f);
                }
            }
            Vec3 blocked=new Vec3(e.horizontalCollision?flight.velocity.x:0,e.verticalCollision?flight.velocity.y:0,e.horizontalCollision?flight.velocity.z:0);
            if (blocked.length() > .65 && flight.wallReady<=e.tickCount) {
                flight.wallReady=e.tickCount+10;
                if (e instanceof LivingEntity) e.hurt(level.damageSources().flyIntoWall(), impactDamage(e,blocked.length()));
                if(!controlled(e)){if(owner!=null)Impacts.resolve(owner,e,flight.previous,flight.velocity);iterator.remove();continue;}
            }
            flight.previous = e.position(); flight.velocity = e.getDeltaMovement();
        }
    }
    public static float impactDamage(Entity e,double speed) {double material=e instanceof MatterBody b?1+Math.min(4,b.hardness())*.25:1;return (float)Math.min(Settings.MAX_COLLISION_DAMAGE.get(),Math.sqrt(mass(e))*speed*speed*2*material*Settings.COLLISION_DAMAGE.get());}
    public static void reset() { CLAIMS.clear(); FLIGHTS.clear(); }
    private static final class Flight {
        UUID owner; final Entity entity; int life = 120,wallReady; Vec3 previous, velocity; final Map<UUID,Integer> hit = new HashMap<>();
        Flight(UUID owner, Entity entity, Vec3 velocity) { this.owner = owner; this.entity = entity; this.previous = entity.position(); this.velocity = velocity; }
    }
    private Physics() {}
}
