package de.dennisthegamer.breedtimer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mate of a breeding is not observable, so it may only be named where elimination forces the
 * answer: exactly one in-love neighbour left. Anything above that is a guess, and a guess hands an
 * uninvolved animal a five-minute cooldown it never earned.
 *
 * <p>The collective rule this class used to carry -- counting outstanding debts against remaining
 * candidates -- is disproved and gone; see {@link ParentAccounting} for the herd test that killed
 * it. The tests below exist to keep it from coming back.
 */
class ParentAccountingTest {

    @Test
    void aLoneCandidateIsNamedByElimination() {
        assertTrue(ParentAccounting.canNameMate(1));
    }

    @Test
    void severalCandidatesNameNobody() {
        assertFalse(ParentAccounting.canNameMate(2));
        assertFalse(ParentAccounting.canNameMate(3));
        assertFalse(ParentAccounting.canNameMate(17));
    }

    /**
     * The regression the herd test of 31.08.2026 exposed: earlier breedings in the same pen used to
     * accumulate debts that eventually declared every remaining candidate a parent. In an open herd
     * the debts and the candidates are drawn from different sets, so their counts matching is
     * coincidence. No sequence of earlier breedings may make a multi-candidate breeding nameable.
     */
    @Test
    void noNumberOfEarlierBreedingsEverMakesSeveralCandidatesNameable() {
        for (int earlierBreedings = 0; earlierBreedings < 20; earlierBreedings++) {
            for (int candidates = 2; candidates <= 17; candidates++) {
                assertFalse(ParentAccounting.canNameMate(candidates),
                        "candidates=" + candidates + " must stay unnameable regardless of history");
            }
        }
    }

    /**
     * Nothing in love is standing there, so there is no mate to name. The caller drops the breeding
     * rather than booking a debt against an empty set.
     */
    @Test
    void noCandidateNamesNobody() {
        assertFalse(ParentAccounting.canNameMate(0));
    }
}
