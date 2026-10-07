package dev.chronicle;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.chronicle.entity.ResonantCrystal;
import dev.chronicle.power.*;
import dev.chronicle.world.Chambers;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

public final class ServerEvents {
    private final Map<UUID, Integer> exposure = new HashMap<>();
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!Potential.acquired(p) && p.isAlive()) {
            boolean nearby = !p.level().getEntitiesOfClass(ResonantCrystal.class, p.getBoundingBox().inflate(5)).isEmpty();
            int progress = nearby ? exposure.getOrDefault(p.getUUID(), 0)+1 : 0;
            if (progress == 0) exposure.remove(p.getUUID()); else exposure.put(p.getUUID(), progress);
            if (progress >= Settings.AWAKEN_TIME.get()) { Potential.grant(p); exposure.remove(p.getUUID()); p.displayClientMessage(Component.literal("The crystal answers. Hold G to move matter, right click to channel force, and press B for a barrier."), false); }
        }
        Concentration.tick(p);
    }
    @SubscribeEvent public void level(TickEvent.LevelTickEvent e) { if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel level) Physics.tick(level); }
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e) { if (e.phase == TickEvent.Phase.END) Chambers.tick(); }
    @SubscribeEvent public void chunk(ChunkEvent.Load e) { if (e.isNewChunk() && e.getLevel() instanceof ServerLevel level) Chambers.enqueue(level, e.getChunk().getPos()); }
    @SubscribeEvent public void join(EntityJoinLevelEvent e) { if (!e.getLevel().isClientSide) Physics.recoverOrphan(e.getEntity()); }
    @SubscribeEvent public void impact(net.minecraftforge.event.entity.ProjectileImpactEvent e) { if (Physics.controlled(e.getProjectile())) e.setCanceled(true); }
    @SubscribeEvent public void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer old && e.getEntity() instanceof ServerPlayer replacement) { Concentration.release(old); Potential.inherit(old, replacement); } }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) Concentration.synchronize(p); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) { Concentration.release(p); exposure.remove(p.getUUID()); } }
    @SubscribeEvent public void travel(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) { Concentration.release(p); Concentration.synchronize(p); } }
    @SubscribeEvent public void stop(ServerStoppingEvent e) { Concentration.reset(); Chambers.clear(); dev.chronicle.compat.GunBridge.clear(); exposure.clear(); }

    @SubscribeEvent public void hurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        float damage = event.getAmount();
        boolean penetrating = dev.chronicle.compat.GunBridge.penetrates(event.getEntity());
        // Actual impact damage is authoritative for unknown mod bullets and hitscan sources.
        boolean projectile = event.getSource().is(DamageTypeTags.IS_PROJECTILE) || event.getSource().getDirectEntity() instanceof Projectile;
        if (!penetrating && Concentration.shield(event.getEntity(), damage, event.getSource().getEntity())) { event.setAmount(0); return; }
        if (event.getEntity() instanceof ServerPlayer p && Potential.active(p)) {
            if (!penetrating && projectile && damage <= Ward.capacity(p)) {
                event.setAmount(0); Potential.spend(p, Math.max(.5, damage));
                if (event.getSource().getDirectEntity() instanceof Projectile bullet) { bullet.setOwner(p); Physics.velocity(bullet, bullet.getDeltaMovement().scale(-1)); }
            } else { event.setAmount(damage * (float)Math.max(.25, .92 - Potential.level(p)*.006)); Potential.spend(p, damage*.4); }
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && event.getSource().getDirectEntity() == attacker && Potential.active(attacker)
            && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) {
            event.setAmount(event.getAmount() + (float)Potential.force(attacker)*2); Potential.spend(attacker, .6);
        }
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("psychokinesis");
        root.then(Commands.literal("level").executes(c -> {
            ServerPlayer p=c.getSource().getPlayerOrException();
            if(!Potential.acquired(p)) { c.getSource().sendFailure(Component.literal("You have not awakened psychokinesis.")); return 0; }
            c.getSource().sendSuccess(() -> Component.literal("Psychokinesis level " + Potential.level(p) + "/10"), false); return Potential.level(p);
        }));
        for (String verb : List.of("grant", "max", "remove")) {
            root.then(Commands.literal(verb).requires(source -> source.hasPermission(2)).executes(c -> command(verb, List.of(c.getSource().getPlayerOrException())))
                .then(Commands.argument("players", EntityArgument.players()).executes(c -> command(verb, EntityArgument.getPlayers(c, "players")))));
        }
        root.then(Commands.literal("setlevel").requires(s -> s.hasPermission(2)).then(Commands.argument("players",EntityArgument.players()).then(Commands.argument("level",IntegerArgumentType.integer(1,10)).executes(c -> {
            var players=EntityArgument.getPlayers(c,"players"); int level=IntegerArgumentType.getInteger(c,"level"); for(var p:players){ Potential.setLevel(p,level); Concentration.synchronize(p); } return players.size();
        }))));
        root.then(Commands.literal("setprogress").requires(s -> s.hasPermission(2)).then(Commands.argument("players",EntityArgument.players()).then(Commands.argument("experience",DoubleArgumentType.doubleArg(0)).executes(c -> {
            var players=EntityArgument.getPlayers(c,"players"); double xp=DoubleArgumentType.getDouble(c,"experience"); for(var p:players){ Potential.setProgress(p,xp); Concentration.synchronize(p); } return players.size();
        }))));
        root.then(Commands.literal("resetprogress").requires(s -> s.hasPermission(2)).then(Commands.argument("players",EntityArgument.players()).executes(c -> { var players=EntityArgument.getPlayers(c,"players"); for(var p:players) Potential.resetProgress(p); return players.size(); })));
        root.then(Commands.literal("spawncrystal").requires(s -> s.hasPermission(2)).executes(c -> { var p=c.getSource().getPlayerOrException(); var crystal=Chronicle.CRYSTAL.get().create(p.serverLevel()); if(crystal==null) return 0; crystal.moveTo(p.getX(),p.getY(),p.getZ(),0,0); return p.serverLevel().addFreshEntity(crystal)?1:0; }));
        event.getDispatcher().register(root);
    }
    private int command(String verb, Collection<ServerPlayer> players) {
        for (var p : players) {
            if (verb.equals("remove")) { Concentration.release(p); Potential.remove(p); exposure.remove(p.getUUID()); }
            else if (verb.equals("max")) Potential.maximize(p); else Potential.grant(p);
            Concentration.synchronize(p); p.displayClientMessage(Component.literal("Psychokinesis: " + verb), false);
        }
        return players.size();
    }
}
