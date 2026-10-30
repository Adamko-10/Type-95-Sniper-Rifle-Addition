package dev.type95addition.net;

import net.minecraft.network.FriendlyByteBuf;

/** Server to the players around: this player started (with {@code heldTicks} already held) or stopped charging. */
public record ChargeSyncPayload(int entityId, boolean charging, int heldTicks) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeBoolean(this.charging);
        buf.writeVarInt(this.heldTicks);
    }

    public static ChargeSyncPayload read(FriendlyByteBuf buf) {
        return new ChargeSyncPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
    }
}
