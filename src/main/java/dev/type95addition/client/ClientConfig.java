package dev.type95addition.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.type95addition.LightningTuning;
import dev.type95addition.Type95Addition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The player's own settings, {@code config/type95_addition-client.json}: the ammo picked (explosion, lightning,
 * homing or gauss), the Lightning Formula's settings (sent to the server, which holds them to its limits) and the purely
 * visual switches. Changed in game with the J screen; editing the file works too (read when the game starts).
 */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int EXPLOSION = 0;
    public static final int LIGHTNING = 1;
    public static final int HOMING = 2;
    public static final int GAUSS = 3;
    private static final String[] AMMO_KEYS = {"explosion", "lightning", "homing", "gauss"};

    /** The ammo in the rifle (R switches): the rifle's own Explosion Formula, or one of the addon's. */
    public static int ammo = LIGHTNING;
    public static float power = LightningTuning.DEFAULT.power();
    /** {@link LightningTuning#INFINITE} for True Erasure (stored as -1 in the file). */
    public static float damage = LightningTuning.DEFAULT.damage();
    public static int bolts = LightningTuning.DEFAULT.bolts();
    public static boolean fire = LightningTuning.DEFAULT.fire();
    public static boolean terrain = LightningTuning.DEFAULT.terrain();
    public static boolean blast = LightningTuning.DEFAULT.blast();
    public static Palette palette = Palette.STORM;
    public static boolean flash = true;
    public static boolean shake = true;
    public static boolean zoom = true;
    /** Holding right click looks through a scope (like the Explosion Formula), with scroll to zoom. */
    public static boolean scope = false;
    /** While charging, left click (or hold it) marks where the strike lands: a mob, a block or the sky. */
    public static boolean marking = true;
    /** The Gauss Formula's slug goes through blocks softer than stone (if the server allows it). */
    public static boolean gaussBlocks = LightningTuning.DEFAULT.gaussBlocks();

    private ClientConfig() {
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(Type95Addition.MOD_ID + "-client.json");
    }

    public static LightningTuning tuning() {
        return new LightningTuning(power, damage, bolts, fire, terrain, blast, gaussBlocks);
    }

    public static void load() {
        Path path = path();
        if (Files.isRegularFile(path)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                ammo = bool(o, "lightning_ammo", true) ? LIGHTNING : EXPLOSION;
                if (o.has("ammo")) {
                    ammo = Math.max(0, java.util.Arrays.asList(AMMO_KEYS).indexOf(o.get("ammo").getAsString()));
                }
                power = (float)number(o, "power", power);
                double d = number(o, "damage", damage);
                damage = d < 0.0 ? LightningTuning.INFINITE : (float)d;
                bolts = (int)number(o, "bolts", bolts);
                fire = bool(o, "fire", fire);
                terrain = bool(o, "terrain", terrain);
                blast = bool(o, "blast", blast);
                palette = Palette.byKey(o.has("colour") ? o.get("colour").getAsString() : palette.key);
                flash = bool(o, "screen_flash", flash);
                shake = bool(o, "camera_shake", shake);
                zoom = bool(o, "zoom_while_charging", zoom);
                scope = bool(o, "scope", scope);
                marking = bool(o, "mark_with_left_click", marking);
                gaussBlocks = bool(o, "gauss_through_blocks", gaussBlocks);
            } catch (IOException | RuntimeException e) {
                Type95Addition.LOGGER.warn("Could not read {}, using the defaults: {}", path, e.toString());
            }
        }
        save();
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("ammo", AMMO_KEYS[ammo]);
        o.addProperty("power", power);
        o.addProperty("damage", damage == LightningTuning.INFINITE ? -1.0 : damage);
        o.addProperty("bolts", bolts);
        o.addProperty("fire", fire);
        o.addProperty("terrain", terrain);
        o.addProperty("blast", blast);
        o.addProperty("colour", palette.key);
        o.addProperty("screen_flash", flash);
        o.addProperty("camera_shake", shake);
        o.addProperty("zoom_while_charging", zoom);
        o.addProperty("scope", scope);
        o.addProperty("mark_with_left_click", marking);
        o.addProperty("gauss_through_blocks", gaussBlocks);
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Type95Addition.LOGGER.warn("Could not write {}: {}", path(), e.toString());
        }
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        return o.has(key) ? o.get(key).getAsBoolean() : def;
    }

    private static double number(JsonObject o, String key, double def) {
        return o.has(key) ? o.get(key).getAsDouble() : def;
    }
}
