package de.dennisthegamer.breedtimer.util;

import de.dennisthegamer.breedtimer.util.BreedCooldownHelper.AnimalState;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How a cooldown the mod cannot prove is carried to the renderer.
 *
 * <p>A doubted cooldown is marked rather than given a state of its own. Every filter, count and
 * config switch in the mod branches on {@link AnimalState} through an exhaustive switch on every
 * supported Minecraft version, so a fourth constant would have to be handled in all of them --
 * which is what made {@code AGE_LOCKED} expensive. The doubt rides along as a flag instead, the
 * same way {@code BLOCKED} carries its {@code BlockReason}.
 *
 * <p>No level is needed: the record holds its animal without touching it.
 */
class AnimalTimerInfoTest {

    @Test
    void aMeasuredCooldownIsNotMarkedUncertain() {
        AnimalTimerInfo info = new AnimalTimerInfo(null, AnimalState.COOLDOWN, 100, 0);
        assertFalse(info.uncertain());
    }

    /**
     * The point of the flag: a doubted animal still counts, filters and sorts as a cooldown, so
     * nothing that switches on the state has to learn a new case.
     */
    @Test
    void aDoubtedCooldownStaysACooldownAndIsMarked() {
        AnimalTimerInfo info =
                new AnimalTimerInfo(null, AnimalState.COOLDOWN, 100, 0, null, true);
        assertEquals(AnimalState.COOLDOWN, info.state());
        assertTrue(info.uncertain());
    }

    /** A blocked animal is a measured fact, so the reason-carrying constructor must not mark it. */
    @Test
    void theBlockReasonConstructorLeavesTheTimerCertain() {
        AnimalTimerInfo info = new AnimalTimerInfo(null, AnimalState.BLOCKED, 40, 0,
                BreedCooldownHelper.BlockReason.HURT);
        assertFalse(info.uncertain());
    }
}
