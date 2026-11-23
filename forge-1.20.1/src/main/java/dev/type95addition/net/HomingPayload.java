package dev.type95addition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the players around: a Homing Formula shot left {@code muzzle}; bullet i chases entity {@code targets[i]}
 * (-1: none, it flies to {@code point}, where the crosshair was).
 */
public record HomingPayload(Vec3 muzzle, Vec3 look, Vec3 point, int[] targets) {
    public void write(FriendlyByteBuf buf) {
        AddonNetwork.writeVec3(buf, this.muzzle);
        AddonNetwork.writeVec3(buf, this.look);
        AddonNetwork.writeVec3(buf, this.point);
        buf.writeVarIntArray(this.targets);
    }

    public static HomingPayload read(FriendlyByteBuf buf) {
        return new HomingPayload(AddonNetwork.readVec3(buf), AddonNetwork.readVec3(buf), AddonNetwork.readVec3(buf), buf.readVarIntArray());
    }
}
