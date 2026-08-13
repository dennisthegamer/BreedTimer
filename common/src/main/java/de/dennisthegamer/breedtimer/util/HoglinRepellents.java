package de.dennisthegamer.breedtimer.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.hoglin.HoglinAi;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Replays vanilla's own repellent scan on the client, because the verdict it feeds never leaves the server.
 *
 * <p>{@code Hoglin.canFallInLove()} is {@code !HoglinAi.isPacified(this) && super.canFallInLove()}, and
 * {@code PACIFIED} is a brain memory -- brains are not serialised to clients at all, so there is no packet to
 * read. Its one and only source, though, is {@code HoglinSpecificSensor.findNearestRepellent}, which is a
 * 17x9x17 block scan for {@code BlockTags.HOGLIN_REPELLENTS} -- and blocks are fully client-side.
 *
 * <p>Vanilla sets the memory with a 200-tick expiry and only re-sets it while it is absent, so PACIFIED is true
 * exactly when a repellent was seen within the last 200 ticks. The {@link #HOLD_TICKS} latch below reproduces
 * that rather than reporting the raw scan: without it, pulling the fungus out would flip our label to "can
 * breed" up to ten seconds before the game agrees. This is the same device the panda bamboo latch uses, and for
 * the same reason.
 *
 * <p>What it still cannot reproduce: vanilla's sensor phase is randomised per hoglin
 * ({@code Sensor.randomlyDelayStart} -> {@code nextInt(20)}), and the pacify behaviour is registered in the IDLE
 * and FIGHT activities only -- a hoglin fleeing piglins re-acquires nothing. Both are stated in the changelog
 * rather than papered over.
 */
public final class HoglinRepellents {

    private HoglinRepellents() {}

    /** Mirrors {@code HoglinAi.REPELLENT_PACIFY_TIME}, which is private. */
    private static final int HOLD_TICKS = 200;
    /** Mirrors {@code Sensor.DEFAULT_SCAN_RATE}, the interval vanilla's sensor re-runs at. */
    private static final int SCAN_INTERVAL_TICKS = 20;

    /** Ticks left on our replica of the PACIFIED memory, per hoglin. */
    private static final Map<UUID, Integer> repelled = new HashMap<>();
    /** Ticks until this hoglin is scanned again. Staggered on first sighting so a sounder does not scan in step. */
    private static final Map<UUID, Integer> nextScan = new HashMap<>();

    private static final Predicate<BlockState> REPELLENT = state -> state.is(BlockTags.HOGLIN_REPELLENTS);

    public static void clear() {
        repelled.clear();
        nextScan.clear();
    }

    /** Whether this hoglin is, as far as we can tell, pacified right now. */
    public static boolean isRepelled(UUID uuid) {
        return repelled.containsKey(uuid);
    }

    /**
     * Advances one loaded hoglin. Called from {@code BreedCooldownHelper.tick}'s entity loop, which is the only
     * place in the mod that already walks every loaded entity once per tick.
     */
    public static void tick(Hoglin hoglin, int delta) {
        UUID uuid = hoglin.getUUID();

        Integer hold = repelled.get(uuid);
        if (hold != null) {
            int left = hold - delta;
            if (left <= 0) repelled.remove(uuid); else repelled.put(uuid, left);
        }

        int due = nextScan.getOrDefault(uuid,
                // Stagger the first scan the way vanilla staggers the sensor, so a sounder of eight hoglins
                // does not run eight 2601-block sweeps on the same tick.
                Math.floorMod(uuid.hashCode(), SCAN_INTERVAL_TICKS)) - delta;
        if (due > 0) {
            nextScan.put(uuid, due);
            return;
        }
        nextScan.put(uuid, SCAN_INTERVAL_TICKS);

        if (scanFor(hoglin)) {
            // Re-armed on every positive scan, exactly like the memory's setWithExpiry(TRUE, 200).
            repelled.put(uuid, HOLD_TICKS);
        }
    }

    /**
     * True if any repellent stands in the 17x9x17 box vanilla looks at.
     *
     * <p>Answers "any", not "closest": vanilla stores the nearest position because a behaviour walks away from
     * it, and we only need the boolean the gate is built on. That turns the worst case from a full 2601-block
     * sweep into a first-match exit, and the palette test below usually skips the sweep entirely -- warped fungus
     * does not grow in a crimson forest, which is where hoglins live.
     */
    private static boolean scanFor(Hoglin hoglin) {
        Level level = hoglin.level();
        BlockPos origin = hoglin.blockPosition();
        int rh = HoglinAi.REPELLENT_DETECTION_RANGE_HORIZONTAL;   // 8
        int rv = HoglinAi.REPELLENT_DETECTION_RANGE_VERTICAL;     // 4
        int minX = origin.getX() - rh, maxX = origin.getX() + rh;
        int minZ = origin.getZ() - rh, maxZ = origin.getZ() + rh;
        int minY = origin.getY() - rv, maxY = origin.getY() + rv;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int cx = minX >> 4; cx <= (maxX >> 4); cx++) {
            for (int cz = minZ >> 4; cz <= (maxZ >> 4); cz++) {
                var chunk = level.getChunk(cx, cz);
                if (chunk == null) continue;
                int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
                int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);
                for (int y = minY; y <= maxY; ) {
                    int yEnd = Math.min(maxY, (((y >> 4) + 1) << 4) - 1);
                    int index = chunk.getSectionIndex(y);
                    if (index < 0 || index >= chunk.getSections().length
                            || !chunk.getSection(index).maybeHas(REPELLENT)) {
                        y = yEnd + 1;
                        continue;
                    }
                    for (int yy = y; yy <= yEnd; yy++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                mutable.set(x, yy, z);
                                if (level.getBlockState(mutable).is(BlockTags.HOGLIN_REPELLENTS)) return true;
                            }
                        }
                    }
                    y = yEnd + 1;
                }
            }
        }
        return false;
    }
}
