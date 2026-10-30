package dev.type95addition.client;

import cyou.rimuru.rimulib.client.ClientHooks;
import dev.type95addition.AddonSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;

/**
 * The scope (J screen, off by default): while charging in first person the view goes through the rifle's scope like
 * the Explosion Formula's, zoomed (scroll changes the magnification, 1.5-12x), the hand hidden and the mouse slowed
 * to match. The scope itself is drawn by {@link AdditionHud}.
 */
public final class Scope {
    private static final float MIN = 1.5F;
    private static final float MAX = 12.0F;
    private static float magnification = 4.0F;
    private static float shown = 1.0F;
    private static float shownBefore = 1.0F;
    private static boolean was;

    private Scope() {
    }

    public static void register() {
        ClientHooks.hideHand(Scope::active);
        ClientHooks.limitTurn((dx, dy) -> {
            if (!active()) {
                return null;
            }
            double slow = 1.0 / Math.sqrt(shown);
            return new double[] {dx * slow, dy * slow};
        });
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return ClientConfig.scope && ChargeClient.charging() && mc.options.getCameraType().isFirstPerson();
    }

    public static void tick(Minecraft mc) {
        boolean now = active();
        if (now && !was && mc.player != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(AddonSounds.SCOPE, 1.0F, 0.6F));
        }
        was = now;
        shownBefore = shown;
        float goal = now ? magnification : 1.0F;
        shown += (goal - shown) * 0.45F;
    }

    /** Scroll while scoped: true = the scope took it (the hotbar doesn't change). */
    public static boolean scroll(double amount) {
        if (!active() || amount == 0.0) {
            return false;
        }
        float before = magnification;
        magnification = Mth.clamp(magnification * (amount > 0.0 ? 1.25F : 0.8F), MIN, MAX);
        if (magnification != before) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(AddonSounds.SCOPE, 1.6F + magnification * 0.05F, 0.25F));
        }
        return true;
    }

    /** The field of view multiplier: 1 / the magnification (eased in and out). */
    public static float fov(float partial) {
        return 1.0F / Mth.lerp(partial, shownBefore, shown);
    }

    public static float magnification() {
        return magnification;
    }

    /** 0-1: how far the scope has come up (for the overlay fading in). */
    public static float raised(float partial) {
        float m = Mth.lerp(partial, shownBefore, shown);
        return Mth.clamp((m - 1.0F) / Math.max(0.01F, magnification - 1.0F), 0.0F, 1.0F);
    }
}
