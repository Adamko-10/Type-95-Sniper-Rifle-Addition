package dev.type95addition.client.fx;

import cyou.rimuru.rimulib.client.render.PostPass;
import cyou.rimuru.rimulib.client.render.ScreenFx;
import dev.type95addition.AddonSounds;
import dev.type95addition.LightningTuning;
import dev.type95addition.client.ClientConfig;
import dev.type95addition.client.Palette;
import dev.type95addition.client.anim.RifleAnim;
import dev.type95addition.net.BoltPayload;
import dev.type95addition.net.StrikePayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * What a strike looks and sounds like (all client-side). Two layers:
 * <ul>
 *   <li>particles ({@link Arcs}): the beam, a coloured bolt from the sky over each vanilla one, sparks, debris and
 *       smoke, the shock ring and the arcs racing over the ground, chain lightning;</li>
 *   <li>the shader pass ({@code t95_strike}, through {@link StormFx}): the storm and its lit clouds, the bolts as
 *       columns of light that light up the land, the flash, the plasma dome, the shock wave, the crater glowing,
 *       and the screen effects (flash, bloom, colour split, zoom blur, ripple).</li>
 * </ul>
 * Thunder comes late from far strikes: sound travels about 17 blocks a tick.
 */
public final class StrikeFx {
    private static final int MAIN = 3;
    private static final double SOUND_SPEED = 17.0;
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final List<Strike> STRIKES = new ArrayList<>();
    /** Bolts not part of a strike (the charge's own little ones): {x, y, z, client tick, strength}. */
    private static final List<double[]> LOOSE = new ArrayList<>();
    private static final List<Object[]> DELAYED_SOUNDS = new ArrayList<>();
    private static float shake;
    private static int ticks;

    private StrikeFx() {
    }

    private abstract static class Effect {
        final int life;
        int age;

        Effect(int life) {
            this.life = life;
        }

        abstract void frame(ClientLevel level, int age);
    }

    /** One strike as the shader sees it. */
    private static final class Strike {
        final StrikePayload payload;
        final int fired;
        final double radius;
        final double shockRadius;
        final int shockTicks;
        final List<double[]> bolts = new ArrayList<>();
        /** Where the beam left the rifle, fixed at the shot (it must not follow the shooter afterwards). */
        final Vec3 muzzle;

        Strike(StrikePayload payload, int fired, Vec3 muzzle) {
            this.payload = payload;
            this.fired = fired;
            this.muzzle = muzzle;
            this.radius = LightningTuning.radius(payload.scale());
            this.shockRadius = LightningTuning.shockRadius(payload.scale());
            this.shockTicks = (int)Math.round((25.0 + 12.0 * Math.sqrt(payload.scale())) * 1.2);
        }

        /** Ticks since the main bolt (negative before it). */
        float age(float partial) {
            return ticks - this.fired - MAIN + partial;
        }

        /** When everything about it has faded. */
        boolean over() {
            float age = this.age(0.0F);
            return age > this.payload.storm() + 60 + this.shockTicks && age > (this.payload.crater() > 0.0F ? 600 : 0);
        }
    }

    public static void clear() {
        EFFECTS.clear();
        STRIKES.clear();
        LOOSE.clear();
        DELAYED_SOUNDS.clear();
        shake = 0.0F;
    }

    public static void tick(Minecraft mc) {
        ticks++;
        shake *= 0.88F;
        if (shake < 0.01F) {
            shake = 0.0F;
        }
        ClientLevel level = mc.level;
        if (level == null) {
            clear();
            return;
        }
        List<Effect> running = new ArrayList<>(EFFECTS);
        EFFECTS.clear();
        for (Effect effect : running) {
            effect.frame(level, effect.age);
            effect.age++;
            if (effect.age < effect.life) {
                EFFECTS.add(effect);
            }
        }
        STRIKES.removeIf(Strike::over);
        LOOSE.removeIf(b -> ticks - b[3] > 30);
        Iterator<Object[]> it = DELAYED_SOUNDS.iterator();
        while (it.hasNext()) {
            Object[] s = it.next();
            if ((int)s[0] <= ticks) {
                Vec3 at = (Vec3)s[1];
                level.playLocalSound(at.x, at.y, at.z, (SoundEvent)s[2], SoundSource.WEATHER, (float)s[3], (float)s[4], false);
                it.remove();
            }
        }
    }

