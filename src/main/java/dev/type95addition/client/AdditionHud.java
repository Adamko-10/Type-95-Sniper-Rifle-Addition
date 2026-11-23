package dev.type95addition.client;

import dev.type95addition.LightningTuning;
import dev.type95addition.Type95Addition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import dev.type95addition.client.fx.MarkFx;
import java.util.Locale;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;

/**
 * The addon's HUD: the screen flash of a strike, the ammo in the rifle (right of the hotbar, with the keys for a
 * few seconds after switching), the charge bar under the crosshair and the recharge bar.
 */
public final class AdditionHud {
    private static final int EXPLOSION_COLOUR = 0xFFFF8A3D;
    private static final int HOMING_COLOUR = 0xFFFF2E6E;
    private static final int GAUSS_COLOUR = 0xFF7FD4FF;
    /** The Gauss Formula's stages: blue, bright blue, white (like the coils). */
    private static final int[] GAUSS_STAGES = {0xFF2A5BFF, 0xFF4CC2FF, 0xFFF4FBFF};

    private AdditionHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        float partial = delta.getGameTimeDeltaPartialTick(true);
        int w = g.guiWidth();
        int h = g.guiHeight();

        // (a strike's flash is a screen effect now: StrikeFx.passes)
        if (mc.options.hideGui || mc.player == null || mc.screen != null || !Type95Addition.holdsRifle(mc.player)) {
            return;
        }
        float scoped = Scope.raised(partial);
        if (scoped > 0.01F) {
            scope(g, mc, partial, scoped);
        }
        MarkFx.hud(g, partial);
        Font font = mc.font;
        Palette palette = ClientConfig.palette;
        int colour = switch (ClientConfig.ammo) {
            case ClientConfig.LIGHTNING -> 0xFF000000 | palette.glow;
            case ClientConfig.HOMING -> HOMING_COLOUR;
            case ClientConfig.GAUSS -> GAUSS_COLOUR;
            default -> EXPLOSION_COLOUR;
        };

        // The ammo, right of the hotbar
        Component ammo = Component.translatable(switch (ClientConfig.ammo) {
            case ClientConfig.LIGHTNING -> "ammo.type95_addition.lightning";
            case ClientConfig.HOMING -> "ammo.type95_addition.homing";
            case ClientConfig.GAUSS -> "ammo.type95_addition.gauss";
            default -> "ammo.type95_addition.explosion";
        });
        int x = w / 2 + 98;
        int y = h - 19;
        g.drawString(font, Component.translatable("hud.type95_addition.ammo", ammo), x, y, colour, true);
        if (ChargeClient.ammoJustSwitched()) {
            g.drawString(font, Component.translatable("hud.type95_addition.ammo_hint", AdditionClient.AMMO_KEY.getTranslatedKeyMessage(),
                AdditionClient.SETTINGS_KEY.getTranslatedKeyMessage()), x, y - 11, 0xFFB0B0B0, true);
        }
        if (ClientConfig.ammo != ClientConfig.EXPLOSION && ChargeClient.cooldownTicks() > 0) {
            int bw = font.width(Component.translatable("hud.type95_addition.ammo", ammo));
            g.fill(x, y + 10, x + bw, y + 11, 0x80000000);
            g.fill(x, y + 10, x + (int)(bw * ChargeClient.cooldownLeft()), y + 11, colour);
        }

