package de.dennisthegamer.breedtimer.util;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure geometry tests for {@link DropWindows#nearestUnambiguousIndex}, the part of the drop-window
 * attribution rule that decides which same-species adult a heard egg/scute sound belongs to. No
 * running level is needed -- the method takes plain positions.
 */
class DropWindowsTest {

    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);

    @Test
    void emptyListHasNoWinner() {
        assertEquals(-1, DropWindows.nearestUnambiguousIndex(List.of(), ORIGIN));
    }

    @Test
    void singleCandidateIsUnambiguous() {
        List<Vec3> positions = List.of(new Vec3(0.5, 0, 0));
        assertEquals(0, DropWindows.nearestUnambiguousIndex(positions, ORIGIN));
    }

    @Test
    void twoWellSeparatedCandidatesPickTheNearest() {
        List<Vec3> positions = List.of(new Vec3(0.5, 0, 0), new Vec3(-5, 0, 0));
        assertEquals(0, DropWindows.nearestUnambiguousIndex(positions, ORIGIN));
    }

    @Test
    void aRivalWithinTheAmbiguityRadiusOfTheWinnerDiscardsIt() {
        // Direct case: the only other candidate sits inside the 1-block ambiguity radius of the
        // winner (distance 0.5), even though it is farther from the packet than the winner.
        List<Vec3> positions = List.of(new Vec3(0.5, 0, 0), new Vec3(1.0, 0, 0));
        assertEquals(-1, DropWindows.nearestUnambiguousIndex(positions, ORIGIN));
    }

    @Test
    void aThirdCandidateFartherFromThePacketButCloseToTheWinnerStillTriggersAmbiguity() {
        // Regression test for a review finding: an earlier version of this method only compared the
        // winner against the second-nearest-TO-THE-PACKET candidate. Here that candidate (B) is far
        // enough from the winner (A) to pass -- but a THIRD candidate (C), farther from the packet
        // than B and so never checked by the old code, sits well inside the ambiguity radius of the
        // winner. A correct implementation must still refuse to pick a winner.
        Vec3 a = new Vec3(0.5, 0, 0);   // nearest to the packet: distance 0.5
        Vec3 b = new Vec3(0, 1.0, 0);   // second-nearest to the packet: distance 1.0; distance to A ~1.118 (passes)
        Vec3 c = new Vec3(1.4, 0, 0);   // third-nearest to the packet: distance 1.4; distance to A = 0.9 (ambiguous!)

        assertEquals(-1, DropWindows.nearestUnambiguousIndex(List.of(a, b, c), ORIGIN),
                "a third candidate close to the winner must still trigger the ambiguity rule, even "
                        + "though it is farther from the packet than the second candidate");
    }

    @Test
    void ambiguityHoldsRegardlessOfListOrder() {
        Vec3 a = new Vec3(0.5, 0, 0);
        Vec3 b = new Vec3(0, 1.0, 0);
        Vec3 c = new Vec3(1.4, 0, 0);

        assertEquals(-1, DropWindows.nearestUnambiguousIndex(List.of(c, a, b), ORIGIN));
        assertEquals(-1, DropWindows.nearestUnambiguousIndex(List.of(b, c, a), ORIGIN));
    }

    @Test
    void threeCandidatesAllFarApartPickTheNearestCleanly() {
        Vec3 a = new Vec3(0.5, 0, 0);
        Vec3 b = new Vec3(-5, 0, 0);
        Vec3 c = new Vec3(0, 5, 0);

        assertEquals(0, DropWindows.nearestUnambiguousIndex(List.of(a, b, c), ORIGIN));
    }

    @Test
    void exactlyOnTheAmbiguityRadiusCountsAsAmbiguous() {
        // <= AMBIGUITY_RADIUS, not <: the boundary itself must still refuse to guess.
        List<Vec3> positions = List.of(new Vec3(0, 0, 0), new Vec3(1.0, 0, 0));
        assertEquals(-1, DropWindows.nearestUnambiguousIndex(positions, ORIGIN));
    }
}
