package dev.type95addition.net;

import dev.type95addition.Type95Addition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the players around: a Lightning Formula was fired. Everything the clients draw for it follows from these
 * (the beam, the storm over the target, the flash, the shock wave, the plasma dome, the crater's glow); the bolts
 * themselves each send a {@link BoltPayload}. {@code shooterId} is -1 when the shooter is unknown.
 *
 * @param scale  the strike's size ({@code LightningTuning.scale}); its radii follow from it
 * @param crater the crater's radius, 0 for none
 * @param storm  ticks the storm keeps striking after the first volley
 */
public record StrikePayload(int shooterId, Vec3 muzzle, Vec3 target, float charge, float scale, float crater, int storm, long seed)
    implements CustomPacketPayload {
    public static final Type<StrikePayload> TYPE = new Type<>(Type95Addition.id("strike"));
    public static final StreamCodec<FriendlyByteBuf, StrikePayload> CODEC = CustomPacketPayload.codec(
        StrikePayload::write, StrikePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.shooterId);
        buf.writeVec3(this.muzzle);
        buf.writeVec3(this.target);
        buf.writeFloat(this.charge);
        buf.writeFloat(this.scale);
        buf.writeFloat(this.crater);
        buf.writeVarInt(this.storm);
        buf.writeLong(this.seed);
    }

    private static StrikePayload read(FriendlyByteBuf buf) {
        return new StrikePayload(buf.readVarInt(), buf.readVec3(), buf.readVec3(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readVarInt(), buf.readLong());
    }

    @Override
    public Type<StrikePayload> type() {
        return TYPE;
    }
}
