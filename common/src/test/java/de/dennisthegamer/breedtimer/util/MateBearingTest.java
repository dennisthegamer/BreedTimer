package de.dennisthegamer.breedtimer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code BreedGoal.tick()} feeds {@code getLookControl().setLookAt(this.partner, ...)} and
 * {@code breed()} from the same {@code this.partner} field, so the head of the animal that
 * broadcasts entity event 18 is aimed at the animal it just bred with. Head rotation is one of the
 * few things vanilla does synchronise, which makes the mate observable rather than guessed.
 *
 * <p>Observable is not exact: the yaw arrives quantised to 1.40625 degrees, up to three ticks apart,
 * and is interpolated over three more on the client. These tests pin the rule that turns that into
 * an answer only where the answer is forced, and refuses it everywhere else -- a wrongly named mate
 * is worse than an unnamed one, because it hands a five-minute cooldown to an uninvolved animal.
 */
class MateBearingTest {

    /**
     * Vanilla's own convention, from {@code LookControl}: yaw 0 faces +Z, and east (+X) is -90.
     * Getting this backwards would name the animal opposite the real mate.
     */
    @Test
    void bearingFollowsTheVanillaYawConvention() {
        assertEquals(0.0, MateBearing.bearingDegrees(0.0, 1.0), 1e-9);
        assertEquals(-90.0, MateBearing.bearingDegrees(1.0, 0.0), 1e-9);
        assertEquals(90.0, MateBearing.bearingDegrees(-1.0, 0.0), 1e-9);
        assertEquals(180.0, Math.abs(MateBearing.bearingDegrees(0.0, -1.0)), 1e-9);
    }

    @Test
    void namesTheCandidateTheHeadIsAimedAt() {
        double[] bearings = {0.0, 90.0, -120.0};
        assertEquals(0, MateBearing.name(2.0, bearings));
        assertEquals(1, MateBearing.name(88.0, bearings));
        assertEquals(2, MateBearing.name(-118.0, bearings));
    }

    /**
     * The discontinuity at +/-180 is the one place a naive subtraction names the wrong animal: a
     * head at 179 and a candidate at -179 are two degrees apart, not 358.
     */
    @Test
    void measuresAcrossTheYawDiscontinuity() {
        assertEquals(0, MateBearing.name(179.0, new double[] {-179.0, 90.0}));
        assertEquals(0, MateBearing.name(-179.0, new double[] {179.0, 90.0}));
    }

    /**
     * Two candidates the head cannot separate. Naming either one is a coin flip, so the rule has to
     * decline -- this is the case the mod already handles by doubting everyone.
     */
    @Test
    void namesNobodyWhenTwoCandidatesSitTooCloseTogether() {
        assertEquals(-1, MateBearing.name(0.0, new double[] {0.0, MateBearing.MIN_MARGIN_DEGREES - 1}));
        assertEquals(-1, MateBearing.name(0.0, new double[] {5.0, -5.0}));
    }

    /**
     * The margin is measured between the best and the runner-up, so a third animal standing far
     * away must not rescue a pair that is itself ambiguous, and must not spoil a clear winner.
     */
    @Test
    void onlyTheRunnerUpDecidesTheMargin() {
        assertEquals(0, MateBearing.name(0.0, new double[] {0.0, 40.0, 170.0}));
        assertEquals(-1, MateBearing.name(0.0, new double[] {0.0, 2.0, 170.0}));
    }

    /**
     * The head points at nothing we are considering. That means the mate is not among the
     * candidates at all -- it may have been killed or streamed out -- and naming the closest of the
     * rest would be pure invention.
     */
    @Test
    void namesNobodyWhenTheHeadPointsAwayFromEveryCandidate() {
        assertEquals(-1, MateBearing.name(0.0, new double[] {MateBearing.MAX_OFF_DEGREES + 1, 150.0}));
    }

    /**
     * A single candidate has no runner-up to beat, so the margin cannot apply -- but the head must
     * still be pointing at it, otherwise the one animal left is not the mate either.
     */
    @Test
    void aLoneCandidateStillHasToBeAimedAt() {
        assertEquals(0, MateBearing.name(0.0, new double[] {10.0}));
        assertEquals(-1, MateBearing.name(0.0, new double[] {MateBearing.MAX_OFF_DEGREES + 1}));
    }

    @Test
    void namesNobodyWithoutCandidates() {
        assertEquals(-1, MateBearing.name(0.0, new double[] {}));
    }

    /**
     * The margin has to exceed the error the transport itself introduces, or the rule would be
     * naming animals out of rounding noise: the yaw is packed into a byte by
     * {@code Mth.packDegrees}, which is 360/256 degrees per step.
     */
    @Test
    void theMarginIsWiderThanTheWireQuantisation() {
        assertTrue(MateBearing.MIN_MARGIN_DEGREES > 360.0 / 256.0 * 2);
    }
}
