package dev.type95addition.net;

import dev.type95addition.ServerConfig;
import dev.type95addition.world.LightningCasting;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The addon's packets (Fabric networking). The client's receivers are registered from the client side
 * ({@code client.AdditionClient}), so nothing client-only loads on a dedicated server.
 */
public final class AddonNetwork {
    private AddonNetwork() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ChargePayload.TYPE, ChargePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TunePayload.TYPE, TunePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MarkPayload.TYPE, MarkPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SettingsPayload.TYPE, SettingsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargeSyncPayload.TYPE, ChargeSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrikePayload.TYPE, StrikePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoltPayload.TYPE, BoltPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CooldownPayload.TYPE, CooldownPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ChargePayload.TYPE, (payload, context) ->
            LightningCasting.onCharge(context.player(), payload.action()));
        ServerPlayNetworking.registerGlobalReceiver(TunePayload.TYPE, (payload, context) ->
            LightningCasting.tune(context.player(), payload.tuning()));
        ServerPlayNetworking.registerGlobalReceiver(MarkPayload.TYPE, (payload, context) ->
            LightningCasting.mark(context.player(), payload));
    }

    public static void sendSettings(ServerPlayer player) {
        send(player, new SettingsPayload(ServerConfig.chargeTicks(), ServerConfig.maxPower(), ServerConfig.maxDamage(),
            ServerConfig.maxBolts(), ServerConfig.fire(), ServerConfig.terrain(), ServerConfig.blast(), ServerConfig.survival(),
            (float)ServerConfig.range()));
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** To the player and everyone who can see them. */
    public static void sendAround(ServerPlayer player, CustomPacketPayload payload) {
        send(player, payload);
        for (ServerPlayer other : PlayerLookup.tracking(player)) {
            if (other != player) {
                send(other, payload);
            }
        }
    }

    /** To everyone within {@code radius} of any of the points (each player once). */
    public static void sendNear(ServerLevel level, CustomPacketPayload payload, double radius, Vec3... points) {
        Set<ServerPlayer> players = new HashSet<>();
        for (Vec3 point : points) {
            players.addAll(PlayerLookup.around(level, point, radius));
        }
        for (ServerPlayer player : players) {
            send(player, payload);
        }
    }
}
