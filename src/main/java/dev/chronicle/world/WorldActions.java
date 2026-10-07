package dev.chronicle.world;

import dev.chronicle.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import java.util.*;
import java.util.function.Consumer;

/** Small precision edits share a bounded dimension queue rather than scanning whole regions each tick. */
public final class WorldActions {
    private record Work(ServerPlayer actor,ArrayDeque<BlockPos> points,Consumer<BlockPos> action){}
    private static final Map<ServerLevel,ArrayDeque<Work>> QUEUES=new HashMap<>();
    public static void enqueue(ServerPlayer actor,Collection<BlockPos> points,Consumer<BlockPos> action){var queue=QUEUES.computeIfAbsent(actor.serverLevel(),k->new ArrayDeque<>());if(queue.size()<128)queue.add(new Work(actor,new ArrayDeque<>(points),action));}
    public static void tick(ServerLevel level){tick(level,Settings.WORLD_EDIT_BUDGET.get());}
    public static void tick(ServerLevel level,int budget){var queue=QUEUES.get(level);if(queue==null)return;while(budget-->0&&!queue.isEmpty()){Work work=queue.remove();if(!work.actor.isAlive()||work.actor.level()!=level)continue;BlockPos pos=work.points.poll();if(pos!=null&&WorldAccess.loaded(level,pos))work.action.accept(pos);if(!work.points.isEmpty())queue.add(work);}if(queue.isEmpty())QUEUES.remove(level);}
    public static void clear(){QUEUES.clear();}
    private WorldActions(){}
}
