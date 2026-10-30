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
 * The player's own settings, {@code config/type95_addition-client.json}: the ammo picked, the Lightning Formula's
 * settings (sent to the server, which holds them to its limits) and the purely visual switches. Changed in game
 * with the J screen; editing the file works too (read when the game starts).
 */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean lightning = true;
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

    private ClientConfig() {
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(Type95Addition.MOD_ID + "-client.json");
    }

    public static LightningTuning tuning() {
        return new LightningTuning(power, damage, bolts, fire, terrain, blast);
    }

    public static void load() {
        Path path = path();
        if (Files.isRegularFile(path)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                lightning = bool(o, "lightning_ammo", lightning);
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
            } catch (IOException | RuntimeException e) {
                Type95Addition.LOGGER.warn("Could not read {}, using the defaults: {}", path, e.toString());
            }
        }
        save();
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("lightning_ammo", lightning);
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
