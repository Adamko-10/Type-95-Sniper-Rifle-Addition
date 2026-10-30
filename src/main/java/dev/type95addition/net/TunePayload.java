package dev.type95addition.net;

import dev.type95addition.LightningTuning;
import dev.type95addition.Type95Addition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player's Lightning Formula settings (on joining and whenever they change). */
public record TunePayload(LightningTuning tuning) implements CustomPacketPayload {
    public static final Type<TunePayload> TYPE = new Type<>(Type95Addition.id("tune"));
    public static final StreamCodec<FriendlyByteBuf, TunePayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> payload.tuning.write(buf), buf -> new TunePayload(LightningTuning.read(buf)));

    @Override
    public Type<TunePayload> type() {
        return TYPE;
    }
}
