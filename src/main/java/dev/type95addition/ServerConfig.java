package dev.type95addition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The server's side of the settings: {@code config/type95_addition-server.json}. Read when the game starts and
 * when a world/server starts (edit it, then reopen the world or restart the server). Missing keys are filled in
 * with their defaults and written back, each with a {@code _help} line explaining it.
 */
public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, String> HELP = new LinkedHashMap<>();
    private static JsonObject values = new JsonObject();

    static {
        option("charge_seconds", 8.0, "Seconds of holding right click to reach full charge (4 stages).");
        option("cooldown_seconds", 6.0, "Seconds before a player can fire the Lightning Formula again.");
        option("creative_cooldown_seconds", 1.0, "The same for creative-mode players.");
        option("range", 512.0, "Blocks: how far the rifle can call a strike (never further than the loaded world).");
        option("max_power", 20.0, "Highest power a player may set (0.1-20; Type 95's own Explosion Formula stops at 10).");
        option("max_damage", 0.0, "Highest damage a player may set; 0 = no limit (True Erasure allowed).");
        option("max_bolts", 16, "Most bolts a player may set (1-16).");
        option("max_crater_radius", 420.0, "Blocks: the biggest crater a strike may blast (bigger ones take longer to carve).");
        option("hurt_shooter", false, "The strike also hurts the player who fired it.");
        option("fire", true, "Players may turn on fire.");
        option("terrain", true, "Players may turn on terrain scorching (also needs the mobGriefing game rule).");
        option("blast", true, "Players may turn on the thunderclap blast (it only breaks blocks with terrain on too).");
        option("survival", true, "Survival players may use the Lightning Formula (creative players always can).");
    }

    private ServerConfig() {
    }

    private static void option(String key, Object value, String help) {
        DEFAULTS.put(key, value);
        HELP.put(key, help);
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(Type95Addition.MOD_ID + "-server.json");
    }

    public static synchronized void load() {
        Path path = path();
        JsonObject read = new JsonObject();
        if (Files.isRegularFile(path)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) {
                    read = parsed.getAsJsonObject();
                }
            } catch (IOException | RuntimeException e) {
                Type95Addition.LOGGER.warn("Could not read {}, using the defaults: {}", path, e.toString());
            }
        }
        // 1.0.0's files (no version) had max_power 10 as their default: lift it to 1.1.0's 20 unless it was changed
        if (!read.has("config_version") && read.has("max_power") && read.get("max_power").getAsDouble() == 10.0) {
            read.remove("max_power");
        }
        JsonObject out = new JsonObject();
        out.addProperty("config_version", 2);
        for (Map.Entry<String, Object> entry : DEFAULTS.entrySet()) {
            String key = entry.getKey();
            out.addProperty("_help_" + key, HELP.get(key));
            JsonElement value = read.get(key);
            if (value == null || !value.isJsonPrimitive()) {
                Object def = entry.getValue();
                if (def instanceof Boolean b) {
                    out.addProperty(key, b);
                } else {
                    out.addProperty(key, (Number)def);
                }
            } else {
                out.add(key, value);
            }
        }
        values = out;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(out), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Type95Addition.LOGGER.warn("Could not write {}: {}", path, e.toString());
        }
    }

    private static double number(String key) {
        try {
            return values.get(key).getAsDouble();
        } catch (RuntimeException e) {
            return ((Number)DEFAULTS.get(key)).doubleValue();
        }
    }

    private static boolean flag(String key) {
        try {
            return values.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return (Boolean)DEFAULTS.get(key);
        }
    }

    public static int chargeTicks() {
        return (int)Math.round(Math.max(1.0, Math.min(60.0, number("charge_seconds"))) * 20.0);
    }

    public static int cooldownTicks(boolean creative) {
        double seconds = number(creative ? "creative_cooldown_seconds" : "cooldown_seconds");
        return (int)Math.round(Math.max(0.0, Math.min(3600.0, seconds)) * 20.0);
    }

    public static double range() {
        return Math.max(16.0, Math.min(2048.0, number("range")));
    }

    public static float maxPower() {
        return (float)Math.max(LightningTuning.MIN_POWER, number("max_power"));
    }

    /** 0 = no limit. */
    public static float maxDamage() {
        double cap = number("max_damage");
        return cap <= 0.0 ? 0.0F : (float)Math.max(LightningTuning.MIN_DAMAGE, cap);
    }

    public static int maxBolts() {
        return (int)Math.max(1.0, number("max_bolts"));
    }

    public static double maxCraterRadius() {
        return Math.max(4.0, Math.min(1024.0, number("max_crater_radius")));
    }

    public static boolean hurtShooter() {
        return flag("hurt_shooter");
    }

    public static boolean fire() {
        return flag("fire");
    }

    public static boolean terrain() {
        return flag("terrain");
    }

    public static boolean blast() {
        return flag("blast");
    }

    public static boolean survival() {
        return flag("survival");
    }
}
