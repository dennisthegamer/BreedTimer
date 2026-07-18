package de.dennisthegamer.breedtimer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.dennisthegamer.breedtimer.platform.Platforms;
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
    public boolean showBabyTimer = true;
    public float backgroundOpacity = 0.5f;
    public boolean playSound = true;
    public boolean compactMode = false;
    /** Freie HUD-Position (Anker + Offset). Nach {@link #load()} immer non-null. */
    public HudPlacement hudPlacement = null;
    /** Vom Nutzer gespeicherte Positions-Slots. */
    public List<HudPreset> hudSlots = new ArrayList<>();

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

        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            LOGGER.error("Failed to save BreedTimer config", e);
        }
    }
}
