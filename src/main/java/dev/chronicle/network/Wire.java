package dev.chronicle.network;

import dev.chronicle.Chronicle;
import dev.chronicle.client.Presentation;
import dev.chronicle.power.Concentration;
import dev.chronicle.power.Intent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import dev.chronicle.entity.MatterBody;
import io.netty.buffer.Unpooled;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

public final class Wire {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Chronicle.ID,"intent"),()->"remake-3","remake-3"::equals,"remake-3"::equals);
    public record Input(int buttons, int wheel) {
        static void write(Input m, FriendlyByteBuf b) { b.writeVarInt(m.buttons & 2047); b.writeByte(Integer.compare(m.wheel, 0)); }
        static Input read(FriendlyByteBuf b) { return new Input(b.readVarInt(), b.readByte()); }
        static void handle(Input m, Supplier<NetworkEvent.Context> c) { var ctx = c.get(); ctx.enqueueWork(() -> { if (ctx.getSender() != null) Concentration.accept(ctx.getSender(), new Intent(m.buttons, m.wheel)); }); ctx.setPacketHandled(true); }
    }
    public record View(int entity,boolean acquired,boolean active,int color,boolean effects,Vec3 ward,double radius,boolean plane,float integrity,Vec3 normal,int shape,Vec3 impact,int impactAge,boolean flying,boolean holding,float output) {
        public View(int entity,boolean acquired,boolean active,int color,boolean effects,Vec3 ward,double radius,boolean plane,float integrity){this(entity,acquired,active,color,effects,ward,radius,plane,integrity,new Vec3(0,0,1),plane?2:0,null,100,false,false,1);}
        public View(int entity,boolean acquired,boolean active,int color,boolean effects,Vec3 ward,double radius,boolean plane,float integrity,Vec3 normal,int shape,Vec3 impact,int impactAge,boolean flying,boolean holding){this(entity,acquired,active,color,effects,ward,radius,plane,integrity,normal,shape,impact,impactAge,flying,holding,1);}
        static void write(View m,FriendlyByteBuf b){b.writeVarInt(m.entity);b.writeBoolean(m.acquired);b.writeBoolean(m.active);b.writeInt(m.color);b.writeBoolean(m.effects);writeVec(b,m.ward);b.writeDouble(m.radius);b.writeBoolean(m.plane);b.writeFloat(m.integrity);writeVec(b,m.normal);b.writeByte(m.shape);writeVec(b,m.impact);b.writeVarInt(m.impactAge);b.writeBoolean(m.flying);b.writeBoolean(m.holding);b.writeFloat(m.output);}
        static View read(FriendlyByteBuf b){return new View(b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readInt(),b.readBoolean(),readVec(b),b.readDouble(),b.readBoolean(),b.readFloat(),readVec(b),b.readByte(),readVec(b),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readFloat());}
        static void handle(View m, Supplier<NetworkEvent.Context> c) { var ctx = c.get(); ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Presentation.receive(m))); ctx.setPacketHandled(true); }
    }
    public record Snapshot(int entity,byte[] cells) {
        static void write(Snapshot m,FriendlyByteBuf b){b.writeVarInt(m.entity);b.writeByteArray(m.cells);}
        static Snapshot read(FriendlyByteBuf b){return new Snapshot(b.readVarInt(),b.readByteArray(1048576));}
        static void handle(Snapshot m,Supplier<NetworkEvent.Context> c){var ctx=c.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->Presentation.receive(m)));ctx.setPacketHandled(true);}
    }
    public static void snapshot(MatterBody body) {FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());try{body.writeSpawnData(b);byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(()->body),new Snapshot(body.getId(),bytes));}finally{b.release();}}
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
        CHANNEL.registerMessage(3, Snapshot.class, Snapshot::write, Snapshot::read, Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    private Wire() {}
}
