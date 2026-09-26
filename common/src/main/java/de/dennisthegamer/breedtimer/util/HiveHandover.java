package de.dennisthegamer.breedtimer.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Carries a bee's estimates across the identity break a hive stay causes.
 *
 * <p>A bee that enters a hive is not stored as an entity: {@code BeehiveBlockEntity.Occupant.of}
 * saves its NBT and then strips {@code IGNORED_BEE_TAGS}, a list whose last entry is literally
 * {@code "UUID"}. {@code Occupant.createEntity} strips the same list again before
 * {@code loadEntityRecursive}, so the released bee is built with a freshly generated UUID. Every
 * estimate in this mod is keyed by UUID, so the bee that comes out is a stranger: its growth
 * estimate was re-seeded at the full grow time and its cooldown read "Ready".
 *
 * <p>Vanilla does not pause the bee while it is inside. {@code setBeeReleaseData} charges the whole
 * stay against the bee on the way out — {@code updateBeeAge} moves {@code age} by
 * {@code ticksInHive} toward zero, and {@code setInLoveTime} subtracts it outright — so this class
 * has to charge the stay too, rather than hand back the frozen figures the mod stopped ticking when
 * the entity unloaded.
 *
 * <p>Deliberately free of Minecraft types, like {@link FeedProbe} and {@link BreedingAttribution}:
 * the interesting part is the bookkeeping, and keeping it plain makes it testable without a game.
 */
public final class HiveHandover {

    /**
     * What the mod knew about a bee the moment it vanished into a hive. Zero means "nothing to
     * carry" for each countdown, which is the common case for all three at once — an ordinary
     * forager is neither on cooldown, nor a baby, nor in love.
     */
    public record State(int cooldownTicks, int growthTicks, int loveTicks, boolean baby,
                        int doubtTicks) {

        /** The common case: whatever this bee carries, nobody is doubting it. */
        public State(int cooldownTicks, int growthTicks, int loveTicks, boolean baby) {
            this(cooldownTicks, growthTicks, loveTicks, baby, 0);
        }
    }

    /**
     * The outcome of a claim.
     *
     * <p>{@code candidates} says how honest the answer is. Matching a released bee to a parked one
     * is a guess whenever a hive held more bees of the same kind than it let out, and the caller
     * books a guessed cooldown as a doubt rather than as a measured one.
     */
    public record Claim(UUID parkedUuid, State before, State after, int candidates, boolean confirmed) {
        /** Whether the hive offered more than one bee this claim could have been. */
        public boolean ambiguous() {
            return candidates > 1;
        }
    }

    /** One bee a hive let out, as the caller sees it. */
    public record Release(boolean baby, boolean ageLocked) {}

    /** A withdrawn entry: the bee came back under its own UUID, so it was never inside at all. */
    public record Unparked(State charged, boolean confirmed) {}

    /** One parked bee, with the stay it has accrued so far. */
    private static final class Parked {
        final UUID uuid;
        final long hive;
        final State state;
        /**
         * The stay vanilla requires of this particular bee before it may leave: 600 ticks, or 2400 if
         * it went in carrying nectar. Recorded because {@code BeeData.tick()} returns
         * {@code ticksInHive > minTicksInHive} against the occupant's <em>own</em> figure, so two bees
         * in one hive can become eligible in the opposite order to the one they entered in.
         */
        final int minTicksInHive;
        final boolean confirmed;
        /** Ticks since the bee vanished — our stand-in for the {@code ticksInHive} we cannot read. */
        int elapsed;

        Parked(UUID uuid, long hive, State state, int minTicksInHive, boolean confirmed, int elapsed) {
            this.uuid = uuid;
            this.hive = hive;
            this.state = state;
            this.minTicksInHive = minTicksInHive;
            this.confirmed = confirmed;
            this.elapsed = elapsed;
        }

    }

