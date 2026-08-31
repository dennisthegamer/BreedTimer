package de.dennisthegamer.breedtimer.util;

/**
 * Names the second parent of a breeding in the one case where naming it is not a guess.
 *
 * <p>Vanilla broadcasts entity event 18 from the goal owner alone and sends nothing that names its
 * partner -- {@code finalizeSpawnChildFromBreeding} snaps the child onto the broadcaster, the
 * experience orb spawns there too, and {@code loveCause} never leaves the server. So the mate is
 * not observable. If exactly one animal near the broadcaster is still believed to be in love, it
 * is the mate by elimination; with more than one, the game does not say which, and the mod says
 * nothing rather than picking.
 *
 * <p><strong>This class used to do arithmetic, and the arithmetic was wrong.</strong> The idea was
 * conservation of parents: a birth consumes two animals in love, one of them nameable, so the other
 * was booked as a debt against the in-love neighbours around that spot; once as many debts stood as
 * there were neighbours left, every remaining neighbour had to be a parent and all could be named
 * at once. It holds in a closed group -- four animals fed together, two pairs breeding -- and that
 * is the case it was built and unit-tested for.
 *
 * <p>A herd test on 31.08.2026 (81 cows, four feeding waves, 304 events) disproved it. The debt
 * clustering asked whether a debt was booked within three blocks of <em>the current birth</em>,
 * while the candidate count came from the in-love neighbours of <em>one particular parent</em>. In
 * a herd everything is within three blocks of everything, so the two numbers were drawn from
 * different sets and their equality was coincidence rather than proof. It settled 13 of 108
 * breedings, and where it settled it was demonstrably wrong: single births named 7 and 5 mates at
 * once, animals were named as the mate of a foreign birth while broadcasting their own event 18 in
 * the same wave, and 155 event-18 broadcasts arrived from 60 of the 81 cows while the mod held them
 * on a five-minute cooldown -- which vanilla cannot do, since neither {@code setInLove} nor
 * {@code finalizeSpawnChildFromBreeding} is reachable while a real cooldown runs.
 *
 * <p>Kept as a class rather than folded into the caller for two reasons: it stays free of Minecraft
 * types and therefore unit-testable, which is how every decided rule in this mod is carried; and
 * the account above is the record that stops the arithmetic being reinvented.
 */
public final class ParentAccounting {

    private ParentAccounting() {}

    /**
     * Whether the mate of a breeding can be named without guessing.
     *
     * @param candidateCount how many in-love neighbours this breeding could have taken its mate from
     * @return {@code true} only when a single candidate makes the answer forced
     */
    public static boolean canNameMate(int candidateCount) {
        return candidateCount == 1;
    }
}
