package de.dennisthegamer.breedtimer.render;

/**
 * The mod's whole colour vocabulary, in one place, so a label, a block label and a compact-HUD
 * segment cannot disagree about what "ready" looks like.
 *
 * <p>Presets rather than eight colour pickers: the state is already carried redundantly by the
 * glyph prefixes and by the text itself, so what a user needs is a set that has been checked, not
 * the freedom to build one that is worse than the default. Contrast ratios and dichromat
 * separation figures are in the Phase 6 plan; every value here was measured, not chosen by eye.
 */
public enum StatePalette {

    /** What the mod has always drawn. Kept exactly, so no existing screenshot goes stale. */
    DEFAULT(0x55FF55, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FFFF, 0xFF55FF, 0xAAAAAA, 0xFFFFFF, false),

    /**
     * Deuteranopia and protanopia -- together about 99% of colour vision deficiency. Built on the
     * blue-yellow axis a red-green dichromat keeps, with the three cooldown tiers as one warm
     * lightness ramp. In-love deliberately shares ready's blue: the axis has no eighth
     * distinguishable hue, and the two states are the same "actionable" band anyway.
     */
    COLORBLIND(0x7FD4FF, 0xC06000, 0xE8A33D, 0xFFE066, 0x5C86E8, 0x7FD4FF, 0x909090, 0xFFFFFF, false),

    /**
     * Every colour at 9.92:1 or better against black, where DEFAULT bottoms out at 6.68:1, and the
     * text outline forced on so that holds against snow and sand too. This does NOT help with
     * colour blindness -- that is what COLORBLIND is for, and the description says so.
     */
    HIGH_CONTRAST(0x7CFF7C, 0xFF9494, 0xFFC062, 0xFFFF8C, 0x8CF2FF, 0xFFA6FF, 0xC0C0C0, 0xFFFFFF, true);

    public final int ready, coolFar, coolMid, coolNear, young, love, inert, neutral;
    /** Whether choosing this preset implies the text outline regardless of the tick box. */
    public final boolean forcesOutline;

    StatePalette(int ready, int coolFar, int coolMid, int coolNear,
                 int young, int love, int inert, int neutral, boolean forcesOutline) {
        this.ready = ready;
        this.coolFar = coolFar;
        this.coolMid = coolMid;
        this.coolNear = coolNear;
        this.young = young;
        this.love = love;
        this.inert = inert;
        this.neutral = neutral;
        this.forcesOutline = forcesOutline;
    }

    /** The palette in force. One accessor, so no caller reads the config field directly. */
    public static StatePalette current() {
        return de.dennisthegamer.breedtimer.config.BreedTimerConfig.get().colorPreset;
    }
}
