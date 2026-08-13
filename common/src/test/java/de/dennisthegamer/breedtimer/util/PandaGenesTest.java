package de.dennisthegamer.breedtimer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PandaGenesTest {

    private static final double EPSILON = 1e-9;

    private static void assertRowSumsToOne(int mainA, int hiddenA, int mainB, int hiddenB) {
        double[] odds = PandaGenes.cubOdds(mainA, hiddenA, mainB, hiddenB);
        double sum = 0.0;
        for (double p : odds) sum += p;
        assertEquals(1.0, sum, EPSILON,
                "row for (" + mainA + "," + hiddenA + ")x(" + mainB + "," + hiddenB + ") must sum to 1");
    }

    @Test
    void everyRowSumsToOneForASpreadOfParentPairs() {
        assertRowSumsToOne(0, 0, 0, 0);
        assertRowSumsToOne(0, 4, 0, 4);
        assertRowSumsToOne(4, 4, 4, 4);
        assertRowSumsToOne(4, 4, 0, 4);
        assertRowSumsToOne(4, 4, 0, 0);
        assertRowSumsToOne(1, 1, 4, 4);
        assertRowSumsToOne(2, 5, 3, 6);
        assertRowSumsToOne(6, 6, 6, 6);
        assertRowSumsToOne(5, 5, 5, 5);
    }

    @Test
    void twoNormalParentsMatchExactAnchors() {
        // (NORMAL,NORMAL) x (NORMAL,NORMAL), exact fractions from the brief.
        double[] odds = PandaGenes.cubOdds(0, 0, 0, 0);
        assertEquals(260067.0 / 262144.0, odds[PandaGenes.NORMAL], EPSILON, "normal");
        assertEquals(1.0 / 65536.0, odds[PandaGenes.BROWN], EPSILON, "brown");
        assertEquals(25.0 / 262144.0, odds[PandaGenes.WEAK], EPSILON, "weak");
        // lazy, worried, playful, aggressive: each 1/512.
        assertEquals(1.0 / 512.0, odds[1], EPSILON, "lazy");
        assertEquals(1.0 / 512.0, odds[2], EPSILON, "worried");
        assertEquals(1.0 / 512.0, odds[3], EPSILON, "playful");
        assertEquals(1.0 / 512.0, odds[6], EPSILON, "aggressive");
    }

    @Test
    void brownOddsMatchTheFourWorkedAnchors() {
        // Exact fractions, independently re-derived (not transcribed from the brief's rounded
        // percentages) via a fraction-arithmetic script implementing the same formula.
        assertEquals(15625.0 / 65536.0, PandaGenes.cubOdds(0, 4, 0, 4)[PandaGenes.BROWN], EPSILON,
                "(N,BROWN) x (N,BROWN)");
        assertEquals(62001.0 / 65536.0, PandaGenes.cubOdds(4, 4, 4, 4)[PandaGenes.BROWN], EPSILON,
                "(BROWN,BROWN) x (BROWN,BROWN)");
        assertEquals(31125.0 / 65536.0, PandaGenes.cubOdds(4, 4, 0, 4)[PandaGenes.BROWN], EPSILON,
                "(BROWN,BROWN) x (N,BROWN)");
        assertEquals(249.0 / 65536.0, PandaGenes.cubOdds(4, 4, 0, 0)[PandaGenes.BROWN], EPSILON,
                "(BROWN,BROWN) x (N,N)");
    }

    @Test
    void coversTheExpressedGeneNotJustTheRecessiveOne() {
        // The four anchors above all read PandaGenes.BROWN off the expressed array. These two close
        // the gap by checking a NORMAL and a WEAK read from the same array, so a bug that only
        // affected indices other than BROWN could not slip through unnoticed.
        assertEquals(197571.0 / 262144.0, PandaGenes.cubOdds(0, 4, 0, 4)[PandaGenes.NORMAL], EPSILON,
                "(N,BROWN) x (N,BROWN), expressed NORMAL");
        assertEquals(64009.0 / 262144.0, PandaGenes.cubOdds(0, 5, 0, 5)[PandaGenes.WEAK], EPSILON,
                "(N,WEAK) x (N,WEAK), expressed WEAK");
    }

    @Test
    void mutationFloorForAGeneNeitherParentCarries() {
        // Neither parent carries WORRIED (id 2, non-recessive): floor is (1/32)*W(WORRIED) = (1/32)*(1/16) = 1/512.
        double[] odds = PandaGenes.cubOdds(0, 0, 0, 0);
        assertEquals(1.0 / 512.0, odds[2], EPSILON);
        // Neither parent carries WEAK (id 5, recessive): floor is ((1/32)*W(WEAK))^2 = (5/512)^2.
        assertEquals(Math.pow(5.0 / 512.0, 2), odds[PandaGenes.WEAK], EPSILON);
        // Neither parent carries BROWN (id 4, recessive): floor is ((1/32)*W(BROWN))^2 = (1/256)^2.
        assertEquals(Math.pow(1.0 / 256.0, 2), odds[PandaGenes.BROWN], EPSILON);
    }

    @Test
    void symmetricUnderSwappingTheTwoParents() {
        double[] ab = PandaGenes.cubOdds(1, 4, 0, 6);
        double[] ba = PandaGenes.cubOdds(0, 6, 1, 4);
        assertArrayEquals(ab, ba, EPSILON);
    }

    @Test
    void independenceTrapMainAndHiddenGeneAreNotIndependent() {
        // Regression test for the master plan's original error: a table built from the marginals
        // P(main) and P(hidden) treated as independent gives brown ~= 0.238, but the two slots are
        // drawn from the same coin-flipped pair of parents, so the true joint is very different.
        // Parents (LAZY,LAZY) x (BROWN,BROWN): brief's exact anchor is 0.00379944 for
        // P(main=BROWN and hidden=BROWN), not the marginal product 0.23841858.
        double[] mixed = PandaGenes.cubOdds(1, 1, 4, 4);
        assertEquals(249.0 / 65536.0, mixed[PandaGenes.BROWN], EPSILON,
                "must equal the exact joint, not the marginal product ~0.238");
        assertTrue(mixed[PandaGenes.BROWN] < 0.01, "must NOT be the ~23.8% a marginals-only table would give");
    }
}
