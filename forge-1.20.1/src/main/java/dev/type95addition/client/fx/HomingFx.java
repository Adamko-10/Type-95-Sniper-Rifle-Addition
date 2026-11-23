package dev.type95addition.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import cyou.rimuru.rimulib.mc1201.client.render.PostPass;
import cyou.rimuru.rimulib.mc1201.client.render.ScreenFx;
import dev.type95addition.net.CrushPayload;
import dev.type95addition.net.HomingPayload;
import dev.type95addition.world.HomingShot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The Homing Formula's looks, in the Phantom Ruby's red and magenta: the bullets (a hot trail with little ruby cubes
 * spinning round it, flown here from the same launch as on the server), and each crush - dark streaks raining down
 * onto the one caught, a magenta outline, a ruby cube coming down on it and pressing it flat (fliers are slammed to
 * the ground first, bosses with a bigger cube and rings), and the {@code t95_homing} pass: the world turns red round
 * it and the picture glitches.
 */
public final class HomingFx {
    static final int CORE = 0xFFD8EC;
    static final int GLOW = 0xFF1F5E;
    static final int DEEP = 0x8A0030;
    static final int DARK = 0x24000A;
    /** The outline of a crushed one (the vanilla glowing outline, in this colour). */
    public static final int OUTLINE = 0xFF3D8B;
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private static final List<Bullet> BULLETS = new ArrayList<>();
    private static final Map<Integer, Crush> CRUSHES = new HashMap<>();
    private static int ticks;

    private HomingFx() {
    }

    private static final class Bullet {
        final int target;
        Vec3 aim;
        Vec3 pos;
        Vec3 vel;
        int age;

        Bullet(int target, Vec3 aim, Vec3 pos, Vec3 vel) {
            this.target = target;
            this.aim = aim;
            this.pos = pos;
            this.vel = vel;
        }
    }

    private static final class Crush {
        final int fall;
        final int press;
        final boolean boss;
        final float seed = Arcs.RANDOM.nextFloat() * 100.0F;
        int age;

        Crush(CrushPayload p) {
            this.fall = p.fall();
            this.press = Math.max(1, p.press());
            this.boss = p.boss();
        }

        int flatAt() {
            return this.fall + (int)(this.press * HomingShot.FLATTEN);
        }
    }

    public static void onHoming(HomingPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        int n = Math.min(HomingShot.BULLETS, p.targets().length);
        for (int i = 0; i < n; i++) {
            BULLETS.add(new Bullet(p.targets()[i], p.point(), p.muzzle(), HomingShot.launch(p.look(), i)));
        }
        if (level != null) {
            Arcs.ring(level, p.muzzle(), p.look(), 0.45, 0.2F, ticks * 0.3, GLOW, CORE, 0.07F, 6);
            cube(level, p.muzzle(), 0.28, ticks * 0.4, GLOW, CORE, 0.05F, 6);
            for (int i = 0; i < 14; i++) {
                Arcs.spark(level, p.muzzle(), 0.25, i % 3 == 0 ? CORE : GLOW, 0.06F, 8);
            }
        }
    }

    public static void onCrush(CrushPayload p) {
        CRUSHES.put(p.entity(), new Crush(p));
    }

    public static void clear() {
        BULLETS.clear();
        CRUSHES.clear();
    }

    // ---- The squash (applied by the entity render mixins) --------------------------------------------------------

    /** How flat an entity is drawn: 0 as it is, 1 flat as paper (pressed slowly, then all at once, and kept flat). */
    public static float squash(Entity e, float partial) {
        Crush c = CRUSHES.get(e.getId());
        if (c == null) {
            return 0.0F;
        }
        float t = c.age + partial - c.fall;
        if (t <= 0.0F) {
            return 0.0F;
        }
        float p = t / c.press;
        if (p < HomingShot.FLATTEN) {
            float q = p / HomingShot.FLATTEN;
            return Math.max(0.0F, 0.3F * q + 0.05F * Mth.sin(t * 1.9F) * q);
        }
        return Mth.lerp(Math.min(1.0F, (p - HomingShot.FLATTEN) * c.press / 2.0F), 0.3F, 1.0F);
    }

