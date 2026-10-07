package dev.chronicle.test;

import com.mojang.authlib.GameProfile;
import dev.chronicle.Chronicle;
import dev.chronicle.entity.MatterBody;
import dev.chronicle.world.MassJobs;
import dev.chronicle.world.WorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Transfer interruption and large selection tests exercise actual world/body ownership. */
@GameTestHolder(Chronicle.ID)
@PrefixGameTestTemplate(false)
public final class MassAuditTests {
    private static BlockPos point(GameTestHelper h) { return h.absolutePos(new BlockPos(2, 3, 2)); }
    private static ServerPlayer player(GameTestHelper h, BlockPos pos) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "mass-audit"));
        player.setPos(Vec3.atCenterOf(pos));
        return player;
    }

    @GameTest(template = "empty")
    public static void failed_block_entity_restoration_retains_authoritative_cell(GameTestHelper h) {
        BlockPos from = point(h), target = from.above(2);
        h.getLevel().setBlock(from, Blocks.STONE.defaultBlockState(), 18);
        MatterBody body = MatterBody.capture(h.getLevel(), List.of(from), null);
        h.assertTrue(body != null, "Source must become a real carrier");
        var old = body.cells().get(0);
        body.removeCell(old);
        // Simulate a mod block whose inventory-bearing snapshot can no longer instantiate a block entity.
        CompoundTag inventory = new CompoundTag();
        inventory.putString("id", "minecraft:chest");
        body.addCell(new MatterBody.Cell(old.offset(), old.state(), inventory));
        h.assertTrue(MassJobs.place(body, target, null), "Transfer must enqueue");
        MassJobs.tick(h.getLevel(), 16);
        h.assertTrue(body.isAlive() && body.mass() == 1, "Unrestorable inventory data must remain owned by the carrier");
        h.assertTrue(h.getLevel().getBlockState(target).isAir(), "Failed target restoration must leave the destination unchanged");
        h.assertTrue(body.cells().get(0).data() != null, "Failure must preserve the complete original snapshot");
        body.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rolled_back_small_placement_cannot_duplicate_cells(GameTestHelper h) {
        BlockPos from = point(h), target = from.above(2);
        h.getLevel().setBlock(from, Blocks.STONE.defaultBlockState(), 18);
        h.getLevel().setBlock(from.east(), Blocks.GOLD_BLOCK.defaultBlockState(), 18);
        MatterBody body = MatterBody.capture(h.getLevel(), List.of(from, from.east()), null);
        var invalid = body.cells().get(1);
        body.removeCell(invalid);
        CompoundTag data = new CompoundTag();
        data.putString("id", "minecraft:chest");
        body.addCell(new MatterBody.Cell(invalid.offset(), invalid.state(), data));
        h.assertFalse(body.place(target, null), "Unsupported restoration must stop the transfer");
        h.assertTrue(h.getLevel().getBlockState(target).isAir(), "Atomic rollback must restore the first destination");
        h.assertTrue(body.isAlive() && body.mass() == 2,
            "Both original cells remain exclusively in the carrier after rollback");
        h.assertTrue(h.getLevel().getBlockState(target.east()).isAir(), "Failed restoration must revert its own target");
        var unsupported = body.cells().stream().filter(c -> c.data() != null).findFirst().orElseThrow();
        body.removeCell(unsupported);
        body.addCell(new MatterBody.Cell(unsupported.offset(), unsupported.state(), null));
        h.assertTrue(body.place(target, null), "A valid retry must transfer the recovered snapshot");
        h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.STONE)
            && h.getLevel().getBlockState(target.east()).is(Blocks.GOLD_BLOCK), "Retry must preserve both original blocks");
        h.assertFalse(body.isAlive(), "Successful retry must relinquish all carrier ownership");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void disappearing_capture_carrier_does_not_delete_remaining_source(GameTestHelper h) {
        BlockPos start = point(h);
        h.getLevel().setBlock(start, Blocks.STONE.defaultBlockState(), 18);
        h.getLevel().setBlock(start.east(), Blocks.GOLD_BLOCK.defaultBlockState(), 18);
        ServerPlayer actor = player(h, start);
        AtomicReference<MatterBody> result = new AtomicReference<>();
        MassJobs.capture(actor, start, 1, 2, result::set);
        MatterBody carrier = null;
        for (int i = 0; i < 64 && carrier == null; i++) {
            MassJobs.tick(h.getLevel(), 1);
            carrier = h.getLevel().getEntitiesOfClass(MatterBody.class,
                new AABB(start, start.offset(2, 2, 2)), MatterBody::transferring).stream().findFirst().orElse(null);
        }
        h.assertTrue(carrier != null, "Capture must enter its persistent carrier stage");
        h.assertTrue(carrier.mass() == 0, "Fixture removes the carrier before any extraction commits");
        carrier.discard(); // Entity removal models the condition encountered after chunk unloading.
        MassJobs.tick(h.getLevel(), 32);
        h.assertTrue(h.getLevel().getBlockState(start).is(Blocks.STONE), "Removed carrier must not receive source ownership");
        h.assertTrue(h.getLevel().getBlockState(start.east()).is(Blocks.GOLD_BLOCK), "Remaining cells must survive an interrupted extraction");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void offline_owner_cannot_settle_through_permission_checks(GameTestHelper h) {
        BlockPos start = point(h);
        h.getLevel().setBlock(start.below(), Blocks.BEDROCK.defaultBlockState(), 18);
        h.getLevel().setBlock(start, Blocks.GOLD_BLOCK.defaultBlockState(), 18);
        ServerPlayer actor = player(h, start); // Deliberately absent from the server's connected player list.
        MatterBody body = MatterBody.capture(h.getLevel(), List.of(start), actor);
        h.assertTrue(body != null, "Authorised extraction must succeed");
        body.release();
        for (int i = 0; i < 80; i++) body.tick();
        h.assertTrue(body.isAlive() && body.mass() == 1, "Offline-owner settling must retain the carrier until its permissions can be checked");
        h.assertTrue(h.getLevel().getBlockState(start).isAir(), "Automatic placement must not fall back to an unrestricted actor");
        body.discard();
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void queued_1728_cell_mass_preserves_inventory_and_restart_snapshot(GameTestHelper h) {
        // Large fixtures must not inherit the small structure grid's terrain-dependent
        // origin. A fixed region isolates them from other concurrently running fixtures.
        BlockPos start = new BlockPos(10000, 288, 10000);
        BlockPos end = start.offset(11, 11, 11);
        List<ChunkPos> forced = new ArrayList<>();
        for (int x = (start.getX() - 1) >> 4; x <= (end.getX() + 1) >> 4; x++) {
            for (int z = (start.getZ() - 1) >> 4; z <= (end.getZ() + 1) >> 4; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!h.getLevel().getForcedChunks().contains(chunk.toLong())) {
                    h.getLevel().setChunkForced(x, z, true);
                    forced.add(chunk);
                }
                h.getLevel().getChunk(x, z);
            }
        }
        // Rerunning the persisted test world can leave old floating cubes nearby. An air moat
        // makes this fixture's connected component exactly its intended 1,728 cells.
        for (BlockPos pos : BlockPos.betweenClosed(start.offset(-1, -1, -1), end.offset(1, 1, 1)))
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
        for (BlockPos pos : BlockPos.betweenClosed(start, start.offset(11, 11, 11)))
            h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 18);
        h.getLevel().setBlock(start, Blocks.CHEST.defaultBlockState(), 18);
        ((ChestBlockEntity) h.getLevel().getBlockEntity(start)).setItem(0, new ItemStack(Items.DIAMOND, 7));
        AtomicReference<MatterBody> result = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<String> diagnostic = new AtomicReference<>("Capture callback has not completed yet");
        AtomicReference<Integer> callbackRemaining = new AtomicReference<>();
        ServerPlayer actor = player(h, start);
        h.assertTrue(actor.isAlive() && actor.level() == h.getLevel(), "Stress actor must remain alive in the fixture dimension");
        h.assertTrue(WorldAccess.loaded(h.getLevel(), start) && WorldAccess.edit(actor, start),
            "Stress source must be loaded and editable before submission");
        MassJobs.capture(actor, start, 24, 1728, body -> {
            int remaining = 0;
            String firstRemaining = "none";
            for (BlockPos pos : BlockPos.betweenClosed(start, end))
                if (!h.getLevel().getBlockState(pos).isAir()) {
                    remaining++;
                    if(firstRemaining.equals("none"))firstRemaining=pos.toShortString()+":"+h.getLevel().getBlockState(pos);
                }
            diagnostic.set("Capture callback: mass=" + (body == null ? "null" : body.mass())
                + ", alive=" + (body != null && body.isAlive()) + ", sourceRemaining=" + remaining+", first="+firstRemaining);
            callbackRemaining.set(remaining);
            result.set(body);
            completed.set(true);
        });
        h.assertTrue(h.getLevel().getBlockState(start).is(Blocks.CHEST), "Large queue submission must leave the source in place");
        h.succeedWhen(() -> {
            MatterBody body = result.get();
            h.assertTrue(completed.get() && body != null && body.isAlive() && body.mass() == 1728,
                "A 12-cubed region must complete as one body. " + diagnostic.get());
            h.assertTrue(callbackRemaining.get() != null && callbackRemaining.get() == 0,
                "The capture callback must observe every source cell empty. " + diagnostic.get());
            for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
                var state = h.getLevel().getBlockState(pos);
                if (!state.isAir()) {
                    BlockPos expectedOffset = pos.subtract(start);
                    var savedCell = body.cells().stream().filter(c -> c.offset().equals(expectedOffset)).findFirst();
                    h.assertTrue(false, "Every source cell must transfer exactly once; first remaining="
                        + pos.toShortString() + ", state=" + state + ", snapshotOffset=" + expectedOffset.toShortString()
                        + ", snapshotCell=" + savedCell.map(c -> c.state().toString()).orElse("missing")
                        + ". " + diagnostic.get());
                }
            }
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(start, start.offset(12, 12, 12)).inflate(2),
                item -> item.getItem().is(Items.DIAMOND)).isEmpty(), "Inventory transfer must not emit duplicate items");
            CompoundTag saved = new CompoundTag();
            body.saveWithoutId(saved);
            MatterBody restored = Chronicle.MATTER.get().create(h.getLevel());
            restored.load(saved);
            h.assertTrue(restored.mass() == 1728, "Restart snapshot must preserve the entire large mass");
            var chest = restored.cells().stream().filter(c -> c.state().is(Blocks.CHEST)).findFirst().orElseThrow();
            var contents = chest.data().getList("Items", CompoundTag.TAG_COMPOUND);
            h.assertTrue(contents.size() == 1 && contents.getCompound(0).getByte("Count") == 7,
                "Restart must preserve all seven diamonds exactly once");
            h.assertFalse(restored.isHeld() || restored.transferring(), "A saved carrier must recover without a stranded transfer lock");
            body.discard();
            restored.discard();
            // Keep the fixture loaded through its complete assertion pass. Releasing in the
            // capture callback can unload/reload these distant chunks before source validation.
            for (ChunkPos chunk : forced) h.getLevel().setChunkForced(chunk.x, chunk.z, false);
        });
    }
}
