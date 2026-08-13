package de.dennisthegamer.breedtimer.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BabyGrowthTrackerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void firstSightingSeedsFullGrowthAndDoesNotCountDownYet() {
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.tick(ID, false, 1);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS, t.remainingFor(ID));
    }

    @Test
    void subsequentTicksCountDownByDelta() {
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.tick(ID, false, 1);
        t.tick(ID, false, 100);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS - 100, t.remainingFor(ID));
    }

    @Test
    void estimateClampsAtZeroAndIsNotDropped() {
        // Dropping the entry would make the next tick re-seed the full grow time,
        // which is the "countdown loops back to 20:00" bug 1.6.0 fixed.
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.tick(ID, false, 1);
        t.tick(ID, false, BabyGrowthTracker.BABY_GROW_TICKS * 2);
        assertEquals(0, t.remainingFor(ID));
        t.tick(ID, false, 100);
        assertEquals(0, t.remainingFor(ID), "expired estimate must stay at 0, not re-seed");
    }

    @Test
    void ageLockedEstimateDoesNotAdvance() {
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.tick(ID, true, 1);
        t.tick(ID, true, 500);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS, t.remainingFor(ID));
    }

    @Test
    void ageLockFlipReseedsInBothDirections() {
        // setAgeLockedData() flips the flag AND calls setAge(getBabyStartAge()),
        // a full reset in both directions.
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.tick(ID, false, 1);
        t.tick(ID, false, 5000);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS - 5000, t.remainingFor(ID));

        t.tick(ID, true, 1);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS, t.remainingFor(ID), "lock must reseed");

        t.tick(ID, true, 5000);
        t.tick(ID, false, 1);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS, t.remainingFor(ID), "unlock must reseed too");
    }

    @Test
    void feedSpeedUpReproducesVanillaDoubleTruncation() {
        // getSpeedUpSecondsWhenFeeding(t) = (int)((t / 20) * 0.1f) seconds, then * 20 ticks.
        assertEquals(2400, BabyGrowthTracker.feedSpeedUpTicks(24000));
        assertEquals(0, BabyGrowthTracker.feedSpeedUpTicks(0));
        assertEquals(0, BabyGrowthTracker.feedSpeedUpTicks(-5));
    }

    @Test
    void feedingStopsHelpingUnderTenSeconds() {
        // 9 seconds: (int)((180/20) * 0.1f) = (int)0.9 = 0. Documented in CHANGELOG.
        assertEquals(0, BabyGrowthTracker.feedSpeedUpTicks(180), "0:09 must yield no speed-up");
        assertEquals(0, BabyGrowthTracker.feedSpeedUpTicks(199));
        assertEquals(20, BabyGrowthTracker.feedSpeedUpTicks(200), "0:10 is the first that helps");
    }

    @Test
    void onFedSeedsWithSpeciesGrowTimeNotTrackerDefault() {
        // A sniffer calf fed before its first tick() must not collapse to 24000.
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.onFed(ID, 48000);
        assertEquals(48000 - BabyGrowthTracker.feedSpeedUpTicks(48000), t.remainingFor(ID, 48000));
    }

    @Test
    void reduceBySeedsWhenNoEntryExists() {
        // Otherwise the first equine feed on an untracked foal is silently dropped.
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.reduceBy(ID, 400, BabyGrowthTracker.BABY_GROW_TICKS);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS - 400, t.remainingFor(ID));
    }

    @Test
    void reduceByFloorsAtZero() {
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.tick(ID, false, 1);
        t.reduceBy(ID, 999999, BabyGrowthTracker.BABY_GROW_TICKS);
        assertEquals(0, t.remainingFor(ID));
    }

    @Test
    void forgetDropsTheEstimateAndTheLockState() {
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.tick(ID, true, 1);
        t.forget(ID);
        t.tick(ID, false, 1);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS, t.remainingFor(ID),
                "a forgotten entity must not report a stale lock flip");
    }

    @Test
    void snapshotRestoreRoundTripsAndLeavesLockStateEmpty() {
        BabyGrowthTracker a = new BabyGrowthTracker();
        a.tick(ID, false, 1);
        a.tick(ID, false, 3000);
        Map<UUID, Integer> saved = new HashMap<>(a.snapshot());

        BabyGrowthTracker b = new BabyGrowthTracker();
        b.restore(saved);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS - 3000, b.remainingFor(ID));

        // Empty lockState means "no flip seen", so the first tick must not reseed.
        b.tick(ID, false, 100);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS - 3100, b.remainingFor(ID));
    }

    @Test
    void tadpoleTrackerUsesItsOwnGrowTime() {
        BabyGrowthTracker t = new BabyGrowthTracker(12000);
        t.tick(ID, false, 1);
        assertEquals(12000, t.remainingFor(ID));
    }

    @Test
    void setWritesTheExactFigureRatherThanAnyEstimate() {
        // A mob released from a bucket: no prior tick() call, so no guessed 20:00 was ever seeded.
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.set(ID, 1234);
        assertEquals(1234, t.remainingFor(ID));
    }

    @Test
    void setOverridesAnAlreadySeededEstimate() {
        // The ordinary case: the entity streamed into view and was ticked (seeding 20:00) in the same
        // tick the bucket-release match runs, so set() must win over whatever tick() guessed.
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.tick(ID, false, 1);
        assertEquals(BabyGrowthTracker.BABY_GROW_TICKS, t.remainingFor(ID));
        t.set(ID, 500);
        assertEquals(500, t.remainingFor(ID));
    }

    @Test
    void setClampsNegativeInputAtZero() {
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.set(ID, -50);
        assertEquals(0, t.remainingFor(ID));
    }

    @Test
    void setLeavesLockStateUntouchedSoTheNextTickIsTreatedAsAFirstSighting() {
        // Documented behaviour: set() does not touch lockState, so the next tick() call records the
        // current lock flag as the first sighting rather than comparing it to a stale one -- exactly
        // like any other newly seen baby.
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.set(ID, 5000);
        t.tick(ID, true, 100);
        // A first sighting with ageLocked=true must not decrement the figure set() just wrote.
        assertEquals(5000, t.remainingFor(ID), "first tick after set() must not treat the lock as a flip");
    }

    @Test
    void hasIsFalseUntilFirstSeenThenTrueAfterTickOrSet() {
        BabyGrowthTracker t = new BabyGrowthTracker();
        assertFalse(t.has(ID), "a tracker that has never seen this uuid must report no entry");
        t.tick(ID, false, 1);
        assertTrue(t.has(ID));

        BabyGrowthTracker t2 = new BabyGrowthTracker();
        assertFalse(t2.has(ID));
        t2.set(ID, 100);
        assertTrue(t2.has(ID));
    }

    @Test
    void forgetClearsHasToo() {
        BabyGrowthTracker t = new BabyGrowthTracker();
        t.set(ID, 100);
        assertTrue(t.has(ID));
        t.forget(ID);
        assertFalse(t.has(ID), "a forgotten entity must look exactly like one never seen");
    }
}
