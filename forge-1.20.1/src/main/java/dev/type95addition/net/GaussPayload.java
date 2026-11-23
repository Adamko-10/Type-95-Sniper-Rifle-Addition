package dev.type95addition.net;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the players near the line: a Gauss Formula shot went from {@code muzzle} to {@code end}, came out of a
 * block at each of {@code exits} and went through a mob at each of {@code hits}.
 */
public record GaussPayload(int shooterId, Vec3 muzzle, Vec3 end, float charge, List<Vec3> exits, List<Vec3> hits) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.shooterId);
        AddonNetwork.writeVec3(buf, this.muzzle);
        AddonNetwork.writeVec3(buf, this.end);
        buf.writeFloat(this.charge);
        buf.writeCollection(this.exits, AddonNetwork::writeVec3);
        buf.writeCollection(this.hits, AddonNetwork::writeVec3);
    }

    public static GaussPayload read(FriendlyByteBuf buf) {
        return new GaussPayload(buf.readVarInt(), AddonNetwork.readVec3(buf), AddonNetwork.readVec3(buf), buf.readFloat(),
            buf.readList(AddonNetwork::readVec3), buf.readList(AddonNetwork::readVec3));
    }
}
