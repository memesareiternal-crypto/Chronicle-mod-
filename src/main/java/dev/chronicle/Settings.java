package dev.chronicle;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Settings {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_LEVEL, STRUCTURE_LIMIT, TERRAIN_BUDGET, CRYSTAL_RARITY, AWAKEN_TIME;
    public static final ForgeConfigSpec.IntValue FLIGHT_LEVEL, FIELD_LEVEL, SENSE_LEVEL, HEAL_LEVEL, REGION_LEVEL, BURST_LEVEL;
    public static final ForgeConfigSpec.DoubleValue XP_RATE, AGE_XP, XP_CURVE, CAPACITY, CAPACITY_GROWTH, RECOVERY, EXERT_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue RANGE, RANGE_GROWTH, MAX_RANGE, FORCE, FORCE_GROWTH, RESISTANCE, HARDNESS, BULLET_CAPACITY, FIELD_RADIUS, FLIGHT_COST, HEAL_RATE;
    public static final ForgeConfigSpec.BooleanValue INVENTORIES, PVP, TERRAIN, MOB_COLLISIONS, HINTS, AURA;
    public static final ForgeConfigSpec.IntValue ARMOR_COOLDOWN, CHOKE_TIME, CRYSTAL_MIN_Y, CRYSTAL_MAX_Y;
    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("growth");
        MAX_LEVEL = b.defineInRange("maximumLevel", 100, 1, 1000);
        XP_RATE = b.defineInRange("experiencePerEffort", .08, 0., 100.);
        AGE_XP = b.defineInRange("experiencePerOnlineSecond", .025, 0., 100.);
        XP_CURVE = b.defineInRange("experienceCurve", 45., 1., 100000.);
        CAPACITY = b.defineInRange("initialStrainCapacity", 75., 1., 100000.);
        CAPACITY_GROWTH = b.defineInRange("strainCapacityPerLevel", 7., 0., 10000.);
        RECOVERY = b.defineInRange("restRecoveryPerTick", .45, 0., 1000.);
        EXERT_DAMAGE = b.defineInRange("overexertionDamagePerSecond", 1., .01, 1000.);
        b.pop().push("physics");
        RANGE = b.defineInRange("initialReach", 10., 2., 128.);
        RANGE_GROWTH = b.defineInRange("reachPerLevel", .7, 0., 10.);
        MAX_RANGE = b.defineInRange("maximumReach", 96., 2., 256.);
        FORCE = b.defineInRange("initialForce", .18, .01, 10.);
        FORCE_GROWTH = b.defineInRange("forcePerLevel", .025, 0., 10.);
        RESISTANCE = b.defineInRange("livingResistance", 1., 0., 100.);
        HARDNESS = b.defineInRange("cutHardnessPerLevel", .15, 0., 100.);
        BULLET_CAPACITY = b.defineInRange("projectileDamageCapacityPerLevel", .65, 0., 100.);
        STRUCTURE_LIMIT = b.defineInRange("maximumStructureVolume", 4096, 1, 32768);
        TERRAIN_BUDGET = b.defineInRange("maximumChangedBlocksPerAction", 192, 1, 4096);
        INVENTORIES = b.define("carryBlockEntityData", true);
        TERRAIN = b.define("allowTerrainChanges", true);
        PVP = b.define("affectOtherPlayers", true);
        MOB_COLLISIONS = b.define("thrownMobsDamageOthers", true);
        ARMOR_COOLDOWN = b.defineInRange("armorStripCooldownTicks", 600, 20, 72000);
        CHOKE_TIME = b.defineInRange("maximumChokeTicks", 80, 10, 600);
        b.pop().push("sustainedPower");
        FIELD_RADIUS = b.defineInRange("fieldRadius", 4., 1., 32.);
        FLIGHT_COST = b.defineInRange("flightEffortPerTick", .6, .01, 100.);
        HEAL_RATE = b.defineInRange("healingPerSecond", .5, 0., 20.);
        b.pop().push("unlocks");
        FLIGHT_LEVEL = b.defineInRange("flight", 8, 1, 1000);
        FIELD_LEVEL = b.defineInRange("fields", 12, 1, 1000);
        SENSE_LEVEL = b.defineInRange("radar", 18, 1, 1000);
        HEAL_LEVEL = b.defineInRange("regeneration", 25, 1, 1000);
        REGION_LEVEL = b.defineInRange("largeStructures", 30, 1, 1000);
        BURST_LEVEL = b.defineInRange("psionicExplosion", 50, 1, 1000);
        b.pop().push("awakening");
        CRYSTAL_RARITY = b.comment("Average newly generated overworld chunks per chamber attempt").defineInRange("chamberRarity", 300, 1, 100000);
        AWAKEN_TIME = b.defineInRange("exposureTicks", 80, 1, 12000);
        CRYSTAL_MIN_Y = b.defineInRange("minimumY", -55, -64, 300);
        CRYSTAL_MAX_Y = b.defineInRange("maximumY", -25, -64, 300);
        b.pop().push("presentation");
        HINTS = b.comment("Vanilla action-bar hints only; no custom HUD").define("controlHints", true);
        AURA = b.comment("Thin geometry around active players; never particles").define("thinAura", true);
        b.pop(); SPEC = b.build();
    }
    private Settings() {}
}
