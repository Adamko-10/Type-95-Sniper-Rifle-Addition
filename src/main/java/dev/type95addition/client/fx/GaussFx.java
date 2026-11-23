package dev.type95addition.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import cyou.rimuru.rimulib.client.render.ScreenFx;
import dev.type95addition.AddonSounds;
import dev.type95addition.client.ChargeClient;
import dev.type95addition.client.ClientConfig;
import dev.type95addition.client.anim.RifleAnim;
import dev.type95addition.net.GaussPayload;
import dev.type95addition.world.GaussShot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Gauss Formula's looks and sounds. While charging: five coils round the barrel light up one after another,
 * blue in stage 1, bright blue in stage 2, white at full, over a rising capacitor whine; the shooter sees a faint
 * aim line along the slug's path ({@link GaussShot#trace}), rings where it will come out of blocks, and the mobs in
 * line outlined. The shot: a white line of light that hangs for about a second, a spiral of ionised air round it, a
 * shock cone at the muzzle, sparks where it leaves blocks and goes through mobs; a crack at the rifle, then the
 * boom, which reaches far listeners late (from the nearest point of the line, 17 blocks a tick).
 */
public final class GaussFx {
    /** The outline of a mob in the aim line. */
    public static final int OUTLINE = 0x9FE4FF;
    private static final int[] GLOW = {0x2A5BFF, 0x4CC2FF, 0xDDF3FF};
    private static final int[] CORE = {0x9CB8FF, 0xCFF1FF, 0xFFFFFF};
    private static final int COILS = 5;
    /** Where along the barrel (stock 0, muzzle 1) the first and last coil are. */
    private static final float COIL_FROM = 0.45F;
    private static final float COIL_TO = 0.95F;
    /** First person: Type 95's barrel in the hand's view space (see {@link ChargeFx#gun}), and the coils' radius round it. */
    private static final Vector3f STOCK_VIEW = new Vector3f(0.373F, -0.315F, -0.6F);
    private static final Vector3f MUZZLE_VIEW = new Vector3f(0.067F, -0.117F, -1.0F);
    private static final float COIL_RADIUS = 0.016F;
    /** Third person: Type 95's muzzle and barrel direction (towards the muzzle) in the held item's space. */
    private static final Vector3f HELD_MUZZLE = new Vector3f(-0.322F, 0.698F, 0.244F);
    private static final Vector3f HELD_AXIS = new Vector3f(-0.282F, 0.902F, 0.328F).normalize();

    /** Other players charging: entity id -> the client tick their charge began. */
    private static final Map<Integer, Integer> REMOTE = new HashMap<>();
    private static final Map<Integer, Integer> STAGES = new HashMap<>();
    private static final Map<Integer, ChargeSound> SOUNDS = new HashMap<>();
    private static final Set<Integer> TARGETED = new HashSet<>();
    private static int ticks;
    private static int firedAt = -100;
    private static float firedCharge;

    private GaussFx() {
    }

    public static void sync(int entityId, boolean charging, int heldTicks) {
        if (charging) {
            REMOTE.put(entityId, ticks - heldTicks);
        } else {
            REMOTE.remove(entityId);
        }
    }

    public static void clear() {
        REMOTE.clear();
        STAGES.clear();
        TARGETED.clear();
        SOUNDS.values().forEach(ChargeSound::end);
        SOUNDS.clear();
    }