    public static boolean outlined(Entity e) {
        return CRUSHES.containsKey(e.getId());
    }

    /** Squashes the pose about the feet (shaking while it's pressed). */
    public static void squash(PoseStack pose, float s, float time) {
        if (s <= 0.0F) {
            return;
        }
        if (s < 0.99F) {
            pose.translate(Mth.sin(time * 3.1F) * 0.035F, 0.0F, Mth.cos(time * 2.7F) * 0.035F);
        }
        float xz = 1.0F + 0.65F * s;
        pose.scale(xz, Math.max(0.04F, 1.0F - 0.96F * s), xz);
    }

    // ---- Ticking ------------------------------------------------------------------------------------------------

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        ticks++;
        Iterator<Bullet> bullets = BULLETS.iterator();
        while (bullets.hasNext()) {
            Bullet b = bullets.next();
            Entity t = b.target < 0 ? null : level.getEntity(b.target);
            boolean chasing = t != null && t.isAlive();
            if (chasing) {
                b.aim = HomingShot.aim(t);
            }
            b.vel = HomingShot.steer(b.pos, b.vel, b.aim, b.age);
            Vec3 next = b.pos.add(b.vel);
            trail(level, b, next);
            if (HomingShot.reaches(b.pos, next, b.aim, chasing ? HomingShot.hitRadius(t) : 0.8)) {
                bullets.remove();
                impact(level, b.aim, b.vel);
            } else if (++b.age > HomingShot.MAX_AGE) {
                bullets.remove();
            } else {
                b.pos = next;
            }
        }

