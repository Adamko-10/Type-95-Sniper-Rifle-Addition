package dev.type95addition.net;

import dev.type95addition.Type95Addition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the players around: one bolt of a strike hit here.
 *
 * @param kind {@link #MAIN} the first and biggest, {@link #VOLLEY} the ones right after it, {@link #STORM} the storm's
 *             later bolts, {@link #CHAIN} one that jumped sideways from {@code from} to someone
 * @param from where a chain bolt jumped from (unused otherwise)
 */
public record BoltPayload(Vec3 pos, float size, int kind, Vec3 from) implements CustomPacketPayload {
    public static final int MAIN = 0;
    public static final int VOLLEY = 1;
    public static final int STORM = 2;
    public static final int CHAIN = 3;

    public static final Type<BoltPayload> TYPE = new Type<>(Type95Addition.id("bolt"));
    public static final StreamCodec<FriendlyByteBuf, BoltPayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> {
            buf.writeVec3(payload.pos);
            buf.writeFloat(payload.size);
            buf.writeVarInt(payload.kind);
            buf.writeVec3(payload.from);
        },
        buf -> new BoltPayload(buf.readVec3(), buf.readFloat(), buf.readVarInt(), buf.readVec3()));

    public boolean main() {
        return this.kind == MAIN;
    }

    @Override
    public Type<BoltPayload> type() {
        return TYPE;
    }
}
