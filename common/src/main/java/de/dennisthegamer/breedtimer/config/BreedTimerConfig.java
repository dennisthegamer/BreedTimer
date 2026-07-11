package de.dennisthegamer.breedtimer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.dennisthegamer.breedtimer.platform.Platforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
