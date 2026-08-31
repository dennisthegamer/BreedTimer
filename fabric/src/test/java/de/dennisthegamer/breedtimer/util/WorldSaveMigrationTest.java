package de.dennisthegamer.breedtimer.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The F-1 legacy-migration file-resolution decision behind {@link WorldSaveMigration}. Pure
 * path/existence logic, no Minecraft dependency -- see that class's javadoc for the singleplayer
 * world-identity migration this implements (BreedCooldownHelper/VillagerCooldownHelper.onWorldJoin
 * are the client-facing callers, not testable the same way).
 */
class WorldSaveMigrationTest {

    /**
     * Singleplayer state moved into the world folder itself, because the save-folder name it used
     * to be keyed by is only unique inside one Minecraft installation. Across instances -- MultiMC,
     * Prism, Pandora -- three different worlds can all be called "New World", and the config-dir
     * file they shared then carried a foreign world's animals.
     */
    @Test
    void prefersTheInWorldFileOverBothConfigFiles(@TempDir Path dir) throws IOException {
        Path inWorld = Files.createFile(dir.resolve("in-world.json"));
        Path current = Files.createFile(dir.resolve("current.json"));
        Path legacy  = Files.createFile(dir.resolve("legacy.json"));
        assertEquals(inWorld, WorldSaveMigration.resolveLoadFile(inWorld, current, legacy));
    }

    /** The migration itself: nothing in the world folder yet, so the old config file is read. */
    @Test
    void fallsBackToTheConfigFileWhileTheWorldFolderHasNothingYet(@TempDir Path dir) throws IOException {
        Path inWorld = dir.resolve("in-world.json");
        Path current = Files.createFile(dir.resolve("current.json"));
        assertEquals(current, WorldSaveMigration.resolveLoadFile(inWorld, current, null));
    }

    /** A fresh world in a fresh instance: the caller gets the in-world path to write to. */
    @Test
    void returnsTheInWorldFileWhenNothingExistsAnywhere(@TempDir Path dir) {
        Path inWorld = dir.resolve("in-world.json");
        Path current = dir.resolve("current.json");
        assertEquals(inWorld, WorldSaveMigration.resolveLoadFile(inWorld, current, null));
    }

    /**
     * The longest upgrade path there is: a world that never migrated to F-1 -- renamed, so its
     * folder name and level name differ -- jumping straight to the in-world scheme. Neither newer
     * location exists, so the pre-F-1 file keyed by level name is the only state there is.
     */
    @Test
    void reachesThePreF1FileWhenNeitherNewerLocationExists(@TempDir Path dir) throws IOException {
        Path inWorld = dir.resolve("in-world.json");
        Path current = dir.resolve("current.json");
        Path legacy  = Files.createFile(dir.resolve("legacy.json"));
        assertEquals(legacy, WorldSaveMigration.resolveLoadFile(inWorld, current, legacy));
    }

    /** Once the world folder holds state, no older file may pull the world back in time. */
    @Test
    void theInWorldFileWinsOverThePreF1FileToo(@TempDir Path dir) throws IOException {
        Path inWorld = Files.createFile(dir.resolve("in-world.json"));
        Path current = dir.resolve("current.json");
        Path legacy  = Files.createFile(dir.resolve("legacy.json"));
        assertEquals(inWorld, WorldSaveMigration.resolveLoadFile(inWorld, current, legacy));
    }

    /**
     * Multiplayer: there is no world folder, so both callers hand in the same config path and the
     * legacy candidate is null. The duplicate must not confuse the walk.
     */
    @Test
    void multiplayerPassesTheSameConfigPathTwiceAndStillResolvesIt(@TempDir Path dir) throws IOException {
        Path config = Files.createFile(dir.resolve("server.json"));
        assertEquals(config, WorldSaveMigration.resolveLoadFile(config, config, null));
    }

    @Test
    void prefersTheCurrentFileWhenItAlreadyExists(@TempDir Path dir) throws IOException {
        Path current = dir.resolve("newid.json");
        Path legacy = dir.resolve("oldid.json");
        Files.writeString(current, "{}");
        Files.writeString(legacy, "{}");

        assertEquals(current, WorldSaveMigration.resolveLoadFile(current, legacy));
    }

    @Test
    void fallsBackToTheLegacyFileWhenOnlyItExists(@TempDir Path dir) throws IOException {
        Path current = dir.resolve("newid.json");
        Path legacy = dir.resolve("oldid.json");
        Files.writeString(legacy, "{}");

        assertEquals(legacy, WorldSaveMigration.resolveLoadFile(current, legacy));
    }

    @Test
    void returnsTheCurrentFileWhenNeitherExists(@TempDir Path dir) {
        Path current = dir.resolve("newid.json");
        Path legacy = dir.resolve("oldid.json");

        assertEquals(current, WorldSaveMigration.resolveLoadFile(current, legacy));
    }

    @Test
    void ignoresANullLegacyFileEvenWhenTheCurrentOneIsMissing(@TempDir Path dir) {
        // What BreedTimerClient passes for multiplayer and the "unknown" fallback, and for any
        // singleplayer world whose folder name already equals its level name: there is no legacy
        // id to fall back to because the two ids never diverged.
        Path current = dir.resolve("newid.json");

        assertEquals(current, WorldSaveMigration.resolveLoadFile(current, null));
    }

    @Test
    void aPreExistingCurrentFileWinsEvenWithALegacyFilePresent(@TempDir Path dir) throws IOException {
        // Once a world has been joined under the new scheme at least once, its own file exists and
        // migration must never run again -- even if the legacy file is still sitting there.
        Path current = dir.resolve("newid.json");
        Path legacy = dir.resolve("oldid.json");
        Files.writeString(current, "{\"cooldown\":{}}");
        Files.writeString(legacy, "{\"cooldown\":{\"stale\":1}}");

        Path resolved = WorldSaveMigration.resolveLoadFile(current, legacy);

        assertEquals(current, resolved);
        assertEquals("{\"cooldown\":{}}", Files.readString(resolved));
    }
}
