package dev.type95addition;

import dev.type95addition.net.AddonNetwork;
import dev.type95addition.world.LightningCasting;
import dev.type95addition.world.LightningStrike;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Type 95 Addition: a second ammo type for rimuru.dev's Type 95 Sniper Rifle, the Lightning Formula.
 *
 * <p>Type 95's own classes are obfuscated with new names every release, so the addon never touches them: it only
 * knows the rifle's item id. In Lightning mode the client never starts "using" the rifle (see
 * {@code client.mixin.MinecraftMixin}), so none of Type 95's charge, scope or shot code runs; the addon's own charge
 * ({@link LightningCasting}) and strike ({@link LightningStrike}) take its place.
 */
public final class Type95Addition implements ModInitializer {
    public static final String MOD_ID = "type95_addition";
    public static final Logger LOGGER = LoggerFactory.getLogger("Type 95 Addition");

    /** Type 95's rifle. */
    public static final Identifier RIFLE = Identifier.fromNamespaceAndPath("tanya_demo", "type95_rifle");

    public static final ResourceKey<DamageType> LIGHTNING_FORMULA = ResourceKey.create(Registries.DAMAGE_TYPE, id("lightning_formula"));

    /** The arcs of every charge and strike (drawn by {@code client.fx.ArcParticle}; spawned on the client only). */
    public static final SimpleParticleType ARC = Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("arc"), FabricParticleTypes.simple(true));

    /** Holds a crater's chunks loaded while they are carved (one at a time, radius 0). */
    public static final TicketType CRATER_TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, id("crater"),
        new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));

    @Override
    public void onInitialize() {
        ServerConfig.load();
        AddonSounds.init();
        AddonNetwork.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            LightningCasting.tick(server);
            LightningStrike.tickAll();
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ServerConfig.load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LightningCasting.clear();
            LightningStrike.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> AddonNetwork.sendSettings(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LightningCasting.forget(handler.getPlayer()));
        LOGGER.info("Type 95 Addition is loaded: the Lightning Formula is ready.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isRifle(ItemStack stack) {
        return !stack.isEmpty() && RIFLE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** The Lightning Formula fires from the main hand only (the off hand keeps the rifle's own Explosion Formula). */
    public static boolean holdsRifle(Player player) {
        return player != null && isRifle(player.getMainHandItem());
    }
}
