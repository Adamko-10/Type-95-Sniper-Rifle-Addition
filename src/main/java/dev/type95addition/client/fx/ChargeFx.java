package dev.type95addition.client.fx;

import cyou.rimuru.rimulib.client.render.PostPass;
import cyou.rimuru.rimulib.client.render.ScreenFx;
import dev.type95addition.AddonSounds;
import dev.type95addition.LightningTuning;
import dev.type95addition.client.anim.RifleAnim;
import dev.type95addition.client.ChargeClient;
import dev.type95addition.client.ClientConfig;
import dev.type95addition.client.Palette;
import dev.type95addition.net.ChargeSyncPayload;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The charge around the gun, for the local player and for everyone else seen charging. Each stage (a quarter of
 * the charge time) adds its own kind of lightning on top of the last:
 * <ol>
 *   <li><b>Static</b>: sparks fizz round the muzzle, tiny arcs jump along the barrel;</li>
 *   <li><b>Crawling arcs</b>: arcs crawl out from the barrel, sparks spiral in to the muzzle;</li>
 *   <li><b>Ball lightning</b>: three orbs circle the barrel and spit arcs at it, arcs start hitting the ground;</li>
 *   <li><b>Storm crown</b>: two counter-turning rings of lightning in front of the muzzle, the sky flickers.</li>
 * </ol>
 * Sounds: a hum that rises with the charge, a crack at each new stage, a thunder roll at full charge.
 */
public final class ChargeFx {
    /** Other players charging: entity id -> the client tick their charge began. */
    private static final Map<Integer, Integer> REMOTE = new HashMap<>();
    private static final Map<Integer, Integer> STAGES = new HashMap<>();
    private static final Map<Integer, ChargeSound> SOUNDS = new HashMap<>();
    /** Where each charge began: its storm gathers over that spot (it would slide along with the player otherwise). */
    private static final Map<Integer, Vec3> ANCHORS = new HashMap<>();
    private static int ticks;

    private ChargeFx() {
    }

