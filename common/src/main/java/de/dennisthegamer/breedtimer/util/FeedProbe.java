package de.dennisthegamer.breedtimer.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns the player's own feeding into evidence about a cooldown the client cannot see.
 *
 * <p>{@code Animal.age} is never synchronised, so an adult that already bred looks exactly like one
 * that is free -- which is why the mod's mate attribution has to leave some animals undecided. But
 * offering food is an experiment with an observable answer. The client runs
 * {@code Animal.mobInteract} for an adult too: the food test passes, the {@code ServerPlayer}
 * branch is skipped because the local player is not one, {@code canAgeUp()} fails for an adult, and
 * it returns {@code CONSUME}. So the attempt itself is visible here.
 *
 * <p>What follows decides it. Vanilla broadcasts entity event 18 from {@code setInLove}, and
 * {@code setInLove} is only reached when the server accepted the food -- which for an adult means
 * its age had run back to zero. Hearts therefore prove the animal was free; silence proves the
 * server refused it, and the only thing that refuses food to a healthy, tame, unblocked adult is
 * the breeding cooldown.
 *
 * <p>Only UUIDs cross this class's boundary, so the whole rule is testable without a level.
 */
public final class FeedProbe {

    /**
     * How long to wait for the hearts before calling it a refusal. One second: the packet has to
     * make a server round trip, which is immediate in singleplayer and still comfortable on a
     * remote server, while staying short enough that the answer feels like part of the click.
     */
    public static final int PROBE_TICKS = 20;

    /** Feeds waiting for their answer. Empty except in the second after the player feeds something. */
    private final Map<UUID, Integer> waiting = new HashMap<>();

    /** The local player just offered food to this adult. */
    public void onFeedAttempt(UUID uuid) {
        waiting.put(uuid, PROBE_TICKS);
    }

    /** Hearts arrived for this animal, so the server accepted: it was not on cooldown after all. */
    public void onLoveSeen(UUID uuid) {
        waiting.remove(uuid);
    }

    /**
     * Advances the waiting feeds and reports the ones that were never answered.
     *
     * @return animals now known to have been refused, i.e. on a breeding cooldown
     */
    public List<UUID> tick(int delta) {
        if (waiting.isEmpty()) return List.of();
        List<UUID> refused = null;
        Iterator<Map.Entry<UUID, Integer>> entries = waiting.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Integer> entry = entries.next();
            int left = entry.getValue() - delta;
            if (left > 0) {
                entry.setValue(left);
                continue;
            }
            if (refused == null) refused = new ArrayList<>();
            refused.add(entry.getKey());
            entries.remove();
        }
        return refused == null ? List.of() : refused;
    }

    /** Nothing here outlives a world; see {@code BreedCooldownHelper.onWorldJoin}. */
    public void clear() {
        waiting.clear();
    }
}
