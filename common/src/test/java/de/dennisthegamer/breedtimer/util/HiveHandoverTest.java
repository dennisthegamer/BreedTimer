package de.dennisthegamer.breedtimer.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A bee that enters a hive comes back out as a different entity: {@code BeehiveBlockEntity}
 * strips "UUID" in {@code IGNORED_BEE_TAGS}, so every UUID-keyed estimate the mod holds is
 * orphaned and the released bee is seeded from scratch -- a baby's countdown jumped back to the
 * full 20:00. These tests pin the handover that carries the estimate across that identity break.
 *
 * <p>They also pin what the handover must <em>not</em> claim to know. A hive holds up to three bees
 * and releases them in whatever order they each become eligible, so matching one that comes out to
 * one that went in is a guess more often than not.
 */
class HiveHandoverTest {

    private static final long HIVE = HiveHandover.key(10, 64, -30);
    /** Long enough that a bee which went in without nectar is allowed to leave. */
    private static final int A_MINUTE = 1200;
    private static final HiveHandover.Release ADULT = new HiveHandover.Release(false, false);
    private static final HiveHandover.Release BABY = new HiveHandover.Release(true, false);

    private final HiveHandover handover = new HiveHandover();

    private UUID parkAdult(int cooldown) {
        UUID bee = UUID.randomUUID();
        handover.park(bee, HIVE, new HiveHandover.State(cooldown, 0, 0, false), false);
        return bee;
    }

    /** A bee that vanished beside {@code hive} out of earshot: parked, but only as a guess. */
    private UUID parkGuessed(long hive, boolean baby, int figure) {
        UUID bee = UUID.randomUUID();
        handover.park(bee, hive, baby ? new HiveHandover.State(0, figure, 0, true)
                : new HiveHandover.State(figure, 0, 0, false), false, false, 0);
        return bee;
    }

    private void parkGuessed(long hive, int cooldown) {
        handover.park(UUID.randomUUID(), hive, new HiveHandover.State(cooldown, 0, 0, false),
                false, false, 0);
    }

    private UUID parkBaby(int growth) {
        UUID bee = UUID.randomUUID();
        handover.park(bee, HIVE, new HiveHandover.State(0, growth, 0, true), false);
        return bee;
    }

    @Test
    void babyGrowthSurvivesTheHiveAndIsChargedForTheTimeInside() {
        parkBaby(3000);

        handover.tick(A_MINUTE);
        HiveHandover.Claim claim = handover.claim(HIVE, true, false);

        assertNotNull(claim, "a baby bee leaving the hive it entered must be recognised");
        // updateBeeAge: age < 0 -> setAge(min(0, age + ticksInHive)), so the stay counts in full.
        assertEquals(1800, claim.after().growthTicks());
    }

    @Test
    void breedingCooldownSurvivesTheHiveAndIsChargedForTheTimeInside() {
        parkAdult(6000);

        handover.tick(A_MINUTE);
        HiveHandover.Claim claim = handover.claim(HIVE, false, false);

        assertNotNull(claim);
        // The other half of updateBeeAge: age > 0 -> setAge(max(0, age - ticksInHive)).
        assertEquals(4800, claim.after().cooldownTicks());
    }

    /**
     * {@code setInLove} grants 600 ticks and {@code BeeData.tick()} will not let a bee out until it
     * has been inside longer than its 600-tick minimum, so love cannot survive a lawful departure.
     * Pinned because it is the one carried figure that is provably always spent.
     */
    @Test
    void loveIsAlwaysSpentByTheTimeABeeMayLawfullyLeave() {
        UUID bee = UUID.randomUUID();
        handover.park(bee, HIVE, new HiveHandover.State(0, 0, 600, false), false);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE + 1);
        HiveHandover.Claim claim = handover.claim(HIVE, false, false);

