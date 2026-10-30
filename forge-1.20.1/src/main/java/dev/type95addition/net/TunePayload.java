package dev.type95addition.net;

import dev.type95addition.LightningTuning;
import net.minecraft.network.FriendlyByteBuf;

/** Client to server: the player's Lightning Formula settings (on joining and whenever they change). */
public record TunePayload(LightningTuning tuning) {
    public void write(FriendlyByteBuf buf) {
        this.tuning.write(buf);
    }

    public static TunePayload read(FriendlyByteBuf buf) {
        return new TunePayload(LightningTuning.read(buf));
    }
}
