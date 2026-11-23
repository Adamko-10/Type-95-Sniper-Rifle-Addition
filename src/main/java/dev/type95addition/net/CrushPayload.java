package dev.type95addition.net;

import dev.type95addition.Type95Addition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to the players around: a homing bullet caught {@code entity}. It is slammed down for {@code fall} ticks (0
 * when it was on the ground), then pressed flat for {@code press} ticks and dies.
 */
public record CrushPayload(int entity, int fall, int press, boolean boss) implements CustomPacketPayload {
    public static final Type<CrushPayload> TYPE = new Type<>(Type95Addition.id("crush"));
    public static final StreamCodec<FriendlyByteBuf, CrushPayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> {
            buf.writeVarInt(payload.entity);
            buf.writeVarInt(payload.fall);
            buf.writeVarInt(payload.press);
            buf.writeBoolean(payload.boss);
        },
        buf -> new CrushPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<CrushPayload> type() {
        return TYPE;
    }
}
