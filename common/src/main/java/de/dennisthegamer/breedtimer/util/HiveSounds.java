package de.dennisthegamer.breedtimer.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * The enter and exit sounds of nearby hives, held briefly so the bees they belong to can be matched.
 *
 * <p>A hive announces every bee it takes in and lets out. {@code BeehiveBlockEntity.addOccupant}
 * plays {@code BEEHIVE_ENTER} at the block's corner and {@code releaseOccupant} plays
 * {@code BEEHIVE_EXIT} at its centre, both with a {@code null} source player, so the server sends
 * them to everyone within 16 blocks. That names the hive exactly. The bee's last position does not:
 * in an in-game test it pointed at the wrong hive for 518 of 695 bees heard going in, because the
 * hives in that farm stand side by side and on top of each other.
 *
 * <p>Deliberately free of Minecraft types, like {@link HiveHandover}: the caller turns the packet
 * into a block position and the bees into plain coordinates, and the matching stays testable.
 */
public final class HiveSounds {

    /** A heard sound, as handed to the bee it was matched with. */
    public record Heard(long hive, long gameTime) {}

    /** A bee's position, the last one seen for a bee that vanished or the first for one that appeared. */
    public record Pos(double x, double y, double z) {}

    /**
     * How many client ticks a sound waits for its bee. The sound and the entity packet leave the
     * server in the same tick, but can be handled a frame apart; three is ample for that and short
     * enough that an old sound cannot explain an unrelated bee.
     */
    public static final int WINDOW_TICKS = 3;

    /**
     * How far a bee may be from the centre of a heard hive and still be matched to it.
     * {@code BeeEnterHiveGoal} hands the bee over within two blocks of the hive, and a released bee
     * is placed just in front of it; the rest is a tick of flight and the client's interpolation.
     */
    public static final double MATCH_RADIUS = 3.5;

    /** The range the server sends a hive's sound to: {@code PlayerList.broadcast} at volume 1. */
    public static final double SOUND_RANGE = 16.0;

    /**
     * Distance inside {@link #SOUND_RANGE} within which the player counts as having heard the hive.
     * The enter sound comes from the block's corner, the check here from its centre, and the player
     * moves; the margin keeps the edge on the side of "could not tell".
     */
    public static final double EARSHOT = SOUND_RANGE - 2.0;

    private static final class Pending {
        final boolean enter;
        final long hive;
        final long gameTime;
        int age;

        Pending(boolean enter, long hive, long gameTime) {
            this.enter = enter;
            this.hive = hive;
            this.gameTime = gameTime;
        }
    }

    private final List<Pending> pending = new ArrayList<>();

    /** A hive at this block was heard taking a bee in ({@code enter}) or letting one out. */
    public void heard(boolean enter, int x, int y, int z, long gameTime) {
        pending.add(new Pending(enter, HiveHandover.key(x, y, z), gameTime));
    }

    /** Bees that just vanished; index for index, the hive heard taking each one in, or {@code null}. */
    public List<Heard> matchEnters(List<Pos> bees) {
        return match(true, bees);
    }

    /** Bees that just appeared; index for index, the hive heard letting each one out, or {@code null}. */
    public List<Heard> matchExits(List<Pos> bees) {
        return match(false, bees);
    }

    /**
     * Pairs bees with sounds, closest pair first, each sound used once. Several bees can go in or
     * come out together -- at dusk and dawn a whole farm does -- and pairing them one bee at a time
     * would let the first bee take a sound that fits a later one better.
     */
    private List<Heard> match(boolean enter, List<Pos> bees) {
        Heard[] matched = new Heard[bees.size()];
        List<double[]> pairs = new ArrayList<>();
        for (int b = 0; b < bees.size(); b++) {
            for (int s = 0; s < pending.size(); s++) {
                Pending sound = pending.get(s);
                if (sound.enter != enter) continue;
                double distance = distanceToCentre(bees.get(b), sound.hive);
                if (distance <= MATCH_RADIUS) pairs.add(new double[] {distance, b, s});
            }
        }
        pairs.sort((one, other) -> Double.compare(one[0], other[0]));
        boolean[] soundUsed = new boolean[pending.size()];
        for (double[] pair : pairs) {
            int bee = (int) pair[1];
            int sound = (int) pair[2];
            if (matched[bee] != null || soundUsed[sound]) continue;
            soundUsed[sound] = true;
            matched[bee] = new Heard(pending.get(sound).hive, pending.get(sound).gameTime);
        }
        for (int s = soundUsed.length - 1; s >= 0; s--) {
            if (soundUsed[s]) pending.remove(s);
        }
        return Arrays.asList(matched);
    }

    /** Ages every held sound by one client tick and drops those nobody claimed in time. */
    public void tick() {
        for (Iterator<Pending> sounds = pending.iterator(); sounds.hasNext(); ) {
            if (++sounds.next().age >= WINDOW_TICKS) sounds.remove();
        }
    }

    /** Dropped on a world change, like every other tracker in the mod. */
    public void clear() {
        pending.clear();
    }

    /** Whether a player at this position would have heard a sound from {@code hive}. */
    public static boolean withinEarshot(double x, double y, double z, long hive) {
        return distanceToCentre(new Pos(x, y, z), hive) <= EARSHOT;
    }

    private static double distanceToCentre(Pos pos, long hive) {
        double dx = pos.x() - (HiveHandover.unpackX(hive) + 0.5);
        double dy = pos.y() - (HiveHandover.unpackY(hive) + 0.5);
        double dz = pos.z() - (HiveHandover.unpackZ(hive) + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
