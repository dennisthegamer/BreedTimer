package de.dennisthegamer.breedtimer.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Which file a world's saved state is read from, shared by {@link BreedCooldownHelper} and
 * {@link VillagerCooldownHelper} so the two per-world save schemes make exactly the same choice.
 *
 * <p>Two migrations are layered here, newest first.
 *
 * <p><strong>Singleplayer state lives in the world folder.</strong> It used to live in the config
 * directory under the world's save-folder name, on the reasoning that two worlds can never share a
 * save folder. That holds inside one Minecraft installation and breaks across instances: with
 * MultiMC, Prism or Pandora, three different worlds can each be called "New World", and if those
 * instances share a config directory the file one wrote was loaded by another. Observed in the
 * wild -- two such files held 496 and 351 animals with not a single UUID in common. A file inside
 * the world folder cannot collide, and it follows the world through copies, junctions and instance
 * switches instead of being left behind.
 *
 * <p><strong>Before that, F-1 moved the key</strong> from the level's display name to its
 * save-folder name (see {@code BreedTimerClient.resolveWorldIdentity}), because two worlds can
 * share a display name. Most worlds' folder name equals their level name, so the two ids coincide
 * and the caller passes null for the legacy candidate.
 *
 * <p>Nothing is written or deleted here: saves always go to the first candidate from that point on,
 * so an older file that gets read this way is left exactly as it was found.
 */
final class WorldSaveMigration {

    private WorldSaveMigration() {}

    /**
     * @param candidates the files that could hold this world's state, in order of preference:
     *                   where it is written today first, then each older location it might still
     *                   be sitting in. Null entries are skipped, which is how callers say "this
     *                   world never had that kind of id".
     * @return the first candidate that exists; otherwise the first candidate, unchanged, for the
     *         caller's own "no save yet" handling and as the file it will write to
     */
    static Path resolveLoadFile(Path... candidates) {
        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) return candidate;
        }
        return candidates[0];
    }
}
