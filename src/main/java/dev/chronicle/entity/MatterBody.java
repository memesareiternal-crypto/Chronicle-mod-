package dev.chronicle.entity;

import dev.chronicle.Chronicle;
import dev.chronicle.Settings;
import dev.chronicle.world.WorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/** One physical body and one cached exterior mesh, regardless of the number of stored blocks. */
public final class MatterBody extends Entity implements IEntityAdditionalSpawnData {
    private static final EntityDataAccessor<BlockPos> EXTENT = SynchedEntityData.defineId(MatterBody.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> TURN = SynchedEntityData.defineId(MatterBody.class, EntityDataSerializers.INT);
    private final List<Cell> cells = new ArrayList<>();
    private long lastHeld = Long.MIN_VALUE;
    private int stillTicks;
    private UUID owner;
    public MatterBody(EntityType<? extends MatterBody> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData() { entityData.define(EXTENT, new BlockPos(1, 1, 1)); entityData.define(TURN, 0); }
    public record Cell(BlockPos offset, BlockState state, CompoundTag data) {}
    public List<Cell> cells() { return Collections.unmodifiableList(cells); }
    public int mass() { return cells.size(); }
    public BlockPos extent() { return entityData.get(EXTENT); }
    public int turn() { return entityData.get(TURN); }
    public void rotate(int delta) { entityData.set(TURN, Math.floorMod(turn() + delta, 4)); }
    public int width() { return turn() % 2 == 0 ? extent().getX() : extent().getZ(); }
    public int depth() { return turn() % 2 == 0 ? extent().getZ() : extent().getX(); }
    public void held() { lastHeld = level().getGameTime(); setNoGravity(true); stillTicks = 0; }
    public void release() { lastHeld = Long.MIN_VALUE; setNoGravity(false); }
    public boolean isHeld() { return lastHeld != Long.MIN_VALUE && level().getGameTime() - lastHeld <= 5; }
    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.scalable(Math.max(extent().getX(), extent().getZ()), extent().getY()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (EXTENT.equals(key)) refreshDimensions(); }
    @Override public boolean isPickable() { return true; }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }
    @Override public double getPassengersRidingOffset() { return extent().getY(); }

    public static MatterBody capture(ServerLevel level, Collection<BlockPos> positions, ServerPlayer actor) {
        if (positions.isEmpty() || positions.size() > Settings.STRUCTURE_LIMIT.get()) return null;
        BlockPos low = new BlockPos(positions.stream().mapToInt(BlockPos::getX).min().orElseThrow(), positions.stream().mapToInt(BlockPos::getY).min().orElseThrow(), positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
        BlockPos high = new BlockPos(positions.stream().mapToInt(BlockPos::getX).max().orElseThrow(), positions.stream().mapToInt(BlockPos::getY).max().orElseThrow(), positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow());
        long volume = (long)(high.getX() - low.getX() + 1) * (high.getY() - low.getY() + 1) * (high.getZ() - low.getZ() + 1);
        if (volume > Settings.STRUCTURE_LIMIT.get()) return null;
        MatterBody body = Chronicle.MATTER.get().create(level);
        if (body == null) return null;
        for (BlockPos pos : new LinkedHashSet<>(positions)) {
            if (!WorldAccess.loaded(level, pos)) return null;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(level, pos) < 0 || (actor != null && !WorldAccess.edit(actor, pos))) return null;
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null && !Settings.INVENTORIES.get()) return null;
            body.cells.add(new Cell(pos.subtract(low), state, blockEntity == null ? null : blockEntity.saveWithFullMetadata()));
        }
        if (body.cells.isEmpty()) return null;
        body.owner = actor == null ? null : actor.getUUID();
        body.entityData.set(EXTENT, high.subtract(low).offset(1, 1, 1));
        body.setPos(low.getX() + body.width() / 2., low.getY(), low.getZ() + body.depth() / 2.);
        if (!level.addFreshEntity(body)) return null;
        // onRemove must not drop container contents that are already in the snapshot.
        for (Cell cell : body.cells) level.removeBlockEntity(low.offset(cell.offset));
        for (Cell cell : body.cells) level.setBlock(low.offset(cell.offset), Blocks.AIR.defaultBlockState(), 18);
        for (Cell cell : body.cells) level.updateNeighborsAt(low.offset(cell.offset), cell.state.getBlock());
        body.held(); return body;
    }

