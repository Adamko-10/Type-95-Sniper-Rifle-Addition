package dev.type95addition.net;

import dev.type95addition.Type95Addition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the players around: a Homing Formula shot left {@code muzzle}; bullet i chases entity {@code targets[i]}
 * (-1: none, it flies to {@code point}, where the crosshair was).
 */
public record HomingPayload(Vec3 muzzle, Vec3 look, Vec3 point, int[] targets) implements CustomPacketPayload {
    public static final Type<HomingPayload> TYPE = new Type<>(Type95Addition.id("homing"));
    public static final StreamCodec<FriendlyByteBuf, HomingPayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> {
            buf.writeVec3(payload.muzzle);
            buf.writeVec3(payload.look);
            buf.writeVec3(payload.point);
            buf.writeVarIntArray(payload.targets);
        },
        buf -> new HomingPayload(buf.readVec3(), buf.readVec3(), buf.readVec3(), buf.readVarIntArray()));

    @Override
    public Type<HomingPayload> type() {
        return TYPE;
    }
}
