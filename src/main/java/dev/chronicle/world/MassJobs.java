package dev.chronicle.world;

import dev.chronicle.Chronicle;
import dev.chronicle.Settings;
import dev.chronicle.entity.MatterBody;
import dev.chronicle.network.Wire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Consumer;

/** Dimension-wide work queue. A cell has exactly one authoritative owner during every transfer. */
public final class MassJobs {
    private interface Job { boolean step(); void finish(); }
    private static final Map<ServerLevel,ArrayDeque<Job>> JOBS=new HashMap<>();
    private static final Map<ServerLevel,Set<BlockPos>> RESERVED=new HashMap<>();
    private static Set<BlockPos> reserved(ServerLevel level) { return RESERVED.computeIfAbsent(level,k->new HashSet<>()); }
    public static boolean locked(ServerLevel level,BlockPos pos){var points=RESERVED.get(level);return points!=null&&points.contains(pos);}
    public static void capture(ServerPlayer actor,BlockPos start,int radius,int limit,Consumer<MatterBody> completion) {
        enqueue(actor.serverLevel(),new Capture(actor.serverLevel(),actor,start,radius,limit,completion));
    }
    private static void enqueue(ServerLevel level,Job job) { JOBS.computeIfAbsent(level,k->new ArrayDeque<>()).add(job); }
    public static void shatter(MatterBody body,ServerPlayer actor){if(body.transferring())return;body.transferring(true);enqueue(actor.serverLevel(),new Job(){final ArrayDeque<MatterBody.Cell> cells=new ArrayDeque<>(body.cells());public boolean step(){if(!actor.isAlive()||actor.level()!=body.level()||!body.isAlive())return true;var cell=cells.poll();if(cell==null)return true;net.minecraft.world.level.block.Block.dropResources(cell.state(),actor.serverLevel(),body.blockPosition(),null,actor,net.minecraft.world.item.ItemStack.EMPTY);body.removeCell(cell);return cells.isEmpty();}public void finish(){body.transferring(false);if(body.mass()==0)body.discard();else{body.release();Wire.snapshot(body);}}});}
    public static boolean place(MatterBody body,BlockPos base,ServerPlayer actor) {
        if(body.transferring() || !(body.level() instanceof ServerLevel level)) return false;
        body.transferring(true); enqueue(level,new Place(level,body,base,actor)); return true;
    }
    public static void tick(ServerLevel level) { tick(level,Settings.WORLD_EDIT_BUDGET.get()); }
    public static int tick(ServerLevel level,int budget) {
        var queue=JOBS.get(level);if(queue==null)return budget;
        while(budget>0 && !queue.isEmpty()) { budget--;Job job=queue.remove();if(job.step())job.finish();else queue.add(job); }
        if(queue.isEmpty())JOBS.remove(level);
        return budget;
    }
    public static int pending(ServerLevel level) { var queue=JOBS.get(level);return queue==null?0:queue.size(); }
    public static void clear() { for(var queue:JOBS.values())for(Job job:queue)job.finish();JOBS.clear();RESERVED.clear(); }

