package dev.type95addition.world;

import dev.type95addition.Type95Addition;
import dev.type95addition.net.AddonNetwork;
import dev.type95addition.net.GaussPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Gauss Formula: a magnetic slug, charged in three stages while right click is held. It hits at once along the
 * crosshair (no drop, no travel time), goes through 2 / 4 / 6 mobs by stage (a little weaker after each), punches
 * through blocks softer than stone (glass, leaves, wood, wool...; up to {@link #MAX_BLOCKS}, if the player and the
 * server allow it) and stops at stone or harder. Part of the damage ignores armour. {@link #trace} is shared with
 * the client, which draws the same path as an aim line while charging.
 */
public final class GaussShot {
    /** Ticks per stage: stage 2 after one, stage 3 (full) after two. */
    public static final int STAGE_TICKS = 20;
    public static final int FULL_TICKS = 2 * STAGE_TICKS;
    /** A shorter press fizzles (a stray click doesn't fire). */
    public static final int MIN_HOLD = 3;
    public static final int COOLDOWN = 20;
    public static final int MAX_BLOCKS = 12;
    /** The share of each hit that ignores armour. */
    private static final float ARMOUR_PIERCE = 0.4F;
    /** Each mob the slug goes through leaves it this much of its damage. */
    private static final float FALLOFF = 0.85F;

    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Type95Addition.id("gauss_formula"));
    /** The armour-piercing part (in the {@code bypasses_armor} tag). */
    public static final ResourceKey<DamageType> DAMAGE_PIERCE = ResourceKey.create(Registries.DAMAGE_TYPE, Type95Addition.id("gauss_formula_pierce"));

    private GaussShot() {
    }

    /** 0-1: how far the charge got. */
    public static float charge(int held) {
        return Mth.clamp(held / (float)FULL_TICKS, 0.0F, 1.0F);
    }

    /** 1-3. */
    public static int stage(int held) {
        return 1 + Math.min(2, held / STAGE_TICKS);
    }

    /** How many mobs the slug goes through. */
    public static int pierce(int stage) {
        return 2 * stage;
    }

    /** Damage to the first mob hit (10 for a tap, 40 at full charge). */
    public static float damage(float charge) {
        return 10.0F + 30.0F * charge;
    }

    /**
     * The slug's path: where it stops, the blocks it went through (and the points where it came out of each), and
     * the mobs it hit, nearest first.
     */
    public record Path(Vec3 end, List<BlockPos> blocks, List<Vec3> exits, List<Entity> hits) {
    }

    public static Path trace(Level level, Entity shooter, Vec3 from, Vec3 dir, double range, int pierce, boolean throughBlocks) {
        Vec3 to = from.add(dir.scale(range));
        List<BlockPos> blocks = new ArrayList<>();
        List<Vec3> exits = new ArrayList<>();
        Vec3 stop = BlockGetter.traverseBlocks(from, to, level, (l, pos) -> {
            BlockState state = l.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(l, pos);
            BlockHitResult in = shape.isEmpty() ? null : shape.clip(from, to, pos);
            if (in == null) {
                return null;
            }
            if (!throughBlocks || blocks.size() >= MAX_BLOCKS || !soft(l, pos, state)) {
                return in.getLocation();
            }
            blocks.add(pos.immutable());
            BlockHitResult out = shape.clip(to, from, pos);
            exits.add(out == null ? in.getLocation() : out.getLocation());
            return null;
        }, l -> null);
        Vec3 end = stop == null ? to : stop;

        double reach = from.distanceTo(end);
        record Hit(Entity entity, double distance) {
        }
        List<Hit> found = new ArrayList<>();
        for (Entity e : level.getEntities(shooter, new AABB(from, end).inflate(1.0),
            e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable())) {
            Optional<Vec3> at = e.getBoundingBox().inflate(e.getPickRadius()).clip(from, end);
            at.ifPresent(p -> found.add(new Hit(e, from.distanceTo(p))));
        }
        found.sort(Comparator.comparingDouble(Hit::distance));
        List<Entity> hits = new ArrayList<>();
        for (Hit h : found) {
            if (hits.size() >= pierce) {
                break;
            }
            hits.add(h.entity);
            if (hits.size() == pierce) {
                // the slug's spent: it stops in the last one
                reach = h.distance + h.entity.getBbWidth() * 0.5;
                end = from.add(dir.scale(reach));
            }
        }
        double max = reach;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (from.distanceTo(exits.get(i)) > max + 0.01) {
                blocks.remove(i);
                exits.remove(i);
            }
        }
        return new Path(end, blocks, exits, hits);
    }

    /** Softer than stone: the slug punches through it. */
    public static boolean soft(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) >= 0.0F
            && state.getBlock().getExplosionResistance() < Blocks.STONE.getExplosionResistance();
    }

    /** Glass and leaves are left with a clean hole (anything else soft is only gone through). */
    private static boolean shatters(BlockState state) {
        // shortcut: "glass" is anything that sounds like glass and is as weak as it (glass, panes, ice, glowstone)
        return state.is(BlockTags.LEAVES) || state.getSoundType() == SoundType.GLASS && state.getBlock().getExplosionResistance() < 1.0F;
    }

    public static void fire(ServerPlayer player, int held, boolean throughBlocks) {
        ServerLevel level = player.level();
        float charge = charge(held);
        int stage = stage(held);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 muzzle = LightningCasting.muzzle(player);
        double range = LightningCasting.loadedReach(level, eye, look, dev.type95addition.ServerConfig.range());
        Path path = trace(level, player, eye, look, range, pierce(stage), throughBlocks);

        float damage = damage(charge);
        List<Vec3> hitPoints = new ArrayList<>();
        for (Entity e : path.hits()) {
            hitPoints.add(e.getBoundingBox().getCenter());
            hurt(level, player, (LivingEntity)e, damage);
            damage *= FALLOFF;
        }
        if (level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            for (BlockPos pos : path.blocks()) {
                if (shatters(level.getBlockState(pos))) {
                    level.destroyBlock(pos, false, player);
                }
            }
        }
        Vec3 mid = muzzle.add(path.end()).scale(0.5);
        AddonNetwork.sendNear(level, new GaussPayload(player.getId(), muzzle, path.end(), charge, path.exits(), hitPoints), 192.0,
            muzzle, mid, path.end());
        LightningCasting.recoil(player, look, charge * 0.6F);
        Type95Addition.LOGGER.info("Gauss Formula fired by {}: stage {} charge {}, {} hit, {} blocks, stopped at {}",
            player.getName().getString(), stage, charge, path.hits().size(), path.blocks().size(), path.end());
    }

    private static void hurt(ServerLevel level, ServerPlayer player, LivingEntity e, float damage) {
        var types = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        e.invulnerableTime = 0;
        e.hurtServer(level, new DamageSource(types.getOrThrow(DAMAGE_PIERCE), player), damage * ARMOUR_PIERCE);
        if (e.isAlive()) {
            e.invulnerableTime = 0;
            e.hurtServer(level, new DamageSource(types.getOrThrow(DAMAGE), player), damage * (1.0F - ARMOUR_PIERCE));
        }
    }
}
