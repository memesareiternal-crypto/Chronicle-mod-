package dev.chronicle.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class ResonantCrystal extends Entity {
    public ResonantCrystal(EntityType<? extends ResonantCrystal> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean isPickable() { return true; }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