    /** {@code Occupant.of}: 2400 ticks when the bee went in with nectar, 600 when it did not. */
    public static final int MIN_TICKS_IN_HIVE_WITH_NECTAR = 2400;
    public static final int MIN_TICKS_IN_HIVE = 600;

    /**
     * How far short of its minimum stay a bee may be and still be recognised.
     *
     * <p>Vanilla releases a bee the tick {@code ticksInHive > minTicksInHive} first holds, so every
     * bee leaves at its threshold rather than somewhere past it. Our count of that stay starts later
     * than vanilla's -- it begins when the client notices the entity is gone, not when the hive took
     * it -- so it always arrives at the threshold from below, and a strict comparison threw away
     * roughly half of all claims. An in-game test caught it exactly: one bee refused at 2400, the
     * next let through at 2402, a tenth of a second apart.
     *
     * <p>Two seconds is far more than the lag ever observed, and still nowhere near the 1800 ticks
     * that separate a nectar-laden bee's stay from an empty one's, so it costs nothing in
     * discrimination.
     */
    public static final int CLAIM_TOLERANCE_TICKS = 40;

    /**
     * {@code BeehiveBlockEntity.MAX_OCCUPANTS}. A hive cannot hold more bees than this, so it cannot
     * owe us more estimates than this either. The cap matters because a refused claim leaves its
     * entry behind: the same test showed five entries at a three-bee hive, and every surplus one
     * makes the next guess worse.
     */
    public static final int MAX_PARKED_PER_HIVE = 3;


    /**
     * How far from the hive a bee was heard leaving a guessed entry may have been filed and still be
     * that bee. A guess comes from the bee's last position, and in an in-game test it landed one
     * to three blocks along the row or two blocks up in the row above -- never further than 3.6.
     */
    public static final double NEIGHBOUR_RADIUS = 4.0;

    /**
     * How far short of due a guess next door may be and still be taken. A guess is timed from when
     * the bee vanished, not from a sound, and in an in-game test every one that was the bee in
     * front of us sat at 2399 to 2405 of 2400. Anything shorter still owes its hive time and cannot be
     * out yet; replayed with the full {@link #CLAIM_TOLERANCE_TICKS} a bee took a guess 16 ticks short,
     * the entry of a bee that came out of its own hive a moment later.
     */
    public static final int NEIGHBOUR_EARLY_TICKS = 5;

    /**
     * How long an unclaimed entry is held. A bee can legitimately stay in overnight, and there is no
     * vanilla cap on {@code ticksInHive}, so the window cannot be tight; this is the same hour
     * {@code BreedCooldownHelper.FORGET_AFTER_UNSEEN_TICKS} already calls the point past which a
     * figure is worthless, reused so the mod has one answer to that question rather than two.
     *
     * <p>A long window is cheap here because a stale entry carries nothing: {@link #charge} has been
     * subtracting the whole time, so by the time one is old enough to be claimed by the wrong bee
     * every countdown in it has already been charged to zero and the claim changes nothing. The
     * exception is an age-locked baby, whose estimate never decays — which is exactly why this
     * expiry exists at all.
     */
    public static final int FORGET_AFTER_TICKS = 72000;

    private final List<Parked> parked = new ArrayList<>();

    /** Returned by a hive lookup that found nothing; no real block position packs to it. */
    public static final long NO_HIVE = Long.MIN_VALUE;

    /**
     * Packs a block position into one long, so hives can key the list without allocating.
     *
     * <p>The same scheme {@code BlockPos.asLong} uses, reimplemented rather than called so this
     * class stays Minecraft-free and so exactly one definition exists for callers to agree on.
     */
    public static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    /**
     * Undoes {@link #key}, so hives can be compared by position -- a guess filed next door is found
     * by its distance. The shifts mirror {@code BlockPos.getX} and its siblings:
     * 26 bits of x, 26 of z, 12 of y, each sign-extended back out.
     */
    public static int unpackX(long key) {
        return (int) (key >> 38);
    }

