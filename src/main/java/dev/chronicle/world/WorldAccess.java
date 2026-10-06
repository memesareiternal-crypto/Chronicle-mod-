package dev.chronicle.world;

import dev.chronicle.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

public final class WorldAccess {
    public static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos);
    }
    public static boolean edit(ServerPlayer p, BlockPos pos) {
        return Settings.TERRAIN.get() && p.getAbilities().mayBuild && loaded(p.serverLevel(), pos) && p.level().mayInteract(p, pos)
            && !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(p.level(), pos, p.level().getBlockState(pos), p));
    }
    public static void harvest(ServerPlayer p, BlockPos pos, BlockState state) {
        Block.dropResources(state, p.serverLevel(), pos, p.level().getBlockEntity(pos), p, ItemStack.EMPTY);
        p.level().removeBlockEntity(pos);
        p.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
    private WorldAccess() {}
}
