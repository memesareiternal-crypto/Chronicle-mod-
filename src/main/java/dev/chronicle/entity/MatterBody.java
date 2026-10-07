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
    private boolean transferring;
    private double physicalMass, hardness;
    private int revision;
    private Vec3 centerOfMass=Vec3.ZERO;
    public MatterBody(EntityType<? extends MatterBody> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData() { entityData.define(EXTENT,new BlockPos(1,1,1));entityData.define(TURN,0); }
    public record Cell(BlockPos offset, BlockState state, CompoundTag data) {}
    public List<Cell> cells() { return Collections.unmodifiableList(cells); }
    public int mass() { return cells.size(); }
    public double physicalMass() { if(physicalMass<=0) measure(); return physicalMass; }
    public double hardness() { if(physicalMass<=0) measure(); return hardness; }
    public Vec3 centerOfMass() {if(physicalMass<=0)measure();return centerOfMass;}
    public int revision() { return revision; }
    public void owner(UUID value){owner=value;}
    public boolean transferring() { return transferring; }
    public void transferring(boolean value) { transferring=value; setNoGravity(value); }
    public void addCell(Cell cell) { cells.add(cell); physicalMass=0; revision++; }
    public void removeCell(Cell cell) { cells.remove(cell); physicalMass=0; revision++; }
    public void initialize(BlockPos low, BlockPos high, UUID actor) { owner=actor; entityData.set(EXTENT,high.subtract(low).offset(1,1,1)); setPos(low.getX()+width()/2.,low.getY(),low.getZ()+depth()/2.); }
    private void measure() {
        physicalMass=0;hardness=0;centerOfMass=Vec3.ZERO;
        for(Cell cell:cells) { double h=Math.max(0,cell.state.getDestroySpeed(level(),blockPosition())); double weight=(cell.state.getFluidState().isEmpty()?1+Math.min(8,h):.8)*Settings.MASS_DENSITY.get();physicalMass+=weight;hardness+=h;centerOfMass=centerOfMass.add(Vec3.atCenterOf(cell.offset).scale(weight)); }
        if(physicalMass>0)centerOfMass=centerOfMass.scale(1/physicalMass);
        hardness=cells.isEmpty()?0:hardness/cells.size();
    }
    public BlockPos extent() { return entityData.get(EXTENT); }
    public int turn() { return entityData.get(TURN); }
    public void rotate(int delta) { entityData.set(TURN, Math.floorMod(turn() + delta, 4)); }
    public int width() { return turn() % 2 == 0 ? extent().getX() : extent().getZ(); }
    public int depth() { return turn() % 2 == 0 ? extent().getZ() : extent().getX(); }
    public void held() { lastHeld = level().getGameTime(); setNoGravity(true); stillTicks = 0; }
    public void release() { lastHeld = Long.MIN_VALUE; setNoGravity(false); }
    public boolean isHeld() { return lastHeld != Long.MIN_VALUE && level().getGameTime() - lastHeld <= 5; }
    @Override public EntityDimensions getDimensions(Pose pose) {return EntityDimensions.scalable(Math.max(extent().getX(),extent().getZ()),extent().getY());}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {super.onSyncedDataUpdated(key);if(EXTENT.equals(key))refreshDimensions();}
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
        if(transferring) return false;
        if(cells.size()>Settings.WORLD_EDIT_BUDGET.get())return dev.chronicle.world.MassJobs.place(this,base,actor);
        for (Cell cell : cells) {
            BlockPos target = base.offset(rotated(cell.offset));
            if (!WorldAccess.loaded(server, target) || !server.getBlockState(target).canBeReplaced()
                || (actor != null && !WorldAccess.edit(actor, target))) return false;
        }
        Map<BlockPos,BlockState> previous=new LinkedHashMap<>();
        for(Cell cell:cells){BlockPos pos=base.offset(rotated(cell.offset));previous.put(pos,server.getBlockState(pos));if(!putCell(server,base,cell)){for(var row:previous.entrySet()){server.removeBlockEntity(row.getKey());server.setBlock(row.getKey(),row.getValue(),18);}return false;}}
        for (Cell cell : cells) server.updateNeighborsAt(base.offset(rotated(cell.offset)), cell.state.getBlock());
        ejectPassengers(); discard(); return true;
    }
    public boolean putCell(ServerLevel server,BlockPos base,Cell cell) {
        BlockPos target=base.offset(rotated(cell.offset));BlockState state=cell.state.rotate(Rotation.values()[turn()]);BlockState previous=server.getBlockState(target);
        if(!server.setBlock(target,state,18)||server.getBlockState(target)!=state)return false;
        if(cell.data!=null) {var be=server.getBlockEntity(target);if(be==null){server.setBlock(target,previous,18);return false;}try{CompoundTag nbt=cell.data.copy();nbt.putInt("x",target.getX());nbt.putInt("y",target.getY());nbt.putInt("z",target.getZ());be.load(nbt);be.setChanged();}catch(RuntimeException failure){server.removeBlockEntity(target);server.setBlock(target,previous,18);return false;} }
        return true;
    }
    public void shatter(ServerPlayer actor) {
        if (!Settings.TERRAIN.get() || transferring) return;
        // Inventory-bearing bodies must be placed first; never synthesize duplicate contents.
        if (cells.stream().anyMatch(c -> c.data != null)) return;
        dev.chronicle.world.MassJobs.shatter(this,actor);
    }
    @Override public void tick() {
        super.tick(); if (level().isClientSide) return;
        if(transferring) { setDeltaMovement(Vec3.ZERO); return; }
        boolean held = isHeld(); setNoGravity(held);
        if (!held) setDeltaMovement(getDeltaMovement().add(0, -Settings.MATTER_GRAVITY.get(), 0).scale(Settings.MATTER_DRAG.get()));
        move(MoverType.SELF, getDeltaMovement());
        if (!held && (onGround() || getDeltaMovement().lengthSqr() < .0005)) stillTicks++; else stillTicks = 0;
        if (stillTicks > 20 && tickCount % 20 == 0) {
            var actor = owner == null ? null : ((ServerLevel)level()).getServer().getPlayerList().getPlayer(owner);
            if(owner!=null && actor==null)return;
            place(BlockPos.containing(getX() - width() / 2., getY(), getZ() - depth() / 2.), actor);
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("extent",NbtUtils.writeBlockPos(extent()));tag.putInt("turn",turn());if(owner!=null)tag.putUUID("owner",owner);
        ListTag list = new ListTag();
        ListTag palette=new ListTag();Map<BlockState,Integer> ids=new HashMap<>();
        for(Cell cell:cells){if(!ids.containsKey(cell.state)){ids.put(cell.state,palette.size());palette.add(NbtUtils.writeBlockState(cell.state));}var row=new CompoundTag();row.put("offset",NbtUtils.writeBlockPos(cell.offset));row.putInt("palette",ids.get(cell.state));if(cell.data!=null)row.put("data",cell.data.copy());list.add(row);}
        tag.put("cells",list);tag.put("palette",palette);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(EXTENT, NbtUtils.readBlockPos(tag.getCompound("extent"))); entityData.set(TURN, Math.floorMod(tag.getInt("turn"), 4));
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null; cells.clear(); physicalMass=0; revision++;
        List<BlockState> palette=new ArrayList<>();for(Tag row:tag.getList("palette",Tag.TAG_COMPOUND))palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),(CompoundTag)row));
        for(Tag row:tag.getList("cells",Tag.TAG_COMPOUND)){var c=(CompoundTag)row;BlockState state=c.contains("state")?NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),c.getCompound("state")):palette.get(c.getInt("palette"));cells.add(new Cell(NbtUtils.readBlockPos(c.getCompound("offset")),state,c.contains("data")?c.getCompound("data"):null));}
        release();
    }
    @Override public void writeSpawnData(FriendlyByteBuf b) {
        List<BlockState> palette=new ArrayList<>();Map<BlockState,Integer> ids=new HashMap<>();for(Cell c:cells) if(!ids.containsKey(c.state)){ids.put(c.state,palette.size());palette.add(c.state);}
        b.writeVarInt(palette.size());for(BlockState state:palette)b.writeVarInt(Block.getId(state));b.writeVarInt(cells.size());for(Cell c:cells){b.writeBlockPos(c.offset);b.writeVarInt(ids.get(c.state));}
    }
    @Override public void readSpawnData(FriendlyByteBuf b) {
        int size=b.readVarInt();if(size<0||size>32768)throw new IllegalArgumentException("Invalid palette");List<BlockState> palette=new ArrayList<>();for(int i=0;i<size;i++)palette.add(Block.stateById(b.readVarInt()));
        int count = b.readVarInt(); if (count < 0 || count > 32768) throw new IllegalArgumentException("Invalid matter snapshot");
        cells.clear();for(int i=0;i<count;i++){BlockPos pos=b.readBlockPos();int id=b.readVarInt();if(id<0||id>=size)throw new IllegalArgumentException("Invalid palette index");cells.add(new Cell(pos,palette.get(id),null));}revision++;physicalMass=0;
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
