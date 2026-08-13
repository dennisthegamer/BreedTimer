package de.dennisthegamer.breedtimer.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The file-resolution decision behind F-1's singleplayer world-identity migration, shared by
 * {@link BreedCooldownHelper} and {@link VillagerCooldownHelper} so the two per-world save
 * schemes make exactly the same choice.
 *
 * <p>Singleplayer identity moved from the level's display name to its save-folder name (see
 * {@code BreedTimerClient.resolveWorldIdentity}), because two worlds can share a display name but
 * never a save folder. Most worlds' folder name already equals their level name, so the two ids
 * coincide and there is nothing to migrate -- the caller passes a null legacy file in that case
 * (and always does for multiplayer and the "unknown" fallback, which never had a different id to
 * begin with). When the ids do differ -- a renamed world, or one whose folder never matched its
 * name -- a fresh save-folder-keyed file will not exist yet the first time that world is joined
 * under the new scheme, so this picks up the old level-name-keyed file instead.
 *
 * <p>Nothing is written or deleted here: saves always go to the current id's file from that point
 * on (each helper's own {@code getSaveFile()} only ever uses the current id), so a legacy file
 * that gets read this way is left exactly as it was found.
 */
final class WorldSaveMigration {

    private WorldSaveMigration() {}

    /**
     * @param currentFile the file for the world's current (save-folder) id -- preferred whenever
     *                    it exists, migrated or not
     * @param legacyFile  the file for the world's legacy (level-name) id, or null when the two ids
     *                    already coincide and there is nothing to migrate from
     * @return {@code currentFile} if it exists or there is no legacy file to fall back to;
     *         otherwise {@code legacyFile} if that one exists; otherwise {@code currentFile}
     *         again, unchanged, for the caller's own "no save yet" handling
     */
    static Path resolveLoadFile(Path currentFile, Path legacyFile) {
        if (Files.exists(currentFile)) return currentFile;
        if (legacyFile != null && Files.exists(legacyFile)) return legacyFile;
        return currentFile;
    }
}
