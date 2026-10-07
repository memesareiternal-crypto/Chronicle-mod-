package dev.chronicle.test;

import com.mojang.authlib.GameProfile;
import dev.chronicle.Chronicle;
import dev.chronicle.Settings;
import dev.chronicle.power.Physics;
import dev.chronicle.power.Potential;
import dev.chronicle.power.Ward;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.UUID;

/** Barrier regressions exercise enclosure decisions and actual projectile acquisition together. */
@GameTestHolder(Chronicle.ID)
@PrefixGameTestTemplate(false)
public final class BarrierAuditTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "barrier-audit"));
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1))));
        Potential.maximize(player);
        return player;
    }

    @GameTest(template="empty") public static void dome_does_not_protect_through_open_bottom(GameTestHelper helper) {
        var player = player(helper);
        var ward = new Ward(player, null, null, Ward.Shape.DOME);
        var attacker = EntityType.ZOMBIE.create(helper.getLevel());
        Vec3 center = ward.center(player);
        try {
            attacker.setNoAi(true);
            attacker.setPos(center.add(0, -ward.radius(player) - 3, 0));
            helper.getLevel().addFreshEntity(attacker);
            helper.assertFalse(ward.protects(player, player, attacker), "An attack entering below the open dome must pass");
            attacker.setPos(center.add(0, ward.radius(player) + 2, 0));
            helper.assertTrue(ward.protects(player, player, attacker), "The same dome must still protect against attacks crossing its top");
            helper.succeed();
        } finally {
            ward.close(player, false);
            attacker.discard();
        }
    }

    @GameTest(template="empty") public static void enclosing_a_villager_does_not_push_it_out(GameTestHelper helper) {
        var player = player(helper);
        var ward = new Ward(player, null, null, Ward.Shape.SPHERE);
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        try {
            villager.setNoAi(true);
            villager.setPos(ward.center(player).add(1, 0, 0));
            villager.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(villager);
            helper.assertTrue(ward.tick(player, false), "A protective enclosure must remain intact");
            helper.assertTrue(villager.getDeltaMovement().lengthSqr() < 1e-10, "Protection must not apply pressure to a stationary enclosed NPC");
            helper.succeed();
        } finally {
            ward.close(player, false);
            villager.discard();
        }
    }

    @GameTest(template="empty") public static void friendly_arrows_can_leave_an_enclosure(GameTestHelper helper) {
        var player = player(helper);
        var ward = new Ward(player, null, null, Ward.Shape.SPHERE);
        var archer = EntityType.SKELETON.create(helper.getLevel());
        Vec3 center = ward.center(player);
        var arrow = new Arrow(helper.getLevel(), center.x, center.y, center.z + ward.radius(player) - .5);
        try {
            archer.setNoAi(true);
            archer.setPos(center.add(1, 0, 0));
            helper.getLevel().addFreshEntity(archer);
            arrow.setOwner(archer);
            arrow.setDeltaMovement(0, 0, 2);
            helper.getLevel().addFreshEntity(arrow);
            helper.assertTrue(ward.tick(player, false), "An outgoing friendly shot must not break the field");
            helper.assertFalse(Physics.controlled(arrow), "The barrier must let an enclosed shooter's arrow leave");
            helper.assertTrue(arrow.getDeltaMovement().z > 1.5, "Outgoing projectile velocity must survive unchanged");
            helper.succeed();
        } finally {
            ward.close(player, false);
            arrow.discard();
            archer.discard();
        }
    }

    @GameTest(template="empty") public static void dropped_items_do_not_spend_projectile_scan_budget(GameTestHelper helper) {
        var player = player(helper);
        var ward = new Ward(player, null, null, Ward.Shape.SPHERE);
        Vec3 center = ward.center(player);
        Vec3 start = center.add(0, 0, ward.radius(player) + .5);
        var clutter = new ArrayList<ItemEntity>();
        var arrow = new Arrow(helper.getLevel(), start.x, start.y, start.z);
        try {
            // Insert clutter in the projectile's spatial section before the arrow so a shared
            // entity budget would exhaust itself before inspecting the incoming threat.
            for (int index = 0; index < Settings.PROJECTILE_BUDGET.get() + 8; index++) {
                var item = new ItemEntity(helper.getLevel(), start.x, start.y, start.z, new ItemStack(Items.COBBLESTONE));
                helper.getLevel().addFreshEntity(item);
                clutter.add(item);
            }
            arrow.setDeltaMovement(0, 0, -2);
            helper.getLevel().addFreshEntity(arrow);
            helper.assertTrue(ward.tick(player, false), "Clutter must not interfere with barrier maintenance");
            helper.assertTrue(Physics.controlled(arrow) && ward.captured() > 0, "A crossing arrow must be captured on this tick despite more items than the scan budget");
            helper.succeed();
        } finally {
            ward.close(player, false);
            arrow.discard();
            for (var item : clutter) item.discard();
        }
    }
}
