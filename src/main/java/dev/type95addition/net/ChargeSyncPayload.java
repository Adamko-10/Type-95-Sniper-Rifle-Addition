package dev.type95addition.net;

import dev.type95addition.Type95Addition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to the players around: this player started (with {@code heldTicks} already held) or stopped charging;
 * {@code gauss}: the Gauss Formula's charge, not the lightning's.
 */
public record ChargeSyncPayload(int entityId, boolean charging, int heldTicks, boolean gauss) implements CustomPacketPayload {
    public static final Type<ChargeSyncPayload> TYPE = new Type<>(Type95Addition.id("charge_sync"));
    public static final StreamCodec<FriendlyByteBuf, ChargeSyncPayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> {
            buf.writeVarInt(payload.entityId);
            buf.writeBoolean(payload.charging);
            buf.writeVarInt(payload.heldTicks);
            buf.writeBoolean(payload.gauss);
        },
        buf -> new ChargeSyncPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<ChargeSyncPayload> type() {
        return TYPE;
    }
}
