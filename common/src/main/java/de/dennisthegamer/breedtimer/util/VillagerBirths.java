package de.dennisthegamer.breedtimer.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Which courting villagers have been seen to produce a child.
 *
 * <p>A villager courtship is invisible from the client except at its ends: {@code
 * VillagerMakeLove.start()} broadcasts entity event 18 to both partners, and the birth it leads to
 * comes 275 to 324 ticks later. Whether it happens at all is not certain -- {@code canStillUse}
 * re-tests the pair every tick, so a partner that dies, is led away or never closes the distance
 * ends the behaviour with no child and no cooldown, and inventing one there is worse than missing
 * it.
 *
 * <p>The birth itself is observable, and exactly: {@code VillagerMakeLove.breed()} calls
 * {@code snapTo} to put the child on one of its parents, adds it -- which registers the tracker and
 * sends the add packet at once -- and then broadcasts entity event 12 to the child alone. The
 * villagers courting around the child at that instant are its parents.
 *
 * <p><strong>The pairing is decided at the birth, not at the end of the courtship, and that is the
 * point of this class.</strong> The mod's courtship timer runs to 325 ticks, so up to fifty of them
 * pass between the birth and the check -- ample time for either parent to walk off the spot its own
 * child was born on. Deciding at the end therefore lost the cooldown for pairs that really had
 * bred. Deciding at the birth cannot: nothing has moved yet.
 *
 * <p>This class holds only the answer, so it stays free of Minecraft types and testable; the radius
 * that produced the answer lives with the caller, which is the only side that can measure it.
 */
public final class VillagerBirths {

    /**
     * Villagers that witnessed a birth of their own and have not spent it yet. Small: a mark lives
     * only from the birth to the end of that courtship, at most fifty ticks.
     */
    private final Set<UUID> confirmed = new HashSet<>();

    /**
     * A child appeared, and these are the courting villagers standing at it.
     *
     * <p>A collection rather than a single parent because one child settles <em>two</em> villagers
     * here -- unlike the animal case, where the event names one broadcaster and the mate has to be
     * worked out.
     */
    public void witnessed(Collection<UUID> parents) {
        confirmed.addAll(parents);
    }

    /**
     * Whether this villager's courtship produced a child, spending the answer as it reports it: a
     * courtship is settled once, and a mark left lying around would let the villager's next one end
     * in a cooldown it never earned.
     */
    public boolean claim(UUID villager) {
        return confirmed.remove(villager);
    }

    /** Nothing here outlives a world; see {@code VillagerCooldownHelper.onWorldJoin}. */
    public void clear() {
        confirmed.clear();
    }
}