    public static void sync(ChargeSyncPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && payload.entityId() == mc.player.getId()) {
            if (!payload.charging()) {
                ChargeClient.stoppedByServer();
            }
            return;
        }
        if (payload.charging()) {
            REMOTE.put(payload.entityId(), ticks - payload.heldTicks());
        } else {
            REMOTE.remove(payload.entityId());
        }
    }

    public static void clear() {
        REMOTE.clear();
        STAGES.clear();
        SOUNDS.values().forEach(ChargeSound::end);
        SOUNDS.clear();
    }

    /** Ticks this entity has been charging, or -1. */
    public static int held(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && entityId == mc.player.getId()) {
            return ChargeClient.charging() ? ChargeClient.held() : -1;
        }
        Integer start = REMOTE.get(entityId);
        return start == null ? -1 : ticks - start;
    }

    public static float charge(int held) {
        return LightningTuning.charge(held, ChargeClient.chargeTicks);
    }

    public static void tick(Minecraft mc) {
        ticks++;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        if (ChargeClient.charging()) {
            boolean firstPerson = mc.options.getCameraType().isFirstPerson();
            // In first person nothing may come right up to the eye (a dot there fills half the screen)
            ArcParticle.cull(firstPerson ? mc.player.getEyePosition() : null);
            draw(level, mc.player, ChargeClient.held(), firstPerson);
            ArcParticle.cull(null);
        } else {
            ended(mc.player.getId());
        }
        Iterator<Map.Entry<Integer, Integer>> it = REMOTE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player) || !player.isAlive()) {
                it.remove();
                ended(entry.getKey());
                continue;
            }
            if (player.distanceToSqr(mc.player) < 128.0 * 128.0) {
                draw(level, player, ticks - entry.getValue(), false);
            }
        }
    }

    private static void ended(int id) {
        STAGES.remove(id);
        ANCHORS.remove(id);
        for (int key : new int[] {id, -id - 1}) {
            ChargeSound sound = SOUNDS.remove(key);
            if (sound != null) {
                sound.end();
            }
        }
    }

    /** Where the gun is: its muzzle and its axis (the camera's when in first person). */
    record Gun(Vec3 muzzle, Vec3 stock, Vec3 forward, Vec3 right, Vec3 up, float k) {
        Vec3 along(double t) {
            return this.stock.add(this.muzzle.subtract(this.stock).scale(t));
        }
    }

    static Gun gun(Player player, boolean firstPerson, float partial) {
        Vec3 eye = player.getEyePosition(partial);
        Vec3 f = player.getViewVector(partial);
        Vec3 r = Arcs.perpendicular(f);
        Vec3 u = r.cross(f).normalize();
        if (firstPerson) {
            // Where the rifle is on screen: the hand is drawn with a fixed 70 degree field of view, the world (and
            // these particles) with the player's own, zoomed while charging; put the dots over the drawn barrel.
            // Offsets per block of depth, measured on Type 95's first-person pose (muzzle just below the crosshair).
            Minecraft mc = Minecraft.getInstance();
            double worldFov = mc.options.fov().get() * ChargeClient.zoom(partial);
            double s = Math.tan(Math.toRadians(worldFov) / 2.0) / Math.tan(Math.toRadians(35.0));
            // (raised towards the centre while aiming: RifleAnim / ItemInHandRendererMixin)
            float aim = RifleAnim.aim(player.getId(), partial);
            Vec3 muzzle = eye.add(f.scale(1.0)).add(r.scale((0.067 - 0.07 * aim) * s)).add(u.scale((-0.117 + 0.035 * aim) * s));
            Vec3 stock = eye.add(f.scale(0.6)).add(r.scale((0.373 - 0.07 * aim) * s)).add(u.scale((-0.315 + 0.035 * aim) * s));
            // and everything shrinks with the zoom, so the effects keep their size next to the (unzoomed) rifle
            return new Gun(muzzle, stock, f, r, u, (float)(0.45 * s));
        }
        Vec3 muzzle = eye.add(f.scale(1.25)).add(r.scale(0.3)).add(u.scale(-0.28));
        Vec3 stock = eye.add(f.scale(0.1)).add(r.scale(0.3)).add(u.scale(-0.4));
        return new Gun(muzzle, stock, f, r, u, 1.0F);
    }

    private static void draw(ClientLevel level, Player player, int held, boolean firstPerson) {
        float c = charge(held);
        int stage = LightningTuning.stage(c);
        boolean full = c >= 1.0F;
        Palette palette = ClientConfig.palette;
        int glow = palette.mix(0.1F + 0.5F * c);
        int core = palette.core;
        Gun gun = gun(player, firstPerson, 1.0F);
        float k = gun.k;
        var rnd = Arcs.RANDOM;

        sounds(level, player, stage, full);
        if (firstPerson && dev.type95addition.client.Scope.active()) {
            // looking through the scope: nothing in front of the lens, only the ground strikes round the player
            if (stage >= 3 && rnd.nextFloat() < (full ? 0.12F : 0.04F)) {
                skyBolt(level, player, glow, core);
            }
            return;
        }

        // 1. Static: sparks round the muzzle and tiny arcs along the barrel
        int sparks = 1 + stage;
        for (int i = 0; i < sparks; i++) {
            Vec3 at = gun.muzzle.add(randomIn(0.22 * k * (1.0 + 0.3 * stage)));
            Arcs.spark(level, at, 0.01 + 0.02 * c, rnd.nextBoolean() ? glow : core, 0.035F * k, 3 + rnd.nextInt(3));
        }
        if (rnd.nextFloat() < 0.35F + 0.1F * stage) {
            Vec3 a = gun.along(rnd.nextDouble()).add(randomIn(0.08 * k));
            Vec3 b = gun.along(rnd.nextDouble()).add(randomIn(0.12 * k));
            Arcs.arc(level, a, b, 0.35F, glow, core, 0.022F * k, 2, 0);
        }

        // 2. Crawling arcs out from the barrel, sparks spiralling in to the muzzle
        if (stage >= 2) {
            int arcs = stage >= 3 ? 2 : 1;
            for (int i = 0; i < arcs; i++) {
                Vec3 from = gun.along(0.2 + 0.8 * rnd.nextDouble());
                Vec3 to = from.add(randomDir().scale((0.45 + 0.5 * rnd.nextDouble()) * k * (0.8 + 0.2 * stage)));
                Arcs.arc(level, from, to, 0.3F, glow, core, 0.03F * k, 2, 1);
            }
            for (int i = 0; i < 2; i++) {
                double angle = rnd.nextDouble() * Math.PI * 2.0;
                Vec3 start = gun.muzzle.add(gun.right.scale(Math.cos(angle) * 1.1 * k)).add(gun.up.scale(Math.sin(angle) * 1.1 * k))
                    .add(gun.forward.scale(-0.3 * k));
                Vec3 v = gun.muzzle.subtract(start).scale(0.22);
                ArcParticle.spawn(level, start.x, start.y, start.z, v.x, v.y, v.z, glow, 0.04F * k, 5, 0.9F);
            }
        }

        // 3. Ball lightning round the barrel, arcs into the ground
        if (stage >= 3) {
            Vec3 centre = gun.muzzle.subtract(gun.forward.scale(0.25 * k));
            for (int i = 0; i < 3; i++) {
                double a = ticks * 0.28 + i * Math.PI * 2.0 / 3.0;
                double r = 0.6 * k * (1.0 + 0.1 * Math.sin(ticks * 0.5 + i));
                Vec3 orb = centre.add(gun.right.scale(Math.cos(a) * r)).add(gun.up.scale(Math.sin(a) * r));
                ArcParticle.spawn(level, orb.x, orb.y, orb.z, 0.0, 0.0, 0.0, glow, 0.11F * k, 2, 0.6F);
                ArcParticle.spawn(level, orb.x, orb.y, orb.z, 0.0, 0.0, 0.0, core, 0.06F * k, 2, 1.0F);
                Arcs.spark(level, orb, 0.015, glow, 0.03F * k, 4);
                if (rnd.nextFloat() < 0.3F) {
                    Arcs.arc(level, orb, gun.along(0.5 + 0.5 * rnd.nextDouble()), 0.3F, glow, core, 0.025F * k, 2, 0);
                }
            }
            if (rnd.nextFloat() < (stage >= 4 ? 0.3F : 0.14F)) {
                groundArc(level, player, gun, glow, core);
            }
            // the storm answering: little bolts start hitting the ground round the player
            if (rnd.nextFloat() < (full ? 0.12F : stage >= 4 ? 0.06F : 0.025F)) {
                skyBolt(level, player, glow, core);
            }
        }

        // 4. Storm crown: two rings of lightning turning against each other in front of the muzzle
        if (stage >= 4) {
            float pulse = full ? 1.0F + 0.12F * Mth.sin(ticks * 0.8F) : 1.0F;
            Vec3 ring = gun.muzzle.add(gun.forward.scale(0.35 * k));
            int ringCore = full ? core : glow;
            Arcs.ring(level, ring, gun.forward, 0.42 * k * pulse, 0.25F, ticks * 0.15, glow, ringCore, 0.028F * k, 2);
            if ((ticks & 1) == 0 || full) {
                Arcs.ring(level, ring.add(gun.forward.scale(0.2 * k)), gun.forward, 0.75 * k * pulse, 0.3F, -ticks * 0.11, glow, ringCore,
                    0.03F * k, 2);
            }
            if (rnd.nextFloat() < 0.6F) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                Vec3 onRing = ring.add(gun.right.scale(Math.cos(a) * 0.42 * k)).add(gun.up.scale(Math.sin(a) * 0.42 * k));
                Arcs.arc(level, onRing, gun.muzzle, 0.25F, glow, core, 0.022F * k, 2, 0);
            }
            if (full && rnd.nextFloat() < 0.05F) {
                level.setSkyFlashTime(2);
            }
            if (full && ticks % 10 == 0) {
                for (int i = 0; i < 14; i++) {
                    Arcs.spark(level, gun.muzzle, 0.12, i % 2 == 0 ? core : glow, 0.04F * k, 6);
                }
            }
        }
    }

    /** An arc from the gun down into the ground a few blocks round the player's feet. */
    private static void groundArc(ClientLevel level, Player player, Gun gun, int glow, int core) {
        var rnd = Arcs.RANDOM;
        double a = rnd.nextDouble() * Math.PI * 2.0;
        double d = 1.2 + 2.2 * rnd.nextDouble();
        double x = player.getX() + Math.cos(a) * d;
        double z = player.getZ() + Math.sin(a) * d;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        if (Math.abs(y - player.getY()) > 4.0) {
            y = player.getY();
        }
        Vec3 ground = new Vec3(x, y + 0.05, z);
        Arcs.arc(level, gun.muzzle, ground, 0.22F, glow, core, 0.04F * gun.k, 3, 2);
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, ground.x, ground.y + 0.1, ground.z,
                rnd.nextGaussian() * 0.3, 0.2 + rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.3);
        }
        play(level, ground, AddonSounds.ZAP, 0.35F, 1.1F + 0.3F * rnd.nextFloat());
    }

    /** A small bolt out of the sky onto the ground a few blocks from the player. */
    private static void skyBolt(ClientLevel level, Player player, int glow, int core) {
        var rnd = Arcs.RANDOM;
        double a = rnd.nextDouble() * Math.PI * 2.0;
        double d = 4.0 + 9.0 * rnd.nextDouble();
        double x = player.getX() + Math.cos(a) * d;
        double z = player.getZ() + Math.sin(a) * d;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        if (Math.abs(y - player.getY()) > 12.0) {
            y = player.getY();
        }
        Vec3 ground = new Vec3(x, y + 0.05, z);
        Vec3 sky = ground.add((rnd.nextDouble() - 0.5) * 8.0, 28.0 + 14.0 * rnd.nextDouble(), (rnd.nextDouble() - 0.5) * 8.0);
        Arcs.arc(level, sky, ground, 0.1F, glow, core, 0.12F, 3, 3);
        for (int i = 0; i < 10; i++) {
            Arcs.spark(level, ground.add(0.0, 0.2, 0.0), 0.25, i % 2 == 0 ? core : glow, 0.08F, 8);
        }
        StrikeFx.light(ground, 0.6F);
        play(level, ground, AddonSounds.ZAP, 1.0F, 0.7F + 0.3F * rnd.nextFloat());
        play(level, ground, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5F, 1.3F);
    }

    // ---- The shader pass (StormFx) ---------------------------------------------------------------------------

    /** One {@code t95_charge} pass per player charging nearby (the closest three), and the local player's screen effects. */
    static void passes(Vec3 camera, float partial, List<PostPass> passes, ScreenFx screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        List<Player> chargers = new ArrayList<>();
        if (ChargeClient.charging()) {
            chargers.add(mc.player);
        }
        for (int id : REMOTE.keySet()) {
            if (mc.level.getEntity(id) instanceof Player player && player.distanceToSqr(camera) < 160.0 * 160.0) {
                chargers.add(player);
            }
        }
        chargers.sort(Comparator.comparingDouble(p -> p.distanceToSqr(camera)));
        Palette palette = ClientConfig.palette;
        for (int n = 0; n < Math.min(3, chargers.size()); n++) {
            Player player = chargers.get(n);
            boolean own = player == mc.player;
            boolean firstPerson = own && mc.options.getCameraType().isFirstPerson();
            int heldNow = held(player.getId());
            float c = LightningTuning.charge(Math.max(0, heldNow), ChargeClient.chargeTicks);
            float secs = (heldNow + partial) / 20.0F;
            int stage = LightningTuning.stage(c);
            boolean full = c >= 1.0F;
            Gun gun = gun(player, firstPerson, partial);
            Vec3 muzzle = gun.muzzle().subtract(camera);
            Vec3 eye = ANCHORS.computeIfAbsent(player.getId(), k -> player.getEyePosition()).subtract(camera);
            float[] glow = rgb(palette.mix(0.15F + 0.4F * c));
            float[] core = rgb(palette.core);
            float dim = own ? Math.max(0.0F, c - 0.2F) * 0.45F : 0.0F;
            float clouds = own ? Mth.clamp((c - 0.45F) * 2.0F, 0.0F, 1.0F) : 0.0F;
            float orb = own && dev.type95addition.client.Scope.active() ? 0.0F : (firstPerson ? 0.05F : 0.12F) * (0.5F + gun.k());
            float seed = player.getId() * 0.618F % 7.0F;
            passes.add(new PostPass("t95_charge", u -> {
                u.v3("Origin", muzzle);
                u.v3("Caster", eye);
                u.f4("P0", c, stage, full ? 1.0F : 0.0F, secs);
                u.f4("P1", glow[0], glow[1], glow[2], dim);
                u.f4("P2", core[0], core[1], core[2], clouds);
                u.f4("P3", orb, firstPerson ? 1.0F : 0.0F, seed, 4.0F + 10.0F * c);
            }));
            if (own) {
                screen.bloom = Math.max(screen.bloom, 0.15F + 0.3F * c);
                screen.bloomRadius = Math.max(screen.bloomRadius, 1.0F + c);
                screen.synthetic = Math.max(screen.synthetic, 0.15F);
                screen.addVignette(palette.glow, 0.12F + 0.35F * c);
                if (full) {
                    screen.aberration = Math.max(screen.aberration, 0.12F + 0.08F * Mth.sin(secs * 9.0F));
                }
            }
        }
    }

    static float[] rgb(int rgb) {
        return new float[] {(rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F};
    }

    private static void sounds(ClientLevel level, Player player, int stage, boolean full) {
        int id = player.getId();
        ChargeSound hum = SOUNDS.get(id);
        if (hum == null || hum.isStopped()) {
            hum = new ChargeSound(player, AddonSounds.CHARGE_LOOP, false);
            SOUNDS.put(id, hum);
            Minecraft.getInstance().getSoundManager().play(hum);
        }
        int shown = full ? 5 : stage;
        Integer before = STAGES.put(id, shown);
        Vec3 at = player.getEyePosition();
        if (before == null) {
            play(level, at, AddonSounds.CHARGE_START, 0.9F, 1.0F);
        } else if (before != shown) {
            if (shown == 5) {
                play(level, at, AddonSounds.FULL_CHARGE, 1.4F, 1.0F);
                play(level, at, AddonSounds.THUNDER_FAR, 0.7F, 1.2F);
                ChargeSound roar = new ChargeSound(player, AddonSounds.FULL_LOOP, true);
                SOUNDS.put(-id - 1, roar);
                Minecraft.getInstance().getSoundManager().play(roar);
            } else {
                play(level, at, AddonSounds.STAGE_UP, 0.9F, 0.75F + 0.17F * shown);
            }
        }
        if (Arcs.RANDOM.nextFloat() < 0.035F * stage) {
            play(level, at, AddonSounds.ZAP, 0.18F + 0.05F * stage, 0.9F + 0.4F * Arcs.RANDOM.nextFloat());
        }
    }

    static void play(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static Vec3 randomIn(double r) {
        var rnd = Arcs.RANDOM;
        return new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).scale(2.0 * r);
    }

    private static Vec3 randomDir() {
        var rnd = Arcs.RANDOM;
        return new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
    }
}
