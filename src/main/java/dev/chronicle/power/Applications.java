package dev.chronicle.power;

import dev.chronicle.Settings;
import dev.chronicle.entity.MatterBody;
import dev.chronicle.world.WorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.sounds.SoundSource;
import java.util.*;

public final class Applications {
    public static BlockHitResult aim(ServerPlayer p) {
        Vec3 from = p.getEyePosition();
        return p.level().clip(new ClipContext(from, from.add(p.getLookAngle().scale(Potential.reach(p))), ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, p));
    }
    public static Entity target(ServerPlayer p) {
        Vec3 from = p.getEyePosition(); BlockHitResult block = aim(p);
        Vec3 end = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : from.add(p.getLookAngle().scale(Potential.reach(p)));
        Entity best = null; double distance = from.distanceToSqr(end);
        for (Entity e : p.level().getEntities(p, new AABB(from, end).inflate(1), e -> Physics.allowed(p, e))) {
            var hit = e.getBoundingBox().inflate(.2).clip(from, end);
            if (hit.isPresent() && hit.get().distanceToSqr(from) < distance) { best = e; distance = hit.get().distanceToSqr(from); }
        }
        return best;
    }
    public static MatterBody lift(ServerPlayer p, boolean connected) {
        BlockHitResult hit = aim(p); if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos start = hit.getBlockPos(); BlockState seed = p.level().getBlockState(start);
        if (connected && (seed.is(BlockTags.LOGS) || seed.is(BlockTags.LEAVES))) {
            Set<BlockPos> visited = new HashSet<>(); List<BlockPos> tree = new ArrayList<>(); ArrayDeque<BlockPos> queue = new ArrayDeque<>(); queue.add(start);
            int limit = Math.min(Settings.STRUCTURE_LIMIT.get(), 16 + Potential.level(p) * 8);
            while (!queue.isEmpty() && tree.size() < limit) {
                BlockPos pos = queue.remove(); if (!visited.add(pos) || start.distManhattan(pos) > 20 || !WorldAccess.loaded(p.serverLevel(), pos)) continue;
                BlockState state = p.level().getBlockState(pos); if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) continue;
                tree.add(pos); for (Direction d : Direction.values()) queue.add(pos.relative(d));
            }
            return MatterBody.capture(p.serverLevel(), tree, p);
        }
        return MatterBody.capture(p.serverLevel(), List.of(start), p);
    }
    public static void pressure(ServerPlayer p, boolean pull, double charge, boolean spherical) {
        double reach = spherical ? 4 + Potential.level(p) * .12 + charge : Potential.reach(p);
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(reach), e -> Physics.allowed(p, e))) {
            Vec3 delta = e.getBoundingBox().getCenter().subtract(eye); double distance = delta.length();
            if (distance > reach || (!spherical && delta.normalize().dot(look) < .82)) continue;
            double strength = Potential.force(p) * charge * Math.max(.1, 1 - distance / reach);
            Vec3 force = (spherical || pull ? delta.normalize() : look).scale(strength * (pull ? -1 : 1));
            Physics.velocity(e, e.getDeltaMovement().add(force));
            if (spherical || charge > 1) e.hurt(p.damageSources().playerAttack(p), (float)(strength * 5));
        }
        Potential.spend(p, (spherical ? 35 : 2) * charge);
    }
    public static boolean interact(ServerPlayer p) {
        var hit = aim(p); if (hit.getType() != HitResult.Type.BLOCK || !WorldAccess.edit(p, hit.getBlockPos())) return false;
        return p.level().getBlockState(hit.getBlockPos()).use(p.level(), p, InteractionHand.MAIN_HAND, hit).consumesAction();
    }
    public static void build(ServerPlayer p, boolean mirror, boolean erase, BlockPos plane) {
        var hit = aim(p); if (hit.getType() != HitResult.Type.BLOCK) return;
        if (erase) {
            var state = p.level().getBlockState(hit.getBlockPos());
            if (state.getDestroySpeed(p.level(), hit.getBlockPos()) >= 0 && WorldAccess.edit(p, hit.getBlockPos())) { WorldAccess.harvest(p, hit.getBlockPos(), state); Potential.spend(p, 1); }
            return;
        }
        ItemStack stack = p.getMainHandItem(); if (!(stack.getItem() instanceof BlockItem item)) return;
        BlockPos placed = hit.getBlockPos().relative(hit.getDirection());
        if (!WorldAccess.edit(p, placed)) return;
        if (!item.place(new BlockPlaceContext(new UseOnContext(p, InteractionHand.MAIN_HAND, hit))).consumesAction()) return;
        if (mirror && !stack.isEmpty()) {
            int x = plane == null ? p.blockPosition().getX() : plane.getX();
            BlockPos target = new BlockPos(x * 2 - placed.getX(), placed.getY(), placed.getZ());
            if (!target.equals(placed) && WorldAccess.edit(p, target)) {
                var mirrored = new BlockHitResult(Vec3.atCenterOf(target.below()), Direction.UP, target.below(), false);
                item.place(new BlockPlaceContext(new UseOnContext(p, InteractionHand.MAIN_HAND, mirrored)));
            }
        }
        Potential.spend(p, .8);
    }
    public static void tend(ServerPlayer p, boolean wide) {
        var hit = aim(p); if (hit.getType() != HitResult.Type.BLOCK) return;
        int radius = wide ? Math.min(8, 2 + Potential.level(p) / 12) : 1;
        for (BlockPos pos : BlockPos.betweenClosed(hit.getBlockPos().offset(-radius, -1, -radius), hit.getBlockPos().offset(radius, 1, radius))) {
            BlockState state = p.level().getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state) || !WorldAccess.edit(p, pos)) continue;
            var loot = Block.getDrops(state, p.serverLevel(), pos, null, p, ItemStack.EMPTY);
            Item seed = crop.getCloneItemStack(p.level(), pos, state).getItem(); boolean replant = false;
            for (ItemStack stack : loot) if (stack.is(seed) && !stack.isEmpty()) { stack.shrink(1); replant = true; break; }
            p.level().setBlock(pos, replant ? crop.getStateForAge(0) : Blocks.AIR.defaultBlockState(), 3);
            for (ItemStack stack : loot) if (!stack.isEmpty()) drop(p, pos, stack);
            Potential.spend(p, .5);
        }
        for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, new AABB(hit.getBlockPos()).inflate(radius + 2))) Physics.velocity(item, p.getEyePosition().subtract(item.position()).normalize().scale(.5));
    }
    public static MatterBody peel(ServerPlayer p, boolean twist) {
        var hit = aim(p); if (hit.getType() != HitResult.Type.BLOCK) return null;
        int r = Math.min(8, 1 + Potential.level(p) / 10); List<BlockPos> points = new ArrayList<>(); BlockPos center = hit.getBlockPos();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            if (x*x + z*z > r*r || points.size() >= Settings.TERRAIN_BUDGET.get()) continue;
            for (int y = 2; y >= -2; y--) {
                BlockPos pos = center.offset(x, y, z); if (!WorldAccess.loaded(p.serverLevel(), pos)) break;
                if (!p.level().getBlockState(pos).isAir() && p.level().getBlockState(pos.above()).canBeReplaced()) { points.add(pos); break; }
            }
        }
        MatterBody body = MatterBody.capture(p.serverLevel(), points, p); if (body != null && twist) body.rotate(1); return body;
    }
    public static void burstTerrain(ServerPlayer p, int charge) {
        if (!Settings.TERRAIN.get()) return;
        int radius = Math.min(16, 3 + Potential.level(p)/15 + charge/25);
        List<BlockPos> positions = BlockPos.betweenClosedStream(p.blockPosition().offset(-radius,-radius,-radius),p.blockPosition().offset(radius,radius,radius))
            .map(BlockPos::immutable).filter(pos -> pos.distSqr(p.blockPosition()) <= radius*radius).sorted(Comparator.comparingDouble(pos -> pos.distSqr(p.blockPosition()))).toList();
        int changed=0;
        for (BlockPos pos:positions) {
            if (changed >= Settings.TERRAIN_BUDGET.get()) break;
            if (!WorldAccess.loaded(p.serverLevel(),pos)) continue;
            BlockState state=p.level().getBlockState(pos);float hardness=state.getDestroySpeed(p.level(),pos);
            if (state.isAir() || hardness < 0 || hardness > Potential.force(p)*3 || !WorldAccess.edit(p,pos)) continue;
            WorldAccess.harvest(p,pos,state);changed++;
        }
        Potential.spend(p,changed*.1);
    }
    public static void edge(ServerPlayer p, Intent.Edge shape, Vec3 anchor, Vec3 previous) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle(); double reach = Potential.reach(p);
        Vec3 right = look.cross(new Vec3(0, 1, 0)); if (right.lengthSqr() < .01) right = new Vec3(1, 0, 0); right = right.normalize();
        double width = 1 + Potential.level(p) * .06;
        List<Segment> segments = new ArrayList<>();
        switch (shape) {
            case LINE -> segments.add(new Segment(anchor == null ? eye : anchor, aim(p).getLocation()));
            case TRACE -> segments.add(new Segment(previous == null ? aim(p).getLocation() : previous, aim(p).getLocation()));
            case PLANE -> { for (int i = -Mth.ceil(width); i <= width; i++) segments.add(new Segment(eye.add(right.scale(i)), eye.add(look.scale(reach)).add(right.scale(i)))); }
            case WHIRL -> { for (int i = 0; i < 24; i++) { double a = Math.PI * 2 * i / 24, b = Math.PI * 2 * (i+1) / 24; segments.add(new Segment(eye.add(Math.cos(a)*width*2, -.5, Math.sin(a)*width*2), eye.add(Math.cos(b)*width*2, -.5, Math.sin(b)*width*2))); } }
            case VOLLEY -> { for (int i = -2; i <= 2; i++) segments.add(new Segment(eye, eye.add(look.scale(reach)).add(right.scale(i * width)))); }
            case SCISSOR -> { Vec3 end = aim(p).getLocation(); segments.add(new Segment(eye.add(right.scale(width)), end)); segments.add(new Segment(eye.add(right.scale(-width)), end)); }
            case SWEEP -> segments.add(new Segment(eye.add(look.scale(Math.min(reach, 5 + width))).add(right.scale(-width*2)), eye.add(look.scale(Math.min(reach, 5 + width))).add(right.scale(width*2))));
            default -> segments.add(new Segment(eye, eye.add(look.scale(reach))));
        }
        Set<UUID> victims = new HashSet<>(); Set<BlockPos> visited = new HashSet<>();
        for (Segment s : segments) {
            for (Entity e : p.level().getEntities(p, new AABB(s.from, s.to).inflate(.5), e -> Physics.allowed(p, e))) {
                if (e.getBoundingBox().inflate(.4).clip(s.from, s.to).isEmpty() || !victims.add(e.getUUID())) continue;
                if (e instanceof Projectile) { e.discard(); continue; }
                if (e instanceof Sheep sheep && sheep.readyForShearing()) { sheep.shear(SoundSource.PLAYERS); continue; }
                if (shape == Intent.Edge.SCISSOR && e instanceof Mob mob) mob.dropLeash(true, true);
                e.hurt(shape == Intent.Edge.PIERCE ? p.damageSources().magic() : p.damageSources().playerAttack(p), (float)(2 + Potential.force(p) * 5));
            }
            Vec3 delta = s.to.subtract(s.from); int steps = Math.min(512, Mth.ceil(delta.length() * 3));
            for (int i = 0; i <= steps && visited.size() < Settings.TERRAIN_BUDGET.get(); i++) {
                BlockPos pos = BlockPos.containing(s.from.add(delta.scale(i / (double)Math.max(1, steps))));
                if (visited.add(pos)) cutBlock(p, pos);
            }
        }
        Potential.spend(p, 1 + segments.size() * .6 + victims.size() * .3);
    }
    private record Segment(Vec3 from, Vec3 to) {}
    private static void cutBlock(ServerPlayer p, BlockPos pos) {
        if (!WorldAccess.loaded(p.serverLevel(), pos)) return;
        var state = p.level().getBlockState(pos); if (state.isAir()) return;
        float hardness = state.getDestroySpeed(p.level(), pos);
        if (hardness < 0 || hardness > .5 + Potential.level(p) * Settings.HARDNESS.get() || !WorldAccess.edit(p, pos)) return;
        if (state.hasBlockEntity()) { WorldAccess.harvest(p, pos, state); return; }
        if (state.is(BlockTags.LOGS)) {
            var id = BuiltInRegistries.BLOCK.getKey(state.getBlock()); String name = id.getPath().replace("stripped_", "").replaceAll("_(log|wood|stem|hyphae)$", "_planks");
            Block planks = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), name));
            if (planks != Blocks.AIR && planks != state.getBlock()) { p.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3); drop(p, pos, new ItemStack(planks, 4)); return; }
        }
        if (state.is(Blocks.PUMPKIN)) { p.level().setBlock(pos, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, p.getDirection()), 3); drop(p, pos, new ItemStack(Items.PUMPKIN_SEEDS, 4)); return; }
        if (state.is(BlockTags.WOOL) || state.is(Blocks.COBWEB) || state.is(Blocks.MELON)) {
            p.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            drop(p, pos, new ItemStack(state.is(Blocks.MELON) ? Items.MELON_SLICE : Items.STRING, state.is(Blocks.MELON) ? 6 : state.is(BlockTags.WOOL) ? 4 : 1)); return;
        }
        WorldAccess.harvest(p, pos, state);
    }
    private static void drop(ServerPlayer p, BlockPos pos, ItemStack stack) {
        var item = new ItemEntity(p.level(), pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, stack);
        item.setDeltaMovement(p.getEyePosition().subtract(item.position()).normalize().scale(.3)); p.level().addFreshEntity(item);
    }
    private Applications() {}
}
