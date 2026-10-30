package dev.type95addition.client;

import dev.type95addition.AddonSounds;
import dev.type95addition.net.MarkPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Marking (J screen, on by default): while holding right click to charge, left click marks where the strike will
 * land - a mob (followed until the shot), a block, or a point in the sky (the strike comes down under it). Holding
 * left click drags the mark along with the crosshair. Without a mark the strike lands on the crosshair as before.
 * The mark is drawn by {@code fx.MarkFx}; the server holds the real one ({@code LightningCasting.mark}).
 */
public final class Marking {
    /** {@code kind}: {@link MarkPayload#POINT}, {@link MarkPayload#ENTITY} or {@link MarkPayload#SKY}. */
    public record Mark(int kind, Vec3 pos, int entityId) {
    }

    private static Mark mark;
    private static boolean wasDown;
    private static int sendIn;
    private static int age;

    private Marking() {
    }

    /** While charging: left click places (or drags) the mark. */
    static void tick(Minecraft mc) {
        if (!ClientConfig.marking || mc.player == null) {
            return;
        }
        age++;
        boolean down = mc.options.keyAttack.isDown();
        if (down) {
            Mark aimed = aim(mc, mc.player);
            if (aimed != null) {
                boolean fresh = !wasDown;
                mark = aimed;
                if (fresh) {
                    age = 0;
                    mc.player.level().playLocalSound(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), AddonSounds.MARK,
                        SoundSource.PLAYERS, 0.8F, aimed.kind == MarkPayload.ENTITY ? 1.25F : 1.0F, false);
                }
                if (fresh || --sendIn <= 0) {
                    sendIn = 2;
                    if (ClientPlayNetworking.canSend(MarkPayload.TYPE)) {
                        ClientPlayNetworking.send(new MarkPayload(aimed.kind, aimed.pos, aimed.entityId));
                    }
                }
            }
        }
        wasDown = down;
    }

    /** The charge ended (the server forgets its mark by itself). */
    static void reset() {
        mark = null;
        wasDown = false;
        sendIn = 0;
    }

    /** Left click goes to the mark while charging (no punching or digging). */
    public static boolean takesAttack() {
        return ClientConfig.marking && ChargeClient.charging();
    }

    public static Mark current() {
        return mark;
    }

    /** Ticks since the mark was placed (for its effects). */
    public static int age() {
        return age;
    }

    /** Where the mark is right now: on its mob (smoothly), or where it was put. */
    public static Vec3 position(float partial) {
        if (mark == null) {
            return null;
        }
        if (mark.kind == MarkPayload.ENTITY) {
            Minecraft mc = Minecraft.getInstance();
            Entity entity = mc.level == null ? null : mc.level.getEntity(mark.entityId);
            if (entity != null && entity.isAlive()) {
                return entity.getPosition(partial);
            }
        }
        return mark.pos;
    }

    private static Mark aim(Minecraft mc, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(ChargeClient.range));
        BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        Vec3 blockHit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, blockHit,
            player.getBoundingBox().expandTowards(blockHit.subtract(eye)).inflate(1.0),
            e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && e != player,
            eye.distanceToSqr(blockHit));
        if (entity != null) {
            return new Mark(MarkPayload.ENTITY, entity.getEntity().position(), entity.getEntity().getId());
        }
        if (block.getType() != HitResult.Type.MISS) {
            return new Mark(MarkPayload.POINT, blockHit, -1);
        }
        return new Mark(MarkPayload.SKY, end, -1);
    }
}
