package dev.type95addition.client.anim;

import dev.type95addition.client.fx.ChargeFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The Lightning Formula's animations: how far each player has raised the rifle to aim (it comes up as the charge
 * starts and goes down after the shot), the recoil of each shot, and the kick it gives the local player's view.
 * Read by {@code mixin.PlayerModelMixin} (third person) and {@code mixin.ItemInHandRendererMixin} (first person).
 */
public final class RifleAnim {
    private static final int RECOIL_TICKS = 14;
    /** Entity id -> {aim weight before this tick, aim weight now}. */
    private static final Map<Integer, float[]> AIM = new HashMap<>();
    /** Entity id -> {client tick of the shot, its charge}. */
    private static final Map<Integer, float[]> RECOIL = new HashMap<>();
    private static int ticks;
    private static float kickLeft;
    private static float kickYaw;

    private RifleAnim() {
    }

    public static void tick(Minecraft mc) {
        ticks++;
        if (mc.level == null) {
            AIM.clear();
            RECOIL.clear();
            return;
        }
        for (var player : mc.level.players()) {
            int id = player.getId();
            boolean charging = ChargeFx.held(id) >= 0;
            boolean recoiling = recoilAge(id, 0.0F) < RECOIL_TICKS;
            float[] w = AIM.computeIfAbsent(id, k -> new float[2]);
            w[0] = w[1];
            float goal = charging || recoiling ? 1.0F : 0.0F;
            w[1] = goal > w[1] ? Math.min(1.0F, w[1] + 0.25F) : Math.max(0.0F, w[1] - 0.12F);
        }
        AIM.entrySet().removeIf(e -> mc.level.getEntity(e.getKey()) == null);
        Iterator<Map.Entry<Integer, float[]>> it = RECOIL.entrySet().iterator();
        while (it.hasNext()) {
            if (ticks - it.next().getValue()[0] > RECOIL_TICKS + 2) {
                it.remove();
            }
        }
        // The view kicks up over a few ticks after a shot, with a little sideways jolt
        LocalPlayer player = mc.player;
        if (player != null && kickLeft > 0.01F) {
            float step = kickLeft * 0.45F;
            kickLeft -= step;
            player.setXRot(Mth.clamp(player.getXRot() - step, -90.0F, 90.0F));
            player.setYRot(player.getYRot() + kickYaw * step);
        }
    }

    /** A player fired: start their recoil (and kick the view if it's us). */
    public static void fired(int entityId, float charge) {
        RECOIL.put(entityId, new float[] {ticks, charge});
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == entityId) {
            kickLeft += 3.0F + 9.0F * charge;
            kickYaw = (float)(Math.random() - 0.5) * 0.4F;
        }
    }

    private static float recoilAge(int id, float partial) {
        float[] r = RECOIL.get(id);
        return r == null ? Float.MAX_VALUE : ticks - r[0] + partial;
    }

    /** 0-1: how far the rifle is raised to aim. */
    public static float aim(int id, float partial) {
        float[] w = AIM.get(id);
        if (w == null) {
            return 0.0F;
        }
        float t = Mth.lerp(partial, w[0], w[1]);
        return t * t * (3.0F - 2.0F * t);
    }

    /** The recoil right now: a hard kick that springs back with a small overshoot. 0 when still. */
    public static float recoil(int id, float partial) {
        float age = recoilAge(id, partial);
        if (age >= RECOIL_TICKS) {
            return 0.0F;
        }
        float strength = 0.5F + 0.5F * RECOIL.get(id)[1];
        float attack = Math.min(1.0F, age / 1.2F);
        float spring = (float)(Math.exp(-age / 3.2) * Math.cos(age * 0.55));
        return strength * attack * spring;
    }

    /** 0-1 shaking of a charging rifle: grows with the charge, strongest at full. */
    public static float tremble(int id) {
        int held = ChargeFx.held(id);
        return held < 0 ? 0.0F : ChargeFx.charge(held);
    }

    public static float time(float partial) {
        return ticks + partial;
    }
}
