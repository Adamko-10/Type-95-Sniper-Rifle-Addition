package dev.type95addition.world;

import dev.type95addition.AddonSounds;
import dev.type95addition.LightningTuning;
import dev.type95addition.ServerConfig;
import dev.type95addition.Type95Addition;
import dev.type95addition.net.AddonNetwork;
import dev.type95addition.net.ChargePayload;
import dev.type95addition.net.ChargeSyncPayload;
import dev.type95addition.net.CooldownPayload;
import dev.type95addition.net.MarkPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The server's side of the charge: who is charging since when, each player's settings and cooldown, and the shot
 * itself (where the strike lands) when the right click comes up.
 */
public final class LightningCasting {
    /** Ticks: a shorter press fizzles (so a stray click doesn't fire). */
    public static final int MIN_HOLD = 8;

    private static final Map<UUID, Long> CHARGING = new HashMap<>();
    private static final Map<UUID, LightningTuning> TUNINGS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    /** Where each charging player has marked the strike to land (left click while charging). */
    private static final Map<UUID, MarkPayload> MARKS = new HashMap<>();

    private LightningCasting() {
    }

    public static void tune(ServerPlayer player, LightningTuning tuning) {
        TUNINGS.put(player.getUUID(), tuning);
    }

    /** A player marked (or cleared) where their charge will land: kept only while they charge, and only within range. */
    public static void mark(ServerPlayer player, MarkPayload mark) {
        UUID id = player.getUUID();
        if (mark.kind() == MarkPayload.CLEAR || !CHARGING.containsKey(id)) {
            MARKS.remove(id);
            return;
        }
        if (mark.pos().distanceTo(player.getEyePosition()) > ServerConfig.range() + 32.0) {
            return;
        }
        MARKS.put(id, mark);
    }

    public static LightningTuning tuning(ServerPlayer player) {
        return TUNINGS.getOrDefault(player.getUUID(), LightningTuning.DEFAULT).clamp();
    }

    public static void onCharge(ServerPlayer player, int action) {
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        switch (action) {
            case ChargePayload.START -> {
                if (!canFire(player)) {
                    stopped(player);
                    return;
                }
                CHARGING.put(id, now);
                AddonNetwork.sendAround(player, new ChargeSyncPayload(player.getId(), true, 0));
            }
            case ChargePayload.RELEASE -> {
                Long start = CHARGING.remove(id);
                MarkPayload mark = MARKS.remove(id);
                if (start == null) {
                    return;
                }
                stopped(player);
                int held = (int)(now - start);
                if (held < MIN_HOLD) {
                    tell(player, Component.translatable("hud.type95_addition.too_short"));
                    return;
                }
                if (canFire(player)) {
                    fire(player, held, mark);
                }
            }
            case ChargePayload.CANCEL -> {
                MARKS.remove(id);
                if (CHARGING.remove(id) != null) {
                    stopped(player);
                }
            }
            default -> {
            }
        }
    }

