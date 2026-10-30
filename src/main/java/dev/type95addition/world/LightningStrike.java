package dev.type95addition.world;

import dev.type95addition.LightningTuning;
import dev.type95addition.ServerConfig;
import dev.type95addition.Type95Addition;
import dev.type95addition.net.AddonNetwork;
import dev.type95addition.net.BoltPayload;
import dev.type95addition.net.StrikePayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One Lightning Formula strike, run from the server tick. Its timeline (ticks after the shot):
 * <ul>
 *   <li>0: the beam leaves the muzzle (clients draw it and everything else from {@link StrikePayload});</li>
 *   <li>{@link #MAIN}: the main bolt: full damage over the whole radius, the crater (stage 3+) and the scorch scars
 *       start;</li>
 *   <li>then the volley: the rest of the first bolts, one every tick or two, spread over the radius;</li>
 *   <li>meanwhile the shock wave runs out from the radius to {@link LightningTuning#shockRadius} (knock-back and
 *       lighter damage), cutting the crater as it goes;</li>
 *   <li>then the storm: for a few seconds more bolts keep coming down, half of them hunting living things and jumping
 *       to them from the last bolt (chain lightning).</li>
 * </ul>
 */
public final class LightningStrike {
    public static final int MAIN = 3;
    private static final int MAX_AGE = 2400;
    private static final List<LightningStrike> ACTIVE = new ArrayList<>();
    private static final List<LightningStrike> STARTED = new ArrayList<>();
    private static boolean ticking;

    private final ServerLevel level;
    private final UUID shooterId;
    private ServerPlayer shooter;
    private final Vec3 muzzle;
    private final Vec3 target;
    private final float charge;
    private final int stage;
    private final LightningTuning tuning;
    private final double scale;
    private final double radius;
    private final double shockRadius;
    private final int shockTicks;
    private final float centreDamage;
    private final long seed;
    private final RandomSource random;
    private final List<Vec3> volley = new ArrayList<>();
    private final Set<Integer> shocked = new HashSet<>();
    private final int stormTicks;
    private final double craterRadius;
    private int nextBolt;
    private int stormStart = -1;
    private int age;
    private Vec3 lastBolt;
    private Scorch scorch;
    private Crater crater;

    LightningStrike(ServerLevel level, ServerPlayer shooter, Vec3 muzzle, Vec3 target, float charge, LightningTuning tuning) {
        this.level = level;
        this.shooter = shooter;
        this.shooterId = shooter.getUUID();
        this.muzzle = muzzle;
        this.target = target;
        this.lastBolt = target;
        this.charge = charge;
        this.stage = LightningTuning.stage(charge);
        this.tuning = tuning;
        this.scale = LightningTuning.scale(charge, tuning.power());
        this.radius = LightningTuning.radius(this.scale);
        this.shockRadius = LightningTuning.shockRadius(this.scale);
        this.shockTicks = (int)Math.round(25.0 + 12.0 * Math.sqrt(this.scale));
        this.centreDamage = tuning.erases() ? LightningTuning.INFINITE : LightningTuning.centreDamage(charge, tuning.damage());
        this.seed = level.getRandom().nextLong();
        this.random = RandomSource.create(this.seed);
        this.stormTicks = (int)Math.round(LightningTuning.stormSeconds(charge, this.scale) * 20.0);
        boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        this.craterRadius = tuning.blast() && griefing && this.stage >= 3
            ? LightningTuning.craterRadius(this.scale, ServerConfig.maxCraterRadius()) : 0.0;

        int count = LightningTuning.boltCount(charge, tuning.bolts(), this.scale);
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        double turn = this.random.nextDouble() * Math.PI * 2.0;
        double spread = Math.min(this.radius * 0.7, 40.0 + 25.0 * this.scale);
        for (int i = 1; i < count; i++) {
            double angle = turn + i * golden;
            double dist = spread * (0.15 + 0.85 * Math.sqrt(this.random.nextDouble()));
            this.volley.add(new Vec3(target.x + Math.cos(angle) * dist, target.y, target.z + Math.sin(angle) * dist));
        }
    }

    static void start(LightningStrike strike) {
        (ticking ? STARTED : ACTIVE).add(strike);
        StrikePayload payload = new StrikePayload(strike.shooter.getId(), strike.muzzle, strike.target, strike.charge,
            (float)strike.scale, (float)strike.craterRadius, strike.stormTicks, strike.seed);
        AddonNetwork.sendNear(strike.level, payload, strike.viewRange(), strike.target, strike.muzzle);
    }

    public static void tickAll() {
        ticking = true;
        Iterator<LightningStrike> it = ACTIVE.iterator();
        while (it.hasNext()) {
            LightningStrike strike = it.next();
            boolean done;
            try {
                done = strike.tick();
            } catch (RuntimeException e) {
                Type95Addition.LOGGER.error("A Lightning Formula strike failed", e);
                done = true;
            }
            if (done) {
                it.remove();
                if (strike.crater != null) {
                    strike.crater.hand();
                }
            }
        }
        ticking = false;
        ACTIVE.addAll(STARTED);
        STARTED.clear();
    }

    public static void clear() {
        for (LightningStrike strike : ACTIVE) {
            if (strike.crater != null) {
                strike.crater.stop();
            }
        }
        ACTIVE.clear();
        STARTED.clear();
    }

    private double viewRange() {
        return Math.max(256.0, Math.min(this.shockRadius, 2048.0));
    }

    /** @return done */
    private boolean tick() {
        this.shooter = this.level.getServer().getPlayerList().getPlayer(this.shooterId);
        if (this.shooter != null && this.shooter.level() != this.level) {
            this.shooter = null;
        }
        if (this.age == MAIN) {
            this.mainBolt();
        }
        if (this.age > MAIN) {
            int since = this.age - MAIN;
            if (this.nextBolt < this.volley.size() && (since % 2 == 0 || this.volley.size() > 12)) {
                this.bolt(this.volley.get(this.nextBolt++), BoltPayload.VOLLEY, null);
            } else if (this.nextBolt >= this.volley.size() && this.stormStart < 0) {
                this.stormStart = this.age;
            }
            if (since <= this.shockTicks && since % 2 == 0) {
                this.shock(since);
            }
            if (this.stormStart >= 0 && this.age - this.stormStart < this.stormTicks) {
                this.storm();
            }
        }
        double front = this.age <= MAIN ? 0.0 : this.radius + (this.shockRadius - this.radius) * this.shockProgress(this.age - MAIN);
        if (this.crater != null) {
            this.crater.tick(Math.max(this.craterRadius * Math.min(1.0, (this.age - MAIN) / 12.0), front * 0.3));
        }
        if (this.scorch != null && !this.scorch.done()) {
            this.scorch.tick();
        }
        this.age++;
        boolean stormDone = this.stormStart >= 0 && this.age - this.stormStart >= this.stormTicks;
        return this.age > MAX_AGE
            || this.age > MAIN + this.shockTicks && stormDone
                && (this.scorch == null || this.scorch.done()) && (this.crater == null || this.crater.done());
    }

    private double shockProgress(int since) {
        double t = Mth.clamp(since / (double)this.shockTicks, 0.0, 1.0);
        return 1.0 - (1.0 - t) * (1.0 - t);
    }

    private void mainBolt() {
        // A real vanilla bolt when fire is on (fire, charged creepers, witches, pigmen...), else a harmless one.
        this.spawnBolt(this.target, !this.tuning.fire());
        AddonNetwork.sendNear(this.level, new BoltPayload(this.target, (float)this.scale, BoltPayload.MAIN, this.target),
            this.viewRange(), this.target);
        this.hitArea(this.target, this.radius, this.centreDamage, 1.0 + 1.5 * this.charge);
        this.clearDrops(this.target, Math.min(this.radius, 96.0));
        boolean griefing = this.level.getGameRules().get(GameRules.MOB_GRIEFING);
        if (this.craterRadius > 0.0) {
            this.crater = new Crater(this.level, this.target, this.craterRadius, this.seed);
        }
        if (this.tuning.terrain() && griefing) {
            this.scorch = new Scorch(this.level, this.target, Math.max(this.radius * 0.5, this.craterRadius * 1.6), this.stage,
                this.tuning.fire(), this.seed, this.craterRadius);
        }
    }

    /** One more bolt: {@code from} is where a chain bolt jumped from. */
    private void bolt(Vec3 spot, int kind, Vec3 from) {
        Vec3 ground = kind == BoltPayload.CHAIN ? spot : this.surface(spot);
        if (ground == null) {
            return;
        }
        this.spawnBolt(ground, true);
        float size = (float)(this.scale * (kind == BoltPayload.VOLLEY ? 0.5 : 0.35));
        AddonNetwork.sendNear(this.level, new BoltPayload(ground, size, kind, from == null ? ground : from), this.viewRange(), ground);
        double r = 3.0 + 2.5 * Math.sqrt(this.scale);
        float damage = this.centreDamage == LightningTuning.INFINITE ? LightningTuning.INFINITE
            : this.centreDamage * (kind == BoltPayload.CHAIN ? 0.6F : 0.4F);
        this.hitArea(ground, r, damage, 0.5);
        if (this.tuning.fire() && this.random.nextFloat() < 0.5F) {
            BlockPos pos = BlockPos.containing(ground);
            if (this.level.getBlockState(pos).isAir()) {
                var fire = BaseFireBlock.getState(this.level, pos);
                if (fire.canSurvive(this.level, pos)) {
                    this.level.setBlockAndUpdate(pos, fire);
                }
            }
        }
        this.lastBolt = ground;
    }

    /** The storm after the volley: a bolt every few ticks, half of them hunting something alive. */
    private void storm() {
        double rate = Math.min(0.25, 0.08 + 0.03 * this.scale);
        if (this.random.nextDouble() > rate) {
            return;
        }
        if (this.random.nextBoolean()) {
            LivingEntity prey = this.prey();
            if (prey != null) {
                this.bolt(prey.position(), BoltPayload.CHAIN, this.lastBolt.add(0.0, 1.0, 0.0));
                return;
            }
        }
        double a = this.random.nextDouble() * Math.PI * 2.0;
        double d = Math.sqrt(this.random.nextDouble()) * Math.min(this.radius * 1.2, 60.0 + 40.0 * this.scale);
        this.bolt(new Vec3(this.target.x + Math.cos(a) * d, this.target.y, this.target.z + Math.sin(a) * d), BoltPayload.STORM, null);
    }

    /** Something alive the storm can reach (nearest to the last bolt first, within two radii of the target). */
    private LivingEntity prey() {
        double reach = Math.min(this.radius * 2.0, 96.0 + 64.0 * this.scale);
        AABB box = new AABB(this.target, this.target).inflate(reach, Math.min(reach, 96.0), reach);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity entity : this.level.getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            double d = entity.distanceToSqr(this.lastBolt) * (0.5 + this.random.nextDouble());
            if (d < bestDist) {
                bestDist = d;
                best = entity;
            }
        }
        return best;
    }

    /** The shock wave: one ring of it every other tick, from the radius out to the shock radius. */
    private void shock(int since) {
        double outer = this.radius + (this.shockRadius - this.radius) * this.shockProgress(since);
        DamageSource source = this.source();
        AABB box = new AABB(this.target, this.target).inflate(outer, Math.min(outer, 160.0), outer);
        for (LivingEntity entity : this.level.getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            double d = entity.position().distanceTo(this.target);
            if (d <= this.radius || d > outer || !this.shocked.add(entity.getId())) {
                continue;
            }
            double t = Mth.clamp((d - this.radius) / Math.max(1.0, this.shockRadius - this.radius), 0.0, 1.0);
            if (this.centreDamage == LightningTuning.INFINITE) {
                this.hurt(entity, source, (float)(120.0 * (1.0 - t)));
            } else {
                float amount = (float)(this.centreDamage * 0.35 * (1.0 - t) * (1.0 - t));
                if (amount >= 0.5F) {
                    this.hurt(entity, source, amount);
                }
            }
            Vec3 away = entity.position().subtract(this.target).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            double k = (0.6 + 1.6 * this.charge) * (1.0 - t);
            entity.push(away.x * k, 0.2 + 0.5 * k, away.z * k);
            entity.hurtMarked = true;
        }
    }

    private void spawnBolt(Vec3 at, boolean visualOnly) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(this.level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) {
            return;
        }
        bolt.snapTo(at.x, at.y, at.z);
        bolt.setVisualOnly(visualOnly);
        if (!visualOnly) {
            bolt.setCause(this.shooter);
        }
        this.level.addFreshEntity(bolt);
    }

    /** The ground at that spot's x/z (the target's height when the spot isn't loaded). Null if it's not loaded. */
    private Vec3 surface(Vec3 spot) {
        int x = Mth.floor(spot.x);
        int z = Mth.floor(spot.z);
        if (this.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)) == null) {
            return null;
        }
        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        // Under a roof or in a cave: stay near the target's height rather than landing on the roof far above
        double y = Math.abs(top - this.target.y) > this.radius + 8.0 ? this.target.y : top;
        return new Vec3(spot.x, y, spot.z);
    }

    private DamageSource source() {
        return new DamageSource(this.level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(Type95Addition.LIGHTNING_FORMULA),
            this.shooter);
    }

    private void hitArea(Vec3 centre, double r, float damage, double knock) {
        DamageSource source = this.source();
        AABB box = new AABB(centre, centre).inflate(r, Math.min(Math.max(r, 6.0), 160.0), r);
        for (LivingEntity entity : this.level.getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            double d = entity.position().distanceTo(centre);
            if (d > r) {
                continue;
            }
            double t = d / r;
            this.shocked.add(entity.getId());
            if (damage == LightningTuning.INFINITE) {
                this.hurt(entity, source, LightningTuning.INFINITE);
                continue;
            }
            float amount = (float)(damage * (1.0 - 0.7 * t * t));
            if (amount >= 0.5F) {
                this.hurt(entity, source, amount);
            }
            Vec3 away = entity.position().subtract(centre).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            double k = knock * (1.0 - t);
            entity.push(away.x * k, 0.25 + 0.5 * k, away.z * k);
            entity.hurtMarked = true;
        }
    }

    private void hurt(LivingEntity entity, DamageSource source, float amount) {
        if (amount == LightningTuning.INFINITE) {
            entity.hurtServer(this.level, source, Float.MAX_VALUE);
            if (entity.isAlive()) {
                entity.kill(this.level);
            }
        } else {
            entity.hurtServer(this.level, source, amount);
        }
    }

    /** Item drops near the centre are vaporised (like Type 95's blast), so a big strike doesn't leave a carpet of loot. */
    private void clearDrops(Vec3 centre, double r) {
        AABB box = new AABB(centre, centre).inflate(r);
        for (ItemEntity item : this.level.getEntitiesOfClass(ItemEntity.class, box, e -> e.position().distanceToSqr(centre) < r * r)) {
            item.discard();
        }
    }

    /** Like Type 95: never creative/spectator players (or what they ride), never armour stands; the shooter only by config. */
    private boolean canHit(Entity entity) {
        if (entity instanceof Player player) {
            if (player.getUUID().equals(this.shooterId) && !ServerConfig.hurtShooter()) {
                return false;
            }
            return player.isAlive() && !player.isCreative() && !player.isSpectator();
        }
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return false;
            }
        }
        return entity.isAlive() && !(entity instanceof ArmorStand);
    }
}
