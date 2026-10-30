package dev.type95addition.net;

import dev.type95addition.Type95Addition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Client to server, while charging: where the strike should land (left click while holding right click).
 *
 * @param kind {@link #CLEAR}, {@link #POINT} (a block), {@link #ENTITY} (followed until the shot), {@link #SKY}
 *             (nothing in the way: the strike comes down under that point)
 */
public record MarkPayload(int kind, Vec3 pos, int entityId) implements CustomPacketPayload {
    public static final int CLEAR = 0;
    public static final int POINT = 1;
    public static final int ENTITY = 2;
    public static final int SKY = 3;

    public static final Type<MarkPayload> TYPE = new Type<>(Type95Addition.id("mark"));
    public static final StreamCodec<FriendlyByteBuf, MarkPayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> {
            buf.writeVarInt(payload.kind);
            buf.writeVec3(payload.pos);
            buf.writeVarInt(payload.entityId);
        },
        buf -> new MarkPayload(buf.readVarInt(), buf.readVec3(), buf.readVarInt()));

    @Override
    public Type<MarkPayload> type() {
        return TYPE;
    }
}
