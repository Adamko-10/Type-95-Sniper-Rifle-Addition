package dev.type95addition;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** The Lightning Formula's own sounds (all synthesised by {@code dev/art/make_sounds.py}; see sounds.json). */
public final class AddonSounds {
    public static final SoundEvent CHARGE_LOOP = register("charge_loop");
    public static final SoundEvent CHARGE_START = register("charge_start");
    public static final SoundEvent STAGE_UP = register("stage_up");
    public static final SoundEvent FULL_CHARGE = register("full_charge");
    public static final SoundEvent FULL_LOOP = register("full_loop");
    public static final SoundEvent FIZZLE = register("fizzle");
    public static final SoundEvent FIRE = register("fire");
    public static final SoundEvent THUNDER_NEAR = register("thunder_near");
    public static final SoundEvent THUNDER_FAR = register("thunder_far");
    public static final SoundEvent ZAP = register("zap");
    public static final SoundEvent SHOCKWAVE = register("shockwave");
    public static final SoundEvent DOME = register("dome");
    public static final SoundEvent MARK = register("mark");
    public static final SoundEvent SCOPE = register("scope");

    private AddonSounds() {
    }

    private static SoundEvent register(String name) {
        Identifier id = Type95Addition.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Loads the class (registers the sounds). */
    public static void init() {
    }
}
