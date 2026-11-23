package dev.type95addition.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server to the players around: a homing bullet caught {@code entity}. It is slammed down for {@code fall} ticks (0
 * when it was on the ground), then pressed flat for {@code press} ticks and dies.
 */
public record CrushPayload(int entity, int fall, int press, boolean boss) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entity);
        buf.writeVarInt(this.fall);
        buf.writeVarInt(this.press);
        buf.writeBoolean(this.boss);
    }

    public static CrushPayload read(FriendlyByteBuf buf) {
        return new CrushPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }
}
