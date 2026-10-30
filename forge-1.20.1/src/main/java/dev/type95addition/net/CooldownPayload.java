package dev.type95addition.net;

import net.minecraft.network.FriendlyByteBuf;

/** Server to client: the Lightning Formula recharges for {@code ticks} (of {@code total}). */
public record CooldownPayload(int ticks, int total) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.ticks);
        buf.writeVarInt(this.total);
    }

    public static CooldownPayload read(FriendlyByteBuf buf) {
        return new CooldownPayload(buf.readVarInt(), buf.readVarInt());
    }
}
