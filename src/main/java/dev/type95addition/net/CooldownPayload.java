package dev.type95addition.net;

import dev.type95addition.Type95Addition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the Lightning Formula recharges for {@code ticks} (of {@code total}). */
public record CooldownPayload(int ticks, int total) implements CustomPacketPayload {
    public static final Type<CooldownPayload> TYPE = new Type<>(Type95Addition.id("cooldown"));
    public static final StreamCodec<FriendlyByteBuf, CooldownPayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> {
            buf.writeVarInt(payload.ticks);
            buf.writeVarInt(payload.total);
        },
        buf -> new CooldownPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<CooldownPayload> type() {
        return TYPE;
    }
}