        // The charge, under the crosshair (the scope shows its own)
        if (ChargeClient.charging() && scoped < 0.5F) {
            float c = ChargeClient.charge(partial);
            int stage = LightningTuning.stage(c);
            int bw = 84;
            int bx = w / 2 - bw / 2;
            int by = h / 2 + 14;
            int seg = (bw - 3 * 2) / 4;
            for (int i = 0; i < 4; i++) {
                int sx = bx + i * (seg + 2);
                g.fill(sx, by, sx + seg, by + 3, 0x90000000);
                float fill = Math.min(1.0F, Math.max(0.0F, c * 4.0F - i));
                if (fill > 0.0F) {
                    int segColour = 0xFF000000 | palette.mix(i / 3.0F * 0.8F);
                    g.fill(sx, by, sx + (int)(seg * fill), by + 3, segColour);
                }
            }
            Component label = c >= 1.0F
                ? Component.translatable("hud.type95_addition.full")
                : Component.translatable("hud.type95_addition.charge", stage);
            int pulse = c >= 1.0F && (mc.player.tickCount / 4 & 1) == 0 ? 0xFF000000 | palette.core : colour;
            g.drawCenteredString(font, label, w / 2, by + 6, pulse);
        }
        int gauss = ChargeClient.gaussHeld();
        if (gauss >= 0 && scoped < 0.5F) {
            gaussBar(g, font, w, h, gauss, partial, mc.player.tickCount);
        }
    }

    /**
     * The Gauss Formula's charge, below and left of the crosshair (the aimed rifle's muzzle is just below it, to the
     * right): three segments (blue, bright blue, white) and the mobs in line.
     */
    private static void gaussBar(GuiGraphics g, Font font, int w, int h, int held, float partial, int tick) {
        int right = w / 2 - 10;
        int bw = 66;
        int bx = right - bw;
        int by = h / 2 + 14;
        int seg = (bw - 2 * 2) / 3;
        float c = (held + partial) / dev.type95addition.world.GaussShot.STAGE_TICKS;
        for (int i = 0; i < 3; i++) {
            int sx = bx + i * (seg + 2);
            g.fill(sx, by, sx + seg, by + 3, 0x90000000);
            float fill = i == 2 ? (c >= 2.0F ? 1.0F : 0.0F) : Math.min(1.0F, Math.max(0.0F, c - i));
            if (fill > 0.0F) {
                g.fill(sx, by, sx + (int)(seg * fill), by + 3, GAUSS_STAGES[i]);
            }
        }
        int stage = dev.type95addition.world.GaussShot.stage(held);
        Component label = stage == 3
            ? Component.translatable("hud.type95_addition.gauss_full")
            : Component.translatable("hud.type95_addition.gauss_charge", stage);
        int targets = dev.type95addition.client.fx.GaussFx.targets();
        if (targets > 0) {
            label = label.copy().append(" · ").append(Component.translatable("hud.type95_addition.gauss_targets", targets));
        }
        // (stage 1's deep blue is hard to read as text: the label starts at the ammo's own light blue)
        int colour = stage == 1 || stage == 3 && (tick / 4 & 1) == 0 ? GAUSS_COLOUR : GAUSS_STAGES[stage - 1];
        g.drawString(font, label, right - font.width(label), by + 6, colour, true);
    }

    /**
     * The scope: black round a circular lens (fading in as it comes up), a thin reticle with mil-dots, the charge as
     * an arc round the lens's edge in the formula's colour, and the magnification, range and stage under the centre.
     */
    private static void scope(GuiGraphics g, Minecraft mc, float partial, float raised) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        int cx = w / 2;
        int cy = h / 2;
        float r = h * (0.44F + 0.06F * (1.0F - raised));
        int black = (int)(255 * Math.min(1.0F, raised * 1.2F)) << 24;
        // the mask, row by row
        for (int y = 0; y < h; y++) {
            float dy = y + 0.5F - cy;
            if (Math.abs(dy) >= r) {
                g.fill(0, y, w, y + 1, black);
            } else {
                int half = (int)Math.sqrt(r * r - dy * dy);
                g.fill(0, y, cx - half, y + 1, black);
                g.fill(cx + half, y, w, y + 1, black);
            }
        }
        if (raised < 0.6F) {
            return;
        }
        Palette palette = ClientConfig.palette;
        int line = 0xE0101418;
        int glow = 0xFF000000 | palette.glow;
        int core = 0xFF000000 | palette.core;
        // the reticle: heavy posts from the edge, thin cross through the middle, mil-dots
        int gap = 10;
        int ri = (int)r;
        g.fill(cx - ri, cy - 1, cx - ri / 3, cy + 1, line);
        g.fill(cx + ri / 3, cy - 1, cx + ri, cy + 1, line);
        g.fill(cx - 1, cy + ri / 3, cx + 1, cy + ri, line);
        g.fill(cx - ri / 3, cy, cx - gap, cy + 1, line);
        g.fill(cx + gap, cy, cx + ri / 3, cy + 1, line);
        g.fill(cx, cy - ri, cx + 1, cy - gap, line);
        g.fill(cx, cy + gap, cx + 1, cy + ri / 3, line);
        for (int i = 1; i <= 4; i++) {
            int d = gap + i * (ri / 3 - gap) / 5;
            g.fill(cx + d - 1, cy - 1, cx + d + 1, cy + 2, line);
            g.fill(cx - d - 1, cy - 1, cx - d + 1, cy + 2, line);
            g.fill(cx - 1, cy + d - 1, cx + 2, cy + d + 1, line);
        }
        g.fill(cx, cy, cx + 1, cy + 1, core);
        // the charge round the lens's edge
        float c = ChargeClient.charge(partial);
        int dots = 180;
        for (int i = 0; i < dots; i++) {
            double a = -Math.PI / 2.0 + i * Math.PI * 2.0 / dots;
            int x = cx + (int)Math.round(Math.cos(a) * (r - 3));
            int y = cy + (int)Math.round(Math.sin(a) * (r - 3));
            boolean lit = i < c * dots;
            g.fill(x - 1, y - 1, x + 1, y + 1, lit ? (c >= 1.0F && (mc.player.tickCount / 3 & 1) == 0 ? core : glow) : 0x60FFFFFF);
        }
        // readouts under the centre
        Font font = mc.font;
        HitResult hit = mc.player.pick(ChargeClient.range, partial, false);
        String range = hit.getType() == HitResult.Type.MISS ? "---"
            : String.valueOf((int)Math.round(hit.getLocation().distanceTo(mc.player.getEyePosition(partial))));
        int stage = LightningTuning.stage(c);
        Component status = c >= 1.0F ? Component.translatable("hud.type95_addition.full")
            : Component.translatable("hud.type95_addition.charge", stage);
        int ty = cy + ri / 3 + 6;
        g.drawCenteredString(font, status, cx, Math.min(ty, cy + ri - 30), glow);
        g.drawString(font, Component.translatable("hud.type95_addition.scope_zoom", String.format(Locale.ROOT, "%.1f", Scope.magnification())),
            cx + ri / 3 + 8, cy + 4, glow);
        g.drawString(font, Component.translatable("hud.type95_addition.scope_range", range), cx + ri / 3 + 8, cy + 14, 0xFFB8C4D0);
        if (ClientConfig.marking && Marking.current() == null) {
            g.drawCenteredString(font, Component.translatable("hud.type95_addition.mark_hint"), cx, Math.min(ty + 11, cy + ri - 18), 0xFFB8C4D0);
        }
    }
}
