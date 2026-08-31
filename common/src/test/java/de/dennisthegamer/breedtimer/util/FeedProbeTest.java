package de.dennisthegamer.breedtimer.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A refused feed is the one thing the player can do that settles what the client cannot see.
 *
 * <p>Offering food to an adult runs {@code Animal.mobInteract} on the client too -- it falls
 * through the {@code ServerPlayer} branch, fails {@code canAgeUp()} and returns {@code CONSUME} --
 * so the attempt is observable. What follows tells us the answer: hearts (entity event 18) mean the
 * animal was free to fall in love, and silence means the server refused it, which for an adult can
 * only be its breeding cooldown.
 */
class FeedProbeTest {

    private final FeedProbe probe = new FeedProbe();
    private final UUID cow = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void aFeedAnsweredByHeartsReportsNothing() {
        probe.onFeedAttempt(cow);
        probe.onLoveSeen(cow);
        assertEquals(List.of(), probe.tick(FeedProbe.PROBE_TICKS));
    }

    @Test
    void aFeedThatGoesUnansweredReportsTheAnimalAsRefused() {
        probe.onFeedAttempt(cow);
        assertEquals(List.of(cow), probe.tick(FeedProbe.PROBE_TICKS));
    }

    @Test
    void aFeedStillWaitingForItsAnswerReportsNothingYet() {
        probe.onFeedAttempt(cow);
        assertEquals(List.of(), probe.tick(FeedProbe.PROBE_TICKS - 1));
    }

    @Test
    void aRefusalIsReportedOnlyOnce() {
        probe.onFeedAttempt(cow);
        probe.tick(FeedProbe.PROBE_TICKS);
        assertEquals(List.of(), probe.tick(FeedProbe.PROBE_TICKS));
    }

    /** Hearts from an animal nobody just fed are none of this class's business. */
    @Test
    void heartsWithoutAPendingFeedAreIgnored() {
        probe.onLoveSeen(cow);
        assertEquals(List.of(), probe.tick(FeedProbe.PROBE_TICKS));
    }

    @Test
    void oneAnimalsAnswerDoesNotSettleAnother() {
        probe.onFeedAttempt(cow);
        probe.onFeedAttempt(other);
        probe.onLoveSeen(other);
        assertEquals(List.of(cow), probe.tick(FeedProbe.PROBE_TICKS));
    }

    /** Feeding again restarts the wait rather than letting the older attempt time out under it. */
    @Test
    void feedingAgainRestartsTheWait() {
        probe.onFeedAttempt(cow);
        probe.tick(FeedProbe.PROBE_TICKS - 1);
        probe.onFeedAttempt(cow);
        assertEquals(List.of(), probe.tick(1));
    }

    @Test
    void clearDropsEveryPendingFeed() {
        probe.onFeedAttempt(cow);
        probe.clear();
        assertEquals(List.of(), probe.tick(FeedProbe.PROBE_TICKS));
    }
}
