package dev.type95addition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the players around: one bolt of a strike hit here.
 *
 * @param kind {@link #MAIN} the first and biggest, {@link #VOLLEY} the ones right after it, {@link #STORM} the storm's
 *             later bolts, {@link #CHAIN} one that jumped sideways from {@code from} to someone
 * @param from where a chain bolt jumped from (unused otherwise)
 */
public record BoltPayload(Vec3 pos, float size, int kind, Vec3 from) {
    public static final int MAIN = 0;
    public static final int VOLLEY = 1;
    public static final int STORM = 2;
    public static final int CHAIN = 3;

    public void write(FriendlyByteBuf buf) {
        AddonNetwork.writeVec3(buf, this.pos);
        buf.writeFloat(this.size);
        buf.writeVarInt(this.kind);
        AddonNetwork.writeVec3(buf, this.from);
    }

    public static BoltPayload read(FriendlyByteBuf buf) {
        return new BoltPayload(AddonNetwork.readVec3(buf), buf.readFloat(), buf.readVarInt(), AddonNetwork.readVec3(buf));
    }

    public boolean main() {
        return this.kind == MAIN;
    }
}
