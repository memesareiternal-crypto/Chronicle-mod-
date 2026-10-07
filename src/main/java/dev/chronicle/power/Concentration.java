package dev.chronicle.power;

import dev.chronicle.Settings;
import dev.chronicle.entity.MatterBody;
import dev.chronicle.network.Wire;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import static dev.chronicle.power.Intent.*;

/** Context-sensitive psychokinesis driven by focus, right click, movement modifiers, and a barrier key. */
public final class Concentration {
    private static final Map<UUID, Concentration> LIVE = new HashMap<>();
    private final ServerPlayer player;
    private Intent input = Intent.IDLE;
    private int previous, lastPacket, charge, barrierCharge, areaTicks, armorReady, areaRadius = 1;
    private boolean chordLatch, flying, oldMayFly, oldFlying, gripLatch, barrierAdjusted, areaSelecting;
    private double distance = 5, viewImpulse;
    private Vec3 lastLook;
    private Ward ward;
    private final LinkedHashMap<UUID, Physics.Hold> held = new LinkedHashMap<>();
    private final Map<UUID, Integer> chokeRest = new HashMap<>();

    private Concentration(ServerPlayer player) { this.player = player; }
    public static void accept(ServerPlayer p, Intent input) {
        if (!Potential.acquired(p) || !p.isAlive()) return;
        Concentration c = LIVE.computeIfAbsent(p.getUUID(), unused -> new Concentration(p));
        c.input = input; c.lastPacket = p.tickCount;
        if (input.wheel() == 0) return;
        if (c.ward != null && input.has(BARRIER)) { c.ward.resize(input.wheel()); c.barrierAdjusted = true; }
        else if (!c.held.isEmpty()) {
            if (input.has(SNEAK)) for (var hold : c.held.values()) {
                if (hold.entity instanceof MatterBody body) body.rotate(input.wheel());
                else hold.entity.setYRot(hold.entity.getYRot() + input.wheel() * 15);
            } else c.distance = Math.max(2, Math.min(Potential.reach(p), c.distance + input.wheel() * 1.5));
        } else if (input.has(GRIP) && input.has(SNEAK)) {
            c.areaRadius = Math.max(1, Math.min(c.maximumAreaRadius(), c.areaRadius + input.wheel()));
            c.hint("Manipulation radius " + c.areaRadius);
        }
    }
    public static void tick(ServerPlayer p) {
        if (!p.isAlive()) { release(p); return; }
        if (Potential.acquired(p)) LIVE.computeIfAbsent(p.getUUID(), unused -> new Concentration(p)).update();
    }
    private boolean pressed(int bit) { return input.has(bit) && (previous & bit) == 0; }
    private boolean released(int bit) { return !input.has(bit) && (previous & bit) != 0; }

