package dev.type95addition.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.type95addition.Type95Addition;
import dev.type95addition.client.anim.RifleAnim;
import dev.type95addition.client.fx.ArcParticle;
import dev.type95addition.client.fx.ChargeFx;
import dev.type95addition.client.fx.MarkFx;
import dev.type95addition.client.fx.StormFx;
import dev.type95addition.client.fx.StrikeFx;
import dev.type95addition.net.AddonNetwork;
import dev.type95addition.net.BoltPayload;
import dev.type95addition.net.ChargeSyncPayload;
import dev.type95addition.net.CooldownPayload;
import dev.type95addition.net.SettingsPayload;
import dev.type95addition.net.StrikePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The client's side, started from {@link Type95Addition}'s constructor on the client only. The camera shake, the zoom
 * and the scope's scroll come from Forge's events here (the Fabric build has mixins for them).
 */
public final class AdditionClient {
    private static final String CATEGORY = "key.category.type95_addition.type95_addition";
    public static final KeyMapping AMMO_KEY = new KeyMapping("key.type95_addition.ammo", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping SETTINGS_KEY = new KeyMapping("key.type95_addition.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    private AdditionClient() {
    }

    public static void init(IEventBus modBus) {
        ClientConfig.load();
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(AMMO_KEY);
            event.register(SETTINGS_KEY);
        });
        modBus.addListener((RegisterParticleProvidersEvent event) -> event.registerSpriteSet(Type95Addition.ARC, ArcParticle.Provider::new));
        modBus.addListener((RegisterGuiOverlaysEvent event) ->
            event.registerAboveAll("hud", (gui, g, partial, width, height) -> AdditionHud.render(g, partial)));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            StormFx.register();
            Scope.register();
        }));

        AddonNetwork.client = payload -> {
            if (payload instanceof SettingsPayload settings) {
                ChargeClient.onSettings(settings);
            } else if (payload instanceof CooldownPayload cooldown) {
                ChargeClient.onCooldown(cooldown);
            } else if (payload instanceof ChargeSyncPayload sync) {
                ChargeFx.sync(sync);
            } else if (payload instanceof StrikePayload strike) {
                StrikeFx.onStrike(strike);
            } else if (payload instanceof BoltPayload bolt) {
                StrikeFx.onBolt(bolt);
            }
        };

        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ChargeClient.sendTuning());
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ChargeClient.reset();
            StrikeFx.clear();
        });
        bus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                tick(Minecraft.getInstance());
            }
        });
        // the scope has its own reticle
        bus.addListener((RenderGuiOverlayEvent.Pre event) -> {
            if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) && Scope.raised(event.getPartialTick()) >= 0.5F) {
                event.setCanceled(true);
            }
        });
        // the zoom while charging, and the scope's (the J screen can turn the first off)
        bus.addListener((ViewportEvent.ComputeFov event) -> {
            if (event.usedConfiguredFov()) {
                float zoom = ChargeClient.zoom((float)event.getPartialTick());
                if (zoom != 1.0F) {
                    event.setFOV(event.getFOV() * zoom);
                }
            }
        });
        // the camera shake of a nearby strike (the J screen can turn it off)
        bus.addListener((ViewportEvent.ComputeCameraAngles event) -> {
            float[] turn = StrikeFx.shake((float)event.getPartialTick());
            if (turn != null) {
                event.setYaw(event.getYaw() + turn[0]);
                event.setPitch(event.getPitch() + turn[1]);
            }
        });
        // scrolling while looking through the scope zooms it instead of changing the hotbar slot
        bus.addListener((InputEvent.MouseScrollingEvent event) -> {
            if (Minecraft.getInstance().screen == null && Scope.scroll(event.getScrollDelta())) {
                event.setCanceled(true);
            }
        });
        // (no key names here: naming a key asks GLFW, and GLFW isn't started yet - that crashes the game at launch)
        Type95Addition.LOGGER.info("Type 95 Addition client ready.");
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            while (AMMO_KEY.consumeClick()) {
            }
            while (SETTINGS_KEY.consumeClick()) {
            }
            return;
        }
        boolean rifle = Type95Addition.holdsRifle(mc.player);
        while (AMMO_KEY.consumeClick()) {
            if (rifle) {
                ChargeClient.switchAmmo();
            }
        }
        while (SETTINGS_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new LightningScreen());
            }
        }
        ChargeClient.tick(mc);
        Scope.tick(mc);
        if (!mc.isPaused()) {
            ChargeFx.tick(mc);
            StrikeFx.tick(mc);
            RifleAnim.tick(mc);
            MarkFx.tick(mc);
        }
    }
}
