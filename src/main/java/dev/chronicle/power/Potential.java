package dev.chronicle.power;

import dev.chronicle.Settings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/** Persistent potential; transient forces live in the server's concentration session. */
public final class Potential {
    private static final String KEY = "chronicle_potential";
    public static CompoundTag data(ServerPlayer p) {
        var root = p.getPersistentData();
        if (!root.contains(KEY)) root.put(KEY, new CompoundTag());
        return root.getCompound(KEY);
    }
    public static boolean acquired(ServerPlayer p) { return data(p).getBoolean("acquired"); }
    public static boolean active(ServerPlayer p) { return acquired(p) && data(p).getBoolean("active"); }
    public static void active(ServerPlayer p, boolean active) { data(p).putBoolean("active", active); }
    public static int level(ServerPlayer p) { return Mth.clamp(data(p).getInt("level"), 1, Settings.MAX_LEVEL.get()); }
    public static int color(ServerPlayer p) { return data(p).getInt("color"); }
    public static double strain(ServerPlayer p) { return data(p).getDouble("strain"); }
    public static double threshold(ServerPlayer p) { return Settings.CAPACITY.get() + level(p) * Settings.CAPACITY_GROWTH.get(); }
    public static double force(ServerPlayer p) { return Settings.FORCE.get() + level(p) * Settings.FORCE_GROWTH.get(); }
    public static double reach(ServerPlayer p) { return Math.min(Settings.MAX_RANGE.get(), Settings.RANGE.get() + level(p) * Settings.RANGE_GROWTH.get()); }
    public static void grant(ServerPlayer p) {
        if (acquired(p)) return;
        var d = data(p); d.putBoolean("acquired", true); d.putBoolean("active", true); d.putInt("level", 1);
        d.putInt("color", Mth.hsvToRgb(p.getRandom().nextFloat(), .42f, 1) & 0xFFFFFF);
    }
    public static void maximize(ServerPlayer p) { grant(p); data(p).putInt("level", Settings.MAX_LEVEL.get()); data(p).putDouble("xp", required(Settings.MAX_LEVEL.get())); }
    public static void remove(ServerPlayer p) { p.getPersistentData().remove(KEY); }
    public static void inherit(ServerPlayer old, ServerPlayer replacement) { replacement.getPersistentData().put(KEY, data(old).copy()); }
    public static double required(int level) { return Settings.XP_CURVE.get() * (level - 1.) * (level - 1.); }
    public static void experience(ServerPlayer p, double amount) {
        var d = data(p); double xp = d.getDouble("xp") + amount; int level = level(p);
        while (level < Settings.MAX_LEVEL.get() && xp >= required(level + 1)) level++;
        d.putDouble("xp", xp); d.putInt("level", level);
    }
    public static void spend(ServerPlayer p, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        data(p).putDouble("strain", strain(p) + amount); experience(p, amount * Settings.XP_RATE.get());
    }
    public static void recover(ServerPlayer p) { data(p).putDouble("strain", Math.max(0, strain(p) - Settings.RECOVERY.get())); }
    public static float exhaustionDamage(ServerPlayer p) { return (float)(Settings.EXERT_DAMAGE.get() * Math.max(0, strain(p) / threshold(p))); }
    private Potential() {}
}
