package de.dennisthegamer.breedtimer.util;

/**
 * Names the second parent of a breeding from where the broadcasting animal was looking.
 *
 * <p>Vanilla sends entity event 18 from the goal owner alone and nothing that names its partner, so
 * the mate used to be nameable only by elimination -- see {@link ParentAccounting} -- which in a pen
 * holding more than one courting pair is almost never possible. There is, however, one thing the
 * game does tell the client: {@code BreedGoal.tick()} calls
 * {@code animal.getLookControl().setLookAt(this.partner, ...)} and {@code breed()} calls
 * {@code spawnChildFromBreeding(level, this.partner)} -- both read the <em>same field</em>. The head
 * of the animal that broadcast the event is therefore aimed at the animal it bred with, not by
 * correlation but by construction. The brain-driven species are better still:
 * {@code AnimalMakeLove.start()} puts a {@code LOOK_TARGET} on both partners through
 * {@code BehaviorUtils.lockGazeAndWalkToEachOther}, so the gaze there is mutual by design.
 *
 * <p>And head rotation is synchronised. {@code ServerEntity.sendChanges} packs the yaw with
 * {@code Mth.packDegrees} into a {@code ClientboundRotateHeadPacket}, which is what makes this
 * usable at all where {@code age}, {@code inLove} and {@code loveCause} are not.
 *
 * <p><strong>Observable is not exact, and the constants below are the whole safety argument.</strong>
 * The yaw arrives quantised to 360/256 = 1.40625 degrees, at most every third tick, and is then
 * interpolated over three more client ticks; the head also lags the true bearing by up to ten
 * degrees per tick while both animals are still moving, and {@code Mob.getMaxHeadYRot()} clamps it
 * to 75 degrees off the body. So this names a mate only where the reading is not close: the winner
 * has to be aimed at, and it has to beat the runner-up by a margin far wider than the transport
 * error. Everywhere else the caller keeps doubting exactly as it did before, which makes this a
 * narrowing of the doubt and never a new way to be wrong -- the failure mode it must not have is
 * naming the wrong animal, because that hands a five-minute cooldown to one that never bred and
 * clears it on the one that did.
 *
 * <p>Free of Minecraft types so the rule is unit-testable, the same reason {@link ParentAccounting}
 * is a class of its own.
 */
public final class MateBearing {

    /**
     * How far off the head may be and still count as aimed at a candidate. Generous, because the
     * reading lags: the mate is inside three blocks at the moment of breeding, so an animal the head
     * misses by more than this is one the goal was not pointing at.
     */
    public static final double MAX_OFF_DEGREES = 45.0;

    /**
     * How far the runner-up has to be behind the winner. An order of magnitude above the 1.40625
     * degrees of wire quantisation, so no answer here comes out of rounding: two candidates this
     * close together are a coin flip and get the doubt instead.
     */
    public static final double MIN_MARGIN_DEGREES = 20.0;

    private MateBearing() {}

    /**
     * The yaw an animal at the origin would need to face the offset {@code (dx, dz)}, in vanilla's
     * own convention -- yaw 0 faces +Z and east is -90, as {@code LookControl} computes it.
     */
    public static double bearingDegrees(double dx, double dz) {
        return Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
    }

    /**
     * Which candidate the broadcasting animal's head was aimed at.
     *
     * @param headYawDegrees   the broadcaster's head yaw as it stood when the event arrived
     * @param candidateBearings the yaw pointing at each candidate, in the caller's own order
     * @return the index of the mate, or {@code -1} where the reading does not force one answer
     */
    public static int name(double headYawDegrees, double[] candidateBearings) {
        int best = -1;
        double bestOff = Double.MAX_VALUE;
        double runnerUpOff = Double.MAX_VALUE;

        for (int i = 0; i < candidateBearings.length; i++) {
            double off = Math.abs(angleDifference(headYawDegrees, candidateBearings[i]));
            if (off < bestOff) {
                runnerUpOff = bestOff;
                bestOff = off;
                best = i;
            } else if (off < runnerUpOff) {
                runnerUpOff = off;
            }
        }

        if (best < 0 || bestOff > MAX_OFF_DEGREES) return -1;
        if (runnerUpOff - bestOff < MIN_MARGIN_DEGREES) return -1;
        return best;
    }

    /**
     * Signed difference between two yaws, wrapped into (-180, 180]. Without the wrap a head at 179
     * and a candidate at -179 read as 358 degrees apart instead of two, which is exactly where a
     * naive comparison would name the animal on the opposite side.
     */
    private static double angleDifference(double from, double to) {
        double diff = (to - from) % 360.0;
        if (diff > 180.0) diff -= 360.0;
        if (diff <= -180.0) diff += 360.0;
        return diff;
    }
}
