package dev.type95addition.net;

import dev.type95addition.Type95Addition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the right click with the rifle in Lightning mode went down, came up, or was called off. */
public record ChargePayload(int action) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int RELEASE = 1;
    public static final int CANCEL = 2;

    public static final Type<ChargePayload> TYPE = new Type<>(Type95Addition.id("charge"));
    public static final StreamCodec<FriendlyByteBuf, ChargePayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> buf.writeVarInt(payload.action), buf -> new ChargePayload(buf.readVarInt()));

    @Override
    public Type<ChargePayload> type() {
        return TYPE;
    }
}
