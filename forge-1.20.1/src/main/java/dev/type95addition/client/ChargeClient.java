package dev.type95addition.client;

import dev.type95addition.LightningTuning;
import dev.type95addition.Type95Addition;
import dev.type95addition.client.fx.ChargeFx;
import dev.type95addition.net.ChargePayload;
import dev.type95addition.net.CooldownPayload;
import dev.type95addition.net.SettingsPayload;
import dev.type95addition.net.TunePayload;
import dev.type95addition.world.LightningCasting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The local player's side of the Lightning Formula: right click with the rifle in Lightning mode starts a charge
 * here instead of Type 95's own use of the rifle ({@code mixin.MinecraftMixin}), letting go fires it. The server
 * decides everything that matters (charge time, cooldown, where it lands); this side only draws and asks.
 */
public final class ChargeClient {
    /** From the server ({@link SettingsPayload}); the default until it has told us. */
    public static int chargeTicks = 160;
    public static float maxPower = LightningTuning.MAX_POWER;
    public static float maxDamage = 0.0F;
    public static int maxBolts = LightningTuning.MAX_BOLTS;
    /** Blocks: how far a strike can be called (for marking). */
    public static float range = 512.0F;
    public static boolean serverFire = true;
    public static boolean serverTerrain = true;
    public static boolean serverBlast = true;
    private static boolean survivalAllowed = true;

    private static boolean charging;
    private static int held;
    private static int cooldown;
    private static int cooldownTotal = 1;
    /** Ticks the ammo label stays big after switching. */
    private static int ammoShown;
    private static boolean warnedNoServer;

    private ChargeClient() {
    }

    /** Right click (Minecraft.startUseItem): true = the addon took it, so the rifle's own use doesn't start. */
    public static boolean interceptUse() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ClientConfig.lightning || player.isSpectator() || !Type95Addition.holdsRifle(player)) {
            return false;
        }
        if (!ClientNet.serverHasAddon()) {
            if (!warnedNoServer) {
                warnedNoServer = true;
                overlay(Component.translatable("hud.type95_addition.no_server"));
            }
            return false;
        }
        if (charging) {
            return true;
        }
        if (cooldown > 0) {
            overlay(Component.translatable("hud.type95_addition.recharging", (cooldown + 19) / 20));
            return true;
        }
        if (!player.isCreative() && !survivalAllowed) {
            overlay(Component.translatable("hud.type95_addition.not_allowed"));
            return true;
        }
        charging = true;
        held = 0;
        ClientNet.send(new ChargePayload(ChargePayload.START));
        return true;
    }

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (ammoShown > 0) {
            ammoShown--;
        }
        if (!charging) {
            return;
        }
        boolean keep = mc.screen == null && player.isAlive() && !player.isSpectator() && ClientConfig.lightning
            && Type95Addition.holdsRifle(player);
        if (!keep) {
            stop(ChargePayload.CANCEL);
        } else if (!mc.options.keyUse.isDown()) {
            if (held < LightningCasting.MIN_HOLD) {
                player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), dev.type95addition.AddonSounds.FIZZLE,
                    SoundSource.PLAYERS, 0.8F, 1.0F, false);
            }
            stop(ChargePayload.RELEASE);
        } else {
            held++;
            Marking.tick(mc);
        }
    }

    private static void stop(int action) {
        charging = false;
        Marking.reset();
        if (ClientNet.serverHasAddon()) {
            ClientNet.send(new ChargePayload(action));
        }
    }

    /** The server refused or ended the charge. */
    public static void stoppedByServer() {
        charging = false;
        Marking.reset();
    }

    public static void switchAmmo() {
        Minecraft mc = Minecraft.getInstance();
        if (charging) {
            stop(ChargePayload.CANCEL);
        }
        ClientConfig.lightning = !ClientConfig.lightning;
        ClientConfig.save();
        ammoShown = 60;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(
            ClientConfig.lightning ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.FLINTANDSTEEL_USE, ClientConfig.lightning ? 1.6F : 1.0F));
    }

    public static void sendTuning() {
        if (ClientNet.serverHasAddon()) {
            ClientNet.send(new TunePayload(ClientConfig.tuning()));
        }
    }

    public static void onSettings(SettingsPayload payload) {
        chargeTicks = Math.max(1, payload.chargeTicks());
        maxPower = payload.maxPower();
        maxDamage = payload.maxDamage();
        maxBolts = payload.maxBolts();
        serverFire = payload.fire();
        serverTerrain = payload.terrain();
        serverBlast = payload.blast();
        survivalAllowed = payload.survival();
        range = payload.range();
        sendTuning();
    }

    public static void onCooldown(CooldownPayload payload) {
        cooldown = payload.ticks();
        cooldownTotal = Math.max(1, payload.total());
    }

    public static void reset() {
        charging = false;
        held = 0;
        cooldown = 0;
        warnedNoServer = false;
        chargeTicks = 160;
        maxPower = LightningTuning.MAX_POWER;
        maxDamage = 0.0F;
        maxBolts = LightningTuning.MAX_BOLTS;
        serverFire = serverTerrain = serverBlast = survivalAllowed = true;
        ChargeFx.clear();
    }

    public static boolean charging() {
        return charging;
    }

    public static int held() {
        return held;
    }

    public static float charge(float partial) {
        return charging ? Mth.clamp((held + partial) / chargeTicks, 0.0F, 1.0F) : 0.0F;
    }

    public static float cooldownLeft() {
        return cooldown / (float)cooldownTotal;
    }

    public static int cooldownTicks() {
        return cooldown;
    }

    public static boolean ammoJustSwitched() {
        return ammoShown > 0;
    }

    /** The field of view multiplier while charging (closes in as the charge grows, like a scope). */
    public static float zoom(float partial) {
        if (ClientConfig.scope && (Scope.active() || Scope.fov(partial) < 0.999F)) {
            return Scope.fov(partial);
        }
        if (!charging || !ClientConfig.zoom) {
            return 1.0F;
        }
        float c = charge(partial);
        float ease = Math.min(1.0F, (held + partial) / 6.0F);
        return 1.0F - ease * (0.15F + 0.35F * c * c);
    }

    private static void overlay(Component message) {
        Minecraft.getInstance().gui.setOverlayMessage(message.copy().withStyle(ChatFormatting.AQUA), false);
    }
}
