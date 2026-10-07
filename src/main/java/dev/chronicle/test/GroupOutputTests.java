package dev.chronicle.test;

import com.mojang.authlib.GameProfile;
import dev.chronicle.Chronicle;
import dev.chronicle.Settings;
import dev.chronicle.power.Applications;
import dev.chronicle.power.Physics;
import dev.chronicle.power.Potential;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Chronicle.ID)
@PrefixGameTestTemplate(false)
public final class GroupOutputTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "group-output"));
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1))));
        Potential.maximize(player);
        return player;
    }

    private static List<Zombie> crowd(GameTestHelper helper) {
        var result = new ArrayList<Zombie>();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 3, 3)));
        for (int index = 0; index < 256; index++) {
            var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 3, 3));
            zombie.setNoAi(true);
            zombie.setNoGravity(false);
            zombie.setPos(center.add((index % 16 - 7.5) * .12, 0, (index / 16 - 7.5) * .12));
            result.add(zombie);
        }
        return result;
    }

    @GameTest(template="empty")
    public static void progression_grips_one_two_dozens_and_hundreds(GameTestHelper helper) {
        try (var settings = new ScaleFixture()) {
            var owner = player(helper);
            var crowd = crowd(helper);
            var holds = new ArrayList<Physics.Hold>();
            try {
                var target = crowd.get(0);
                Potential.setLevel(owner, 1);
                helper.assertTrue(Applications.group(owner, target, 4).size() == 1,
                    "First-stage output must select one actual mob");
                Potential.setLevel(owner, 2);
                helper.assertTrue(Applications.group(owner, target, 4).size() == 2,
                    "Second-stage output must select two actual mobs");
                Potential.setLevel(owner, 6);
                int middle = Applications.group(owner, target, 4).size();
                helper.assertTrue(middle >= 20 && middle < 100,
                    "Intermediate output must select actual mobs in the dozens");
                Potential.setLevel(owner, 10);
                var selection = Applications.group(owner, target, 4);
                helper.assertTrue(selection.size() >= 200,
                    "Maximum-stage output must select actual mobs in the hundreds");
                for (var entity : selection) {
                    var hold = Physics.take(owner, entity);
                    helper.assertTrue(hold != null, "Every selected member must support an independent reversible grip");
                    holds.add(hold);
                    helper.assertTrue(hold.steer(owner, entity.getBoundingBox().getCenter().add(0, .5, 0), false),
                        "The full crowd must be controllable through the shared force solver");
                }
                for (var entity : selection) helper.assertTrue(Physics.controlled(entity),
                    "Hundreds-scale selection must establish real ownership rather than visual-only stasis");
                for (var hold : holds) hold.close();
                for (var entity : selection) {
                    helper.assertFalse(Physics.controlled(entity), "Group release must clear every ownership claim");
                    helper.assertFalse(entity.isNoGravity(), "Group release must restore every original gravity state");
                }
                helper.succeed();
            } finally {
                for (var hold : holds) hold.close();
                for (var entity : crowd) entity.discard();
            }
        }
    }

    @GameTest(template="empty")
    public static void output_adjustment_changes_real_group_capacity_and_force(GameTestHelper helper) {
        try (var settings = new ScaleFixture()) {
            var owner = player(helper);
            var crowd = crowd(helper);
            try {
                var target = crowd.get(0);
                int highCount = Applications.group(owner, target, 4).size();
                double highForce = Potential.force(owner);
                double highReach = Potential.reach(owner);
                int highBlocks = Potential.blocks(owner);
                for (int step = 0; step < 30; step++) Potential.adjustOutput(owner, -1);
                int lowCount = Applications.group(owner, target, 4).size();
                helper.assertTrue(Potential.output(owner) >= .05 && Potential.output(owner) < .06,
                    "Repeated downward adjustment must stop at the supported minimum output");
                helper.assertTrue(highCount >= 200 && lowCount <= 3,
                    "Output adjustment must turn actual hundreds-scale group selection into delicate control");
                helper.assertTrue(Potential.force(owner) < highForce && Potential.reach(owner) < highReach,
                    "Output adjustment must change applied force and reach");
                helper.assertTrue(Potential.blocks(owner) < highBlocks,
                    "Output adjustment must change the carried terrain budget as well");
                for (int step = 0; step < 30; step++) Potential.adjustOutput(owner, 1);
                helper.assertTrue(Potential.output(owner) == 1 && Applications.group(owner, target, 4).size() == highCount,
                    "Increasing output must restore the available group without changing progression");
                helper.assertTrue(Potential.level(owner) == 10,
                    "Delicate control must preserve the player's acquired progression");
                helper.succeed();
            } finally {
                for (var entity : crowd) entity.discard();
            }
        }
    }

    @GameTest(template="empty")
    public static void co_moving_members_of_a_held_group_do_not_damage_each_other(GameTestHelper helper) {
        try (var settings = new ScaleFixture()) {
            var owner = player(helper);
            var first = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 3, 3));
            var second = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 3, 3));
            Physics.Hold firstHold = null;
            Physics.Hold secondHold = null;
            try {
                first.setNoAi(true);
                second.setNoAi(true);
                firstHold = Physics.take(owner, first);
                secondHold = Physics.take(owner, second);
                helper.assertTrue(firstHold != null && secondHold != null, "Both members must share one owner's reversible control");
                Vec3 destination = first.getBoundingBox().getCenter().add(6, 0, 0);
                helper.assertTrue(firstHold.steer(owner, destination, false) && secondHold.steer(owner, destination, false),
                    "Both members must have a real shared moving grip");
                helper.assertTrue(first.getDeltaMovement().length() > .65,
                    "Fixture must move fast enough to exercise physical collision accounting");
                float firstHealth = first.getHealth();
                float secondHealth = second.getHealth();
                first.setPos(first.position().add(first.getDeltaMovement()));
                second.setPos(second.position().add(second.getDeltaMovement()));
                Physics.tick(helper.getLevel());
                helper.assertTrue(first.getHealth() == firstHealth && second.getHealth() == secondHealth,
                    "A co-moving held group must not be treated as hundreds of attacks between its own members");
                helper.succeed();
            } finally {
                if (firstHold != null) firstHold.close();
                if (secondHold != null) secondHold.close();
                first.discard();
                second.discard();
            }
        }
    }

    /** Synchronous tests restore server choices before any queued test can tick. */
    private static final class ScaleFixture implements AutoCloseable {
        private final TestConfigScope configScope = new TestConfigScope();
        private final int maximumLevel = Settings.MAX_LEVEL.get();
        private final int targetLimit = Settings.TARGET_LIMIT.get();
        private final double growth = Settings.TARGET_GROWTH.get();
        private final double strength = Settings.OVERALL_STRENGTH.get();
        private final double outputStep = Settings.OUTPUT_STEP.get();
        private ScaleFixture() {
            Settings.MAX_LEVEL.set(10);
            Settings.TARGET_LIMIT.set(512);
            Settings.TARGET_GROWTH.set(1.9);
            Settings.OVERALL_STRENGTH.set(1.);
            Settings.OUTPUT_STEP.set(.05);
        }
        @Override public void close() {
            try {
                Settings.MAX_LEVEL.set(maximumLevel);
                Settings.TARGET_LIMIT.set(targetLimit);
                Settings.TARGET_GROWTH.set(growth);
                Settings.OVERALL_STRENGTH.set(strength);
                Settings.OUTPUT_STEP.set(outputStep);
            } finally {
                configScope.close();
            }
        }
    }
}
