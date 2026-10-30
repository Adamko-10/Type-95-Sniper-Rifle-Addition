package dev.type95addition.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * One glowing dot of an arc: full-bright, coloured, a few ticks long, fading out. Arcs are chains of these
 * ({@link Arcs}). Spawned straight into the particle engine with {@link #spawn}; the registered provider is only
 * there to get the sprite (and for {@code /particle type95_addition:arc}).
 */
public final class ArcParticle extends TextureSheetParticle {
    private static final double CULL_RADIUS = 0.6;
    private static SpriteSet sprites;
    private static Vec3 cullEye;
    private final float startAlpha;
    private final float startSize;

    private ArcParticle(ClientLevel level, double x, double y, double z, SpriteSet set) {
        super(level, x, y, z);
        this.pickSprite(set);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.9F;
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.startAlpha = 1.0F;
        this.startSize = this.quadSize;
    }

    private ArcParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int rgb, float size,
                        int life, float alpha) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.88F;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.setColor((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
        this.quadSize = size;
        this.startSize = size;
        this.lifetime = Math.max(1, life);
        this.startAlpha = alpha;
        this.setAlpha(alpha);
    }

    /** Dots closer than {@link #CULL_RADIUS} to this point are not spawned (null: no limit). */
    public static void cull(Vec3 eye) {
        cullEye = eye;
    }

    /** Adds one dot (does nothing before the particle textures are loaded). */
    public static void spawn(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int rgb, float size,
                             int life, float alpha) {
        if (sprites == null || level == null) {
            return;
        }
        if (cullEye != null && cullEye.distanceToSqr(x + vx * 3.0, y + vy * 3.0, z + vz * 3.0) < CULL_RADIUS * CULL_RADIUS) {
            return;
        }
        Minecraft.getInstance().particleEngine.add(new ArcParticle(level, x, y, z, vx, vy, vz, rgb, size, life, alpha));
    }

    @Override
    public void tick() {
        super.tick();
        float t = this.age / (float)this.lifetime;
        this.setAlpha(this.startAlpha * (1.0F - t * t));
        this.quadSize = this.startSize * (1.0F - 0.35F * t);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet set;

        public Provider(SpriteSet set) {
            this.set = set;
            sprites = set;
        }

        @Override
        public net.minecraft.client.particle.Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                                   double vx, double vy, double vz) {
            ArcParticle particle = new ArcParticle(level, x, y, z, this.set);
            particle.setColor(0.6F, 0.85F, 1.0F);
            particle.lifetime = 4;
            return particle;
        }
    }
}
