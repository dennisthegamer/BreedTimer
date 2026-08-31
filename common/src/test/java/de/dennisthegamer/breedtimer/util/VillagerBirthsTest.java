package de.dennisthegamer.breedtimer.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A villager courtship ends with a cooldown only where a birth actually happened, and the pairing
 * has to be decided at the instant of the birth rather than when the courtship timer runs out.
 * Vanilla gives birth 275 to 324 ticks into a courtship the mod checks at 325, so up to fifty ticks
 * pass in between -- long enough for either parent to walk out of range of the spot its own child
 * was born on, which is how a pair that had just bred ended up with no cooldown at all.
 */
class VillagerBirthsTest {

    private static final UUID ONE = UUID.nameUUIDFromBytes(new byte[] {1});
    private static final UUID TWO = UUID.nameUUIDFromBytes(new byte[] {2});
    private static final UUID FAR = UUID.nameUUIDFromBytes(new byte[] {3});

    /**
     * The child is placed on one parent and the pair is within sqrt(5) blocks of each other, so one
     * birth settles two villagers -- and the second must still find it after the first has taken it.
     */
    @Test
    void bothParentsWitnessingOneBirthAreBothConfirmed() {
        VillagerBirths births = new VillagerBirths();
        births.witnessed(List.of(ONE, TWO));
        assertTrue(births.claim(ONE));
        assertTrue(births.claim(TWO));
    }

    /** The case the class exists for: a villager that witnessed nothing gets no cooldown. */
    @Test
    void aVillagerThatWitnessedNoBirthIsNotConfirmed() {
        VillagerBirths births = new VillagerBirths();
        births.witnessed(List.of(ONE, TWO));
        assertFalse(births.claim(FAR));
    }

    @Test
    void nothingIsConfirmedWithoutABirth() {
        assertFalse(new VillagerBirths().claim(ONE));
    }

    /**
     * Claiming is what settles a courtship, and a courtship is settled once. Leaving the mark behind
     * would let the villager's next courtship end in a cooldown it never earned.
     */
    @Test
    void aConfirmationIsSpentWhenItIsClaimed() {
        VillagerBirths births = new VillagerBirths();
        births.witnessed(List.of(ONE));
        assertTrue(births.claim(ONE));
        assertFalse(births.claim(ONE));
    }

    /**
     * Two births before either courtship is checked -- a breeder does this constantly. Each parent
     * has to keep its own mark rather than one birth overwriting the other.
     */
    @Test
    void marksFromSeparateBirthsDoNotDisplaceEachOther() {
        VillagerBirths births = new VillagerBirths();
        births.witnessed(List.of(ONE));
        births.witnessed(List.of(TWO));
        assertTrue(births.claim(ONE));
        assertTrue(births.claim(TWO));
    }

    @Test
    void clearingDropsEverything() {
        VillagerBirths births = new VillagerBirths();
        births.witnessed(List.of(ONE));
        births.clear();
        assertFalse(births.claim(ONE));
    }
}