    public static int unpackY(long key) {
        return (int) (key << 52 >> 52);
    }

    public static int unpackZ(long key) {
        return (int) (key << 26 >> 38);
    }

    /**
     * A tracked bee just vanished at {@code hive}; hold what we knew about it.
     *
     * <p>Ignores a bee already parked. The caller only fires this on the tick a bee stops being
     * loaded, but a second entry for one bee would put a phantom occupant in the hive and hand its
     * estimate to whichever bee came out next.
     *
     * <p>A bee carrying nothing at all — the ordinary forager, neither on cooldown nor a baby nor in
     * love — is parked all the same, even though its entry can only ever hand back zeroes. Skipping
     * those would misalign the order {@link #claim} relies on: if an empty bee and a bee on cooldown
     * both go in and only the second is parked, the first one out claims the cooldown that belongs to
     * the second. An entry nobody needs is cheap; one handed to the wrong bee is the original bug.
     */
    public void park(UUID uuid, long hive, State state, boolean nectar) {
        park(uuid, hive, state, nectar, true, 0);
    }

    /**
     * @param confirmed    whether the hive was heard taking the bee in. An unconfirmed entry is a
     *                     guess -- the bee vanished beside a hive out of earshot, and may just as well
     *                     have left the player's tracking range -- and {@link #unpark} withdraws it if
     *                     the bee walks back in under its own UUID
     * @param elapsedSoFar the stay already served when the entry is made: the hive's sound can arrive
     *                     a tick or two before the caller gets round to parking
     */
    public void park(UUID uuid, long hive, State state, boolean nectar, boolean confirmed,
                     int elapsedSoFar) {
        if (isParked(uuid)) return;
        parked.add(new Parked(uuid, hive, state,
                nectar ? MIN_TICKS_IN_HIVE_WITH_NECTAR : MIN_TICKS_IN_HIVE, confirmed, elapsedSoFar));
        if (confirmed) evictBeyondCapacity(hive);
    }

    private boolean isParked(UUID uuid) {
        for (Parked existing : parked) {
            if (existing.uuid.equals(uuid)) return true;
        }
        return false;
    }

    /**
     * The bee turned up again under its own UUID, so whatever it did, it did not go into a hive:
     * vanilla would have given it a new one. Drops its entry and hands back what it held, charged as
     * if the time had passed outside -- which it did, the bee kept ageing wherever it was.
     *
     * @return the withdrawn entry, or {@code null} if nothing was held for this bee
     */
    public Unparked unpark(UUID uuid) {
        for (Iterator<Parked> entries = parked.iterator(); entries.hasNext(); ) {
            Parked entry = entries.next();
            if (!entry.uuid.equals(uuid)) continue;
            entries.remove();
            return new Unparked(charge(entry, false), entry.confirmed);
        }
        return null;
    }

    /**
     * Keeps a hive's heard entries down to the number of bees it can actually contain.
     *
     * <p>A hive refuses a fourth bee outright ({@code addOccupant} checks the list size before it
     * stores anything), so more than three heard entries means one whose bee left unseen, and the
     * oldest is the likeliest: an entry only lives that long when no bee ever came out to claim it.
     *
     * <p>Guessed entries are never evicted, and do not count here. A guess may belong to the hive
     * next door, so it says nothing about how full this one is; and every cap tried on them pushed
     * out guesses whose bees were still inside -- in an in-game test with three rows of hives stacked
     * on each other, eight parents' cooldowns within twenty seconds. Guesses resolve on their own instead: {@link #unpark} withdraws one when
     * its bee comes back, a released bee claims it, and {@link #FORGET_AFTER_TICKS} drops the rest.
     */
    private void evictBeyondCapacity(long hive) {
        while (true) {
            Parked oldest = null;
            int here = 0;
            for (Parked entry : parked) {
                if (entry.hive != hive || !entry.confirmed) continue;
                if (oldest == null) oldest = entry;
                here++;
            }
            if (here <= MAX_PARKED_PER_HIVE) return;
            parked.remove(oldest);
        }
    }

