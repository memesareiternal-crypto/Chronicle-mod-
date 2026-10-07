package dev.chronicle.power;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.lang.reflect.Field;

/** Keeps server-authorized forces from triggering vanilla's unrelated floating timeout. */
public final class FlightGuard {
    private static final Field FLOATING = ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class, "f_9736_");
    private static final Field FLOATING_TICKS = ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class, "f_9737_");

    /** Call only while an active server-owned flight or grip is moving this player. */
    public static void allowControlledFlight(ServerPlayer player) {
        if (player.connection == null) return;
        try {
            FLOATING.setBoolean(player.connection, false);
            FLOATING_TICKS.setInt(player.connection, 0);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to acknowledge server-controlled psychokinetic movement", exception);
        }
    }

    private FlightGuard() {}
}
