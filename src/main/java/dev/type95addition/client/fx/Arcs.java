package dev.type95addition.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Lightning drawn with {@link ArcParticle}s: jagged arcs (with forks), jagged rings and loose sparks. */
public final class Arcs {
    public static final RandomSource RANDOM = RandomSource.create();
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private Arcs() {
    }

    /**
     * A jagged arc from {@code a} to {@code b}: a glow line with a thinner hot core in it.
     *
     * @param jag   how far it wanders sideways, as a share of its length (0.1 = tight, 0.3 = wild)
     * @param size  the glow's thickness in blocks
     * @param forks how many levels of side branches it may grow
     */
    public static void arc(ClientLevel level, Vec3 a, Vec3 b, float jag, int glow, int core, float size, int life, int forks) {
        double len = a.distanceTo(b);
        if (len < 1.0E-3 || level == null) {
            return;
        }
        Vec3 dir = b.subtract(a).scale(1.0 / len);
        Vec3 u = perpendicular(dir);
        Vec3 v = dir.cross(u);
        int n = Mth.clamp((int)(len / 0.22), 3, 64);
        double[] ou = bridge(n);
        double[] ov = bridge(n);
        double amp = len * jag;
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            pts[i] = a.add(dir.scale(len * i / n)).add(u.scale(ou[i] * amp)).add(v.scale(ov[i] * amp));
        }
        double spacing = Math.max(size * 0.28, len / 900.0);
        for (int i = 0; i < n; i++) {
            line(level, pts[i], pts[i + 1], spacing, glow, core, size, life);
        }
        if (forks > 0) {
            int count = 1 + RANDOM.nextInt(2);
            for (int f = 0; f < count; f++) {
                int at = 1 + RANDOM.nextInt(Math.max(1, n - 1));
                Vec3 side = u.scale(RANDOM.nextGaussian()).add(v.scale(RANDOM.nextGaussian())).normalize();
                double reach = Math.min(len * (0.08 + 0.1 * RANDOM.nextDouble()), 2.0 + len * 0.05);
                Vec3 to = pts[at].add(dir.scale(reach)).add(side.scale(reach * 0.8));
                arc(level, pts[at], to, jag * 1.2F, glow, core, size * 0.6F, life, forks - 1);
            }
        }
    }

    /** A jagged ring around {@code centre}, facing along {@code normal}, turned by {@code spin} radians. */
    public static void ring(ClientLevel level, Vec3 centre, Vec3 normal, double radius, float jag, double spin, int glow, int core,
                            float size, int life) {
        Vec3 u = perpendicular(normal);
        Vec3 v = normal.cross(u);
        int n = Mth.clamp((int)(radius * Math.PI * 2.0 / 0.25), 10, 96);
        Vec3 first = null;
        Vec3 prev = null;
        for (int i = 0; i <= n; i++) {
            Vec3 p;
            if (i == n) {
                p = first;
            } else {
                double t = spin + i * Math.PI * 2.0 / n;
                double r = radius * (1.0 + (RANDOM.nextDouble() - 0.5) * jag);
                p = centre.add(u.scale(Math.cos(t) * r)).add(v.scale(Math.sin(t) * r)).add(normal.scale((RANDOM.nextDouble() - 0.5) * jag * radius * 0.5));
            }
            if (prev != null) {
                line(level, prev, p, Math.max(size * 0.28, 0.04), glow, core, size, life);
            } else {
                first = p;
            }
            prev = p;
        }
    }

    /** A loose spark flying off at {@code speed} in a random direction. */
    public static void spark(ClientLevel level, Vec3 at, double speed, int rgb, float size, int life) {
        Vec3 d = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().scale(speed);
        ArcParticle.spawn(level, at.x, at.y, at.z, d.x, d.y, d.z, rgb, size, life, 1.0F);
    }

    private static void line(ClientLevel level, Vec3 a, Vec3 b, double spacing, int glow, int core, float size, int life) {
        double len = a.distanceTo(b);
        int dots = Math.max(1, (int)Math.ceil(len / spacing));
        for (int i = 0; i < dots; i++) {
            double t = i / (double)dots;
            double x = Mth.lerp(t, a.x, b.x);
            double y = Mth.lerp(t, a.y, b.y);
            double z = Mth.lerp(t, a.z, b.z);
            ArcParticle.spawn(level, x, y, z, 0.0, 0.0, 0.0, glow, size, life, 0.55F);
            if ((i & 1) == 0) {
                ArcParticle.spawn(level, x, y, z, 0.0, 0.0, 0.0, core, size * 0.45F, life, 1.0F);
            }
        }
    }

    /** A random walk pinned to 0 at both ends, about -1..1. */
    private static double[] bridge(int n) {
        double[] w = new double[n + 1];
        for (int i = 1; i <= n; i++) {
            w[i] = w[i - 1] + RANDOM.nextGaussian();
        }
        double max = 1.0E-6;
        for (int i = 0; i <= n; i++) {
            w[i] -= w[n] * i / n;
            max = Math.max(max, Math.abs(w[i]));
        }
        double norm = 1.0 / Math.max(max, Math.sqrt(n) * 0.5);
        for (int i = 0; i <= n; i++) {
            w[i] *= norm;
        }
        return w;
    }

    static Vec3 perpendicular(Vec3 dir) {
        Vec3 u = dir.cross(UP);
        if (u.lengthSqr() < 1.0E-6) {
            u = dir.cross(new Vec3(1.0, 0.0, 0.0));
        }
        return u.normalize();
    }
}
