package dev.type95addition.world;

import dev.type95addition.AddonSounds;
import dev.type95addition.Type95Addition;
import dev.type95addition.net.AddonNetwork;
import dev.type95addition.net.CrushPayload;
import dev.type95addition.net.HomingPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Homing Formula: one right click fires {@link #BULLETS} bullets that flower out of the muzzle and curve onto the
 * mobs and players nearest the crosshair. A hit one is held where it is (a flier is slammed down to the ground
 * first), pressed flat and dies, "crushed by a homing bullet". The bullets fly here and, from the same launch, on
 * every client ({@link #steer} is shared), so only the launch and the crushes are sent.
 */
public final class HomingShot {
    public static final int BULLETS = 10;
    public static final int COOLDOWN = 30;
    public static final int MAX_AGE = 100;
    /** The share of the press at which the body gives way and goes flat (it dies at the end of the press). */
    public static final float FLATTEN = 0.85F;
    private static final double RANGE = 160.0;
    private static final double CONE = Math.cos(Math.toRadians(35.0));
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private static final List<Bullet> FLYING = new ArrayList<>();
    private static final Map<LivingEntity, Crush> CRUSHING = new HashMap<>();

    private HomingShot() {
    }

    private static final class Bullet {
        final ServerLevel level;
        final UUID shooter;
        final LivingEntity target;
        Vec3 aim;
        Vec3 pos;
        Vec3 vel;
        int age;

        Bullet(ServerLevel level, UUID shooter, LivingEntity target, Vec3 aim, Vec3 pos, Vec3 vel) {
            this.level = level;
            this.shooter = shooter;
            this.target = target;
            this.aim = aim;
            this.pos = pos;
            this.vel = vel;
        }
    }

    private record Crush(ServerLevel level, UUID shooter, Vec3 hold, double groundY, int fall, int press, int[] age) {
    }

    public static void fire(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 muzzle = LightningCasting.muzzle(player);
        Vec3 point = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            .getLocation();
        List<LivingEntity> targets = targets(player, eye, look);
        int[] ids = new int[BULLETS];
        for (int i = 0; i < BULLETS; i++) {
            LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
            ids[i] = target == null ? -1 : target.getId();
            FLYING.add(new Bullet(level, player.getUUID(), target, target == null ? point : aim(target), muzzle, launch(look, i)));
        }
        AddonNetwork.sendNear(level, new HomingPayload(muzzle, look, point, ids), 256.0, muzzle);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, AddonSounds.FIRE, SoundSource.PLAYERS, 1.2F, 1.6F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0F, 0.6F);
        Type95Addition.LOGGER.info("Homing Formula fired by {} at {} targets", player.getName().getString(), targets.size());
    }

    /** The living things nearest the crosshair (within a 35 degree cone, in sight), nearest first. */
    private static List<LivingEntity> targets(ServerPlayer player, Vec3 eye, Vec3 look) {
        List<LivingEntity> found = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE), e ->
            e != player && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative()) && !e.isAlliedTo(player)
                && !CRUSHING.containsKey(e) && aim(e).subtract(eye).normalize().dot(look) > CONE && player.hasLineOfSight(e));
        found.sort(Comparator.comparingDouble(e -> -aim(e).subtract(eye).normalize().dot(look)));
        return found.size() > BULLETS ? found.subList(0, BULLETS) : found;
    }

    // ---- Shared with the client's bullets -------------------------------------------------------------------------

    /** Bullet {@code i}'s first velocity: the ten fan out in a ring round the line of fire. */
    public static Vec3 launch(Vec3 look, int i) {
        Vec3 right = look.cross(UP);
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(look);
        double a = i * Math.PI * 2.0 / BULLETS;
        return look.scale(0.5).add(right.scale(Math.cos(a) * 0.7)).add(up.scale(Math.sin(a) * 0.7)).normalize().scale(0.9);
    }

    /** The next velocity: faster and turning harder the longer it flies, so it always gets there. */
    public static Vec3 steer(Vec3 pos, Vec3 vel, Vec3 aim, int age) {
        double speed = Math.min(3.0, 0.9 + age * 0.12);
        double turn = Math.min(1.0, 0.06 + age * 0.03);
        return vel.lerp(aim.subtract(pos).normalize().scale(speed), turn).normalize().scale(speed);
    }

    /** The segment {@code from}-{@code to} passes within {@code radius} of {@code aim}. */
    public static boolean reaches(Vec3 from, Vec3 to, Vec3 aim, double radius) {
        Vec3 seg = to.subtract(from);
        double t = Mth.clamp(aim.subtract(from).dot(seg) / Math.max(1.0E-6, seg.lengthSqr()), 0.0, 1.0);
        return from.add(seg.scale(t)).distanceToSqr(aim) < radius * radius;
    }

    public static Vec3 aim(Entity e) {
        return e.getBoundingBox().getCenter();
    }

    public static double hitRadius(Entity e) {
        return Math.max(0.6, e.getBbWidth() * 0.5 + 0.3);
    }

    public static boolean boss(Entity e) {
        return e instanceof WitherBoss || e instanceof EnderDragon || e instanceof LivingEntity l && l.getMaxHealth() >= 150.0F;
    }

    // ---- Ticking ------------------------------------------------------------------------------------------------

    public static void tickAll() {
        Iterator<Bullet> bullets = FLYING.iterator();
        while (bullets.hasNext()) {
            Bullet b = bullets.next();
            boolean chasing = b.target != null && b.target.isAlive() && b.target.level() == b.level;
            if (chasing) {
                b.aim = aim(b.target);
            }
            b.vel = steer(b.pos, b.vel, b.aim, b.age);
            Vec3 next = b.pos.add(b.vel);
            if (reaches(b.pos, next, b.aim, chasing ? hitRadius(b.target) : 0.8)) {
                bullets.remove();
                hit(b, chasing ? b.target : nearest(b.level, b.aim));
            } else if (++b.age > MAX_AGE) {
                bullets.remove();
            } else {
                b.pos = next;
            }
        }

        Iterator<Map.Entry<LivingEntity, Crush>> crushes = CRUSHING.entrySet().iterator();
        while (crushes.hasNext()) {
            Map.Entry<LivingEntity, Crush> entry = crushes.next();
            LivingEntity e = entry.getKey();
            Crush c = entry.getValue();
            int age = ++c.age[0];
            if (!e.isAlive() || e.level() != c.level) {
                crushes.remove();
                continue;
            }
            double y = age < c.fall ? Mth.lerp(Math.pow(age / (double)c.fall, 3.0), c.hold.y, c.groundY) : c.groundY;
            hold(e, c.hold.x, y, c.hold.z);
            if (c.fall > 0 && age == c.fall) {
                c.level.playSound(null, e.getX(), y, e.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE,
                    boss(e) ? 4.0F : 1.2F, boss(e) ? 0.5F : 1.1F);
            }
            if (age == c.fall + (int)(c.press * FLATTEN)) {
                c.level.playSound(null, e.getX(), y, e.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, boss(e) ? 3.0F : 1.5F, 0.5F);
                c.level.playSound(null, e.getX(), y, e.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.5F, 0.6F);
            }
            if (age >= c.fall + c.press) {
                crushes.remove();
                kill(c, e);
            }
        }
    }

    /** A bullet that lost its target (or had none) crushes whatever stands where it bursts. */
    private static LivingEntity nearest(ServerLevel level, Vec3 at) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.5),
                e -> e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative()))
            .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(at))).orElse(null);
    }

    private static void hit(Bullet b, LivingEntity e) {
        b.level.playSound(null, b.aim.x, b.aim.y, b.aim.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.5F, 0.7F);
        if (e == null || CRUSHING.containsKey(e) || e.getUUID().equals(b.shooter)) {
            return;
        }
        boolean boss = boss(e);
        int ground = LightningCasting.groundBelow(b.level, e.position(), 320.0);
        double groundY = ground == Integer.MIN_VALUE ? e.getY() : Math.min(e.getY(), ground);
        int fall = e.getY() - groundY > 1.5 ? (boss ? 30 : 12) : 0;
        int press = boss ? 50 : 30;
        CRUSHING.put(e, new Crush(b.level, b.shooter, e.position(), groundY, fall, press, new int[1]));
        AddonNetwork.sendNear(b.level, new CrushPayload(e.getId(), fall, press, boss), 256.0, e.position());
    }

    /** Held still (mobs keep thinking, but every tick they're put back). */
    private static void hold(LivingEntity e, double x, double y, double z) {
        e.setDeltaMovement(Vec3.ZERO);
        if (e instanceof ServerPlayer player) {
            if (player.position().distanceToSqr(x, y, z) > 0.01) {
                player.teleportTo(x, y, z);
            }
            return;
        }
        e.setPos(x, y, z);
        if (e instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    private static void kill(Crush c, LivingEntity e) {
        ServerPlayer shooter = c.level.getServer().getPlayerList().getPlayer(c.shooter);
        DamageSource source = new DamageSource(c.level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
            .getOrThrow(Type95Addition.HOMING_FORMULA), shooter);
        e.invulnerableTime = 0;
        e.hurtServer(c.level, source, Float.MAX_VALUE);
        // (a dying dragon keeps 1 health through its death animation; anything else still standing goes anyway - not a player, the totem saves them)
        boolean dragonDying = e instanceof EnderDragon d && d.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING;
        if (e.isAlive() && !dragonDying && !(e instanceof Player)) {
            e.kill(c.level);
        }
    }

    public static void clear() {
        FLYING.clear();
        CRUSHING.clear();
    }
}
