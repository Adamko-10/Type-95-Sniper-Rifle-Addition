package dev.type95addition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * Client to server, while charging: where the strike should land (left click while holding right click).
 *
 * @param kind {@link #CLEAR}, {@link #POINT} (a block), {@link #ENTITY} (followed until the shot), {@link #SKY}
 *             (nothing in the way: the strike comes down under that point)
 */
public record MarkPayload(int kind, Vec3 pos, int entityId) {
    public static final int CLEAR = 0;
    public static final int POINT = 1;
    public static final int ENTITY = 2;
    public static final int SKY = 3;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.kind);
        AddonNetwork.writeVec3(buf, this.pos);
        buf.writeVarInt(this.entityId);
    }

    public static MarkPayload read(FriendlyByteBuf buf) {
        return new MarkPayload(buf.readVarInt(), AddonNetwork.readVec3(buf), buf.readVarInt());
    }
}
