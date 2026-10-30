package dev.type95addition.client.fx;

import cyou.rimuru.rimulib.client.render.PostPass;
import cyou.rimuru.rimulib.client.render.ScreenFx;
import dev.type95addition.LightningTuning;
import dev.type95addition.client.ChargeClient;
import dev.type95addition.client.ClientConfig;
import dev.type95addition.client.Marking;
import dev.type95addition.client.Palette;
import dev.type95addition.net.MarkPayload;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What the local player's mark looks like: the {@code t95_mark} pass (a circle on the ground, the strike's reach as a
 * ring that grows with the charge, a beacon), sparks circling down onto it and little bolts striking it, and a
 * marker on the HUD with the distance (and the mob's name).
 */
public final class MarkFx {
    private MarkFx() {
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        Vec3 at = Marking.position(1.0F);
        if (level == null || at == null || !ChargeClient.charging()) {
            return;
        }
        Palette palette = ClientConfig.palette;
        var rnd = Arcs.RANDOM;
        int age = Marking.age();
        float c = ChargeClient.charge(0.0F);
        // a ring of sparks turning round the mark, closing in
        for (int i = 0; i < 3; i++) {
            double a = age * 0.35 + i * Math.PI * 2.0 / 3.0;
            double r = 2.2 + 0.6 * Math.sin(age * 0.2 + i);
            Vec3 p = at.add(Math.cos(a) * r, 0.2 + 0.15 * i, Math.sin(a) * r);
            Vec3 v = at.subtract(p).scale(0.06).add(0.0, 0.03, 0.0);
            ArcParticle.spawn(level, p.x, p.y, p.z, v.x, v.y, v.z, i == 0 ? palette.core : palette.glow, 0.12F, 8, 0.9F);
        }
        // now and then a thin bolt comes down on it, more often as the charge grows
        if (rnd.nextFloat() < 0.04F + 0.12F * c) {
            Vec3 sky = at.add((rnd.nextDouble() - 0.5) * 6.0, 18.0 + 10.0 * rnd.nextDouble(), (rnd.nextDouble() - 0.5) * 6.0);
            Arcs.arc(level, sky, at.add(0.0, 0.1, 0.0), 0.12F, palette.glow, palette.core, 0.07F, 2, 1);
        }
    }

    static void passes(Vec3 camera, float partial, List<PostPass> passes, ScreenFx screen) {
        Vec3 at = Marking.position(partial);
        Marking.Mark mark = Marking.current();
        if (at == null || mark == null || !ChargeClient.charging()) {
            return;
        }
        Palette palette = ClientConfig.palette;
        float[] glow = ChargeFx.rgb(palette.glow);
        float[] core = ChargeFx.rgb(palette.core);
        float c = ChargeClient.charge(partial);
        float reach = (float)LightningTuning.radius(LightningTuning.scale(c, ClientConfig.power));
        float age = (Marking.age() + partial) / 20.0F;
        Vec3 rel = at.subtract(camera);
        float onMob = mark.kind() == MarkPayload.ENTITY ? 1.0F : 0.0F;
        passes.add(new PostPass("t95_mark", u -> {
            u.v3("Target", rel);
            u.f4("P0", age, 3.5F, reach, c);
            u.f4("P1", glow[0], glow[1], glow[2], onMob);
            u.f4("P2", core[0], core[1], core[2], 2.7F);
        }));
        screen.bloom = Math.max(screen.bloom, 0.2F);
    }

    /** The HUD marker: a diamond over the mark with the distance (and the mob's name). */
    public static void hud(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 at = Marking.position(partial);
        Marking.Mark mark = Marking.current();
        if (at == null || mark == null || mc.player == null || !ChargeClient.charging()) {
            return;
        }
        float[] screen = StormFx.onScreen(at.add(0.0, mark.kind() == MarkPayload.ENTITY ? 2.2 : 0.5, 0.0));
        if (screen == null) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        int x = Mth.clamp((int)(screen[0] * w), 8, w - 8);
        int y = Mth.clamp((int)((1.0F - screen[1]) * h), 8, h - 8);
        int colour = 0xFF000000 | ClientConfig.palette.glow;
        int core = 0xFF000000 | ClientConfig.palette.core;
        int s = 5 + (int)(2.0F * Mth.sin((Marking.age() + partial) * 0.4F));
        for (int i = -s; i <= s; i++) {
            int span = s - Math.abs(i);
            g.fill(x - span, y + i, x - span + 1, y + i + 1, colour);
            g.fill(x + span, y + i, x + span + 1, y + i + 1, colour);
        }
        g.fill(x - 1, y - 1, x + 1, y + 1, core);
        int metres = (int)Math.round(mc.player.getEyePosition(partial).distanceTo(at));
        Component label;
        Entity entity = mark.kind() == MarkPayload.ENTITY && mc.level != null ? mc.level.getEntity(mark.entityId()) : null;
        if (entity != null) {
            label = Component.translatable("hud.type95_addition.mark_entity", entity.getName(), metres);
        } else {
            label = Component.translatable("hud.type95_addition.mark", String.format(Locale.ROOT, "%d", metres));
        }
        int half = mc.font.width(label) / 2 + 2;
        g.drawCenteredString(mc.font, label, Mth.clamp(x, half, w - half), Math.min(y + s + 4, h - 10), colour);
    }
}
