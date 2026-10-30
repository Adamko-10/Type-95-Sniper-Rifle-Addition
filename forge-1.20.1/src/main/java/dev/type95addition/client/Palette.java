package dev.type95addition.client;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The Lightning Formula's colours (J screen): each a glow colour and a hot core colour, as 0xRRGGBB. */
public enum Palette {
    STORM("storm", 0x3FA8FF, 0xE6F6FF),
    THUNDER("thunder", 0xFFC93C, 0xFFFBE6),
    VIOLET("violet", 0xA45CFF, 0xF3E8FF),
    CRIMSON("crimson", 0xFF3B4E, 0xFFE6E8),
    WHITE("white", 0xC8D8FF, 0xFFFFFF),
    EMERALD("emerald", 0x3CFF9A, 0xE8FFF2);

    public final String key;
    public final int glow;
    public final int core;

    Palette(String key, int glow, int core) {
        this.key = key;
        this.glow = glow;
        this.core = core;
    }

    public Component label() {
        return Component.translatable("palette.type95_addition." + this.key);
    }

    /** Between the glow (0) and the core (1) colour. */
    public int mix(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int)Mth.lerp(t, this.glow >> 16 & 0xFF, this.core >> 16 & 0xFF);
        int g = (int)Mth.lerp(t, this.glow >> 8 & 0xFF, this.core >> 8 & 0xFF);
        int b = (int)Mth.lerp(t, this.glow & 0xFF, this.core & 0xFF);
        return r << 16 | g << 8 | b;
    }

    public static Palette byKey(String key) {
        for (Palette palette : values()) {
            if (palette.key.equals(key)) {
                return palette;
            }
        }
        return STORM;
    }
}
