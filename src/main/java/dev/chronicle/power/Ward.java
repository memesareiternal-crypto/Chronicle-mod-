package dev.chronicle.power;

import dev.chronicle.Settings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A deformable volume anchored to the wielder, a target, or a point in the world. */
public final class Ward {
    private Vec3 anchor;
    private final Entity attachment;
    private final boolean plane;
    private final List<Physics.Hold> caught = new ArrayList<>();
    private final List<Physics.Hold> orbit = new ArrayList<>();
    private double integrity;
    private double scale = 1;
    public Ward(ServerPlayer p, Vec3 anchor, Entity attachment, boolean plane) { this.anchor = anchor; this.attachment = attachment; this.plane = plane; integrity = capacity(p) * 8; }
    public static double capacity(ServerPlayer p) { return (2 + Potential.level(p) * Settings.BULLET_CAPACITY.get()) * Potential.strength(); }
    public Vec3 center(ServerPlayer p) { return attachment != null && attachment.isAlive() ? attachment.getBoundingBox().getCenter() : anchor == null ? p.getBoundingBox().getCenter() : anchor; }
    public double radius(ServerPlayer p) { return (Settings.FIELD_RADIUS.get() + Potential.level(p) * .1) * Math.sqrt(Potential.strength()) * scale; }
    public boolean plane() { return plane; }
    public float integrity(ServerPlayer p) { return (float)Math.max(0, Math.min(1, integrity / Math.max(1, capacity(p) * 8))); }
    public void resize(int direction) { scale = Math.max(.5, Math.min(3, scale + direction * .25)); }
    public boolean protects(ServerPlayer p, Entity entity) {
        Vec3 d = entity.getBoundingBox().getCenter().subtract(center(p));
        return plane ? Math.abs(d.dot(p.getLookAngle())) < .8 && d.lengthSqr() < Math.pow(radius(p), 2) : d.lengthSqr() < Math.pow(radius(p), 2);
    }
    public boolean absorb(ServerPlayer p, float amount) {
        if (amount > capacity(p) * 2.5 || integrity <= 0) { integrity -= amount; return false; }
        if (!orbit.isEmpty()) {
            var hold = orbit.remove(0); hold.close();
            if (hold.entity instanceof ItemEntity item) { item.getItem().shrink(1); if (item.getItem().isEmpty()) item.discard(); }
            integrity = Math.min(capacity(p)*8, integrity + amount*.5);
        }
        integrity -= amount; Potential.spend(p, amount * .9); return true;
    }
    public boolean tick(ServerPlayer p, boolean compress) {
        if (integrity <= 0 || center(p).distanceToSqr(p.position()) > Math.pow(Potential.reach(p)*2, 2)) return false;
        Vec3 center = center(p); double radius = radius(p);
        for (Entity e : p.level().getEntities(p, new AABB(center, center).inflate(radius + 1), e -> Physics.allowed(p, e))) {
            Vec3 delta = e.getBoundingBox().getCenter().subtract(center); double distance = delta.length();
            if (distance > radius + 1) continue;
            if (e instanceof Projectile projectile) {
                if (projectile.getOwner() == p || caught.stream().anyMatch(h -> h.entity == e)) continue;
                if (Physics.damageEstimate(projectile) <= capacity(p)*2.5 && caught.size() < 8 + Potential.level(p)) {
                    var hold = Physics.take(p, e); if (hold != null) { caught.add(hold); Potential.spend(p, Math.max(1, Physics.damageEstimate(projectile))); }
                }
            } else if (e instanceof LivingEntity living && e != attachment) {
                double attack = living.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 1 : living.getAttributeValue(Attributes.ATTACK_DAMAGE);
                if (attack > capacity(p) * 2.5) { if (p.tickCount % 10 == 0) integrity -= attack; continue; }
                if (compress) {
                    Physics.velocity(e, delta.normalize().scale(-.15));
                    if (p.tickCount % 10 == 0) e.hurt(p.damageSources().playerAttack(p), (float)(1 + Potential.force(p)));
                } else if (distance < radius) Physics.velocity(e, delta.normalize().scale(.3 + Potential.force(p)*.2));
            } else if (e instanceof ItemEntity && orbit.size() < 4 && orbit.stream().noneMatch(h -> h.entity == e)) {
                var hold = Physics.take(p, e); if (hold != null) orbit.add(hold);
            }
        }
        Iterator<Physics.Hold> it = caught.iterator(); int index = 0;
        while (it.hasNext()) {
            var hold = it.next(); double angle = index++ * 2.399;
            Vec3 position = center.add(Math.cos(angle)*radius*.8, .5 + index*.04, Math.sin(angle)*radius*.8);
            if (!hold.steer(p, position, false)) { hold.close(); it.remove(); }
        }
        it = orbit.iterator(); index = 0;
        while (it.hasNext()) {
            var hold = it.next(); double angle = p.tickCount*.1 + index++ * Math.PI/2;
            if (!hold.steer(p, center.add(Math.cos(angle)*radius, .2, Math.sin(angle)*radius), false)) { hold.close(); it.remove(); }
        }
        Potential.spend(p, .28 + caught.size()*.055 + (compress ? .5 : 0)); return true;
    }
    public void close(ServerPlayer p, boolean redirect) {
        for (var hold : caught) { hold.close(); if (redirect) Physics.launch(p, hold.entity, p.getLookAngle().scale(1.5 + Potential.force(p))); }
        for (var hold : orbit) hold.close(); caught.clear(); orbit.clear();
    }
}
