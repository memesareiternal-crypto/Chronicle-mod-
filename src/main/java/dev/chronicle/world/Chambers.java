package dev.chronicle.world;

import dev.chronicle.Chronicle;
import dev.chronicle.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

public final class Chambers {
    private record Pending(ServerLevel level, ChunkPos chunk) {}
    private static final Queue<Pending> QUEUE = new ArrayDeque<>();
    public static void enqueue(ServerLevel level, ChunkPos pos) { if (level.dimension() == Level.OVERWORLD) QUEUE.add(new Pending(level, pos)); }
    public static void tick() {
        for (int i = 0; i < 2 && !QUEUE.isEmpty(); i++) {
            var next = QUEUE.remove(); ServerLevel level = next.level;
            if (!level.hasChunk(next.chunk.x, next.chunk.z)) continue;
            Random random = new Random(level.getSeed() ^ next.chunk.toLong());
            if (random.nextInt(Settings.CRYSTAL_RARITY.get()) != 0) continue;
            int low = Math.max(level.getMinBuildHeight()+6, Settings.CRYSTAL_MIN_Y.get());
            int high = Math.min(level.getMaxBuildHeight()-7, Settings.CRYSTAL_MAX_Y.get());
            if (high < low) continue;
            for (int attempt = 0; attempt < 30; attempt++) {
                BlockPos center = new BlockPos(next.chunk.getMinBlockX()+8, low + random.nextInt(high-low+1), next.chunk.getMinBlockZ()+8);
                if (!level.getBlockState(center).is(BlockTags.BASE_STONE_OVERWORLD)) continue;
                if (form(level, center)) break;
            }
        }
    }
    public static boolean form(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5,-4,-5), center.offset(5,4,5))) {
            if (!WorldAccess.loaded(level, pos) || level.getBlockEntity(pos) != null || level.getBlockState(pos).getDestroySpeed(level,pos) < 0) return false;
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5,-4,-5), center.offset(5,4,5))) {
            double x=pos.getX()-center.getX(), y=(pos.getY()-center.getY())*1.2, z=pos.getZ()-center.getZ();
            double radius = Math.sqrt(x*x+y*y+z*z); if (radius > 5.1) continue;
            level.setBlock(pos, (radius > 4.25 ? Blocks.SMOOTH_BASALT : radius > 3.5 ? Blocks.CALCITE : Blocks.AIR).defaultBlockState(), 2);
        }
        var crystal = Chronicle.CRYSTAL.get().create(level); if (crystal == null) return false;
        crystal.setPos(center.getX()+.5, center.getY()-2, center.getZ()+.5); return level.addFreshEntity(crystal);
    }
    public static void clear() { QUEUE.clear(); }
    private Chambers() {}
}