    /** Charges end when the player dies, switches item or leaves. */
    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Long>> it = CHARGING.entrySet().iterator();
        while (it.hasNext()) {
            ServerPlayer player = server.getPlayerList().getPlayer(it.next().getKey());
            if (player == null) {
                it.remove();
            } else if (!player.isAlive() || player.isSpectator() || !Type95Addition.holdsRifle(player)) {
                it.remove();
                MARKS.remove(player.getUUID());
                stopped(player);
            }
        }
    }

    public static void forget(ServerPlayer player) {
        CHARGING.remove(player.getUUID());
        TUNINGS.remove(player.getUUID());
        MARKS.remove(player.getUUID());
    }

    public static void clear() {
        CHARGING.clear();
        TUNINGS.clear();
        COOLDOWN_UNTIL.clear();
        MARKS.clear();
    }

    public static int cooldown(ServerPlayer player) {
        return (int)Math.max(0L, COOLDOWN_UNTIL.getOrDefault(player.getUUID(), 0L) - player.level().getGameTime());
    }

    private static boolean canFire(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !Type95Addition.holdsRifle(player)) {
            return false;
        }
        if (!player.isCreative() && !ServerConfig.survival()) {
            tell(player, Component.translatable("hud.type95_addition.not_allowed"));
            return false;
        }
        int cooldown = cooldown(player);
        if (cooldown > 0) {
            tell(player, Component.translatable("hud.type95_addition.recharging", (cooldown + 19) / 20));
            return false;
        }
        return true;
    }

    private static void stopped(ServerPlayer player) {
        AddonNetwork.sendAround(player, new ChargeSyncPayload(player.getId(), false, 0));
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.AQUA), true);
    }

    private static void fire(ServerPlayer player, int held, MarkPayload mark) {
        ServerLevel level = player.serverLevel();
        float charge = LightningTuning.charge(held, ServerConfig.chargeTicks());
        LightningTuning tuning = tuning(player);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 muzzle = eye.add(look.scale(1.2)).add(right.scale(0.22)).add(0.0, -0.12, 0.0);

        double reach = loadedReach(level, eye, look, ServerConfig.range());
        Vec3 end = eye.add(look.scale(reach));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        Vec3 blockHit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, blockHit,
            player.getBoundingBox().expandTowards(blockHit.subtract(eye)).inflate(1.0),
            e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable(),
            eye.distanceToSqr(blockHit));

        Vec3 target;
        Vec3 marked = mark == null ? null : marked(level, mark);
        if (marked != null) {
            target = marked;
        } else if (entity != null) {
            target = entity.getEntity().position();
        } else if (block.getType() != HitResult.Type.MISS) {
            target = blockHit;
        } else {
            // Nothing in the way: the strike comes down on the ground under the end of the line (or in mid-air).
            int ground = groundBelow(level, end, 320.0);
            target = ground == Integer.MIN_VALUE ? end : new Vec3(end.x, ground, end.z);
        }

        LightningStrike.start(new LightningStrike(level, player, muzzle, target, charge, tuning));

        int total = ServerConfig.cooldownTicks(player.isCreative());
        COOLDOWN_UNTIL.put(player.getUUID(), level.getGameTime() + total);
        AddonNetwork.send(player, new CooldownPayload(total, total));

        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, AddonSounds.FIRE, SoundSource.PLAYERS, 2.0F + 2.0F * charge,
            1.15F - 0.25F * charge);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS,
            0.8F + charge, 1.4F - 0.3F * charge);
        recoil(player, look, charge);
        Type95Addition.LOGGER.info("Lightning Formula fired by {}: charge {} power {} damage {} bolts {} at {}",
            player.getName().getString(), charge, tuning.power(), tuning.damage(), tuning.bolts(), target);
    }

    /** Where a mark puts the strike: on the marked mob (if it's still alive and here), the block, or under the sky point. */
    private static Vec3 marked(ServerLevel level, MarkPayload mark) {
        if (mark.kind() == MarkPayload.ENTITY) {
            Entity entity = level.getEntity(mark.entityId());
            if (entity != null && entity.isAlive()) {
                return entity.position();
            }
            return mark.pos();
        }
        if (mark.kind() == MarkPayload.SKY) {
            int ground = groundBelow(level, mark.pos(), 320.0);
            return ground == Integer.MIN_VALUE ? mark.pos() : new Vec3(mark.pos().x, ground, mark.pos().z);
        }
        return mark.pos();
    }

    private static void recoil(ServerPlayer player, Vec3 look, float charge) {
        if (player.isPassenger() || player.getAbilities().flying) {
            return;
        }
        double push = 0.15 + 0.45 * charge;
        player.push(-look.x * push, player.onGround() ? 0.1 + 0.1 * charge : 0.0, -look.z * push);
        player.hurtMarked = true;
    }

    /** How far along the look the world is loaded (the strike never lands in unloaded chunks). */
    private static double loadedReach(ServerLevel level, Vec3 from, Vec3 dir, double max) {
        for (double d = 0.0; d <= max; d += 8.0) {
            Vec3 p = from.add(dir.scale(d));
            if (level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(p.x), SectionPos.blockToSectionCoord(p.z)) == null) {
                return Math.max(0.0, d - 8.0);
            }
        }
        return max;
    }

    /** The y just above the first solid block or liquid below {@code from}, or MIN_VALUE if none / not loaded. */
    static int groundBelow(ServerLevel level, Vec3 from, double depth) {
        int x = (int)Math.floor(from.x);
        int z = (int)Math.floor(from.z);
        if (level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)) == null) {
            return Integer.MIN_VALUE;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Math.min((int)Math.floor(from.y), (level.getMaxBuildHeight() - 1));
        int bottom = Math.max(level.getMinBuildHeight(), (int)Math.floor(from.y - depth));
        for (int y = top; y >= bottom; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.blocksMotion() || !state.getFluidState().isEmpty()) {
                return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }
}
