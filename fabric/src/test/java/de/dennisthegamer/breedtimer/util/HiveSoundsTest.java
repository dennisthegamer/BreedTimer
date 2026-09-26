package de.dennisthegamer.breedtimer.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A hive announces every bee it takes in and lets out: {@code addOccupant} plays
 * {@code BEEHIVE_ENTER} and {@code releaseOccupant} plays {@code BEEHIVE_EXIT}, both at the hive's
 * own block, to every player within 16 blocks. An in-game test on 26.09 heard 695 of them, and the hive a
 * bee's last position pointed at was the wrong one for three in four -- the hives in that farm
 * stand side by side and on top of each other. These tests pin the matching that lets the sound
 * name the hive instead.
 */
class HiveSoundsTest {

    private final HiveSounds sounds = new HiveSounds();

    private static final long HIVE_A = HiveHandover.key(-7, -59, 7);
    private static final long HIVE_B = HiveHandover.key(-8, -59, 7);

    @Test
    void aVanishedBeeGoesToTheHiveThatWasHeardEvenWhenANeighbourIsCloser() {
        sounds.heard(true, -7, -59, 7, 100);

        // Closer to the centre of -8 (at -7.5) than to that of -7 (at -6.5): the old guess.
        List<HiveSounds.Heard> matched = sounds.matchEnters(List.of(new HiveSounds.Pos(-7.9, -58.5, 8.3)));

        assertEquals(HIVE_A, matched.get(0).hive());
    }

    @Test
    void theMatchCarriesTheGameTimeTheSoundWasHeardAt() {
        sounds.heard(true, -7, -59, 7, 1234);

        assertEquals(1234, sounds.matchEnters(List.of(new HiveSounds.Pos(-6.5, -58.5, 8))).get(0).gameTime());
    }

    @Test
    void beesVanishingTogetherAreEachMatchedToTheHiveNearestThem() {
        sounds.heard(true, -7, -59, 7, 100);
        sounds.heard(true, -12, -59, 7, 100);

        List<HiveSounds.Heard> matched = sounds.matchEnters(List.of(
                new HiveSounds.Pos(-11.5, -58.5, 8), new HiveSounds.Pos(-6.5, -58.5, 8)));

        assertEquals(HiveHandover.key(-12, -59, 7), matched.get(0).hive());
        assertEquals(HIVE_A, matched.get(1).hive());
    }

    @Test
    void oneSoundIsOneBee() {
        sounds.heard(true, -7, -59, 7, 100);

        List<HiveSounds.Heard> matched = sounds.matchEnters(List.of(
                new HiveSounds.Pos(-6.5, -58.5, 8), new HiveSounds.Pos(-6.4, -58.5, 8.1)));

        assertEquals(1, matched.stream().filter(heard -> heard != null).count());
    }

    @Test
    void aSoundIsNotMatchedTwiceAcrossCalls() {
        sounds.heard(true, -7, -59, 7, 100);
        sounds.matchEnters(List.of(new HiveSounds.Pos(-6.5, -58.5, 8)));

        assertNull(sounds.matchEnters(List.of(new HiveSounds.Pos(-6.5, -58.5, 8))).get(0));
    }

    @Test
    void aBeeFarFromEveryHeardHiveIsLeftUnmatched() {
        sounds.heard(true, -7, -59, 7, 100);

        assertNull(sounds.matchEnters(List.of(new HiveSounds.Pos(-0.5, -58.5, 8))).get(0));
    }

    @Test
    void anExitSoundDoesNotExplainABeeGoingIn() {
        sounds.heard(false, -7, -59, 7, 100);

        assertNull(sounds.matchEnters(List.of(new HiveSounds.Pos(-6.5, -58.5, 8))).get(0));
        assertEquals(HIVE_A, sounds.matchExits(List.of(new HiveSounds.Pos(-6.5, -58.5, 8))).get(0).hive());
    }

    /**
     * The sound and the entity packet can land a frame apart, so a sound waits a little for its bee
     * -- but not for ever, or an old sound would explain some unrelated bee much later.
     */
    @Test
    void anUnclaimedSoundExpires() {
        sounds.heard(true, -7, -59, 7, 100);

        for (int i = 0; i < HiveSounds.WINDOW_TICKS; i++) sounds.tick();

        assertNull(sounds.matchEnters(List.of(new HiveSounds.Pos(-6.5, -58.5, 8))).get(0));
    }

    @Test
    void aSoundStillWaitsWithinItsWindow() {
        sounds.heard(true, -7, -59, 7, 100);

        for (int i = 0; i < HiveSounds.WINDOW_TICKS - 1; i++) sounds.tick();

        assertNotNull(sounds.matchEnters(List.of(new HiveSounds.Pos(-6.5, -58.5, 8))).get(0));
    }

    /**
     * Silence only means something where the player would have heard the hive: the server sends the
     * sound to everyone within 16 blocks of it. A margin keeps the edge, where it may or may not have
     * arrived, on the side of "could not tell".
     */
    @Test
    void earshotIsTheSoundsRangeLessAMargin() {
        assertTrue(HiveSounds.withinEarshot(-6.5 + 10, -58.5, 7.5, HIVE_A));
        assertFalse(HiveSounds.withinEarshot(-6.5 + 15, -58.5, 7.5, HIVE_A));
    }

    @Test
    void clearingForgetsEverySound() {
        sounds.heard(true, -7, -59, 7, 100);
        sounds.heard(false, -8, -59, 7, 100);

        sounds.clear();

        assertNull(sounds.matchEnters(List.of(new HiveSounds.Pos(-6.5, -58.5, 8))).get(0));
        assertNull(sounds.matchExits(List.of(new HiveSounds.Pos(-7.5, -58.5, 8))).get(0));
        assertFalse(HIVE_B == HiveHandover.NO_HIVE);
    }
}
