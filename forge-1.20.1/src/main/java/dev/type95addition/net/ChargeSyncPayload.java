package dev.type95addition.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server to the players around: this player started (with {@code heldTicks} already held) or stopped charging;
 * {@code gauss}: the Gauss Formula's charge, not the lightning's.
 */
public record ChargeSyncPayload(int entityId, boolean charging, int heldTicks, boolean gauss) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeBoolean(this.charging);
        buf.writeVarInt(this.heldTicks);
        buf.writeBoolean(this.gauss);
    }

    public static ChargeSyncPayload read(FriendlyByteBuf buf) {
        return new ChargeSyncPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
    }
}