    /** One bee out of {@code hive}; see {@link #claim(long, List)}. */
    public Claim claim(long hive, boolean baby, boolean ageLocked) {
        return claim(hive, List.of(new Release(baby, ageLocked))).get(0);
    }

    /**
     * Bees that came out of {@code hive} together, in entity-id order. Hands back, index for index,
     * the estimates of the bee each one was, already charged for the stay -- or {@code null} for a bee
     * nothing is waiting for.
     *
     * <p>Babies and adults are matched separately, because baby-ness is the one identifying bit the
     * client actually has: {@code DATA_BABY_ID} is synchronised.
     *
     * <p>Which entries: a bee leaves on the first tick its stay passes its own minimum
     * ({@code BeeData.tick()}), so the entries that have <em>just</em> become due are the bees in
     * front of us, and one that has been due for minutes is a bee that left unseen. The tolerance
     * only fills in when nothing is due yet -- it covers measurement lag, and under a tick sprint the
     * client's clock is coarse.
     *
     * <p>In which order: {@code tickOccupants} walks the occupant list in the order the bees went in,
     * and each released bee takes its id from {@code ServerLevel.getNextEntityId} as it is built, so
     * the lowest id belongs to the entry parked first.
     */
    public List<Claim> claim(long hive, List<Release> released) {
        Claim[] claims = new Claim[released.size()];
        for (boolean baby : new boolean[] {false, true}) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < released.size(); i++) {
                if (released.get(i).baby() == baby) slots.add(i);
            }
            if (slots.isEmpty()) continue;

            List<Parked> due = new ArrayList<>();
            List<Parked> nearlyDue = new ArrayList<>();
            for (Parked entry : parked) {
                if (entry.hive != hive || entry.state.baby() != baby) continue;
                if (entry.elapsed > entry.minTicksInHive) due.add(entry);
                else if (entry.elapsed > entry.minTicksInHive - CLAIM_TOLERANCE_TICKS) nearlyDue.add(entry);
            }
            // Most recently due first. Bees that went in on the same tick are indistinguishable, and
            // there the larger figure wins: a timer left standing a little too long is the lesser
            // error against one that puts "Ready" on a bee that is still waiting.
            due.sort(Comparator.<Parked>comparingInt(entry -> entry.elapsed)
                    .thenComparingInt(entry -> -conservativeWeight(entry.state)));
            nearlyDue.sort(Comparator.<Parked>comparingInt(entry -> -entry.elapsed)
                    .thenComparingInt(entry -> -conservativeWeight(entry.state)));
            List<Parked> pool = new ArrayList<>(due);
            pool.addAll(nearlyDue);
            if (pool.size() < slots.size()) pool.addAll(guessesNextDoor(hive, baby));
            int competing = due.size() >= slots.size() ? due.size() : pool.size();

