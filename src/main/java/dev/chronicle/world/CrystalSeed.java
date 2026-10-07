package dev.chronicle.world;

import dev.chronicle.Chronicle;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class CrystalSeed extends Item {
    public CrystalSeed(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        var p = context.getPlayer(); var pos = context.getClickedPos().relative(context.getClickedFace());
        if (p == null || !p.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()) || !context.getLevel().mayInteract(p, pos)) return InteractionResult.FAIL;
        var crystal = Chronicle.CRYSTAL.get().create(context.getLevel()); if (crystal == null) return InteractionResult.FAIL;
        crystal.setPos(pos.getX()+.5, pos.getY(), pos.getZ()+.5);
        if (!context.getLevel().noCollision(crystal) || !context.getLevel().addFreshEntity(crystal)) return InteractionResult.FAIL;
        if (!p.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