    public static MatterBody captureBox(ServerPlayer player, BlockPos a, BlockPos b) {
        long size = (long)(Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1) * (Math.abs(a.getZ() - b.getZ()) + 1);
        if (size > Settings.STRUCTURE_LIMIT.get()) return null;
        return capture(player.serverLevel(), BlockPos.betweenClosedStream(a, b).map(BlockPos::immutable).toList(), player);
    }
    public BlockPos rotated(BlockPos p) {
        return switch (turn()) {
            case 1 -> new BlockPos(extent().getZ() - 1 - p.getZ(), p.getY(), p.getX());
            case 2 -> new BlockPos(extent().getX() - 1 - p.getX(), p.getY(), extent().getZ() - 1 - p.getZ());
            case 3 -> new BlockPos(p.getZ(), p.getY(), extent().getX() - 1 - p.getX());
            default -> p;
        };
    }
    public boolean place(BlockPos base, ServerPlayer actor) {
        if (!(level() instanceof ServerLevel server)) return false;
        for (Cell cell : cells) {
            BlockPos target = base.offset(rotated(cell.offset));
            if (!WorldAccess.loaded(server, target) || !server.getBlockState(target).canBeReplaced()
                || (actor != null && !WorldAccess.edit(actor, target))) return false;
        }
        for (Cell cell : cells) {
            BlockPos target = base.offset(rotated(cell.offset));
            server.setBlock(target, cell.state.rotate(Rotation.values()[turn()]), 18);
            if (cell.data != null && server.getBlockEntity(target) != null) {
                CompoundTag nbt = cell.data.copy(); nbt.putInt("x", target.getX()); nbt.putInt("y", target.getY()); nbt.putInt("z", target.getZ());
                server.getBlockEntity(target).load(nbt); server.getBlockEntity(target).setChanged();
            }
        }
        for (Cell cell : cells) server.updateNeighborsAt(base.offset(rotated(cell.offset)), cell.state.getBlock());
        ejectPassengers(); discard(); return true;
    }
    public void shatter(ServerPlayer actor) {
        if (!Settings.TERRAIN.get()) return;
        // Inventory-bearing bodies must be placed first; never synthesize duplicate contents.
        if (cells.stream().anyMatch(c -> c.data != null)) return;
        for (Cell cell : cells) Block.dropResources(cell.state, actor.serverLevel(), blockPosition(), null, actor, net.minecraft.world.item.ItemStack.EMPTY);
        discard();
    }
    @Override public void tick() {
        super.tick(); if (level().isClientSide) return;
        boolean held = isHeld(); setNoGravity(held);
        if (!held) setDeltaMovement(getDeltaMovement().add(0, -.04, 0).scale(.98));
        move(MoverType.SELF, getDeltaMovement());
        if (!held && (onGround() || getDeltaMovement().lengthSqr() < .0005)) stillTicks++; else stillTicks = 0;
        if (stillTicks > 20 && tickCount % 20 == 0) {
            var actor = owner == null ? null : ((ServerLevel)level()).getServer().getPlayerList().getPlayer(owner);
            place(BlockPos.containing(getX() - width() / 2., getY(), getZ() - depth() / 2.), actor);
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("extent", NbtUtils.writeBlockPos(extent())); tag.putInt("turn", turn()); if (owner != null) tag.putUUID("owner", owner);
        ListTag list = new ListTag();
        for (Cell cell : cells) { var row = new CompoundTag(); row.put("offset", NbtUtils.writeBlockPos(cell.offset)); row.put("state", NbtUtils.writeBlockState(cell.state)); if (cell.data != null) row.put("data", cell.data.copy()); list.add(row); }
        tag.put("cells", list);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(EXTENT, NbtUtils.readBlockPos(tag.getCompound("extent"))); entityData.set(TURN, Math.floorMod(tag.getInt("turn"), 4));
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null; cells.clear();
        for (Tag row : tag.getList("cells", Tag.TAG_COMPOUND)) { var c = (CompoundTag)row; cells.add(new Cell(NbtUtils.readBlockPos(c.getCompound("offset")), NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), c.getCompound("state")), c.contains("data") ? c.getCompound("data") : null)); }
        release();
    }
    @Override public void writeSpawnData(FriendlyByteBuf b) { b.writeVarInt(cells.size()); for (Cell c : cells) { b.writeBlockPos(c.offset); b.writeVarInt(Block.getId(c.state)); } }
    @Override public void readSpawnData(FriendlyByteBuf b) {
        int count = b.readVarInt(); if (count < 0 || count > 32768) throw new IllegalArgumentException("Invalid matter snapshot");
        cells.clear(); for (int i = 0; i < count; i++) cells.add(new Cell(b.readBlockPos(), Block.stateById(b.readVarInt()), null));
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
