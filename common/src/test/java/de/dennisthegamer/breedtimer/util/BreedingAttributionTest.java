package de.dennisthegamer.breedtimer.util;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rule that decides what an incoming entity event 18 meant. Vanilla broadcasts that byte from
 * exactly two places -- {@code Animal.setInLove} and {@code Animal.finalizeSpawnChildFromBreeding}
 * -- and only the second one produces a newborn, so a birth sighting is the one signal that tells
 * them apart without guessing.
 *
 * <p>No running level is needed: species are compared by identity through a plain {@code Object}
 * key (production passes {@code EntityType<?>}, these tests pass strings) and positions are plain
 * {@link Vec3}, the same trick {@code DropWindowsTest} uses.
 */
class BreedingAttributionTest {

    private static final Object COW = "cow";
    private static final Object SHEEP = "sheep";
    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);

    private final BreedingAttribution attribution = new BreedingAttribution();
    private final UUID parent = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    /**
     * The regression test for the reported bug. An animal whose {@code setInLove} we never saw --
     * fed before a relog, or out of view -- used to have its breeding read as a fresh love, which
     * replaced the real five-minute cooldown with a phantom thirty-second one.
     */
    @Test
    void birthResolvesAsBredEvenWhenWeNeverSawTheAnimalFallInLove() {
        attribution.onLoveEvent(parent, COW, ORIGIN, false);
        assertEquals(parent, attribution.claimBirth(COW, ORIGIN));
        assertTrue(attribution.tick(BreedingAttribution.BIRTH_WINDOW_TICKS).isEmpty(),
                "a claimed event must not resolve a second time when its window runs out");
    }

    @Test
    void noBirthAndNoBeliefInLoveResolvesAsSetInLove() {
        attribution.onLoveEvent(parent, COW, ORIGIN, false);
        assertEquals(Map.of(parent, BreedingAttribution.Verdict.IN_LOVE),
                attribution.tick(BreedingAttribution.BIRTH_WINDOW_TICKS));
    }

    /**
     * The fallback that keeps frogs and sniffers working. Both broadcast event 18 when they breed
     * but produce no baby -- a frog only sets {@code IS_PREGNANT} in its brain, a sniffer drops a
     * {@code sniffer_egg} item -- so for them the old "we thought it was in love" rule is the only
     * signal there is.
     */
    @Test
    void noBirthButWeBelievedItWasInLoveStillResolvesAsBred() {
        attribution.onLoveEvent(parent, COW, ORIGIN, true);
        assertEquals(Map.of(parent, BreedingAttribution.Verdict.BRED),
                attribution.tick(BreedingAttribution.BIRTH_WINDOW_TICKS));
    }

    @Test
    void pendingEventDoesNotResolveBeforeItsWindowRunsOut() {
        attribution.onLoveEvent(parent, COW, ORIGIN, false);
        assertTrue(attribution.tick(BreedingAttribution.BIRTH_WINDOW_TICKS - 1).isEmpty());
    }

    @Test
    void birthTooFarFromTheParentIsNotClaimed() {
        attribution.onLoveEvent(parent, COW, ORIGIN, false);
        double justOutside = Math.sqrt(BreedingAttribution.BIRTH_RADIUS_SQ) + 0.1;
        assertNull(attribution.claimBirth(COW, new Vec3(justOutside, 0, 0)));
    }

    @Test
    void birthOfAnotherSpeciesIsNotClaimed() {
        attribution.onLoveEvent(parent, COW, ORIGIN, false);
        assertNull(attribution.claimBirth(SHEEP, ORIGIN));
    }

    @Test
    void birthArrivingAfterTheWindowIsNotClaimed() {
        attribution.onLoveEvent(parent, COW, ORIGIN, false);
        attribution.tick(BreedingAttribution.BIRTH_WINDOW_TICKS);
        assertNull(attribution.claimBirth(COW, ORIGIN));
    }

    /**
     * One calf explains one parent. Vanilla broadcasts the breeding event on the goal owner alone,
     * so a second pending parent standing nearby must keep waiting for its own evidence rather than
     * be settled by somebody else's child -- the same consume-on-match rule the allay duplication
     * matcher uses.
     */
    @Test
    void oneBirthClaimsTheNearestPendingParentOnly() {
        attribution.onLoveEvent(parent, COW, ORIGIN, false);
        attribution.onLoveEvent(other, COW, new Vec3(1.0, 0, 0), false);

        assertEquals(parent, attribution.claimBirth(COW, new Vec3(0.1, 0, 0)));
        assertEquals(Map.of(other, BreedingAttribution.Verdict.IN_LOVE),
                attribution.tick(BreedingAttribution.BIRTH_WINDOW_TICKS));
    }

    @Test
    void aBirthWithNothingPendingIsSimplyIgnored() {
        assertNull(attribution.claimBirth(COW, ORIGIN));
    }

    @Test
    void clearDropsEverythingPending() {
        attribution.onLoveEvent(parent, COW, ORIGIN, false);
        attribution.clear();
        assertTrue(attribution.tick(BreedingAttribution.BIRTH_WINDOW_TICKS).isEmpty());
    }

    /**
     * The guard that keeps the newborn scan out of the common tick. Nothing is pending on almost
     * every tick of the game, and the scan it gates walks every loaded entity.
     */
    @Test
    void hasPendingIsTrueOnlyWhileAnEventIsUnresolved() {
        assertFalse(attribution.hasPending());
        attribution.onLoveEvent(parent, COW, ORIGIN, false);
        assertTrue(attribution.hasPending());
        attribution.tick(BreedingAttribution.BIRTH_WINDOW_TICKS);
        assertFalse(attribution.hasPending());
    }

    @Test
    void hasPendingGoesFalseAfterABirthConsumesTheOnlyEvent() {
        attribution.onLoveEvent(parent, COW, ORIGIN, false);
        attribution.claimBirth(COW, ORIGIN);
        assertFalse(attribution.hasPending());
    }
}
