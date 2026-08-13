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
