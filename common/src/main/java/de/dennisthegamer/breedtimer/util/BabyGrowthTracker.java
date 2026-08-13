package de.dennisthegamer.breedtimer.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Baby growth bookkeeping, free of Minecraft types so the math stays testable.
 *
 * <p>The client never learns an animal's real age: {@code AgeableMob} synchronises only the
 * {@code DATA_BABY_ID} and {@code AGE_LOCKED} booleans, never the {@code age} field itself.
 * Every value here is therefore an <em>estimate</em> that starts at {@link #BABY_GROW_TICKS}
 * the first time we see a baby, corrected by the two things a client can actually observe:
 * a feed by the local player ({@link #onFed}) and the age-lock flag.
 */
public final class BabyGrowthTracker {

    /** Mirrors {@code AgeableMob.BABY_START_AGE} (-24000). */
    public static final int BABY_GROW_TICKS = 24000;

    /** Mirrors the 0.1f in {@code AgeableMob.getSpeedUpSecondsWhenFeeding}. */
    private static final float FEED_SPEEDUP_FRACTION = 0.1f;

    private final Map<UUID, Integer> remaining = new HashMap<>();
    /** Last age-lock state seen per entity, so a flip can be told apart from a steady state. */
    private final Map<UUID, Boolean> lockState = new HashMap<>();

    /** How long a fresh entry starts at. Animals use {@link #BABY_GROW_TICKS}; tadpoles differ. */
    private final int growTicks;

    public BabyGrowthTracker() {
        this(BABY_GROW_TICKS);
    }

    /** For growth that is not an {@code AgeableMob}'s, such as a tadpole's run to {@code ticksToBeFrog}. */
    public BabyGrowthTracker(int growTicks) {
        this.growTicks = growTicks;
    }

    /**
     * Ticks a feed takes off the remaining growth time.
     *
     * <p>Reproduces vanilla exactly, including its truncation:
     * {@code getSpeedUpSecondsWhenFeeding(t) = (int) ((t / 20) * 0.1f)} seconds, applied through
     * {@code ageUp(seconds, true)} which adds {@code seconds * 20} to the age. The integer
     * division means feeding a nearly grown baby makes no progress at all — matching vanilla
     * rather than a naive 10% keeps our estimate from drifting away from the real animal.
     */
    public static int feedSpeedUpTicks(int remainingTicks) {
        if (remainingTicks <= 0) return 0;
        return (int) ((remainingTicks / 20) * FEED_SPEEDUP_FRACTION) * 20;
    }

    /**
     * Advances the estimate for one loaded baby by {@code delta} ticks.
     *
     * <p>The first sighting only registers the animal; an age-locked baby keeps its estimate
     * because {@code canAgeUp()} is {@code isBaby() && !isAgeLocked()}, so it never grows.
     * The estimate clamps at zero and is deliberately <em>not</em> dropped here: removing it
     * while the animal is still a baby would make the next call re-seed the full grow time and
     * the countdown would loop back to 20:00.
     */
    public void tick(UUID uuid, boolean ageLocked, int delta) {
        tick(uuid, ageLocked, delta, growTicks);
    }

    /**
     * As {@link #tick(UUID, boolean, int)}, but seeding new entries with {@code seedTicks} instead
     * of this tracker's default. A sniffer's {@code getBabyStartAge()} is -48000, twice every other
     * animal's, and it is the only override of that method in the game.
     *
     * <p>Toggling the age lock is not a pause. {@code AgeableMob.setAgeLockedData()} flips the flag
     * and then calls {@code setAge(getBabyStartAge())} — a full reset, in both directions — so a
     * golden dandelion restarts the whole growth clock. The flag is synced, so each observed flip
     * reseeds the estimate rather than merely freezing it.
     */
    public void tick(UUID uuid, boolean ageLocked, int delta, int seedTicks) {
        Boolean wasLocked = lockState.put(uuid, ageLocked);
        Integer current = remaining.get(uuid);
        if (current == null) {
            remaining.put(uuid, seedTicks);
            return;
        }
        if (wasLocked != null && wasLocked != ageLocked) {
            remaining.put(uuid, seedTicks);
            return;
        }
        if (ageLocked) return;
        remaining.put(uuid, Math.max(0, current - delta));
    }

    /**
     * Records that the local player fed this baby, taking the same cut off our estimate that
     * vanilla takes off the real age.
     */
    public void onFed(UUID uuid) {
        int current = remaining.getOrDefault(uuid, growTicks);
        remaining.put(uuid, Math.max(0, current - feedSpeedUpTicks(current)));
    }

    /**
     * As {@link #onFed(UUID)}, but seeding an unknown entity with its species' grow time.
     * The no-argument form falls back to this tracker's default, which is wrong for a sniffer
     * (48000) fed before its first {@code tick} entry exists.
     */
    public void onFed(UUID uuid, int seedTicks) {
        int current = remaining.getOrDefault(uuid, seedTicks);
        remaining.put(uuid, Math.max(0, current - feedSpeedUpTicks(current)));
    }

    /** Takes a flat number of ticks off the estimate, for feeds that are not proportional. */
    public void reduceBy(UUID uuid, int ticks) {
        Integer current = remaining.get(uuid);
        if (current == null) return;
        remaining.put(uuid, Math.max(0, current - ticks));
    }

    /**
     * As {@link #reduceBy(UUID, int)}, but seeding an unknown entity rather than doing nothing.
     * The two-argument form silently dropped the first feed on an animal whose estimate had not
     * been created yet.
     */
    public void reduceBy(UUID uuid, int ticks, int seedTicks) {
        int current = remaining.getOrDefault(uuid, seedTicks);
        remaining.put(uuid, Math.max(0, current - ticks));
    }

    /** Drops the estimate once the animal is known to have grown up. */
    public void forget(UUID uuid) {
        remaining.remove(uuid);
        lockState.remove(uuid);
    }

    /**
     * Replaces the estimate with a figure we actually know, rather than adjusting one we guessed.
     *
     * <p>Everything else here corrects an estimate: {@link #onFed} takes vanilla's proportional cut,
     * {@link #reduceBy} a flat one, and {@link #tick} counts down. This is the one entry point that has a real
     * number to write, and it exists for exactly one caller -- a mob released from a bucket, whose age travelled
     * across the release inside {@code BUCKET_ENTITY_DATA}. Do not use it to "correct" anything inferred; an
     * estimate written through this method would look exact to every reader downstream.
     *
     * <p>Note {@code lockState} is deliberately <em>not</em> touched: the next {@link #tick} call records the
     * current lock state as the first sighting, which is the same thing that happens for any newly seen baby.
     */
    public void set(UUID uuid, int remainingTicks) {
        remaining.put(uuid, Math.max(0, remainingTicks));
    }

    /**
     * Whether an estimate already exists for this entity. The one caller is the bucket-release match: a
     * freshly spawned baby has no entry yet, which is what tells it apart from a long-resident one of the
     * same species standing nearby when a bucket is emptied.
     */
    public boolean has(UUID uuid) {
        return remaining.containsKey(uuid);
    }

    /** Estimated ticks left, falling back to the full grow time for animals we have not seen. */
    public int remainingFor(UUID uuid) {
        return remaining.getOrDefault(uuid, growTicks);
    }

    /** As {@link #remainingFor(UUID)}, with a species-specific fallback. */
    public int remainingFor(UUID uuid, int seedTicks) {
        return remaining.getOrDefault(uuid, seedTicks);
    }

    public void clear() {
        remaining.clear();
        lockState.clear();
    }

    public Map<UUID, Integer> snapshot() {
        return new HashMap<>(remaining);
    }

    public void restore(Map<UUID, Integer> saved) {
        remaining.putAll(saved);
    }
}
