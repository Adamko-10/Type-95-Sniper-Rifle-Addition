package dev.type95addition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** The Lightning Formula's own sounds (all synthesised by {@code dev/art/make_sounds.py}; see sounds.json). */
public final class AddonSounds {
    private static final Map<ResourceLocation, SoundEvent> ALL = new LinkedHashMap<>();

    public static final SoundEvent CHARGE_LOOP = create("charge_loop");
    public static final SoundEvent CHARGE_START = create("charge_start");
    public static final SoundEvent STAGE_UP = create("stage_up");
    public static final SoundEvent FULL_CHARGE = create("full_charge");
    public static final SoundEvent FULL_LOOP = create("full_loop");
    public static final SoundEvent FIZZLE = create("fizzle");
    public static final SoundEvent FIRE = create("fire");
    public static final SoundEvent THUNDER_NEAR = create("thunder_near");
    public static final SoundEvent THUNDER_FAR = create("thunder_far");
    public static final SoundEvent ZAP = create("zap");
    public static final SoundEvent SHOCKWAVE = create("shockwave");
    public static final SoundEvent DOME = create("dome");
    public static final SoundEvent MARK = create("mark");
    public static final SoundEvent SCOPE = create("scope");

    private AddonSounds() {
    }

    private static SoundEvent create(String name) {
        ResourceLocation id = Type95Addition.id(name);
        SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
        ALL.put(id, sound);
        return sound;
    }

    /** From the RegisterEvent for sounds. */
    static void register(BiConsumer<ResourceLocation, SoundEvent> registry) {
        ALL.forEach(registry);
    }
}