    /** Ticks this player has been charging the Gauss Formula, or -1. */
    public static int held(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && entityId == mc.player.getId()) {
            return ChargeClient.gaussHeld();
        }
        Integer start = REMOTE.get(entityId);
        return start == null ? -1 : ticks - start;
    }

    /** In the line of the local player's charging shot (drawn with {@link #OUTLINE}). */
    public static boolean targeted(Entity e) {
        return TARGETED.contains(e.getId());
    }

    public static int targets() {
        return TARGETED.size();
    }

    public static void tick(Minecraft mc) {
        ticks++;
        TARGETED.clear();
        ClientLevel level = mc.level;
        Player self = mc.player;
        if (level == null || self == null) {
            return;
        }
        int own = ChargeClient.gaussHeld();
        if (own >= 0) {
            boolean firstPerson = mc.options.getCameraType().isFirstPerson();
            ArcParticle.cull(firstPerson ? self.getEyePosition() : null);
            draw(level, self, own, firstPerson);
            ArcParticle.cull(null);
            aimLine(level, self, own, firstPerson);
        } else {
            ended(self.getId());
        }
        Iterator<Map.Entry<Integer, Integer>> it = REMOTE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            if (!(level.getEntity(entry.getKey()) instanceof Player player) || !player.isAlive()) {
                it.remove();
                ended(entry.getKey());
            } else if (player.distanceToSqr(self) < 128.0 * 128.0) {
                draw(level, player, ticks - entry.getValue(), false);
            }
        }
    }

    private static void ended(int id) {
        STAGES.remove(id);
        ChargeSound sound = SOUNDS.remove(id);
        if (sound != null) {
            sound.end();
        }
    }

    // ---- The charge ---------------------------------------------------------------------------------------------

    /**
     * The sounds, and at full charge sparks off the muzzle in first person (the coils are drawn with the rifle:
     * {@link #handCoils}, {@link #heldCoils}; in third person the muzzle's place is only guessed, so no sparks).
     */
    private static void draw(ClientLevel level, Player player, int held, boolean firstPerson) {
        int stage = GaussShot.stage(held);
        sounds(level, player, stage);
        if (stage == 3 && firstPerson && !dev.type95addition.client.Scope.active()) {
            ChargeFx.Gun gun = ChargeFx.gun(player, true, 1.0F);
            Arcs.spark(level, gun.muzzle(), 0.03, Arcs.RANDOM.nextBoolean() ? CORE[2] : GLOW[1], 0.02F * gun.k(), 4);
        }
    }

    /** Which stage's colour coil {@code i} shows (they light one by one over the stage), or -1 while still dark. */
    static int lit(int held, int i) {
        int stage = GaussShot.stage(held);
        float within = stage == 3 ? 1.0F : held % GaussShot.STAGE_TICKS / (float)GaussShot.STAGE_TICKS;
        return (i + 1) / (float)COILS <= within + 1.0E-3F ? stage - 1 : stage - 2;
    }

    /** At full charge the coils throb. */
    static float pulse(int held, int i) {
        return GaussShot.stage(held) == 3 ? 1.0F + 0.25F * Mth.sin(ticks * 0.7F + i) : 1.0F;
    }

    /**
     * Third person (any player, seen by anyone): the coils round the rifle in the player's hand, in the held item's
     * space. Type 95 draws the rifle there with its own code, so the barrel was measured: from the muzzle
     * {@link #HELD_MUZZLE} back along {@link #HELD_AXIS} (fitted to the harness's stand-in, photographed from three
     * sides: see CLAUDE.md).
     */
    public static void heldCoils(int playerId, PoseStack pose, SubmitNodeCollector collector) {
        int held = held(playerId);
        if (held < 0) {
            return;
        }
        collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buf) ->
            coils(p, buf, held, HELD_MUZZLE, HELD_AXIS, 0.04F, 0.34F, 0.03F));
    }

    /** The coils along a barrel: {@code from}-{@code to} blocks back from the muzzle, round it at radius {@code r}. */
    private static void coils(PoseStack.Pose p, VertexConsumer buf, int held, Vector3f muzzle, Vector3f axis, float from, float to, float r) {
        Vector3f u = new Vector3f(axis).cross(Math.abs(axis.y) > 0.9F ? 1.0F : 0.0F, Math.abs(axis.y) > 0.9F ? 0.0F : 1.0F, 0.0F).normalize();
        Vector3f v = new Vector3f(axis).cross(u).normalize();
        for (int i = 0; i < COILS; i++) {
            int lit = lit(held, i);
            if (lit < 0) {
                continue;
            }
            // coil 0 is the one nearest the stock
            float back = to - (to - from) * i / (COILS - 1);
            Vector3f c = new Vector3f(axis).mul(-back).add(muzzle);
            float pulse = pulse(held, i);
            band(p, buf, c, axis, u, v, r, r * 0.17F * pulse, (lit == 2 ? 0xE0000000 : 0xC0000000) | CORE[lit]);
            band(p, buf, c, axis, u, v, r * 1.3F, r * 0.45F * pulse, 0x50000000 | GLOW[lit]);
        }
    }

    /**
     * First person: the coils as glowing bands round the drawn barrel, in the hand's own pass (the rifle there is
     * drawn over the world, so world particles on it would be hidden). Positions are in the hand's view space, on
     * the line the first-person barrel lies along (the one {@link ChargeFx#gun} measured, before the aiming offset,
     * which the pose already holds).
     */
    public static void handCoils(PoseStack pose, SubmitNodeCollector collector, int playerId) {
        int held = held(playerId);
        Minecraft mc = Minecraft.getInstance();
        if (held < 0 || !mc.options.getCameraType().isFirstPerson() || dev.type95addition.client.Scope.active()) {
            return;
        }
        Vector3f axis = new Vector3f(MUZZLE_VIEW).sub(STOCK_VIEW);
        float length = axis.length();
        axis.normalize();
        collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buf) ->
            coils(p, buf, held, MUZZLE_VIEW, axis, (1.0F - COIL_TO) * length, (1.0F - COIL_FROM) * length, COIL_RADIUS));
    }

    /** A short glowing cylinder round {@code axis} at {@code c}: radius {@code r}, {@code half} long each way. */
    private static void band(PoseStack.Pose p, VertexConsumer buf, Vector3f c, Vector3f axis, Vector3f u, Vector3f v, float r, float half,
                             int argb) {
        int n = 18;
        Vector3f back = new Vector3f(axis).mul(-half);
        Vector3f front = new Vector3f(axis).mul(half);
        for (int j = 0; j < n; j++) {
            double a0 = j * Math.PI * 2.0 / n;
            double a1 = (j + 1) * Math.PI * 2.0 / n;
            Vector3f p0 = new Vector3f(u).mul((float)Math.cos(a0) * r).add(new Vector3f(v).mul((float)Math.sin(a0) * r)).add(c);
            Vector3f p1 = new Vector3f(u).mul((float)Math.cos(a1) * r).add(new Vector3f(v).mul((float)Math.sin(a1) * r)).add(c);
            // both windings: seen from either side
            quad(p, buf, new Vector3f(p0).add(back), new Vector3f(p1).add(back), new Vector3f(p1).add(front), new Vector3f(p0).add(front), argb);
            quad(p, buf, new Vector3f(p0).add(front), new Vector3f(p1).add(front), new Vector3f(p1).add(back), new Vector3f(p0).add(back), argb);
        }
    }

    private static void quad(PoseStack.Pose p, VertexConsumer buf, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int argb) {
        buf.addVertex(p, a).setColor(argb);
        buf.addVertex(p, b).setColor(argb);
        buf.addVertex(p, c).setColor(argb);
        buf.addVertex(p, d).setColor(argb);
    }

    private static void sounds(ClientLevel level, Player player, int stage) {
        int id = player.getId();
        ChargeSound whine = SOUNDS.get(id);
        if (whine == null || whine.isStopped()) {
            whine = new ChargeSound(player, AddonSounds.GAUSS_WHINE, false, true);
            SOUNDS.put(id, whine);
            Minecraft.getInstance().getSoundManager().play(whine);
        }
        Integer before = STAGES.put(id, stage);
        if (before == null || before != stage) {
            ChargeFx.play(level, player.getEyePosition(), AddonSounds.GAUSS_STAGE, 0.6F + 0.2F * stage, 0.8F + 0.2F * stage);
        }
    }

    /** The shooter's faint line along the slug's path, with rings where it leaves blocks and the mobs it hits outlined. */
    private static void aimLine(ClientLevel level, Player player, int held, boolean firstPerson) {
        int stage = GaussShot.stage(held);
        GaussShot.Path path = GaussShot.trace(level, player, player.getEyePosition(), player.getLookAngle(), ChargeClient.range,
            GaussShot.pierce(stage), ClientConfig.gaussBlocks && ChargeClient.serverGaussBlocks);
        path.hits().forEach(e -> TARGETED.add(e.getId()));
        if ((ticks & 1) != 0 || firstPerson && dev.type95addition.client.Scope.active()) {
            return;
        }
        int glow = GLOW[stage - 1];
        Vec3 from = ChargeFx.gun(player, firstPerson, 1.0F).muzzle();
        Vec3 to = path.end();
        double len = from.distanceTo(to);
        Vec3 dir = to.subtract(from).normalize();
        double step = Math.max(0.5, len / 300.0);
        for (double d = 1.5; d < len; d += step) {
            Vec3 p = from.add(dir.scale(d));
            ArcParticle.spawn(level, p.x, p.y, p.z, 0.0, 0.0, 0.0, glow, (float)(0.02 + 0.002 * d), 3, 0.45F);
        }
        Vec3 look = player.getLookAngle();
        for (Vec3 exit : path.exits()) {
            double d = exit.distanceTo(from);
            Arcs.ring(level, exit, look, 0.22 + 0.012 * d, 0.0F, ticks * 0.1, glow, CORE[stage - 1], (float)(0.02 + 0.0015 * d), 3);
        }
    }

    // ---- The shot -----------------------------------------------------------------------------------------------

    public static void onShot(GaussPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float c = p.charge();
        Entity shooter = level.getEntity(p.shooterId());
        boolean own = shooter != null && shooter == mc.player;
        boolean firstPerson = own && mc.options.getCameraType().isFirstPerson();
        ChargeFx.Gun gun = shooter instanceof Player player && player.isAlive() ? ChargeFx.gun(player, firstPerson, 1.0F) : null;
        Vec3 muzzle = gun == null ? p.muzzle() : gun.muzzle();
        Vec3 end = p.end();
        double len = muzzle.distanceTo(end);
        if (len < 0.1) {
            return;
        }
        Vec3 dir = end.subtract(muzzle).scale(1.0 / len);
        float k = gun == null ? 1.0F : gun.k();
        var rnd = Arcs.RANDOM;

        // the line of light: a white core in a blue-white glow, and a faint wide halo, fading over a second
        float size = 0.08F + 0.1F * c;
        Arcs.line(level, muzzle, end, Math.max(size * 0.4, len / 1800.0), GLOW[2], CORE[2], size, 22);
        for (double d = 0.0; d < len; d += Math.max(0.3, len / 900.0)) {
            Vec3 at = muzzle.add(dir.scale(d));
            ArcParticle.spawn(level, at.x, at.y, at.z, 0.0, 0.0, 0.0, GLOW[1], 0.22F + 0.18F * c, 16, 0.2F);
        }
        // the spiral of ionised air round it, slowly spreading
        Vec3 u = Arcs.perpendicular(dir);
        Vec3 v = dir.cross(u);
        double spiral = Math.min(len, 64.0);
        double radius = 0.18 + 0.14 * c;
        for (double d = 0.4; d < spiral; d += 0.09) {
            double a = d * Math.PI * 2.0 * 0.9;
            Vec3 out = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            Vec3 at = muzzle.add(dir.scale(d)).add(out.scale(radius * Math.min(1.0, d / 1.5)));
            float fade = (float)(1.0 - d / spiral);
            ArcParticle.spawn(level, at.x, at.y, at.z, out.x * 0.012, out.y * 0.012, out.z * 0.012, GLOW[1], 0.03F,
                18 + rnd.nextInt(14), 0.55F * (0.4F + 0.6F * fade));
        }
        // the shock cone at the muzzle
        for (int j = 1; j <= 5; j++) {
            Vec3 centre = muzzle.add(dir.scale(0.22 * j * k));
            double r = 0.07 * j * (1.0 + c) * k;
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI * 2.0 / 20.0;
                Vec3 out = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                Vec3 at = centre.add(out.scale(r));
                Vec3 vel = out.scale(0.05 * (1.0 + c)).add(dir.scale(0.06));
                ArcParticle.spawn(level, at.x, at.y, at.z, vel.x, vel.y, vel.z, CORE[2], 0.03F * k, 6 + j, 0.7F);
            }
        }
        ArcParticle.spawn(level, muzzle.x, muzzle.y, muzzle.z, 0.0, 0.0, 0.0, CORE[2], 0.3F * k * (1.0F + c), 2, 0.9F);
        // sparks where it comes out of blocks and goes through mobs, and where it stops
        for (Vec3 exit : p.exits()) {
            burst(level, exit, dir, 10, 0.18);
            BlockState state = level.getBlockState(BlockPos.containing(exit.subtract(dir.scale(0.05))));
            if (!state.isAir()) {
                for (int i = 0; i < 6; i++) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), exit.x, exit.y, exit.z,
                        dir.x * 0.3 + rnd.nextGaussian() * 0.1, dir.y * 0.3 + rnd.nextGaussian() * 0.1, dir.z * 0.3 + rnd.nextGaussian() * 0.1);
                }
            }
        }
        for (Vec3 hit : p.hits()) {
            burst(level, hit, dir, 14, 0.25);
            for (int i = 0; i < 8; i++) {
                level.addParticle(ParticleTypes.CRIT, hit.x, hit.y, hit.z, dir.x * 0.6 + rnd.nextGaussian() * 0.2,
                    dir.y * 0.6 + rnd.nextGaussian() * 0.2, dir.z * 0.6 + rnd.nextGaussian() * 0.2);
            }
        }
        burst(level, end, dir.scale(-1.0), 16, 0.2);
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, rnd.nextGaussian() * 0.02, 0.03, rnd.nextGaussian() * 0.02);
        }

        // the crack at the rifle, then the boom (from the nearest point of the line, late for far listeners)
        level.playLocalSound(muzzle.x, muzzle.y, muzzle.z, AddonSounds.GAUSS_CRACK, SoundSource.PLAYERS, 2.0F + c, 0.95F + 0.15F * c, false);
        Vec3 ear = mc.player == null ? muzzle : mc.player.getEyePosition();
        Vec3 nearest = muzzle.add(dir.scale(Mth.clamp(ear.subtract(muzzle).dot(dir), 0.0, len)));
        StrikeFx.later(nearest, AddonSounds.GAUSS_BOOM, SoundSource.PLAYERS, 4.0F + 4.0F * c, 1.05F - 0.15F * c);

        if (shooter != null) {
            RifleAnim.fired(shooter.getId(), 0.3F + 0.5F * c);
        }
        if (own) {
            StrikeFx.addShake(0.25F + 0.45F * c);
            firedAt = ticks;
            firedCharge = c;
        }
    }

    private static void burst(ClientLevel level, Vec3 at, Vec3 dir, int n, double speed) {
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(Arcs.RANDOM.nextGaussian(), Arcs.RANDOM.nextGaussian(), Arcs.RANDOM.nextGaussian()).normalize()
                .add(dir.scale(1.2)).normalize().scale(speed * (0.4 + Arcs.RANDOM.nextDouble()));
            ArcParticle.spawn(level, at.x, at.y, at.z, d.x, d.y, d.z, i % 3 == 0 ? CORE[2] : GLOW[1], 0.035F, 6 + Arcs.RANDOM.nextInt(6), 1.0F);
        }
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, dir.x * 0.4, dir.y * 0.4, dir.z * 0.4);
    }

    // ---- The screen ---------------------------------------------------------------------------------------------

    /** A blue edge while charging and a short bloom and colour split when the local player fires. */
    static void passes(Vec3 camera, float partial, ScreenFx screen) {
        int held = ChargeClient.gaussHeld();
        if (held >= 0) {
            screen.addVignette(GLOW[GaussShot.stage(held) - 1], 0.06F + 0.12F * GaussShot.charge(held));
        }
        float age = ticks - firedAt + partial;
        if (age < 6.0F) {
            float f = 1.0F - age / 6.0F;
            screen.bloom = Math.max(screen.bloom, 0.45F * f);
            screen.synthetic = Math.max(screen.synthetic, 0.15F);
            screen.aberration = Math.max(screen.aberration, (0.1F + 0.3F * firedCharge) * f);
        }
    }
}