    private static final class Capture implements Job {
        final ServerLevel level;final ServerPlayer actor;final BlockPos start;final int radius,limit;final Consumer<MatterBody> callback;
        final ArrayDeque<BlockPos> frontier=new ArrayDeque<>();final Set<BlockPos> seen=new HashSet<>();final LinkedHashMap<BlockPos,BlockState> selected=new LinkedHashMap<>();
        final List<BlockPos> locks=new ArrayList<>(), changed=new ArrayList<>();
        Iterator<Map.Entry<BlockPos,BlockState>> iterator;MatterBody body;BlockPos low,high;int stage,notified;boolean failed,tree;
        Capture(ServerLevel level,ServerPlayer actor,BlockPos start,int radius,int limit,Consumer<MatterBody> callback) {
            this.level=level;this.actor=actor;this.start=start.immutable();this.radius=radius;this.limit=Math.min(32768,limit);this.callback=callback;frontier.add(this.start);
            var seed=level.getBlockState(start);tree=seed.is(BlockTags.LOGS)||seed.is(BlockTags.LEAVES);
        }
        public boolean step() {
            if(failed)return true;
            if(!actor.isAlive() || actor.level()!=level) { failed=true;return true; }
            if(stage==0) {
                if(frontier.isEmpty() || selected.size()>=limit) {
                    if(selected.isEmpty()){failed=true;return true;}
                    iterator=selected.entrySet().iterator();stage=1;return false;
                }
                BlockPos pos=frontier.remove();if(!seen.add(pos)||!WorldAccess.loaded(level,pos)||start.distSqr(pos)>radius*(double)radius)return false;
                var state=level.getBlockState(pos);if(state.isAir()||state.getDestroySpeed(level,pos)<0 || (tree&&!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES)))return false;
                if(!state.getFluidState().isEmpty()&&!state.getFluidState().isSource())return false;
                BlockPos nextLow=low==null?pos:new BlockPos(Math.min(low.getX(),pos.getX()),Math.min(low.getY(),pos.getY()),Math.min(low.getZ(),pos.getZ()));
                BlockPos nextHigh=high==null?pos:new BlockPos(Math.max(high.getX(),pos.getX()),Math.max(high.getY(),pos.getY()),Math.max(high.getZ(),pos.getZ()));
                long volume=(long)(nextHigh.getX()-nextLow.getX()+1)*(nextHigh.getY()-nextLow.getY()+1)*(nextHigh.getZ()-nextLow.getZ()+1);
                if(volume>Settings.STRUCTURE_LIMIT.get())return false;
                low=nextLow;high=nextHigh;
                selected.put(pos,state);if(radius>0)for(Direction d:Direction.values())frontier.add(pos.relative(d));return false;
            }
            if(stage==1) {
                if(iterator.hasNext()) {var row=iterator.next();var pos=row.getKey();if(!WorldAccess.loaded(level,pos)||reserved(level).contains(pos)||level.getBlockState(pos)!=row.getValue()||!WorldAccess.edit(actor,pos)||(level.getBlockEntity(pos)!=null&&!Settings.INVENTORIES.get())){failed=true;return true;}reserved(level).add(pos);locks.add(pos);return false;}
                body=Chronicle.MATTER.get().create(level);if(body==null){failed=true;return true;}body.initialize(low,high,actor.getUUID());body.transferring(true);if(!level.addFreshEntity(body)){body=null;failed=true;return true;}iterator=selected.entrySet().iterator();stage=2;return false;
            }
            if(stage==2) {
                if(body==null||!body.isAlive()){failed=true;return true;}
                if(iterator.hasNext()) {
                    var row=iterator.next();BlockPos pos=row.getKey();if(!WorldAccess.loaded(level,pos)||level.getBlockState(pos)!=row.getValue()){failed=true;return true;}
                    var blockEntity=level.getBlockEntity(pos);var cell=new MatterBody.Cell(pos.subtract(low),row.getValue(),blockEntity==null?null:blockEntity.saveWithFullMetadata());
                    level.removeBlockEntity(pos);
                    if(!level.setBlock(pos,Blocks.AIR.defaultBlockState(),18)||!level.getBlockState(pos).isAir()){if(blockEntity!=null){level.setBlockEntity(blockEntity);if(cell.data()!=null)blockEntity.load(cell.data());}failed=true;return true;}
                    body.addCell(cell);changed.add(pos);return false;
                }stage=3;
            }
            if(notified<changed.size()){BlockPos pos=changed.get(notified++);level.updateNeighborsAt(pos,selected.get(pos).getBlock());return false;}
            return true;
        }
        public void finish() {
            reserved(level).removeAll(locks);
            if(body!=null) {body.transferring(false);if(body.mass()==0){body.discard();body=null;}else if(body.isAlive()){body.held();Wire.snapshot(body);}else body=null;}
            callback.accept(body);
        }
    }
    private static final class Place implements Job {
        final ServerLevel level;final MatterBody body;final BlockPos base;final ServerPlayer actor;final List<MatterBody.Cell> cells;final List<BlockPos> locks=new ArrayList<>();
        int checked,placed,notified;boolean failed;
        Place(ServerLevel level,MatterBody body,BlockPos base,ServerPlayer actor){this.level=level;this.body=body;this.base=base.immutable();this.actor=actor;cells=new ArrayList<>(body.cells());}
        public boolean step() {
            if(failed||!body.isAlive()||(actor!=null&&(!actor.isAlive()||actor.level()!=level)))return true;
            if(checked<cells.size()) {var cell=cells.get(checked++);BlockPos pos=base.offset(body.rotated(cell.offset()));if(reserved(level).contains(pos)||!WorldAccess.loaded(level,pos)||!level.getBlockState(pos).canBeReplaced()||(actor!=null&&!WorldAccess.edit(actor,pos))){failed=true;return true;}reserved(level).add(pos);locks.add(pos);return false;}
            if(placed<cells.size()) {var cell=cells.get(placed);BlockPos pos=base.offset(body.rotated(cell.offset()));if(!WorldAccess.loaded(level,pos)||!level.getBlockState(pos).canBeReplaced()||!body.putCell(level,base,cell)){failed=true;return true;}body.removeCell(cell);placed++;return false;}
            if(notified<placed) {var cell=cells.get(notified++);level.updateNeighborsAt(base.offset(body.rotated(cell.offset())),cell.state().getBlock());return false;}
            return true;
        }
        public void finish() {reserved(level).removeAll(locks);body.transferring(false);if(body.mass()==0){body.ejectPassengers();body.discard();}else{body.release();Wire.snapshot(body);} }
    }
    private MassJobs() {}
}
