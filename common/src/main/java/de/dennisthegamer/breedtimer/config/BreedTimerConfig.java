package de.dennisthegamer.breedtimer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.dennisthegamer.breedtimer.platform.Platforms;
import de.dennisthegamer.breedtimer.render.StatePalette;
import de.dennisthegamer.hudlib.position.HudAnchor;
import de.dennisthegamer.hudlib.position.HudPlacement;
import de.dennisthegamer.hudlib.position.HudPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BreedTimerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("BreedTimer");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            Platforms.get().getConfigDir().resolve("breedtimer.json");

    private static BreedTimerConfig INSTANCE;

    public boolean enabled = true;
    public boolean showAnimals = true;
    public boolean showVillagers = true;
    public int scanRadius = 16;
    public int fadeStartDistance = 16;
    public int fadeEndDistance = 20;
    public int fovAngle = 90;
    /** Label only the animal under the crosshair. Collapses per-frame label work to roughly one entity. */
    public boolean labelLookAtOnly = false;
    /**
     * True is what the mod has always done: labels draw a see-through pass and ignore terrain. Turning it off
     * adds a line-of-sight test, resolved once per tick rather than once per rendered entity per frame.
     */
    public boolean labelThroughWalls = true;
    public boolean showBabyTimer = true;
    /**
     * The state filter. Each of these hides one kind of label and drops it from the compact HUD counts.
     * "Babies" is the existing {@link #showBabyTimer} above, which was already exactly this filter -- it
     * is presented alongside these five in the config screen rather than duplicated, so the config file
     * cannot contradict itself.
     *
     * <p>{@link #showBlocks} is a sixth switch in the same tab, but it is not a breeding state: it hides
     * all six block-kind labels (turtle egg, sniffer egg, dried ghast, beehive, and the crops Task 34
     * adds) at once, so the Filter tab genuinely controls everything the mod can draw instead of leaving
     * block labels as a blind spot the other five checkboxes cannot reach.
     *
     * <p>All default to true so the feature is invisible until somebody uses it. That relies on Gson
     * running the field initialisers, which it does because this class has no declared constructor.
     * Do not add a constructor with arguments unless a no-arg overload stays: without one Gson falls back
     * to Unsafe.allocateInstance, every initialiser is skipped, and every existing config silently resets.
     */
    public boolean showReady = true;
    public boolean showCooldown = true;
    public boolean showInLove = true;
    public boolean showBlocked = true;
    /**
     * Off by default, unlike the five state filters above: this is the mod's only inferred display
     * (a chicken egg or armadillo scute window derived from a heard sound, never a fact read off a
     * synced field), and defaulting an inference to visible would put a guess in front of every
     * player who has not opted into one. Mirrors {@link #showFoodHint}'s precedent for an
     * off-by-default extra line under a label.
     */
    public boolean showDropWindows = false;
    public boolean showBlocks = true;
    /** Off by default: the food hint puts a second line under every label. */
    public boolean showFoodHint = false;
    /** Opacity of the box behind a floating label in the world. */
    public float labelOpacity = 0.5f;
    /** Opacity of the compact HUD panel's own background. Independent of the label box above. */
    public float hudOpacity = 0.5f;
    /** Text scale of the compact HUD panel. 1.0 is the size it has always drawn at. */
    public float hudScale = 1.0f;

    /**
     * Pre-1.6.0 name of {@link #labelOpacity}, kept only so an existing config carries over. Boxed
     * and left null on a fresh config: Gson omits null fields when writing, so the key disappears
     * from the file the first time it is saved. Deleting the field outright instead would let Gson
     * drop the old key silently and hand both new fields their initialiser, which happens to equal
     * the old default -- so only users who had actually moved the slider would have been reset,
     * which is exactly the wrong set of people.
     */
    private Float backgroundOpacity = null;
    /**
     * A one-pixel black outline around every glyph, the way vanilla draws glowing text displays.
     * Off by default: it changes how every label looks, and the background box already carries the
     * contrast at the default opacity. It exists because the opacity slider reaches 0.00, where the
     * yellow cooldown tier measures 1.07:1 against snow and the label is effectively invisible.
     */
    public boolean textOutline = false;
    /**
     * Which colour set the labels and the compact HUD draw with. An enum rather than eight
     * pickers -- see StatePalette. Gson deserialises an unrecognised enum name to null without a
     * word, so load() guards it the same way hudPlacement is guarded.
     */
    public StatePalette colorPreset = StatePalette.DEFAULT;
    public boolean playSound = true;
    /** Volume of the ready chime. The {@link #playSound} switch above stays the on/off control. */
    public float soundVolume = 0.5f;
    /** Pitch of the ready chime. 1.0 is the note the mod has always played. */
    public float soundPitch = 1.0f;
    public boolean compactMode = false;
    /** Restore the pre-1.6.0 HUD behaviour: count only what is inside the view cone and fade range. */
    public boolean hudInViewOnly = false;
    /** Freie HUD-Position (Anker + Offset). Nach {@link #load()} immer non-null. */
    public HudPlacement hudPlacement = null;
    /** Vom Nutzer gespeicherte Positions-Slots. */
    public List<HudPreset> hudSlots = new ArrayList<>();

    /**
     * Keeps the fade window the right way round. The two sliders are independent, and
     * {@code getDistanceFade} tests the start first — so a start beyond the end would leave labels
     * at full opacity right up to a cut-off that the HUD and the ready sound had already applied.
     */
    private void clampFadeRange() {
        if (fadeStartDistance > fadeEndDistance) fadeStartDistance = fadeEndDistance;
    }

    /**
     * The radius everything must actually scan. The label path applies no scan radius at all, only
     * the fade window, so with the defaults (scanRadius 16, fadeEndDistance 20) an animal at 18
     * blocks got a label but was invisible to the HUD and to the ready chime.
     */
    public int effectiveScanRadius() {
        return Math.max(scanRadius, fadeEndDistance);
    }

    /** The outline actually drawn: the tick box, or forced on by the high-contrast preset. */
    public boolean effectiveTextOutline() {
        return textOutline || colorPreset.forcesOutline;
    }

    public static BreedTimerConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        // Also try the old .json5 path for users upgrading from a YACL install
        Path legacyPath = Platforms.get().getConfigDir().resolve("breedtimer.json5");
        Path readFrom = Files.exists(CONFIG_PATH) ? CONFIG_PATH
                      : Files.exists(legacyPath)  ? legacyPath
                      : null;

        if (readFrom != null) {
            try {
                BreedTimerConfig loaded = GSON.fromJson(Files.readString(readFrom), BreedTimerConfig.class);
                INSTANCE = loaded != null ? loaded : new BreedTimerConfig();
            } catch (Exception e) {
                LOGGER.error("Failed to load BreedTimer config, using defaults", e);
                INSTANCE = new BreedTimerConfig();
            }
        } else {
            INSTANCE = new BreedTimerConfig();
        }

        // Migration: BreedTimer hatte nie eine Legacy-Positions-Enum, daher ist der HudLib-Default
        // (TOP_LEFT, Standardrand) die einzig sinnvolle Ausgangslage. Läuft auf JEDEM Pfad oben, da
        // INSTANCE hier garantiert gesetzt ist.
        if (INSTANCE.hudPlacement == null) {
            INSTANCE.hudPlacement = HudPlacement.of(HudAnchor.TOP_LEFT);
        }
        if (INSTANCE.hudSlots == null) {
            INSTANCE.hudSlots = new ArrayList<>();
        }
        if (INSTANCE.colorPreset == null) INSTANCE.colorPreset = StatePalette.DEFAULT;
        // Migration: backgroundOpacity split into labelOpacity and hudOpacity in 1.6.0. Both start
        // from whatever the old single slider was set to, so the upgrade is a visual no-op; a fresh
        // config never has backgroundOpacity set, so this only ever touches upgraded installs.
        if (INSTANCE.backgroundOpacity != null) {
            INSTANCE.labelOpacity = INSTANCE.backgroundOpacity;
            INSTANCE.hudOpacity   = INSTANCE.backgroundOpacity;
            INSTANCE.backgroundOpacity = null;
        }
        INSTANCE.clampFadeRange();

        save();
    }

    public static void save() {
        if (INSTANCE != null) INSTANCE.clampFadeRange();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            LOGGER.error("Failed to save BreedTimer config", e);
        }
    }
}
