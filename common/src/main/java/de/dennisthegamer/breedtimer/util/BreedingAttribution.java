package de.dennisthegamer.breedtimer.util;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decides what an incoming entity event 18 actually meant, free of Minecraft types beyond
 * {@link Vec3} so the rule stays testable without a running level.
 *
 * <p>Vanilla broadcasts byte 18 from exactly two places -- {@code Animal.setInLove} and
 * {@code Animal.finalizeSpawnChildFromBreeding} -- and the client is told which one it was by
 * nothing at all. The mod used to tell them apart by asking whether it already believed the animal
 * was in love, which is a guess about our own bookkeeping rather than an observation of the game.
 * That guess is wrong in two situations that happen constantly in a pen: when several fed animals
 * stand within {@code BreedGoal}'s eight blocks of each other the second parent is frequently
 * mis-identified, and once an animal carries a cooldown it never earned, its genuine breeding
 * arrives looking like a fresh love and wipes the real five-minute countdown out.
 *
 * <p>The signal used instead is the newborn. {@code Animal.spawnChildFromBreeding} snaps the child
 * onto the broadcasting parent's own position before adding it to the level, so a baby of the same
 * species appearing where the event came from <em>is</em> the breeding, observed rather than
 * inferred.
 *
 * <p>Two species breed without producing one, which is why {@code wasInLove} survives as a
 * fallback rather than being deleted: {@code Frog.spawnChildFromBreeding} passes a {@code null}
 * child and only sets {@code IS_PREGNANT} in its brain, and {@code Sniffer}'s drops a
 * {@code sniffer_egg} item. Both still broadcast 18 and still take the 6000-tick cooldown, so
 * without the fallback they would never show one again. Across all 1131 entity classes in 26.2
 * those two are the only overrides of {@code spawnChildFromBreeding}.
 *
 * <p>The fallback is safe here in a way it is not as the primary rule: a stale love entry expires
 * after {@code LOVE_MODE_TICKS} (600), while a real cooldown runs for 6000, so by the time vanilla
 * would let the animal be fed into love again the stale belief is long gone.
 */
public final class BreedingAttribution {

    /**
     * How long a pending event waits for its newborn. {@code finalizeSpawnChildFromBreeding}
     * broadcasts the event before {@code addFreshEntityWithPassengers} runs, and the entity tracker
     * flushes its add packet at the end of the server tick, so the two can arrive a tick or two
     * apart. Six ticks is generous for that and short enough that an unrelated baby streaming into
     * view has almost no chance to land inside it.
     */
    public static final int BIRTH_WINDOW_TICKS = 6;

    /**
     * How far the newborn may be from where the event came from, squared. The child starts at
     * exactly the parent's position, so this is pure headroom for the two of them pushing apart and
     * the parent walking on during the window.
     */
    public static final double BIRTH_RADIUS_SQ = 4.0;

    /** What a resolved event turned out to have been. */
    public enum Verdict { BRED, IN_LOVE }

    /**
     * One event whose meaning is not settled yet. {@code species} is compared by {@code equals}
     * and nothing else, so production can hand in an {@code EntityType<?>} while tests hand in a
     * string.
     */
    private record Pending(Object species, Vec3 pos, boolean wasInLove, int ticksLeft) {}

    /**
     * Keyed by parent because vanilla cannot send this animal a second event 18 inside the window:
     * {@code BreedGoal.tick} needs 60 ticks of courtship before it breeds, and {@code setInLove}
     * is unreachable while {@code inLove} is still running. Insertion-ordered so the nearest-match
     * tie-break below is deterministic.
     */
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();

    /**
     * Records an event 18 whose meaning is not known yet.
     *
     * @param wasInLove whether the mod believed this animal was in love mode when the event arrived
     */
    public void onLoveEvent(UUID parent, Object species, Vec3 pos, boolean wasInLove) {
        pending.put(parent, new Pending(species, pos, wasInLove, BIRTH_WINDOW_TICKS));
    }

    /**
     * Offers one newborn sighting to the events still waiting, and returns the parent it explains.
     *
     * <p>Consume-on-match, like the allay duplication matcher: one child is produced by one
     * breeding, so a calf that has settled one parent must not also settle another animal that
     * happened to fall in love beside it in the same fraction of a second.
     *
     * @return the parent now known to have bred, or {@code null} if no pending event fits
     */
    public UUID claimBirth(Object species, Vec3 pos) {
        UUID best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Map.Entry<UUID, Pending> entry : pending.entrySet()) {
            Pending candidate = entry.getValue();
            if (!candidate.species().equals(species)) continue;
            double distSq = candidate.pos().distanceToSqr(pos);
            if (distSq > BIRTH_RADIUS_SQ || distSq >= bestDistSq) continue;
            bestDistSq = distSq;
            best = entry.getKey();
        }
        if (best != null) pending.remove(best);
        return best;
    }

    /**
     * Advances every pending window by {@code delta} and reports the ones that ran out without a
     * newborn ever arriving.
     *
     * @return verdicts for the events that resolved on this tick; empty on almost every tick
     */
    public Map<UUID, Verdict> tick(int delta) {
        Map<UUID, Verdict> resolved = null;
        Iterator<Map.Entry<UUID, Pending>> entries = pending.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Pending> entry = entries.next();
            Pending event = entry.getValue();
            int left = event.ticksLeft() - delta;
            if (left > 0) {
                entry.setValue(new Pending(event.species(), event.pos(), event.wasInLove(), left));
                continue;
            }
            if (resolved == null) resolved = new HashMap<>();
            resolved.put(entry.getKey(), event.wasInLove() ? Verdict.BRED : Verdict.IN_LOVE);
            entries.remove();
        }
        return resolved == null ? Map.of() : resolved;
    }

    /**
     * Whether any event is still waiting for its verdict. Almost always false, which is what lets
     * {@code BreedCooldownHelper.tick} skip the newborn scan -- a walk over every loaded entity --
     * on the overwhelming majority of ticks.
     */
    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /** Nothing here outlives a world; see {@code BreedCooldownHelper.onWorldJoin}. */
    public void clear() {
        pending.clear();
    }
}
