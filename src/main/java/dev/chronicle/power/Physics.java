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
        if (e instanceof MatterBody b) return Math.max(1, b.mass() * 2.);
        if (e instanceof ItemEntity item) return .15 + item.getItem().getCount() * .015;
        return Math.max(.2, e.getBbWidth() * e.getBbWidth() * e.getBbHeight() * 4);
    }
    public static void velocity(Entity e, Vec3 velocity) {
        e.setDeltaMovement(velocity); e.hurtMarked = true;
        if (e instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(e));
    }
    public static Hold take(ServerPlayer owner, Entity raw) {
        Entity e = raw.getRootVehicle();
        if (!allowed(owner, e) || CLAIMS.containsKey(e.getUUID())) return null;
        CLAIMS.put(e.getUUID(), owner.getUUID());
        var data = e.getPersistentData();
        data.putBoolean("chronicle_suspended", true); data.putBoolean("chronicle_old_gravity", e.isNoGravity());
        data.putBoolean("chronicle_old_ai", e instanceof Mob mob && mob.isNoAi());
        return new Hold(e, e.isNoGravity(), e instanceof Mob mob && mob.isNoAi());
    }
    public static void recoverOrphan(Entity e) {
        var d = e.getPersistentData(); if (!d.getBoolean("chronicle_suspended")) return;
        e.setNoGravity(d.getBoolean("chronicle_old_gravity"));
        if (e instanceof Mob mob) mob.setNoAi(d.getBoolean("chronicle_old_ai"));
        d.remove("chronicle_suspended"); d.remove("chronicle_old_gravity"); d.remove("chronicle_old_ai");
    }
    public static final class Hold {
        public final Entity entity;
        private final boolean gravity, ai;
        private final Vec3 acceleration;
        private double struggle;
        public int ticks;
        Hold(Entity entity, boolean gravity, boolean ai) {
            this.entity = entity; this.gravity = gravity; this.ai = ai;
            acceleration = entity instanceof AbstractHurtingProjectile fireball ? new Vec3(fireball.xPower,fireball.yPower,fireball.zPower) : null;
        }
        public boolean steer(ServerPlayer p, Vec3 destination, boolean rigid) {
            ticks++;
            if (!entity.isAlive() || entity.level() != p.level() || entity.distanceToSqr(p) > Math.pow(Potential.reach(p) * 1.8, 2)) return false;
            double mass = mass(entity);
            if (entity instanceof LivingEntity living) {
                double resistance = (living.getHealth() * .08 + living.getMaxHealth() * .02 + mass * .3) * Settings.RESISTANCE.get();
                double capacity = 5 + Potential.force(p) * 18;
                struggle = Math.max(0, struggle + (resistance / capacity - 1) * .025 + p.getRandom().nextDouble() * .004);
                if (struggle > 1) return false;
            }
            if (entity instanceof MatterBody body) body.held(); else entity.setNoGravity(true);
            if (entity instanceof Mob mob) mob.setNoAi(true);
            if (entity instanceof AbstractHurtingProjectile fireball) { fireball.xPower=0;fireball.yPower=0;fireball.zPower=0; }
            Vec3 delta = destination.subtract(entity.getBoundingBox().getCenter());
            double acceleration = Math.min(.4, (.5 + Potential.force(p)) / Math.sqrt(mass));
            Vec3 v = delta.scale(acceleration).subtract(entity.getDeltaMovement().scale(.15));
            double cap = 1 + Potential.force(p); if (v.length() > cap) v = v.normalize().scale(cap);
            velocity(entity, rigid ? Vec3.ZERO : v); entity.fallDistance = 0;
            return true;
        }
        public void close() {
            if (entity instanceof MatterBody body) body.release(); else entity.setNoGravity(gravity);
            if (entity instanceof Mob mob) mob.setNoAi(ai);
            if (entity instanceof AbstractHurtingProjectile fireball && acceleration != null) { fireball.xPower=acceleration.x;fireball.yPower=acceleration.y;fireball.zPower=acceleration.z; }
            CLAIMS.remove(entity.getUUID()); var d = entity.getPersistentData();
            d.remove("chronicle_suspended"); d.remove("chronicle_old_gravity"); d.remove("chronicle_old_ai");
        }
    }
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
        if (entity instanceof Projectile projectile) { projectile.setOwner(owner); if (projectile instanceof AbstractArrow arrow) arrow.setBaseDamage(arrow.getBaseDamage() + Potential.level(owner)*.025); }
        if (entity instanceof AbstractHurtingProjectile fireball) { Vec3 direction=velocity.normalize().scale(.1);fireball.xPower=direction.x;fireball.yPower=direction.y;fireball.zPower=direction.z; }
        velocity(entity, velocity);
        FLIGHTS.put(entity.getUUID(), new Flight(owner.getUUID(), entity, velocity));
    }
    public static void tick(ServerLevel level) {
        Iterator<Flight> iterator = FLIGHTS.values().iterator();
        while (iterator.hasNext()) {
            Flight flight = iterator.next(); Entity e = flight.entity;
            if (e.level() != level) continue;
            if (!e.isAlive() || --flight.life <= 0) { iterator.remove(); continue; }
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(flight.owner);
            double speed = Math.max(e.getDeltaMovement().length(), flight.velocity.length());
            float damage = (float)Math.min(2000, Math.sqrt(mass(e)) * speed * speed * 2);
            AABB swept = e.getBoundingBox().minmax(e.getBoundingBox().move(flight.previous.subtract(e.position()))).inflate(.3);
            if (!(e instanceof LivingEntity) || Settings.MOB_COLLISIONS.get()) {
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, swept)) {
                    if (victim == e || victim == owner || (owner != null && !allowed(owner, victim)) || !flight.hit.add(victim.getUUID())) continue;
                    if (victim.getBoundingBox().inflate(e.getBbWidth() / 2. + .2).clip(flight.previous, e.position()).isEmpty() && !victim.getBoundingBox().intersects(e.getBoundingBox())) continue;
                    if (!(e instanceof Snowball) && !(e instanceof ThrownEgg) && !(e instanceof AbstractArrow)) victim.hurt(level.damageSources().thrown(e, owner), damage);
                    if (e instanceof ThrownEgg && victim instanceof Mob mob) { mob.setTarget(null); mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false)); }
                    velocity(victim, flight.velocity.scale(.6));
                    if (e instanceof LivingEntity) e.hurt(level.damageSources().flyIntoWall(), damage * .45f);
                }
            }
            if (speed > .65 && (e.horizontalCollision || e.verticalCollision)) {
                if (e instanceof LivingEntity) e.hurt(level.damageSources().flyIntoWall(), damage);
                iterator.remove(); continue;
            }
            flight.previous = e.position(); flight.velocity = e.getDeltaMovement();
        }
    }
    public static void reset() { CLAIMS.clear(); FLIGHTS.clear(); }
    private static final class Flight {
        final UUID owner; final Entity entity; int life = 120; Vec3 previous, velocity; final Set<UUID> hit = new HashSet<>();
        Flight(UUID owner, Entity entity, Vec3 velocity) { this.owner = owner; this.entity = entity; this.previous = entity.position(); this.velocity = velocity; }
    }
    private Physics() {}
}