    /** Plays a sound at {@code at} when its sound would reach the listener. */
    private static void thunder(Vec3 at, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        double d = mc.player == null ? 0.0 : mc.player.position().distanceTo(at);
        int delay = (int)(d / SOUND_SPEED);
        DELAYED_SOUNDS.add(new Object[] {ticks + delay, at, sound, volume, pitch});
    }

    /** A loose flash of light on the land (the charge's little bolts). */
    static void light(Vec3 at, float strength) {
        LOOSE.add(new double[] {at.x, at.y, at.z, ticks, strength});
    }

    // ---- The packets --------------------------------------------------------------------------------------------

    /** The shot: the beam from the gun to the target, the recoil, and the strike's state for the shader. */
    public static void onStrike(StrikePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Palette palette = ClientConfig.palette;
        float c = payload.charge();
        Vec3 target = payload.target();
        Entity shooter = payload.shooterId() < 0 ? null : level.getEntity(payload.shooterId());
        boolean own = shooter != null && shooter == mc.player;
        Vec3 muzzle = muzzle(payload, shooter, 1.0F);
        STRIKES.add(new Strike(payload, ticks, muzzle));
        if (shooter != null) {
            RifleAnim.fired(shooter.getId(), c);
        }
        float thickness = 0.12F + 0.3F * c;
        EFFECTS.add(new Effect(5) {
            @Override
            void frame(ClientLevel level, int age) {
                float fade = 1.0F - age / 5.0F;
                Arcs.arc(level, muzzle, target, 0.03F, palette.mix(0.3F), palette.core, thickness * (0.6F + 0.4F * fade), 2,
                    age == 0 ? 2 : 1);
                if (age == 0) {
                    for (int i = 0; i < 30; i++) {
                        Arcs.spark(level, muzzle, 0.1 + 0.15 * c, i % 2 == 0 ? palette.core : palette.glow, 0.06F, 7);
                    }
                    level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | palette.core), true, true,
                        muzzle.x, muzzle.y, muzzle.z, 0.0, 0.0, 0.0);
                }
            }
        });
        if (own && ClientConfig.shake) {
            shake = Math.max(shake, 0.5F + 0.8F * c);
        }
    }

    private static Vec3 muzzle(StrikePayload payload, Entity shooter, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (shooter instanceof Player player && player.isAlive()) {
            boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            return ChargeFx.gun(player, firstPerson, partial).muzzle();
        }
        return payload.muzzle();
    }

    /** One bolt landing. */
    public static void onBolt(BoltPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Palette palette = ClientConfig.palette;
        Vec3 at = payload.pos();
        double scale = Math.max(0.05, payload.size());
        boolean main = payload.main();
        Strike strike = nearest(at);
        double radius = strike == null ? 4.0 : strike.radius;
        if (strike != null) {
            strike.bolts.add(new double[] {at.x, at.y, at.z, ticks, main ? 1.0 : payload.kind() == BoltPayload.VOLLEY ? 0.75 : 0.6});
        } else {
            light(at, main ? 1.0F : 0.6F);
        }
        var rnd = Arcs.RANDOM;

        // The coloured bolt from the sky, round the vanilla one
        float thick = (float)Math.min(main ? 2.4 : 1.0, (main ? 0.35 : 0.2) + 0.3 * Math.sqrt(scale));
        double height = Math.min(260.0, (main ? 100.0 : 70.0) + 20.0 * scale);
        Vec3 sky = at.add((rnd.nextDouble() - 0.5) * 20.0, height, (rnd.nextDouble() - 0.5) * 20.0);
        EFFECTS.add(new Effect(main ? 8 : 4) {
            @Override
            void frame(ClientLevel level, int age) {
                if (age % 2 == 1 && age > 1) {
                    return;
                }
                Arcs.arc(level, sky, at, 0.08F, palette.mix(0.25F), palette.core, thick, 2, main ? 4 : 2);
            }
        });

        // Chain lightning: the bolt jumps sideways from the last one to its prey
        if (payload.kind() == BoltPayload.CHAIN) {
            Vec3 from = payload.from();
            EFFECTS.add(new Effect(5) {
                @Override
                void frame(ClientLevel level, int age) {
                    Arcs.arc(level, from, at.add(0.0, 1.0, 0.0), 0.12F, palette.glow, palette.core, 0.18F, 2, 2);
                }
            });
            ChargeFx.play(level, at, AddonSounds.ZAP, 1.5F, 0.8F + 0.3F * rnd.nextFloat());
        }

        // The hit: a flash, sparks, the ground thrown up
        level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | palette.core), true, true, at.x, at.y + 0.5, at.z,
            0.0, 0.0, 0.0);
        int sparks = main ? 80 : 18;
        for (int i = 0; i < sparks; i++) {
            Arcs.spark(level, at.add(0.0, 0.3, 0.0), 0.25 + 0.4 * rnd.nextDouble() * Math.min(4.0, scale + 0.5),
                i % 3 == 0 ? palette.core : palette.glow, main ? 0.14F : 0.09F, 10 + rnd.nextInt(10));
            if (i % 2 == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, true, true, at.x, at.y + 0.2, at.z,
                    rnd.nextGaussian() * 0.9, 0.3 + rnd.nextDouble(), rnd.nextGaussian() * 0.9);
            }
        }
        BlockState ground = level.getBlockState(BlockPos.containing(at).below());
        if (!ground.isAir()) {
            int debris = main ? 140 : 24;
            double spread = main ? Math.min(radius * 0.2, 14.0) : 2.0;
            for (int i = 0; i < debris; i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double d = rnd.nextDouble() * spread;
                double speed = (main ? 0.6 : 0.3) + rnd.nextDouble() * (main ? 1.2 : 0.4);
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, true,
                    at.x + Math.cos(a) * d, at.y + 0.2, at.z + Math.sin(a) * d,
                    Math.cos(a) * speed * 0.6, speed, Math.sin(a) * speed * 0.6);
            }
        }

        // The shock ring and the arcs racing out over the ground
        double ringReach = main ? Math.min(radius, 60.0) : 2.5 + 1.5 * scale;
        int ringLife = main ? 18 : 7;
        int groundArcs = main ? 10 + (int)Math.min(14.0, scale * 4.0) : 3;
        double[] angles = new double[groundArcs];
        for (int i = 0; i < groundArcs; i++) {
            angles[i] = rnd.nextDouble() * Math.PI * 2.0;
        }
        EFFECTS.add(new Effect(ringLife) {
            @Override
            void frame(ClientLevel level, int age) {
                double t = (age + 1) / (double)ringLife;
                double r = ringReach * (1.0 - (1.0 - t) * (1.0 - t));
                Arcs.ring(level, at.add(0.0, 0.25, 0.0), new Vec3(0.0, 1.0, 0.0), Math.max(0.4, r), 0.12F, age * 0.3,
                    palette.glow, palette.mix(0.6F), (float)Math.min(0.6, 0.1 + 0.05 * scale), 2);
                if (age < ringLife / 2 + 2) {
                    for (double angle : angles) {
                        double reach = ringReach * Math.min(1.0, (age + 1) / (ringLife * 0.5)) * 0.9;
                        Vec3 end = groundAt(level, at.x + Math.cos(angle) * reach, at.z + Math.sin(angle) * reach, at.y);
                        Arcs.arc(level, at.add(0.0, 0.15, 0.0), end, 0.18F, palette.glow, palette.core,
                            (float)Math.min(0.4, 0.07 + 0.04 * scale), 2, 1);
                    }
                }
            }
        });

        if (main) {
            // Smoke rising from the hit, sparks crackling over the ground for a few seconds
            double smokeR = Math.min(radius * 0.3, 30.0);
            EFFECTS.add(new Effect(120) {
                @Override
                void frame(ClientLevel level, int age) {
                    if (age < 60 && age % 3 == 0) {
                        for (int i = 0; i < 3; i++) {
                            double a = rnd.nextDouble() * Math.PI * 2.0;
                            double d = Math.sqrt(rnd.nextDouble()) * smokeR;
                            Vec3 p = groundAt(level, at.x + Math.cos(a) * d, at.z + Math.sin(a) * d, at.y);
                            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, p.x, p.y + 0.5, p.z,
                                0.0, 0.08 + 0.05 * rnd.nextDouble(), 0.0);
                        }
                    }
                    for (int e = 0; e < 3; e++) {
                        double a = rnd.nextDouble() * Math.PI * 2.0;
                        double d = Math.sqrt(rnd.nextDouble()) * Math.max(4.0, smokeR);
                        Vec3 p = groundAt(level, at.x + Math.cos(a) * d, at.z + Math.sin(a) * d, at.y);
                        ArcParticle.spawn(level, p.x, p.y + 0.3, p.z, rnd.nextGaussian() * 0.03, 0.12 + rnd.nextDouble() * 0.15,
                            rnd.nextGaussian() * 0.03, rnd.nextBoolean() ? palette.core : palette.glow, 0.1F + 0.08F * rnd.nextFloat(),
                            25 + rnd.nextInt(20), 0.9F);
                    }
                    int n = Math.max(1, (int)(8 * (1.0 - age / 120.0)));
                    for (int i = 0; i < n; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2.0;
                        double d = Math.sqrt(rnd.nextDouble()) * Math.min(radius * 0.5, 40.0);
                        Vec3 p = groundAt(level, at.x + Math.cos(a) * d, at.z + Math.sin(a) * d, at.y);
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, true, true, p.x, p.y + 0.1, p.z,
                            rnd.nextGaussian() * 0.2, 0.15 + rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.2);
                        if (rnd.nextFloat() < 0.25F) {
                            Vec3 q = p.add((rnd.nextDouble() - 0.5) * 3.0, 0.0, (rnd.nextDouble() - 0.5) * 3.0);
                            Arcs.arc(level, p.add(0.0, 0.1, 0.0), q.add(0.0, 0.1, 0.0), 0.3F, palette.glow, palette.core, 0.06F, 2, 0);
                        }
                    }
                }
            });
            // Sounds: the crack and roll of thunder (late, if far), the shock wave, the dome's hum
            double d = mc.player == null ? 0.0 : mc.player.position().distanceTo(at);
            boolean near = d < radius * 1.5 + 64.0;
            thunder(at, near ? AddonSounds.THUNDER_NEAR : AddonSounds.THUNDER_FAR, 6.0F + (float)scale * 4.0F, 0.85F + rnd.nextFloat() * 0.15F);
            thunder(at, AddonSounds.SHOCKWAVE, 4.0F + (float)scale * 2.0F, 0.9F);
            ChargeFx.play(level, at, AddonSounds.DOME, 3.0F, 1.0F);
            ChargeFx.play(level, at, SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.6F);
        } else {
            thunder(at, rnd.nextFloat() < 0.4F ? AddonSounds.THUNDER_NEAR : AddonSounds.ZAP, 2.0F + (float)scale, 0.9F + rnd.nextFloat() * 0.3F);
        }

        // Shake, by distance
        if (mc.player != null && ClientConfig.shake) {
            double d = mc.player.position().distanceTo(at);
            float close = (float)Mth.clamp(1.0 - d / (radius * 3.0 + 48.0), 0.0, 1.0);
            shake = Math.max(shake, close * (main ? (float)Math.min(3.0, 1.0 + 0.5 * scale) : 0.5F));
        }
    }

    private static Strike nearest(Vec3 at) {
        Strike best = null;
        double bestD = Double.MAX_VALUE;
        for (Strike strike : STRIKES) {
            double d = strike.payload.target().distanceToSqr(at);
            if (d < bestD && d < sqr(strike.radius * 3.0 + 64.0)) {
                bestD = d;
                best = strike;
            }
        }
        return best;
    }

    private static double sqr(double x) {
        return x * x;
    }

    private static Vec3 groundAt(ClientLevel level, double x, double z, double near) {
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        if (Math.abs(y - near) > 8.0) {
            y = near;
        }
        return new Vec3(x, y + 0.05, z);
    }

    // ---- The shader pass (StormFx) ---------------------------------------------------------------------------

    /** How bright a bolt still is, {@code age} seconds after it hit: the strike and two re-flashes. */
    private static double boltLight(double age, double strength) {
        if (age < 0.0) {
            return 0.0;
        }
        double v = Math.exp(-age * 5.0) + 0.7 * Math.exp(-sqr((age - 0.22) / 0.04)) + 0.45 * Math.exp(-sqr((age - 0.4) / 0.05));
        return Math.min(1.0, v) * strength;
    }

    static void passes(Vec3 camera, float partial, List<PostPass> passes, ScreenFx screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Palette palette = ClientConfig.palette;
        float[] glow = ChargeFx.rgb(palette.glow);
        float[] core = ChargeFx.rgb(palette.core);
        List<Strike> shown = new ArrayList<>(STRIKES);
        shown.sort(Comparator.comparingDouble(s -> s.payload.target().distanceToSqr(camera)));
        boolean anyLoose = !LOOSE.isEmpty();
        for (int n = 0; n < Math.min(3, shown.size()); n++) {
            Strike s = shown.get(n);
            StrikePayload p = s.payload;
            float ageTicks = s.age(partial);
            float age = ageTicks / 20.0F;
            float since = (ticks - s.fired + partial) / 20.0F;
            float stormEnd = (p.storm() + 20.0F) / 20.0F;
            float cover = (float)(Mth.clamp(since / 1.2, 0.0, 1.0) * Mth.clamp(1.0 - (age - stormEnd) / 5.0, 0.0, 1.0))
                * (0.45F + 0.55F * p.charge());
            float front = (float)(s.shockRadius * Mth.clamp(1.0 - sqr(1.0 - ageTicks / s.shockTicks), 0.0, 1.0));
            if (ageTicks <= 0.0F) {
                front = 0.0F;
            }
            float beam = Math.max(0.0F, 1.0F - since / 0.35F);
            Vec3 target = p.target().subtract(camera);
            Vec3 muzzle = s.muzzle.subtract(camera);
            float[] pts = new float[64];
            fillBolts(pts, s.bolts, n == 0 ? LOOSE : List.of(), camera, partial);
            float seed = (p.seed() & 0xFFFF) / 6553.5F;
            float crater = p.crater();
            float shock = front;
            // the formula's circles: in the sky as the bolt comes down, then burned into the ground
            float sky = (float)(Mth.clamp(since / 0.15, 0.0, 1.0) * (1.0 - Mth.clamp((since - 0.6) / 2.4, 0.0, 1.0)));
            float burnt = age < 0.0F ? 0.0F : (float)(Math.min(1.0, age * 6.0) * Math.exp(-age / 2.5));
            float skyRadius = (float)Math.min(s.radius * 0.5, 300.0) + 12.0F;
            passes.add(new PostPass("t95_strike", u -> {
                u.v3("Target", target);
                u.v3("Origin", muzzle);
                u.f4("P0", age, (float)s.radius, crater, p.charge());
                u.f4("P1", glow[0], glow[1], glow[2], cover);
                u.f4("P2", core[0], core[1], core[2], p.scale());
                u.f4("P3", seed, shock, (float)s.shockRadius, beam);
                u.f4("Sigil", since, skyRadius, sky, burnt);
                u.vec4s("Pts", pts);
            }));
            anyLoose = false;

            // The punch on the screen when the main bolt lands (by distance)
            if (age >= 0.0F && age < 3.0F) {
                double d = camera.distanceTo(p.target());
                float near = (float)Mth.clamp(1.0 - d / (s.radius * 4.0 + 96.0), 0.0, 1.0);
                float k = near * (0.5F + 0.5F * p.charge());
                if (ClientConfig.flash) {
                    screen.addFlash(palette.core, (float)(1.1 * Math.exp(-age * 7.0)) * k);
                }
                screen.aberration = Math.max(screen.aberration, (float)(1.2 * Math.exp(-age * 4.0)) * k);
                screen.zoomBlur = Math.max(screen.zoomBlur, (float)(0.7 * Math.exp(-age * 5.0)) * k);
                float[] at = StormFx.onScreen(p.target());
                if (at != null) {
                    screen.addImpact((float)Math.exp(-age * 1.6) * k, 0, at[0], at[1]);
                }
                screen.contrast = Math.max(screen.contrast, (float)(0.25 * Math.exp(-age * 1.5)) * k);
            }
            screen.bloom = Math.max(screen.bloom, 0.35F + (age >= 0.0F ? (float)(0.9 * Math.exp(-age * 0.8)) : 0.0F));
            screen.bloomRadius = Math.max(screen.bloomRadius, 1.6F);
            screen.synthetic = Math.max(screen.synthetic, 0.25F);
            screen.desaturate = Math.max(screen.desaturate, cover * 0.25F);
            screen.priority = Math.max(screen.priority, 1.0F);
        }
        if (anyLoose) {
            // only the charge's own little bolts: a light-only pass
            float[] pts = new float[64];
            fillBolts(pts, List.of(), LOOSE, camera, partial);
            passes.add(new PostPass("t95_strike", u -> {
                u.v3("Target", Vec3.ZERO.add(0.0, -10000.0, 0.0));
                u.v3("Origin", Vec3.ZERO);
                u.f4("P0", -100.0F, 0.0F, 0.0F, 0.0F);
                u.f4("P1", glow[0], glow[1], glow[2], 0.0F);
                u.f4("P2", core[0], core[1], core[2], 0.3F);
                u.f4("P3", 0.0F, 0.0F, 0.0F, 0.0F);
                u.f4("Sigil", 0.0F, 0.0F, 0.0F, 0.0F);
                u.vec4s("Pts", pts);
            }));
            screen.bloom = Math.max(screen.bloom, 0.4F);
        }
    }

    /** The 16 brightest bolts right now, camera-relative, into the shader's Pts array. */
    private static void fillBolts(float[] pts, List<double[]> bolts, List<double[]> loose, Vec3 camera, float partial) {
        List<double[]> lit = new ArrayList<>();
        for (List<double[]> list : List.of(bolts, loose)) {
            for (double[] b : list) {
                double age = (ticks - b[3] + partial) / 20.0;
                double light = boltLight(age, b[4]);
                if (light > 0.02) {
                    lit.add(new double[] {b[0], b[1], b[2], light});
                }
            }
        }
        lit.sort((a, b) -> Double.compare(b[3], a[3]));
        for (int i = 0; i < Math.min(16, lit.size()); i++) {
            double[] b = lit.get(i);
            pts[i * 4] = (float)(b[0] - camera.x);
            pts[i * 4 + 1] = (float)(b[1] - camera.y);
            pts[i * 4 + 2] = (float)(b[2] - camera.z);
            pts[i * 4 + 3] = (float)b[3];
        }
    }

    /** Degrees to turn the camera by (yaw, pitch), or null when still. */
    public static float[] shake(float partial) {
        if (shake <= 0.0F) {
            return null;
        }
        float t = ticks + partial;
        float a = shake * 0.9F;
        return new float[] {
            a * (Mth.sin(t * 2.1F) * 0.6F + Mth.sin(t * 5.3F) * 0.4F),
            a * (Mth.sin(t * 2.7F + 1.3F) * 0.6F + Mth.sin(t * 6.1F + 0.7F) * 0.4F)
        };
    }
}