            List<Parked> chosen = new ArrayList<>(pool.subList(0, Math.min(slots.size(), pool.size())));
            // Longest stay first is the order they went in; the list order settles bees that share a tick.
            chosen.sort(Comparator.<Parked>comparingInt(entry -> -entry.elapsed)
                    .thenComparingInt(parked::indexOf));
            for (int k = 0; k < chosen.size(); k++) {
                Parked entry = chosen.get(k);
                int slot = slots.get(k);
                parked.remove(entry);
                // How many entries this one bee could have been: one, unless the hive offered more
                // candidates than it let out, and then every surplus entry is another way to be wrong.
                int candidates = competing - slots.size() + 1;
                claims[slot] = new Claim(entry.uuid, entry.state,
                        charge(entry, released.get(slot).ageLocked()), Math.max(1, candidates), entry.confirmed);
            }
        }
        return Arrays.asList(claims);
    }

    /**
     * Guessed entries filed at a nearby hive that have just come due -- the fallback for a bee that
     * came out of {@code hive} with nothing waiting there for it.
     *
     * <p>Only guesses: an entry heard going in is known to be in its own hive and cannot have come out
     * of this one. Only just due: no more than {@link #NEIGHBOUR_EARLY_TICKS} short, since a bee that
     * still owes time is not out yet, and no more than the tolerance over, since a guess overdue by
     * minutes belongs to a bee that left unseen and says nothing about this one. Closest to due
     * first, then closest hive, so the likeliest pick leads.
     */
    private List<Parked> guessesNextDoor(long hive, boolean baby) {
        List<Parked> nextDoor = new ArrayList<>();
        for (Parked entry : parked) {
            if (entry.confirmed || entry.hive == hive || entry.state.baby() != baby) continue;
            int overdue = entry.elapsed - entry.minTicksInHive;
            if (overdue < -NEIGHBOUR_EARLY_TICKS || overdue > CLAIM_TOLERANCE_TICKS) continue;
            if (distance(entry.hive, hive) > NEIGHBOUR_RADIUS) continue;
            nextDoor.add(entry);
        }
        nextDoor.sort(Comparator.<Parked>comparingInt(entry -> Math.abs(entry.elapsed - entry.minTicksInHive - 1))
                .thenComparingDouble(entry -> distance(entry.hive, hive)));
        return nextDoor;
    }

    private static double distance(long hive, long other) {
        int dx = unpackX(hive) - unpackX(other);
        int dy = unpackY(hive) - unpackY(other);
        int dz = unpackZ(hive) - unpackZ(other);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * How long this bee still has to wait, for the purpose of choosing between candidates.
     *
     * <p>A baby is measured by its growth and an adult by its cooldown; a doubt counts for the same
     * comparison as a cooldown, because that is what it is -- one the mod could not confirm.
     */
    private static int conservativeWeight(State state) {
        return state.baby() ? state.growthTicks()
                : Math.max(state.cooldownTicks(), state.doubtTicks());
    }

    /**
     * Applies the stay to the estimates, the way {@code setBeeReleaseData} applies it to the bee.
     *
     * <p>The age lock is honoured because vanilla honours it: {@code updateBeeAge} returns before
     * touching {@code age} when {@code isAgeLocked()}, so a locked baby comes out of the hive
     * exactly as young as it went in. The lock cannot change while the bee is inside — nothing can
     * reach it to feed it — so the flag read at release describes the whole stay.
     */
    private static State charge(Parked parkedBee, boolean ageLocked) {
        State state = parkedBee.state;
        int elapsed = parkedBee.elapsed;
        int growth = ageLocked ? state.growthTicks() : Math.max(0, state.growthTicks() - elapsed);
        int cooldown = ageLocked ? state.cooldownTicks() : Math.max(0, state.cooldownTicks() - elapsed);
        // Not gated on the age lock: setInLoveTime sits outside the updateBeeAge guard.
        int love = Math.max(0, state.loveTicks() - elapsed);
        // A doubt is a cooldown the mod could not confirm, so it runs on the same clock.
        int doubt = ageLocked ? state.doubtTicks() : Math.max(0, state.doubtTicks() - elapsed);
        return new State(cooldown, growth, love, state.baby(), doubt);
    }

    /** How many bees are parked at one hive, whether eligible to leave yet or not. */
    public int parkedAt(long hive) {
        int count = 0;
        for (Parked parkedBee : parked) {
            if (parkedBee.hive == hive) count++;
        }
        return count;
    }

    /** Advances every parked stay by one client tick's worth of game time, dropping the stale ones. */
    public void tick(int delta) {
        parked.removeIf(parkedBee -> (parkedBee.elapsed += delta) >= FORGET_AFTER_TICKS);
    }

    /** Dropped wholesale on a world change, like every other tracker in the mod. */
    public void clear() {
        parked.clear();
    }
}
