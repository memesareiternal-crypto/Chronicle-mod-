package dev.chronicle.test;

import com.mojang.authlib.GameProfile;
import dev.chronicle.Chronicle;
import dev.chronicle.Settings;
import dev.chronicle.power.Applications;
import dev.chronicle.power.FlightGuard;
import dev.chronicle.power.Physics;
import dev.chronicle.power.Potential;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.util.UUID;

/** Regression tests for composite ownership and configurable physical output. */
@GameTestHolder(Chronicle.ID)
@PrefixGameTestTemplate(false)
public final class PhysicsAuditTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "physics-audit"));
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1))));
        Potential.maximize(player);
        return player;
    }

    @GameTest(template="empty")
    public static void dismounted_passenger_claim_is_released(GameTestHelper helper) {
        var owner = player(helper);
        var boat = helper.spawn(EntityType.BOAT, new BlockPos(3, 3, 3));
        var passenger = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 3, 3));
        Physics.Hold hold = null;
        Physics.Hold replacement = null;
        try {
            helper.assertTrue(passenger.startRiding(boat, true), "Passenger must mount the fixture boat");
            hold = Physics.take(owner, passenger);
            helper.assertTrue(hold != null && Physics.controlled(passenger), "Composite acquisition must claim the passenger");
            passenger.stopRiding();
            hold.close();
            helper.assertFalse(Physics.controlled(passenger), "Dismount must not strand a passenger claim after release");
            helper.assertFalse(Physics.controlled(boat), "Release must also clear the original vehicle claim");
            replacement = Physics.take(player(helper), passenger);
            helper.assertTrue(replacement != null, "A released passenger must be available to a different owner");
            helper.succeed();
        } finally {
            if (hold != null) hold.close();
            if (replacement != null) replacement.close();
            boat.discard();
            passenger.discard();
        }
    }

    @GameTest(template="empty")
    public static void remount_cannot_overwrite_another_owners_claim(GameTestHelper helper) {
        var first = player(helper);
        var second = player(helper);
        var passenger = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 3, 3));
        var boat = helper.spawn(EntityType.BOAT, new BlockPos(3, 3, 3));
        Physics.Hold original = null;
        Physics.Hold conflicting = null;
        try {
            original = Physics.take(first, passenger);
            helper.assertTrue(original != null, "The first owner must establish a grip");
            helper.assertTrue(passenger.startRiding(boat, true), "A claimed target must mount the fixture boat");
            conflicting = Physics.take(second, boat);
            helper.assertTrue(conflicting == null, "A vehicle acquisition must reject passengers already claimed by another owner");
            helper.succeed();
        } finally {
            if (conflicting != null) conflicting.close();
            if (original != null) original.close();
            passenger.discard();
            boat.discard();
        }
    }

    @GameTest(template="empty")
    public static void pressure_applies_one_impulse_per_composite(GameTestHelper helper) {
        var owner = player(helper);
        Potential.setLevel(owner, 1);
        var boat = helper.spawn(EntityType.BOAT, new BlockPos(3, 3, 3));
        var passenger = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 3, 3));
        try {
            helper.assertTrue(passenger.startRiding(boat, true), "Passenger must mount the pressure fixture");
            boat.positionRider(passenger);
            Vec3 center = boat.getBoundingBox().getCenter();
            owner.setPos(center.x - 1, center.y - owner.getEyeHeight(), center.z);
            boat.setDeltaMovement(Vec3.ZERO);
            double greatestSingleImpulse = Potential.force(owner) / Math.max(1, Math.pow(Physics.mass(boat), .18));
            Applications.pressure(owner, false, 1, true);
            helper.assertTrue(boat.getDeltaMovement().length() <= greatestSingleImpulse + 1e-6,
                "The boat and its passenger must not count as two independent force recipients");
            helper.succeed();
        } finally {
            passenger.discard();
            boat.discard();
        }
    }

    @GameTest(template="empty")
    public static void disabling_compression_damage_does_not_add_melee_damage(GameTestHelper helper) {
        var owner = player(helper);
        var target = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 3, 3));
        double previous = Settings.COMPRESSION_DAMAGE.get();
        Physics.Hold hold = null;
        try {
            Settings.COMPRESSION_DAMAGE.set(0.);
            hold = Physics.take(owner, target);
            helper.assertTrue(hold != null, "The compression fixture must be grippable");
            float health = target.getHealth();
            for (int tick = 0; tick < 24; tick++) {
                helper.assertTrue(hold.steer(owner, target.getBoundingBox().getCenter(), true), "The fixture grip must remain stable");
                hold.compress(owner, 2);
            }
            helper.assertTrue(target.getHealth() == health, "A zero compression multiplier must suppress all compression damage, including melee reinforcement");
            helper.succeed();
        } finally {
            Settings.COMPRESSION_DAMAGE.set(previous);
            if (hold != null) hold.close();
            target.discard();
        }
    }

    @GameTest(template="empty")
    public static void authorized_flight_clears_timeout_without_granting_permissions(GameTestHelper helper) throws IllegalAccessException {
        var owner = player(helper);
        var floating = ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class, "f_9736_");
        var floatingTicks = ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class, "f_9737_");
        boolean originalFloating = floating.getBoolean(owner.connection);
        int originalTicks = floatingTicks.getInt(owner.connection);
        boolean originalPermission = owner.getAbilities().mayfly;
        boolean originalGravity = owner.isNoGravity();
        try {
            floating.setBoolean(owner.connection, true);
            floatingTicks.setInt(owner.connection, 79);
            FlightGuard.allowControlledFlight(owner);
            helper.assertFalse(floating.getBoolean(owner.connection), "An authorized force must clear the vanilla floating condition");
            helper.assertTrue(floatingTicks.getInt(owner.connection) == 0, "Accumulated floating timeout must also clear");
            helper.assertTrue(owner.getAbilities().mayfly == originalPermission, "Anti-cheat acknowledgement must preserve survival flight permissions");
            helper.assertTrue(owner.isNoGravity() == originalGravity, "The guard must leave physical gravity control to the force solver");
            helper.succeed();
        } finally {
            floating.setBoolean(owner.connection, originalFloating);
            floatingTicks.setInt(owner.connection, originalTicks);
        }
    }
}