    private void update() {
        Vec3 look = player.getLookAngle();
        viewImpulse = lastLook == null ? 0 : Math.min(.75, look.subtract(lastLook).length() * 2); lastLook = look;
        if (player.tickCount - lastPacket > 12) input = Intent.IDLE;
        boolean chord = input.has(GRIP) && input.has(ACT) && input.has(SNEAK) && held.isEmpty();
        if (chord && !chordLatch) { stop(); Potential.active(player, !Potential.active(player)); chordLatch = true; hint(Potential.active(player) ? "Concentration engaged" : "Concentration released"); }
        if (chordLatch) { if (!input.has(GRIP) && !input.has(ACT)) chordLatch = false; previous = input.buttons(); sync(); return; }
        if (player.tickCount % 20 == 0) Potential.experience(player, Settings.AGE_XP.get() * Settings.LEVEL_RATE.get());
        double before = Potential.strain(player);
        if (Potential.active(player)) {
            if (!input.has(GRIP)) gripLatch = false;
            if (pressed(GRIP) && input.has(SNEAK) && held.isEmpty()) { areaSelecting = true; areaTicks = 0; }
            if (areaSelecting) {
                if (!input.has(GRIP)) { areaSelecting = false; areaTicks = 0; }
                else if (++areaTicks >= 8) { Entity target=Applications.liftArea(player,areaRadius); areaSelecting=false; if(target!=null) attach(target); else hint("No movable connected mass"); }
            } else {
                if (input.has(GRIP) && !gripLatch) grip();
                if (released(GRIP) && !input.has(ACT)) drop(false);
            }
            if (input.has(ACT)) { charge = Math.min(36000, charge + 1); sustain(); }
            if (released(ACT)) { act(); charge = 0; }
            if (input.has(BARRIER)) barrierCharge++;
            if (released(BARRIER)) { if (ward != null) { if (!barrierAdjusted) { ward.close(player, true); ward = null; } } else toggleBarrier(barrierCharge >= 8); barrierCharge = 0; barrierAdjusted = false; }
            if (ward != null && !ward.tick(player, input.has(SNEAK) && input.has(BARRIER))) { ward.close(player, false); ward = null; hint("Barrier broken"); }
            if (flying) { player.fallDistance = 0; Potential.spend(player, Settings.FLIGHT_COST.get()); }
            if (Potential.level(player) >= Settings.HEAL_LEVEL.get() && player.tickCount % 20 == 0 && player.getHealth() < player.getMaxHealth() && Potential.strain(player) < Potential.threshold(player) * .65) { player.heal(Settings.HEAL_RATE.get().floatValue()); Potential.spend(player, 2); }
        }
        if (Potential.strain(player) <= before) Potential.recover(player);
        if (player.tickCount % 5 == 0) sync();
        previous = input.buttons();
    }
    private int maximumAreaRadius() { return Math.max(1, Math.min(16, (int)Math.floor((1 + Potential.level(player) * .6) * Math.sqrt(Potential.strength())))); }
    private void grip() {
        if (held.isEmpty() || (input.has(SNEAK) && player.tickCount % 8 == 0)) {
            int max = Math.min(64, Math.max(1, (int)((1 + Potential.level(player) / 2.) * Potential.strength())));
            if (held.size() < max) {
                Entity target = Applications.target(player);
                if (target == null && held.isEmpty()) target = Applications.liftArea(player, 0);
                if (target != null) attach(target);
            }
        }
        Vec3 destination = player.getEyePosition().add(player.getLookAngle().scale(distance));
        if (input.has(JUMP)) destination = destination.add(0, 1, 0);
        if (input.has(SPRINT)) destination = destination.add(0, -1, 0);
        Iterator<Physics.Hold> iterator = held.values().iterator(); int index = 0;
        while (iterator.hasNext()) {
            Physics.Hold hold = iterator.next();
            Vec3 offset = held.size() > 1 ? new Vec3(Math.cos(index * 2.4), .2 * Math.sin(index), Math.sin(index * 2.4)).scale(1 + index * .15) : Vec3.ZERO; index++;
            boolean choke = input.has(SNEAK) && input.has(ACT) && charge > 6 && charge < Settings.CHOKE_TIME.get() && chokeRest.getOrDefault(hold.entity.getUUID(), 0) <= player.tickCount;
            Vec3 softened = destination.add(offset).subtract(hold.entity.position()).scale(Potential.control(player)).add(hold.entity.position());
            if (!hold.steer(player, softened, choke)) { hold.close(); iterator.remove(); hint("Grip resisted or lost"); continue; }
            Potential.spend(player, .04 + Math.sqrt(Physics.mass(hold.entity)) * .025);
        }
    }
    private void attach(Entity entity) { var hold = Physics.take(player, entity); if (hold != null) { held.put(hold.entity.getUUID(), hold); Potential.spend(player, .4 + Math.sqrt(Physics.mass(entity)) * .2); } }
    private void sustain() {
        if (charge < 7 || held.isEmpty()) return;
        Potential.spend(player, .06);
        if (input.has(SNEAK)) for (var hold : held.values()) {
            Entity e = hold.entity;
            if (e instanceof LivingEntity && player.tickCount % 10 == 0 && chokeRest.getOrDefault(e.getUUID(), 0) <= player.tickCount) e.hurt(player.damageSources().playerAttack(player), (float)(.6 + Potential.force(player) * 1.5));
            if (charge == Settings.CHOKE_TIME.get()) chokeRest.put(e.getUUID(), player.tickCount + Settings.CHOKE_TIME.get() * 2);
            if (charge == 20 && e instanceof LivingEntity living) strip(living, false);
            if (charge == 60 && e instanceof LivingEntity living) strip(living, true);
            if (charge == 40 && e instanceof MatterBody body) body.shatter(player);
            Potential.spend(player, .45);
        }
    }
    private void act() {
        boolean sneak = input.has(SNEAK);
        if (!held.isEmpty()) { if (input.has(SPRINT)) place(); else if (!sneak) drop(true); return; }
        if (input.has(JUMP) && sneak) { radar(); return; }
        if (input.has(JUMP)) { toggleFlight(); return; }
        if (input.has(SPRINT) && sneak) { Applications.edge(player, Intent.Edge.SWEEP, null, null); return; }
        if (player.getMainHandItem().getItem() instanceof BlockItem && input.has(SPRINT)) { Applications.build(player, false, false, null); return; }
        if (charge >= 35 && unlock(Settings.BURST_LEVEL.get())) {
            Applications.pressure(player, sneak, 1 + Math.min(100, charge) / 25., true); Applications.burstTerrain(player, Math.min(100, charge));
            effect(Wire.Effect.EXPLOSION, player.position().add(0, 1, 0), Math.min(20, 4 + charge / 10.)); return;
        }
        if (charge < 6 && !sneak && Applications.interact(player)) return;
        Applications.pressure(player, sneak, Math.max(.4, Math.min(100, charge) / 25.), false);
        effect(Wire.Effect.WAVE, player.getEyePosition().add(player.getLookAngle().scale(2)), Math.min(8, 2 + charge / 14.));
    }
    private void toggleBarrier(boolean aimed) {
        if (!unlock(Settings.FIELD_LEVEL.get())) return;
        Entity target = aimed && !input.has(SNEAK) ? Applications.target(player) : null;
        Vec3 anchor = aimed && target == null && !input.has(SNEAK) ? Applications.aim(player).getLocation() : null;
        ward = new Ward(player, anchor, target, input.has(SNEAK));
    }
    private void radar() {
        if (!unlock(Settings.SENSE_LEVEL.get())) return;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(Potential.reach(player) * 1.5))) if (e != player) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
        Potential.spend(player, 8);
    }
    private void toggleFlight() {
        if (!unlock(Settings.FLIGHT_LEVEL.get())) return;
        if (flying) restoreFlight(); else { oldMayFly = player.getAbilities().mayfly; oldFlying = player.getAbilities().flying; player.getAbilities().mayfly = true; player.getAbilities().flying = true; player.onUpdateAbilities(); flying = true; }
    }
    private void effect(int kind, Vec3 center, double radius) {
        if (Settings.GEOMETRY_EFFECTS.get()) Wire.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(center.x, center.y, center.z, 96, player.level().dimension())), new Wire.Effect(kind, center, radius, Potential.color(player)));
    }
    private void strip(LivingEntity target, boolean armor) {
        if (armor && (Potential.level(player) < 8 || player.tickCount < armorReady)) return;
        EquipmentSlot slot = EquipmentSlot.MAINHAND;
        if (armor) { for (var candidate : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) if (!target.getItemBySlot(candidate).isEmpty()) { slot = candidate; break; } if (slot == EquipmentSlot.MAINHAND) return; }
        ItemStack item = target.getItemBySlot(slot); if (item.isEmpty()) return;
        ItemEntity loose = new ItemEntity(player.level(), target.getX(), target.getEyeY(), target.getZ(), item.copy()); if (!player.level().addFreshEntity(loose)) return;
        target.setItemSlot(slot, ItemStack.EMPTY); Physics.velocity(loose, player.getEyePosition().subtract(loose.position()).normalize().scale(.6));
        if (armor) armorReady = player.tickCount + Settings.ARMOR_COOLDOWN.get(); Potential.spend(player, armor ? 18 : 5);
    }
    private void drop(boolean launch) {
        double amount = .35 + Math.min(100, charge) / 35.;
        for (var hold : held.values()) { hold.close(); if (launch) Physics.launch(player, hold.entity, player.getLookAngle().scale((.5 + Potential.force(player)) * Potential.control(player) * (amount + viewImpulse)).add(player.getDeltaMovement().scale(.5))); }
        if (launch) { Potential.spend(player, 4 + held.size() * amount); gripLatch = true; } held.clear();
    }
    private void place() {
        var hit = Applications.aim(player); if (hit.getType() != HitResult.Type.BLOCK) return; BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        Iterator<Physics.Hold> it = held.values().iterator();
        while (it.hasNext()) { var hold = it.next(); if (hold.entity instanceof MatterBody body) { if (body.place(target.offset(-body.width() / 2, 0, -body.depth() / 2), player)) { hold.close(); it.remove(); } else hint("Destination occupied; matter preserved"); } }
    }
    private void restoreFlight() { if (!player.isCreative() && !player.isSpectator()) { player.getAbilities().mayfly = oldMayFly; player.getAbilities().flying = oldFlying; player.onUpdateAbilities(); } flying = false; }
    private void stop() { drop(false); if (ward != null) { ward.close(player, false); ward = null; } if (flying) restoreFlight(); }
    private boolean unlock(int level) { int required = Math.min(10, level); if (Potential.level(player) >= required) return true; hint("Requires potential level " + required); return false; }
    private void hint(String text) { if (Settings.HINTS.get()) player.displayClientMessage(Component.literal(text), true); }
    private void sync() {
        Vec3 center = ward == null ? null : ward.center(player);
        Wire.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new Wire.View(player.getId(), Potential.acquired(player), Potential.active(player), Settings.AURA.get() ? Potential.color(player) : -1, Settings.GEOMETRY_EFFECTS.get(), center, ward == null ? 0 : ward.radius(player), ward != null && ward.plane(), ward == null ? 0 : ward.integrity(player)));
    }
    public static void synchronize(ServerPlayer player) {
        var c = LIVE.get(player.getUUID()); if (c != null) c.sync();
        else Wire.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new Wire.View(player.getId(), Potential.acquired(player), Potential.active(player), Settings.AURA.get() ? Potential.color(player) : -1, Settings.GEOMETRY_EFFECTS.get(), null, 0, false, 0));
    }
    public static void release(ServerPlayer player) { var c = LIVE.remove(player.getUUID()); if (c != null) c.stop(); }
    public static boolean shield(LivingEntity victim, float amount) { return shield(victim, amount, null); }
    public static boolean shield(LivingEntity victim, float amount, Entity attacker) { for (var c : LIVE.values()) if (c.player != attacker && Potential.active(c.player) && c.ward != null && c.player.level() == victim.level() && c.ward.protects(c.player, victim) && c.ward.absorb(c.player, amount)) return true; return false; }
    public static void reset() { for (var c : LIVE.values()) c.stop(); LIVE.clear(); Physics.reset(); }
}
