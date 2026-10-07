package dev.chronicle.network;

import dev.chronicle.Chronicle;
import dev.chronicle.client.Presentation;
import dev.chronicle.power.Concentration;
import dev.chronicle.power.Intent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

public final class Wire {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Chronicle.ID, "intent"), () -> "remake-1", "remake-1"::equals, "remake-1"::equals);
    public record Input(int buttons, int wheel) {
        static void write(Input m, FriendlyByteBuf b) { b.writeByte(m.buttons & 63); b.writeByte(Integer.compare(m.wheel, 0)); }
        static Input read(FriendlyByteBuf b) { return new Input(b.readUnsignedByte(), b.readByte()); }
        static void handle(Input m, Supplier<NetworkEvent.Context> c) { var ctx = c.get(); ctx.enqueueWork(() -> { if (ctx.getSender() != null) Concentration.accept(ctx.getSender(), new Intent(m.buttons, m.wheel)); }); ctx.setPacketHandled(true); }
    }
    public record View(int entity, boolean acquired, boolean active, int color, boolean effects, Vec3 ward, double radius, boolean plane, float integrity) {
        static void write(View m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeBoolean(m.acquired); b.writeBoolean(m.active); b.writeInt(m.color); b.writeBoolean(m.effects); writeVec(b, m.ward); b.writeDouble(m.radius); b.writeBoolean(m.plane); b.writeFloat(m.integrity); }
        static View read(FriendlyByteBuf b) { return new View(b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readInt(), b.readBoolean(), readVec(b), b.readDouble(), b.readBoolean(), b.readFloat()); }
        static void handle(View m, Supplier<NetworkEvent.Context> c) { var ctx = c.get(); ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Presentation.receive(m))); ctx.setPacketHandled(true); }
    }
    public record Effect(int kind, Vec3 center, double radius, int color) {
        public static final int WAVE = 0, EXPLOSION = 1;
        static void write(Effect m, FriendlyByteBuf b) { b.writeByte(m.kind); writeVec(b, m.center); b.writeDouble(m.radius); b.writeInt(m.color); }
        static Effect read(FriendlyByteBuf b) { return new Effect(b.readByte(), readVec(b), b.readDouble(), b.readInt()); }
        static void handle(Effect m, Supplier<NetworkEvent.Context> c) { var ctx = c.get(); ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Presentation.receive(m))); ctx.setPacketHandled(true); }
    }
    private static void writeVec(FriendlyByteBuf b, Vec3 v) { b.writeBoolean(v != null); if (v != null) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); } }
    private static Vec3 readVec(FriendlyByteBuf b) { return b.readBoolean() ? new Vec3(b.readDouble(), b.readDouble(), b.readDouble()) : null; }
    public static void register() {
        CHANNEL.registerMessage(0, Input.class, Input::write, Input::read, Input::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, View.class, View::write, View::read, View::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Effect.class, Effect::write, Effect::read, Effect::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    private Wire() {}
}
