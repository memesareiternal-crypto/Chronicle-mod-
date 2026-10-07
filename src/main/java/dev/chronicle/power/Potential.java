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
    public static double threshold(ServerPlayer p) { return Settings.CAPACITY.get() + Math.pow(level(p)-1,1.5) * Settings.CAPACITY_GROWTH.get(); }
    public static double strength() { return Settings.OVERALL_STRENGTH.get(); }
    public static double output(ServerPlayer p){return data(p).contains("output")?Mth.clamp(data(p).getDouble("output"),.05,1):1;}
    public static void adjustOutput(ServerPlayer p,int direction){data(p).putDouble("output",Mth.clamp(output(p)+Integer.signum(direction)*Settings.OUTPUT_STEP.get(),.05,1));}
    public static double scale(ServerPlayer p) { return Math.pow(Settings.FORCE_SCALING.get(),level(p)-1); }
    public static double force(ServerPlayer p) { return (Settings.FORCE.get() + (level(p)-1) * Settings.FORCE_GROWTH.get()) * scale(p) * strength()*output(p); }
    public static double reach(ServerPlayer p) { return Mth.clamp((Settings.RANGE.get() + Math.pow(level(p)-1,1.55) * Settings.RANGE_GROWTH.get()) * Math.sqrt(strength())*(.25+.75*Math.sqrt(output(p))),2,Math.min(256,Settings.MAX_RANGE.get())); }
    public static int blocks(ServerPlayer p) { return Math.max(1,Math.min(Settings.STRUCTURE_LIMIT.get(),(int)(Settings.INITIAL_BLOCKS.get()*Math.pow(Settings.BLOCK_SCALING.get(),level(p)-1)*strength()*(.1+.9*Math.pow(output(p),1.3))))); }
    public static int area(ServerPlayer p) { return Math.max(1,Math.min(Settings.AREA_LIMIT.get(),(int)Math.ceil((1+(level(p)-1)*1.5)*Math.sqrt(strength())*(.3+.7*Math.sqrt(output(p)))))); }
    public static int targets(ServerPlayer p) { return Math.max(1,Math.min(Settings.TARGET_LIMIT.get(),(int)Math.ceil(1+(Math.pow(Settings.TARGET_GROWTH.get(),level(p)-1)-1)*Math.sqrt(strength())*Math.pow(output(p),2)))); }
    public static double massLimit(ServerPlayer p) { return Math.min(Settings.MAX_MASS.get(), blocks(p) * 8.); }
    public static void grant(ServerPlayer p) {
        if (acquired(p)) return;
        var d = data(p); d.putBoolean("acquired", true); d.putBoolean("active", true); d.putInt("level", 1);
        d.putInt("color", Mth.hsvToRgb(p.getRandom().nextFloat(), .42f, 1) & 0xFFFFFF);
    }
    public static void maximize(ServerPlayer p) { grant(p); data(p).putInt("level", Settings.MAX_LEVEL.get()); data(p).putDouble("xp", required(Settings.MAX_LEVEL.get())); }
    public static void setLevel(ServerPlayer p, int level) { grant(p); int value=Mth.clamp(level,1,Settings.MAX_LEVEL.get()); data(p).putInt("level",value); data(p).putDouble("xp",required(value)); }
    public static void setProgress(ServerPlayer p, double xp) { grant(p); var d=data(p); d.putInt("level",1); d.putDouble("xp",0); experience(p,Math.max(0,xp)); }
    public static void resetProgress(ServerPlayer p) { setLevel(p,1); data(p).putDouble("strain",0); }
    public static void remove(ServerPlayer p) { p.getPersistentData().remove(KEY); }
    public static void inherit(ServerPlayer old, ServerPlayer replacement) { replacement.getPersistentData().put(KEY, data(old).copy()); }
    public static double required(int level) { return Settings.XP_CURVE.get() * (level - 1.) * (level - 1.); }
    public static void experience(ServerPlayer p, double amount) {
        if(!Double.isFinite(amount) || amount<=0) return;
        var d = data(p); double xp = Math.min(1e12,d.getDouble("xp") + amount); int level = level(p);
        while (level < Settings.MAX_LEVEL.get() && xp >= required(level + 1)) level++;
        d.putDouble("xp", xp); d.putInt("level", level);
    }
    public static void spend(ServerPlayer p, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        amount*=.2+.8*output(p);data(p).putDouble("strain", Math.min(threshold(p)*100,strain(p) + amount)); experience(p, Math.min(amount,threshold(p)) * Settings.XP_RATE.get() * Settings.LEVEL_RATE.get());
    }
    public static void recover(ServerPlayer p) { data(p).putDouble("strain", Math.max(0, strain(p) - Settings.RECOVERY.get())); }
    public static double control(ServerPlayer p) { return 1. / (1.+Math.max(0,strain(p)/Math.max(1,threshold(p))-1)*1.5); }
    private Potential() {}
}