        Iterator<Map.Entry<Integer, Crush>> crushes = CRUSHES.entrySet().iterator();
        while (crushes.hasNext()) {
            Map.Entry<Integer, Crush> entry = crushes.next();
            Entity e = level.getEntity(entry.getKey());
            Crush c = entry.getValue();
            c.age++;
            // gone, or still standing well after it should have died (a totem): let it go
            if (e == null || e.isRemoved() || c.age > c.fall + c.press + 10 && e.isAlive() || c.age > 400) {
                crushes.remove();
            } else {
                crushTick(mc, level, e, c);
            }
        }
    }

    private static void trail(ClientLevel level, Bullet b, Vec3 next) {
        Arcs.line(level, b.pos, next, 0.22, GLOW, CORE, 0.15F, 5);
        if ((b.age + b.target) % 3 == 0) {
            cube(level, next, 0.2, b.age * 0.5, DEEP, GLOW, 0.035F, 4);
        }
        // a glitch: now and then a dark square slips off the trail
        if (Arcs.RANDOM.nextFloat() < 0.3F) {
            Vec3 side = Arcs.perpendicular(b.vel.normalize()).scale((Arcs.RANDOM.nextDouble() - 0.5) * 0.8);
            Vec3 at = b.pos.add(side);
            ArcParticle.spawn(level, at.x, at.y, at.z, 0.0, 0.0, 0.0, DARK, 0.12F, 3, 0.9F);
        }
    }

    private static void impact(ClientLevel level, Vec3 at, Vec3 vel) {
        Arcs.ring(level, at, vel.normalize(), 0.7, 0.25F, ticks * 0.3, GLOW, CORE, 0.07F, 5);
        for (int i = 0; i < 12; i++) {
            Arcs.spark(level, at, 0.3, i % 3 == 0 ? CORE : GLOW, 0.07F, 8);
        }
    }

    private static void crushTick(Minecraft mc, ClientLevel level, Entity e, Crush c) {
        var rnd = Arcs.RANDOM;
        double w = e.getBbWidth();
        double h = e.getBbHeight();
        Vec3 feet = e.position();
        float s = squash(e, 0.0F);
        double top = feet.y + h * (1.0 - 0.96 * s);
        boolean falling = c.age < c.fall;
        boolean flat = c.age >= c.flatAt();
        double near = mc.player == null ? 1.0 : Mth.clamp(1.0 - mc.player.distanceTo(e) / (c.boss ? 160.0 : 48.0), 0.0, 1.0);

        // the dark streaks raining down onto it (thicker and faster while it's slammed down)
        int streaks = flat ? 1 : c.boss ? 10 : 3;
        for (int i = 0; i < streaks; i++) {
            double x = feet.x + (rnd.nextDouble() - 0.5) * w * 1.4;
            double z = feet.z + (rnd.nextDouble() - 0.5) * w * 1.4;
            double y = top + 0.6 + rnd.nextDouble() * (c.boss ? 6.0 : 2.5);
            int rgb = rnd.nextInt(3) == 0 ? GLOW : DARK;
            double vy = falling ? -1.1 : -0.55;
            for (int k = 0; k < 6; k++) {
                ArcParticle.spawn(level, x, y + k * 0.18, z, 0.0, vy, 0.0, rgb, c.boss ? 0.07F : 0.035F, 5, 0.9F);
            }
        }
        // the ruby's ring round its feet
        if (ticks % 2 == 0) {
            Arcs.ring(level, feet.add(0.0, 0.05, 0.0), UP, w * 0.7 + 0.5, 0.06F, ticks * 0.15, GLOW, CORE, 0.05F, 3);
        }
        // the cube that presses it flat: it waits over its head, then comes down with it
        double half = Math.max(0.35, Math.max(w, h) * (c.boss ? 0.45 : 0.3));
        if (!flat) {
            Vec3 at = new Vec3(feet.x, top + half + (falling ? half : 0.05), feet.z);
            cube(level, at, half, ticks * (falling ? 0.35 : 0.12), DEEP, GLOW, c.boss ? 0.09F : 0.05F, 2);
        }
        if (c.boss && !flat) {
            // a boss is bound in rings closing in on it, and in chains of light down to the ground while it falls
            double r = w * (0.9 + 0.4 * Math.sin(ticks * 0.3));
            Arcs.ring(level, feet.add(0.0, h * 0.5, 0.0), UP, r, 0.1F, ticks * 0.2, GLOW, CORE, 0.1F, 2);
            Arcs.ring(level, feet.add(0.0, h * 0.5, 0.0), new Vec3(1.0, 0.0, 0.0), r * 0.9, 0.1F, -ticks * 0.2, DEEP, GLOW, 0.08F, 2);
            if (falling) {
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2.0 + ticks * 0.1;
                    Vec3 from = feet.add(Math.cos(a) * w * 0.5, h * 0.5, Math.sin(a) * w * 0.5);
                    Arcs.arc(level, from, from.add(0.0, -24.0, 0.0), 0.04F, GLOW, CORE, 0.08F, 2, 0);
                }
            }
        }

        if (c.fall > 0 && c.age == c.fall) {
            // it hits the ground
            double r = c.boss ? 10.0 : w + 2.0;
            Arcs.ring(level, feet.add(0.0, 0.1, 0.0), UP, r, 0.15F, 0.0, GLOW, CORE, c.boss ? 0.2F : 0.1F, 10);
            for (int i = 0; i < (c.boss ? 8 : 2); i++) {
                level.addParticle(c.boss ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
                    feet.x + (rnd.nextDouble() - 0.5) * w, feet.y + 0.5, feet.z + (rnd.nextDouble() - 0.5) * w, 0.0, 0.0, 0.0);
            }
            StrikeFx.addShake((float)(near * (c.boss ? 2.0 : 0.6)));
        }
        if (c.age == c.flatAt()) {
            // it gives way: the cube bursts and the ruby splashes out over the ground
            Arcs.ring(level, feet.add(0.0, 0.1, 0.0), UP, w * 1.2 + 1.0, 0.2F, 0.0, GLOW, CORE, c.boss ? 0.16F : 0.08F, 8);
            for (int i = 0; i < (c.boss ? 60 : 24); i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double v = 0.2 + rnd.nextDouble() * (c.boss ? 0.8 : 0.35);
                ArcParticle.spawn(level, feet.x, feet.y + 0.2, feet.z, Math.cos(a) * v, 0.05, Math.sin(a) * v,
                    i % 3 == 0 ? CORE : i % 3 == 1 ? GLOW : DARK, c.boss ? 0.12F : 0.07F, 12, 1.0F);
            }
            StrikeFx.addShake((float)(near * (c.boss ? 1.6 : 0.5)));
        }
    }

    /** A wireframe cube of dots, turned {@code spin} radians round the vertical. */
    static void cube(ClientLevel level, Vec3 c, double half, double spin, int glow, int core, float size, int life) {
        double cs = Math.cos(spin);
        double sn = Math.sin(spin);
        Vec3[] v = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? -half : half;
            double y = (i & 2) == 0 ? -half : half;
            double z = (i & 4) == 0 ? -half : half;
            v[i] = c.add(x * cs - z * sn, y, x * sn + z * cs);
        }
        double spacing = Math.max(size * 0.6, half / 6.0);
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit < 8; bit <<= 1) {
                if ((i & bit) == 0) {
                    Arcs.line(level, v[i], v[i | bit], spacing, glow, core, size, life);
                }
            }
        }
    }

    // ---- The screen ---------------------------------------------------------------------------------------------

    static void passes(Vec3 camera, float partial, List<PostPass> passes, ScreenFx screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || CRUSHES.isEmpty() && BULLETS.isEmpty()) {
            return;
        }
        float env = BULLETS.isEmpty() ? 0.0F : 0.2F;
        float kick = 0.0F;
        List<Map.Entry<Integer, Crush>> near = new ArrayList<>(CRUSHES.entrySet());
        near.removeIf(entry -> mc.level.getEntity(entry.getKey()) == null);
        near.sort(Comparator.comparingDouble(entry -> mc.level.getEntity(entry.getKey()).position().distanceToSqr(camera)));
        boolean first = true;
        for (Map.Entry<Integer, Crush> entry : near.subList(0, Math.min(3, near.size()))) {
            Entity e = mc.level.getEntity(entry.getKey());
            Crush c = entry.getValue();
            float age = c.age + partial;
            int end = c.fall + c.press;
            float fade = Math.min(1.0F, age / 4.0F) * Mth.clamp(1.0F - (age - end) / 15.0F, 0.0F, 1.0F);
            if (fade <= 0.0F) {
                continue;
            }
            float s = squash(e, partial);
            float slam = (c.fall > 0 && age >= c.fall ? (float)Math.exp(-(age - c.fall) / 3.0) : 0.0F)
                + (age >= c.flatAt() ? (float)Math.exp(-(age - c.flatAt()) / 3.0) : 0.0F);
            Vec3 rel = e.getPosition(partial).subtract(camera);
            float width = e.getBbWidth();
            float height = e.getBbHeight() * (1.0F - 0.96F * s);
            float boss = c.boss ? 1.0F : 0.0F;
            // (only the nearest turns the world red: the passes run one over the other)
            float tint = first ? 1.0F : 0.0F;
            first = false;
            passes.add(new PostPass("t95_homing", u -> {
                u.v3("Target", rel);
                u.f4("P0", age / 20.0F, width, height, fade);
                u.f4("P1", slam, boss, tint, c.seed);
            }));
            float close = fade * (float)Mth.clamp(1.0 - rel.length() / (c.boss ? 192.0 : 96.0), 0.0, 1.0);
            env = Math.max(env, close);
            kick = Math.max(kick, slam * close);
        }
        if (env > 0.0F) {
            screen.addVignette(0xD0002E, 0.55F * env);
            screen.aberration = Math.max(screen.aberration, 0.3F * env + 1.0F * kick);
            screen.grain = Math.max(screen.grain, 0.2F * env);
            screen.contrast = Math.max(screen.contrast, 0.15F * env);
            screen.bloom = Math.max(screen.bloom, 0.3F);
            screen.synthetic = Math.max(screen.synthetic, 0.15F);
        }
    }
}
