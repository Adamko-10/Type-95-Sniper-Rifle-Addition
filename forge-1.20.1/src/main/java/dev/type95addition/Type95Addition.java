package dev.type95addition;

import cyou.rimuru.rimulib.mc1201.Rimulib;
import dev.type95addition.client.AdditionClient;
import dev.type95addition.net.AddonNetwork;
import dev.type95addition.world.LightningCasting;
import dev.type95addition.world.LightningStrike;
import java.util.Comparator;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;
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
@Mod(Type95Addition.MOD_ID)
public final class Type95Addition {
    public static final String MOD_ID = "type95_addition";
    public static final Logger LOGGER = LoggerFactory.getLogger("Type 95 Addition");

    /** Type 95's rifle. */
    public static final ResourceLocation RIFLE = new ResourceLocation("tanya_demo", "type95_rifle");

    public static final ResourceKey<DamageType> LIGHTNING_FORMULA = ResourceKey.create(Registries.DAMAGE_TYPE, id("lightning_formula"));

    /** The arcs of every charge and strike (drawn by {@code client.fx.ArcParticle}; spawned on the client only). */
    public static final SimpleParticleType ARC = new SimpleParticleType(true);

    /** Holds a crater's chunks loaded while they are carved (one at a time, radius 0). */
    public static final TicketType<ChunkPos> CRATER_TICKET = TicketType.create(MOD_ID + ":crater", Comparator.comparingLong(ChunkPos::toLong));

    public Type95Addition() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Rimulib finds a mod's shaders through a class of that mod: on Forge each mod is its own module, and a class
        // only sees its own module's files (on Fabric any class sees every mod's)
        Rimulib.register(MOD_ID, Type95Addition.class);
        ServerConfig.load();
        modBus.addListener(Type95Addition::register);
        AddonNetwork.register();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                LightningCasting.tick(event.getServer());
                LightningStrike.tickAll();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) -> ServerConfig.load());
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            LightningCasting.clear();
            LightningStrike.clear();
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                AddonNetwork.sendSettings(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                LightningCasting.forget(player);
            }
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AdditionClient.init(modBus);
        }
        LOGGER.info("Type 95 Addition is loaded: the Lightning Formula is ready.");
    }

    private static void register(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE, helper -> helper.register(id("arc"), ARC));
        event.register(Registries.SOUND_EVENT, helper -> AddonSounds.register(helper::register));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static boolean isRifle(ItemStack stack) {
        return !stack.isEmpty() && RIFLE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** The Lightning Formula fires from the main hand only (the off hand keeps the rifle's own Explosion Formula). */
    public static boolean holdsRifle(Player player) {
        return player != null && isRifle(player.getMainHandItem());
    }
}
