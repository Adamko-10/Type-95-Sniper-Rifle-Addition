package dev.type95addition.net;

import net.minecraft.network.FriendlyByteBuf;

/** Client to server: the right click with the rifle in Lightning mode went down, came up, or was called off. */
public record ChargePayload(int action) {
    public static final int START = 0;
    public static final int RELEASE = 1;
    public static final int CANCEL = 2;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.action);
    }

    public static ChargePayload read(FriendlyByteBuf buf) {
        return new ChargePayload(buf.readVarInt());
    }
}
