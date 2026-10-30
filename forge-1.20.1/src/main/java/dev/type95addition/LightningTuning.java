package dev.type95addition;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * One player's Lightning Formula settings (the J screen), sent to the server whenever they change and held to the
 * server's limits there ({@link #clamp()}). Also the strike's numbers, shared by the server (what happens) and the
 * client (the screen's preview and the effects' size).
 *
 * @param power  0.1-20x: the strike's size (Type 95's own Explosion Formula stops at 10x)
 * @param damage 2-1000 per hit at the centre, or {@link #INFINITE} (True Erasure: everything in the radius dies)
 * @param bolts  1-16: how many bolts come down at full charge
 * @param fire   bolts set fires (and a real vanilla bolt comes down first: charged creepers, witches...)
 * @param terrain the ground is scorched: Lichtenberg scars of glass, magma and blackstone (needs mobGriefing)
 * @param blast  from stage 3 the strike blasts a crater (glass, magma and blackstone) out of the ground
 */
public record LightningTuning(float power, float damage, int bolts, boolean fire, boolean terrain, boolean blast) {
    public static final float MIN_POWER = 0.1F;
    public static final float MAX_POWER = 20.0F;
    public static final float MIN_DAMAGE = 2.0F;
    public static final float MAX_DAMAGE = 1000.0F;
    public static final float INFINITE = Float.POSITIVE_INFINITY;
    public static final int MIN_BOLTS = 1;
    public static final int MAX_BOLTS = 16;

    public static final LightningTuning DEFAULT = new LightningTuning(1.0F, 24.0F, 6, true, true, true);

    public boolean erases() {
        return this.damage == INFINITE;
    }

    /** Held to the addon's own ranges and to the server's limits ({@link ServerConfig}). */
    public LightningTuning clamp() {
        float p = Float.isFinite(this.power) ? Mth.clamp(this.power, MIN_POWER, Math.min(MAX_POWER, ServerConfig.maxPower())) : 1.0F;
        float d = this.damage == INFINITE ? INFINITE : Float.isNaN(this.damage) ? DEFAULT.damage : Mth.clamp(this.damage, MIN_DAMAGE, MAX_DAMAGE);
        float cap = ServerConfig.maxDamage();
        if (cap > 0.0F && d > cap) {
            d = cap;
        }
        int b = Mth.clamp(this.bolts, MIN_BOLTS, Math.min(MAX_BOLTS, ServerConfig.maxBolts()));
        return new LightningTuning(p, d, b,
            this.fire && ServerConfig.fire(),
            this.terrain && ServerConfig.terrain(),
            this.blast && ServerConfig.blast());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.power);
        buf.writeFloat(this.damage);
        buf.writeVarInt(this.bolts);
        buf.writeByte((this.fire ? 1 : 0) | (this.terrain ? 2 : 0) | (this.blast ? 4 : 0));
    }

    public static LightningTuning read(FriendlyByteBuf buf) {
        float power = buf.readFloat();
        float damage = buf.readFloat();
        int bolts = buf.readVarInt();
        int flags = buf.readByte();
        return new LightningTuning(power, damage, bolts, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
    }

    // ---- The strike's numbers ------------------------------------------------------------------------------------

    /** 0-1: how far the charge got (full after {@code fullTicks}). */
    public static float charge(int heldTicks, int fullTicks) {
        return Mth.clamp(heldTicks / (float)Math.max(1, fullTicks), 0.0F, 1.0F);
    }

    /** 1-4: the charge's stage (each one looks and sounds different). */
    public static int stage(float charge) {
        return 1 + Math.min(3, (int)(charge * 4.0F));
    }

    /** The strike's size: power, scaled by the charge (a quick shot is a quarter of a full one). */
    public static double scale(float charge, float power) {
        return power * (0.25 + 0.75 * charge);
    }

    /**
     * Blocks: the radius everything inside dies in (well, takes the full damage). 125 at full charge and 1x power,
     * 1115 at 10x (Type 95's Explosion Formula: 110 and 1100), 2215 at 20x.
     */
    public static double radius(double scale) {
        return 15.0 + 110.0 * scale;
    }

    /** Blocks: how far the shock wave runs (knock-back and lighter damage outside the radius). */
    public static double shockRadius(double scale) {
        return radius(scale) * 3.5;
    }

    /** Blocks: the crater's radius (Type 95's stops at 256). */
    public static double craterRadius(double scale, double max) {
        return Math.min(max, 6.0 + 40.0 * scale);
    }

    /** How many bolts come down in the first volley: the setting, scaled by the charge and the power. */
    public static int boltCount(float charge, int bolts, double scale) {
        return Math.min(64, Math.max(1, Math.round(bolts * (0.25F + 0.75F * charge) * (float)Math.max(1.0, Math.sqrt(scale)))));
    }

    /** Seconds the storm keeps striking after the first volley. */
    public static double stormSeconds(float charge, double scale) {
        return charge < 0.5F ? 0.0 : Math.min(12.0, 3.0 + 2.0 * scale);
    }

    /** Damage at the centre (it falls off to 30% at the edge). */
    public static float centreDamage(float charge, float damage) {
        return damage * (0.4F + 0.6F * charge);
    }
}