        assertNotNull(claim);
        assertEquals(0, claim.after().loveTicks());
    }

    @Test
    void anAgeLockedBabyIsNotChargedForTheTimeInside() {
        parkBaby(3000);

        handover.tick(A_MINUTE);
        HiveHandover.Claim claim = handover.claim(HIVE, true, true);

        assertNotNull(claim);
        // updateBeeAge returns before touching age when isAgeLocked(), so the estimate must not move.
        assertEquals(3000, claim.after().growthTicks());
    }

    /**
     * A breeding the mod saw but could not attribute leaves a doubt behind, and a doubt runs on the
     * same clock as a measured cooldown. Dropping it at the hive would put the bee straight back on
     * "Ready" -- the very answer the doubt exists to avoid.
     */
    @Test
    void anUnattributedBreedingDoubtSurvivesTheHiveToo() {
        UUID bee = UUID.randomUUID();
        handover.park(bee, HIVE, new HiveHandover.State(0, 0, 0, false, 6000), false);

        handover.tick(A_MINUTE);
        HiveHandover.Claim claim = handover.claim(HIVE, false, false);

        assertNotNull(claim);
        assertEquals(4800, claim.after().doubtTicks());
    }

    // --- what the handover must refuse to guess ------------------------------------------------

    /**
     * {@code BeeData.tick()} returns {@code ticksInHive > minTicksInHive} against each occupant's own
     * figure, and {@code Occupant.of} sets that figure to 2400 for a bee carrying nectar against 600
     * for one that is not. A bee seen leaving after a minute therefore cannot be the nectar-laden one,
     * however long it has been sitting at the front of the list.
     */
    @Test
    void aNectarLadenBeeIsNotHandedToOneThatLeftTooEarly() {
        UUID withNectar = UUID.randomUUID();
        handover.park(withNectar, HIVE, new HiveHandover.State(6000, 0, 0, false), true);

        handover.tick(A_MINUTE);

        assertNull(handover.claim(HIVE, false, false),
                "1200 ticks is short of the 2400 a nectar-carrying bee owes the hive");
    }

    @Test
    void aNectarLadenBeeIsClaimableOnceItHasServedItsLongerStay() {
        handover.park(UUID.randomUUID(), HIVE,
                new HiveHandover.State(6000, 0, 0, false), true);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE_WITH_NECTAR + 1);
        HiveHandover.Claim claim = handover.claim(HIVE, false, false);

        assertNotNull(claim);
        assertEquals(3599, claim.after().cooldownTicks());
    }

    /**
     * The failure the first attempt at this fix shipped: two adults in one hive, one on a cooldown
     * and one carrying nothing, and whichever came out first took whichever entry happened to be
     * first in the list. The pick is still a guess -- nothing the client can see tells these two
     * apart -- but it must be reported as one, so the caller can decline to act on it.
     */
    @Test
    void twoIndistinguishableBeesInOneHiveMakeTheClaimAmbiguous() {
        parkAdult(6000);
        parkAdult(0);

        handover.tick(A_MINUTE);
        HiveHandover.Claim claim = handover.claim(HIVE, false, false);

        assertNotNull(claim);
        assertEquals(2, claim.candidates());
        assertTrue(claim.ambiguous(), "the mod cannot tell these two apart and must say so");
    }

    @Test
    void aBabyAndAnAdultInOneHiveAreNotAmbiguous() {
        parkBaby(3000);
        parkAdult(6000);

        handover.tick(A_MINUTE);
        HiveHandover.Claim claim = handover.claim(HIVE, true, false);

        assertNotNull(claim);
        assertEquals(1, claim.candidates(), "DATA_BABY_ID is synced, so it can discriminate");
        assertFalse(claim.ambiguous());
        assertEquals(1800, claim.after().growthTicks());
    }

    // --- A: the threshold has to tolerate the client's lag ---------------------------------------

    /**
     * Vanilla releases a bee the moment {@code ticksInHive > minTicksInHive}, and the client only
     * starts counting once it notices the bee is gone -- a tick or two later. Every claim therefore
     * arrives at the threshold from below, and a strict comparison refused roughly half of them.
     * Measured in an in-game test: one bee turned away at 2400, the next let through at 2402.
     */
    @Test
    void aBeeIsStillClaimableWhenTheClientsCountLandsJustShortOfTheMinimum() {
        parkAdult(6000);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE - 2);

        assertNotNull(handover.claim(HIVE, false, false),
                "the client's count lags vanilla's, so the threshold must be approached from below");
    }

    /** The tolerance absorbs measurement lag; it must not blur 600 against 2400. */
    @Test
    void theToleranceDoesNotLetANectarLadenBeeOutEarly() {
        handover.park(UUID.randomUUID(), HIVE, new HiveHandover.State(6000, 0, 0, false), true);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE_WITH_NECTAR - 200);

        assertNull(handover.claim(HIVE, false, false));
    }

    // --- B: when it cannot tell, it must not read low --------------------------------------------

    /**
     * Two adults in one hive are indistinguishable, so the pick is a guess either way. Taking the
     * longest remaining cooldown makes the guess fail safe: the timer can read too high, but it can
     * never drop wrongly or announce "Ready" for a bee that is still waiting.
     */
    @Test
    void anAmbiguousClaimHandsBackTheLongestCandidateSoATimerNeverReadsTooLow() {
        parkAdult(1000);
        parkAdult(6000);

        handover.tick(A_MINUTE);
        HiveHandover.Claim claim = handover.claim(HIVE, false, false);

        assertEquals(2, claim.candidates());
        assertEquals(6000, claim.before().cooldownTicks(), "the longer cooldown must win the tie");
        assertEquals(4800, claim.after().cooldownTicks());
    }

    @Test
    void anAmbiguousBabyClaimTakesTheLongestRemainingGrowth() {
        parkBaby(3000);
        parkBaby(20000);

        handover.tick(A_MINUTE);
        HiveHandover.Claim claim = handover.claim(HIVE, true, false);

        assertEquals(2, claim.candidates());
        assertEquals(18800, claim.after().growthTicks());
    }

    /** A doubt is a cooldown the mod could not confirm, so it counts for the same comparison. */
    @Test
    void aDoubtCountsWhenChoosingTheLongestCandidate() {
        parkAdult(1000);
        handover.park(UUID.randomUUID(), HIVE, new HiveHandover.State(0, 0, 0, false, 6000), false);

        handover.tick(A_MINUTE);
        HiveHandover.Claim claim = handover.claim(HIVE, false, false);

        assertEquals(4800, claim.after().doubtTicks());
        assertEquals(0, claim.after().cooldownTicks());
    }

    // --- which bee is due -----------------------------------------------------------------------

    /**
     * In daylight vanilla lets a bee out on the first tick its stay passes its minimum, so the bee in
     * front of us is the one that has <em>just</em> become due. An entry that has been due for
     * minutes and still not claimed belongs to a bee that left unseen. An in-game test on 26.09 handed such a
     * stale entry to a fresh bee over and over, because the old rule preferred the larger figure.
     */
    @Test
    void aBeeThatHasJustBecomeDueIsPreferredOverOneLongOverdue() {
        parkAdult(5000);
        handover.tick(3000);
        parkAdult(1000);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE + 1);
        HiveHandover.Claim claim = handover.claim(HIVE, false, false);

        assertEquals(1000, claim.before().cooldownTicks());
    }

    /**
     * The tolerance exists for measurement lag, not to put a bee that is not yet due on equal terms
     * with one that is. 10:57:38: a bee 31 ticks short of its stay took the entry of the bee that had
     * actually come out.
     */
    @Test
    void aDueBeeIsPreferredOverOneOnlyWithinTolerance() {
        parkAdult(1000);
        handover.tick(20);
        parkAdult(5000);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE - 20 + 1);
        HiveHandover.Claim claim = handover.claim(HIVE, false, false);

        assertEquals(1000, claim.before().cooldownTicks());
    }

    /**
     * Found in an in-game test on 26.09: a baby read 14:05 instead of 17:15 because the choice compared
     * the figures as they were parked, and a stale entry parked with more growth left had, after its
     * far longer stay was charged, less. Whatever else decides the pick, it must not read low this way.
     */
    @Test
    void aStaleEntryDoesNotWinOnTheFigureItWasParkedWith() {
        parkBaby(23482);
        handover.tick(4000);
        parkBaby(23108);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE + 1);
        HiveHandover.Claim claim = handover.claim(HIVE, true, false);

        assertEquals(23108 - HiveHandover.MIN_TICKS_IN_HIVE - 1, claim.after().growthTicks());
    }

    // --- several bees out of one hive in one tick -------------------------------------------------

    /**
     * At dawn every occupant leaves in the same tick. {@code tickOccupants} walks the occupant list
     * in the order the bees went in, and each released bee takes its id from
     * {@code ServerLevel.getNextEntityId} as it is built -- so the bees the caller passes in entity-id
     * order line up with the entries in the order they were parked.
     */
    @Test
    void beesReleasedTogetherAreMatchedInTheOrderTheyWentIn() {
        // The later bee carries the larger figure, so a pick by size would reverse the order.
        UUID first = parkAdult(1000);
        handover.tick(10);
        UUID second = parkAdult(3000);

        handover.tick(A_MINUTE);
        List<HiveHandover.Claim> claims = handover.claim(HIVE, List.of(ADULT, ADULT));

        assertEquals(first, claims.get(0).parkedUuid());
        assertEquals(second, claims.get(1).parkedUuid());
    }

    @Test
    void releasingExactlyTheBeesWaitingIsNotAGuess() {
        parkAdult(3000);
        parkAdult(1000);

        handover.tick(A_MINUTE);
        List<HiveHandover.Claim> claims = handover.claim(HIVE, List.of(ADULT, ADULT));

        assertFalse(claims.get(0).ambiguous());
        assertFalse(claims.get(1).ambiguous());
    }

    /** A surplus entry is most likely one whose bee left unseen long ago, so the oldest stays behind. */
    @Test
    void aSurplusEntryLeftOverByAMassReleaseIsTheOldest() {
        // The largest figure of the three, so a pick by size would take it first.
        UUID stale = parkAdult(9000);
        handover.tick(5000);
        parkAdult(3000);
        parkAdult(1000);

        handover.tick(A_MINUTE);
        List<HiveHandover.Claim> claims = handover.claim(HIVE, List.of(ADULT, ADULT));

        assertNotEquals(stale, claims.get(0).parkedUuid());
        assertNotEquals(stale, claims.get(1).parkedUuid());
        assertEquals(1, handover.parkedAt(HIVE));
    }

    @Test
    void babiesAndAdultsReleasedTogetherArePairedWithinTheirKind() {
        UUID adultIn = parkAdult(1000);
        handover.tick(10);
        UUID babyIn = parkBaby(9000);
        handover.tick(10);
        UUID laterAdultIn = parkAdult(3000);

        handover.tick(A_MINUTE);
        List<HiveHandover.Claim> claims = handover.claim(HIVE, List.of(ADULT, BABY, ADULT));

        assertEquals(adultIn, claims.get(0).parkedUuid());
        assertEquals(babyIn, claims.get(1).parkedUuid());
        assertEquals(laterAdultIn, claims.get(2).parkedUuid());
    }

    @Test
    void aReleasedBeeWithNothingLeftWaitingIsAnsweredWithNull() {
        UUID only = parkAdult(3000);

        handover.tick(A_MINUTE);
        List<HiveHandover.Claim> claims = handover.claim(HIVE, List.of(ADULT, ADULT));

        assertEquals(only, claims.get(0).parkedUuid());
        assertNull(claims.get(1));
    }

    // --- entries the mod only guessed at -----------------------------------------------------------

    /**
     * A bee that vanished beside a hive out of earshot may have gone in or may just have left the
     * player's tracking range. It is parked as a guess, and if it walks back in under its own UUID
     * the guess is withdrawn -- the regression of 18.09 was exactly this bee losing its timer.
     */
    @Test
    void aGuessedEntryIsWithdrawnWhenItsBeeComesBack() {
        UUID bee = UUID.randomUUID();
        handover.park(bee, HIVE, new HiveHandover.State(6000, 0, 0, false), false, false, 0);

        handover.tick(A_MINUTE);
        HiveHandover.Unparked back = handover.unpark(bee);

        assertNotNull(back);
        assertFalse(back.confirmed());
        assertEquals(4800, back.charged().cooldownTicks());
        assertEquals(0, handover.parkedAt(HIVE));
    }

    @Test
    void unparkingABeeNobodyParkedAnswersNull() {
        assertNull(handover.unpark(UUID.randomUUID()));
    }

    @Test
    void aClaimSaysWhetherItsEntryWasHeardGoingIn() {
        handover.park(UUID.randomUUID(), HIVE, new HiveHandover.State(6000, 0, 0, false), false, false, 0);

        handover.tick(A_MINUTE);

        assertFalse(handover.claim(HIVE, false, false).confirmed());
    }

    /** The stay starts when the hive's sound was heard, which can be a tick or two before the park. */
    @Test
    void aStayCanStartBeforeTheParkItself() {
        handover.park(UUID.randomUUID(), HIVE, new HiveHandover.State(6000, 0, 0, false), false, true, 590);

        handover.tick(11);

        assertEquals(6000 - 601, handover.claim(HIVE, false, false).after().cooldownTicks());
    }

    /**
     * A guessed entry may belong to the hive next door, so it says nothing about how full this one
     * is. In an in-game test confirmed bees going in pushed guessed entries out whose bees were
     * still inside, and those babies came out to a fresh 20:00.
     */
    @Test
    void aGuessedEntryDoesNotCountAgainstTheRoomConfirmedBeesHave() {
        parkAdult(1000);
        parkAdult(2000);
        parkGuessed(HIVE, false, 3000);

        handover.park(UUID.randomUUID(), HIVE, new HiveHandover.State(4000, 0, 0, false), false);

        assertEquals(4, handover.parkedAt(HIVE));
    }

    /**
     * A guess lands a hive or two off, so one hive collects the guesses of its neighbours as well as
     * its own. Replayed against an in-game test, a room of three pushed out guesses whose babies
     * were still inside; a room of three hives' worth kept all of them.
     */
    @Test
    void aHiveHoldsTheGuessesOfItsNeighboursToo() {
        for (int i = 1; i <= HiveHandover.MAX_PARKED_PER_HIVE + 1; i++) parkGuessed(HIVE, false, i * 1000);

        assertEquals(HiveHandover.MAX_PARKED_PER_HIVE + 1, handover.parkedAt(HIVE));
    }

    /**
     * No cap on guesses at all. With room for three hives' worth, an in-game test -- three rows
     * of hives stacked on each other -- still filled it, and eight parents lost their cooldowns within
     * twenty seconds as their guesses were pushed out. What bounds guesses instead is that they
     * resolve: withdrawn when the bee comes back, claimed when a bee comes out, dropped when stale.
     */
    @Test
    void guessesAreNeverPushedOut() {
        for (int i = 1; i <= 30; i++) parkGuessed(HIVE, i * 100);

        assertEquals(30, handover.parkedAt(HIVE));
    }

    /** The bound that replaces the cap: a guess nobody claims or withdraws is dropped all the same. */
    @Test
    void aGuessNobodyResolvesIsDroppedWhenStale() {
        parkGuessed(HIVE, false, 3000);

        handover.tick(HiveHandover.FORGET_AFTER_TICKS);

        assertEquals(0, handover.parkedAt(HIVE));
    }

    // --- a guess filed at the hive next door ---------------------------------------------------------

    private static final long NEXT_DOOR = HiveHandover.key(11, 64, -30);
    private static final long ROW_ABOVE = HiveHandover.key(10, 66, -30);
    private static final long ACROSS_THE_FARM = HiveHandover.key(30, 64, -30);

    /**
     * Out of earshot the hive is guessed from where the bee was last seen, and in a farm of hives
     * standing side by side that guess lands one or two blocks off. When the bee is then heard coming
     * out of its real hive and nothing is waiting there, the guess next door that has just come due
     * is that bee: an in-game test had every one of them within five ticks of its stay.
     */
    @Test
    void aBeeHeardComingOutTakesAGuessFiledAtTheHiveNextDoor() {
        UUID guessed = parkGuessed(NEXT_DOOR, true, 9000);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE + 1);
        HiveHandover.Claim claim = handover.claim(HIVE, true, false);

        assertNotNull(claim);
        assertEquals(guessed, claim.parkedUuid());
    }

    @Test
    void theRowAboveCountsAsNextDoorToo() {
        UUID guessed = parkGuessed(ROW_ABOVE, true, 9000);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE + 1);

        assertEquals(guessed, handover.claim(HIVE, true, false).parkedUuid());
    }

    @Test
    void aGuessAcrossTheFarmIsNotNextDoor() {
        parkGuessed(ACROSS_THE_FARM, true, 9000);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE + 1);

        assertNull(handover.claim(HIVE, true, false));
    }

    /** What was heard going in is known to be in its own hive; it cannot have come out of this one. */
    @Test
    void aConfirmedEntryNextDoorIsNeverTaken() {
        handover.park(UUID.randomUUID(), NEXT_DOOR, new HiveHandover.State(0, 9000, 0, true), false);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE + 1);

        assertNull(handover.claim(HIVE, true, false));
    }

    /**
     * Next door is a fallback for a bee that has just come due, not a place to pick up entries whose
     * bees left unseen long ago: a guess overdue by minutes says nothing about this bee.
     */
    @Test
    void aLongOverdueGuessNextDoorIsNotTaken() {
        parkGuessed(NEXT_DOOR, true, 9000);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE + 2000);

        assertNull(handover.claim(HIVE, true, false));
    }

    /**
     * A guess that still owes its hive time cannot be the bee that is already out. Replayed against
     * an in-game test, the full tolerance let a bee take a guess 16 ticks short of due -- the entry
     * of a bee that came out of its own hive 15 ticks later.
     */
    @Test
    void aGuessNextDoorThatIsNotYetDueIsNotTaken() {
        parkGuessed(NEXT_DOOR, true, 9000);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE - 16);

        assertNull(handover.claim(HIVE, true, false));
    }

    @Test
    void anEntryInTheHiveItselfIsPreferredOverAGuessNextDoor() {
        parkGuessed(NEXT_DOOR, true, 9000);
        UUID own = parkBaby(3000);

        handover.tick(HiveHandover.MIN_TICKS_IN_HIVE + 1);

        assertEquals(own, handover.claim(HIVE, true, false).parkedUuid());
    }

    // --- C: orphans must not pile up -------------------------------------------------------------

    /**
     * Every refused claim leaves its entry behind, and an in-game test showed five of them at a hive
     * that holds three. The surplus is what turns a two-way guess into a five-way one.
     */
    @Test
    void aHiveNeverHoldsMoreEntriesThanItHasRoomForBees() {
        for (int i = 1; i <= 5; i++) parkAdult(i * 1000);

        assertEquals(HiveHandover.MAX_PARKED_PER_HIVE, handover.parkedAt(HIVE));
    }

    @Test
    void theOldestEntryIsTheOneEvictedWhenAHiveOverflows() {
        parkAdult(1000);
        parkAdult(2000);
        parkAdult(3000);
        parkAdult(4000);

        handover.tick(A_MINUTE);
        List<Integer> handedBack = new ArrayList<>();
        for (int i = 0; i < HiveHandover.MAX_PARKED_PER_HIVE; i++) {
            handedBack.add(handover.claim(HIVE, false, false).before().cooldownTicks());
        }

        // Longest first, because an ambiguous claim now picks the most conservative candidate.
        assertEquals(List.of(4000, 3000, 2000), handedBack);
        assertFalse(handedBack.contains(1000), "the oldest entry was the one pushed out");
    }

    /** Crowding one hive must not evict entries belonging to a different one. */
    @Test
    void theCapIsCountedPerHiveNotAcrossAllOfThem() {
        long other = HiveHandover.key(200, 70, 200);
        handover.park(UUID.randomUUID(), other, new HiveHandover.State(6000, 0, 0, false), false);
        for (int i = 1; i <= 5; i++) parkAdult(i * 1000);

        assertEquals(1, handover.parkedAt(other));
        assertEquals(HiveHandover.MAX_PARKED_PER_HIVE, handover.parkedAt(HIVE));
    }

    // --- housekeeping --------------------------------------------------------------------------

    /** A guess next door is found by distance, which unpacks the key; the round trip has to hold. */
    @Test
    void aHiveKeySurvivesBeingUnpacked() {
        for (int[] xyz : new int[][] {{10, 64, -30}, {0, 0, 0}, {-2048, -64, 2048}, {30_000_000, 319, -30_000_000}}) {
            long key = HiveHandover.key(xyz[0], xyz[1], xyz[2]);
            assertEquals(xyz[0], HiveHandover.unpackX(key), "x");
            assertEquals(xyz[1], HiveHandover.unpackY(key), "y");
            assertEquals(xyz[2], HiveHandover.unpackZ(key), "z");
        }
    }

    /**
     * A bee can die in its hive, and a hive can be broken while the player is away. Without an
     * expiry those entries would sit in the list for the rest of the session.
     */
    @Test
    void aParkedBeeThatNeverComesBackIsDropped() {
        // Age-locked, so charging alone would never empty it: its growth estimate never decays.
        parkBaby(3000);

        handover.tick(HiveHandover.FORGET_AFTER_TICKS);

        assertNull(handover.claim(HIVE, true, true));
    }

    @Test
    void theSameBeeIsNotParkedTwice() {
        UUID bee = UUID.randomUUID();
        handover.park(bee, HIVE, new HiveHandover.State(6000, 0, 0, false), false);
        handover.park(bee, HIVE, new HiveHandover.State(6000, 0, 0, false), false);

        handover.tick(A_MINUTE);

        assertNotNull(handover.claim(HIVE, false, false));
        assertNull(handover.claim(HIVE, false, false), "one bee went in, so one bee can come out");
    }

    @Test
    void aBeeFromAnotherHiveIsNotClaimed() {
        parkAdult(6000);

        // Ticked past the minimum stay, so a null answer can only be about the hive.
        handover.tick(A_MINUTE);

        assertNull(handover.claim(HiveHandover.key(200, 70, 200), false, false));
    }

    @Test
    void anAdultDoesNotClaimTheEstimateOfTheBabyItSharesAHiveWith() {
        parkBaby(3000);

        handover.tick(A_MINUTE);

        assertNull(handover.claim(HIVE, false, false));
    }
}
