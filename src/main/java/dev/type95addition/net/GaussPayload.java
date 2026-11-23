package dev.type95addition.net;

import dev.type95addition.Type95Addition;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the players near the line: a Gauss Formula shot went from {@code muzzle} to {@code end}, came out of a
 * block at each of {@code exits} and went through a mob at each of {@code hits}.
 */
public record GaussPayload(int shooterId, Vec3 muzzle, Vec3 end, float charge, List<Vec3> exits, List<Vec3> hits)
    implements CustomPacketPayload {
    public static final Type<GaussPayload> TYPE = new Type<>(Type95Addition.id("gauss"));
    public static final StreamCodec<FriendlyByteBuf, GaussPayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> {
            buf.writeVarInt(payload.shooterId);
            buf.writeVec3(payload.muzzle);
            buf.writeVec3(payload.end);
            buf.writeFloat(payload.charge);
            buf.writeCollection(payload.exits, (b, v) -> b.writeVec3(v));
            buf.writeCollection(payload.hits, (b, v) -> b.writeVec3(v));
        },
        buf -> new GaussPayload(buf.readVarInt(), buf.readVec3(), buf.readVec3(), buf.readFloat(),
            buf.readList(b -> b.readVec3()), buf.readList(b -> b.readVec3())));

    @Override
    public Type<GaussPayload> type() {
        return TYPE;
    }
}
