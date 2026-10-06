package dev.chronicle.compat;

import dev.chronicle.power.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.Method;
import java.util.*;

/** Optional TaCZ bridge: validates combined damage before its armor-piercing split. No hard dependency. */
public final class GunBridge {
    private static Method bulletDamage;
    private static final Map<UUID, Long> PENETRATING = new HashMap<>();
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void install() {
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            Class<?> bullet = Class.forName("com.tacz.guns.entity.EntityKineticBullet");
            bulletDamage = bullet.getMethod("getDamage", Vec3.class);
            Class<? extends Event> type = (Class<? extends Event>)Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            Method victim = type.getMethod("getHurtEntity"), amount = type.getMethod("getBaseAmount"), headshot = type.getMethod("isHeadShot"), multiplier = type.getMethod("getHeadshotMultiplier");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (Class)type, (Event event) -> {
                try {
                    if (!(victim.invoke(event) instanceof LivingEntity target) || target.level().isClientSide) return;
                    float damage = ((Number)amount.invoke(event)).floatValue();
                    if ((Boolean)headshot.invoke(event)) damage *= ((Number)multiplier.invoke(event)).floatValue();
                    boolean blocked = Concentration.shield(target, damage);
                    if (!blocked && target instanceof ServerPlayer p && Potential.active(p) && damage <= Ward.capacity(p)) { Potential.spend(p, Math.max(.5, damage)); blocked = true; }
                    if (blocked && event.isCancelable()) event.setCanceled(true);
                    else PENETRATING.put(target.getUUID(), target.level().getGameTime());
                } catch (ReflectiveOperationException ignored) { /* Unknown API version falls back to ordinary damage events. */ }
            });
        } catch (ReflectiveOperationException unavailable) {
            com.mojang.logging.LogUtils.getLogger().warn("Chronicle: TaCZ API differs; using Forge projectile damage fallback", unavailable);
        }
    }
    public static boolean penetrates(LivingEntity victim) {
        Long tick = PENETRATING.get(victim.getUUID());
        if (tick == null) return false;
        if (tick != victim.level().getGameTime()) { PENETRATING.remove(victim.getUUID()); return false; }
        return true;
    }
    public static double estimate(Projectile bullet) {
        if (bulletDamage == null || !bulletDamage.getDeclaringClass().isInstance(bullet)) return Double.POSITIVE_INFINITY;
        try { return Math.max(0, ((Number)bulletDamage.invoke(bullet, bullet.position())).doubleValue()) * 2; }
        catch (ReflectiveOperationException unavailable) { return Double.POSITIVE_INFINITY; }
    }
    public static void clear() { PENETRATING.clear(); }
    private GunBridge() {}
}
