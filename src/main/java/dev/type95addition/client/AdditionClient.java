package dev.type95addition.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.type95addition.Type95Addition;
import dev.type95addition.client.fx.ArcParticle;
import dev.type95addition.client.fx.ChargeFx;
import dev.type95addition.client.fx.StormFx;
import dev.type95addition.client.fx.StrikeFx;
import dev.type95addition.client.anim.RifleAnim;
import dev.type95addition.net.BoltPayload;
import dev.type95addition.net.ChargeSyncPayload;
import dev.type95addition.net.CooldownPayload;
import dev.type95addition.net.SettingsPayload;
import dev.type95addition.net.StrikePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import dev.type95addition.client.fx.MarkFx;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class AdditionClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Type95Addition.id("type95_addition"));
    public static final KeyMapping AMMO_KEY = new KeyMapping("key.type95_addition.ammo", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping SETTINGS_KEY = new KeyMapping("key.type95_addition.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    @Override
    public void onInitializeClient() {
        ClientConfig.load();
        KeyBindingHelper.registerKeyBinding(AMMO_KEY);
        KeyBindingHelper.registerKeyBinding(SETTINGS_KEY);
        ParticleFactoryRegistry.getInstance().register(Type95Addition.ARC, ArcParticle.Provider::new);
        StormFx.register();
        Scope.register();

        ClientPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE, (payload, context) -> ChargeClient.onSettings(payload));
        ClientPlayNetworking.registerGlobalReceiver(CooldownPayload.TYPE, (payload, context) -> ChargeClient.onCooldown(payload));
        ClientPlayNetworking.registerGlobalReceiver(ChargeSyncPayload.TYPE, (payload, context) -> ChargeFx.sync(payload));
        ClientPlayNetworking.registerGlobalReceiver(StrikePayload.TYPE, (payload, context) -> StrikeFx.onStrike(payload));
        ClientPlayNetworking.registerGlobalReceiver(BoltPayload.TYPE, (payload, context) -> StrikeFx.onBolt(payload));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ChargeClient.sendTuning());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ChargeClient.reset();
            StrikeFx.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(AdditionClient::tick);
        HudElementRegistry.addLast(Type95Addition.id("hud"), AdditionHud::render);
        // the scope has its own reticle
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, crosshair -> (g, delta) -> {
            if (Scope.raised(delta.getGameTimeDeltaPartialTick(true)) < 0.5F) {
                crosshair.render(g, delta);
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
