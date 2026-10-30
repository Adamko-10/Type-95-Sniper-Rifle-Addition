package dev.type95addition.net;

import dev.type95addition.ServerConfig;
import dev.type95addition.Type95Addition;
import dev.type95addition.world.LightningCasting;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The addon's packets (one Forge channel). Players without the addon can still join (the channel may be missing on
 * either side); nothing is sent to them. The client's side of the server's packets is handed to {@link #client},
 * which {@code client.AdditionClient} sets, so nothing client-only loads on a dedicated server.
 */
public final class AddonNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Type95Addition.id("main"),
        () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    /** The client's handler of the server's packets (runs on the client's main thread). */
    public static Consumer<Object> client = payload -> {
    };

    private static int nextId;

    private AddonNetwork() {
    }

    public static void register() {
        toServer(ChargePayload.class, ChargePayload::write, ChargePayload::read,
            (payload, player) -> LightningCasting.onCharge(player, payload.action()));
        toServer(TunePayload.class, TunePayload::write, TunePayload::read,
            (payload, player) -> LightningCasting.tune(player, payload.tuning()));
        toServer(MarkPayload.class, MarkPayload::write, MarkPayload::read,
            (payload, player) -> LightningCasting.mark(player, payload));
        toClient(SettingsPayload.class, SettingsPayload::write, SettingsPayload::read);
        toClient(ChargeSyncPayload.class, ChargeSyncPayload::write, ChargeSyncPayload::read);
        toClient(StrikePayload.class, StrikePayload::write, StrikePayload::read);
        toClient(BoltPayload.class, BoltPayload::write, BoltPayload::read);
        toClient(CooldownPayload.class, CooldownPayload::write, CooldownPayload::read);
    }

    private static <T> void toServer(Class<T> type, BiConsumer<T, FriendlyByteBuf> write, Function<FriendlyByteBuf, T> read,
                                     BiConsumer<T, ServerPlayer> handle) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(write)
            .decoder(read)
            .consumerMainThread((T payload, Supplier<NetworkEvent.Context> context) -> {
                ServerPlayer player = context.get().getSender();
                if (player != null) {
                    handle.accept(payload, player);
                }
            })
            .add();
    }

    private static <T> void toClient(Class<T> type, BiConsumer<T, FriendlyByteBuf> write, Function<FriendlyByteBuf, T> read) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(write)
            .decoder(read)
            .consumerMainThread((T payload, Supplier<NetworkEvent.Context> context) -> client.accept(payload))
            .add();
    }

    public static void writeVec3(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    public static Vec3 readVec3(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    /** For {@code client.ClientNet} (the client's side of the channel). */
    public static SimpleChannel channel() {
        return CHANNEL;
    }

    // ---- Server to client ---------------------------------------------------------------------------------------

    public static void sendSettings(ServerPlayer player) {
        send(player, new SettingsPayload(ServerConfig.chargeTicks(), ServerConfig.maxPower(), ServerConfig.maxDamage(),
            ServerConfig.maxBolts(), ServerConfig.fire(), ServerConfig.terrain(), ServerConfig.blast(), ServerConfig.survival(),
            (float)ServerConfig.range()));
    }

    public static void send(ServerPlayer player, Object payload) {
        if (CHANNEL.isRemotePresent(player.connection.connection)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }

    /** To the player and everyone near enough to see them. */
    public static void sendAround(ServerPlayer player, Object payload) {
        send(player, payload);
        ServerLevel level = player.serverLevel();
        double range = Math.max(4, level.getServer().getPlayerList().getViewDistance()) * 16.0;
        for (ServerPlayer other : level.players()) {
            if (other != player && other.distanceToSqr(player) < range * range) {
                send(other, payload);
            }
        }
    }

    /** To everyone within {@code radius} of any of the points (each player once). */
    public static void sendNear(ServerLevel level, Object payload, double radius, Vec3... points) {
        Set<ServerPlayer> players = new HashSet<>();
        for (Vec3 point : points) {
            for (ServerPlayer player : level.players()) {
                if (player.position().distanceToSqr(point) < radius * radius) {
                    players.add(player);
                }
            }
        }
        for (ServerPlayer player : players) {
            send(player, payload);
        }
    }
}
