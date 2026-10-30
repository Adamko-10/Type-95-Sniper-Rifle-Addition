package dev.type95addition.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server to client, on joining: the part of the server's config the client needs (the charge time, for the charge's
 * stages, and the limits the J screen shows). {@code survival}: survival players may fire the Lightning Formula.
 */
public record SettingsPayload(int chargeTicks, float maxPower, float maxDamage, int maxBolts, boolean fire, boolean terrain,
                              boolean blast, boolean survival, float range) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.chargeTicks);
        buf.writeFloat(this.maxPower);
        buf.writeFloat(this.maxDamage);
        buf.writeVarInt(this.maxBolts);
        buf.writeByte((this.fire ? 1 : 0) | (this.terrain ? 2 : 0) | (this.blast ? 4 : 0) | (this.survival ? 8 : 0));
        buf.writeFloat(this.range);
    }

    public static SettingsPayload read(FriendlyByteBuf buf) {
        int chargeTicks = buf.readVarInt();
        float maxPower = buf.readFloat();
        float maxDamage = buf.readFloat();
        int maxBolts = buf.readVarInt();
        int flags = buf.readByte();
        float range = buf.readFloat();
        return new SettingsPayload(chargeTicks, maxPower, maxDamage, maxBolts, (flags & 1) != 0, (flags & 2) != 0,
            (flags & 4) != 0, (flags & 8) != 0, range);
    }
}
