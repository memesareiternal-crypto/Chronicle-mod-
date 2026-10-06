package dev.chronicle.network;

import dev.chronicle.Chronicle;
import dev.chronicle.client.Presentation;
import dev.chronicle.power.Concentration;
import dev.chronicle.power.Intent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

public final class Wire {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Chronicle.ID, "intent"), () -> "remake-1", "remake-1"::equals, "remake-1"::equals);
    public record Input(int buttons, int wheel) {
        static void write(Input m, FriendlyByteBuf b) { b.writeByte(m.buttons & 31); b.writeByte(Integer.compare(m.wheel, 0)); }
        static Input read(FriendlyByteBuf b) { return new Input(b.readUnsignedByte(), b.readByte()); }
        static void handle(Input m, Supplier<NetworkEvent.Context> c) { var ctx = c.get(); ctx.enqueueWork(() -> { if (ctx.getSender() != null) Concentration.accept(ctx.getSender(), new Intent(m.buttons, m.wheel)); }); ctx.setPacketHandled(true); }
    }
    public record View(int entity, boolean acquired, boolean active, int color, int domain, BlockPos a, BlockPos b) {
        static void write(View m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeBoolean(m.acquired); b.writeBoolean(m.active); b.writeInt(m.color); b.writeVarInt(m.domain); b.writeNullable(m.a, FriendlyByteBuf::writeBlockPos); b.writeNullable(m.b, FriendlyByteBuf::writeBlockPos); }
        static View read(FriendlyByteBuf b) { return new View(b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readInt(), b.readVarInt(), b.readNullable(FriendlyByteBuf::readBlockPos), b.readNullable(FriendlyByteBuf::readBlockPos)); }
        static void handle(View m, Supplier<NetworkEvent.Context> c) { var ctx = c.get(); ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Presentation.receive(m))); ctx.setPacketHandled(true); }
    }
    public static void register() {
        CHANNEL.registerMessage(0, Input.class, Input::write, Input::read, Input::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, View.class, View::write, View::read, View::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    private Wire() {}
}
