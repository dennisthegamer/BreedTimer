package de.dennisthegamer.breedtimer.util;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which entities the label path may draw this tick, for the two gates that cannot be answered from the entity
 * alone: "am I looking straight at it" and "is there a wall in the way".
 *
 * <p>Both are resolved once per client tick rather than once per entity per frame, for the same reason
 * {@link Visibility} exists: the label path is a mixin that runs per rendered living entity per frame, so
 * anything it computes is paid sixty-plus times a second. A tick of latency on "which cow am I looking at" is
 * not observable; sixty raycasts a second per animal is.
 *
 * <p>Both answers are empty and both gates open when their settings are off, so a player who changes nothing
 * pays one boolean read per entity and nothing else.
 */
public final class LookTarget {

    private LookTarget() {}

    /** Entity id under the crosshair, or -1. Only meaningful while {@code labelLookAtOnly} is on. */
    private static int lookedAt = -1;
    /**
     * Entity ids with terrain in front of them. Only populated while {@code labelThroughWalls} is off.
     * Rebuilt from scratch every tick, so there is nothing to evict and no world-leave staleness beyond
     * {@link #clear()} -- unlike the timer maps, this holds no accumulated state.
     */
    private static final Set<Integer> occluded = new HashSet<>();
    /** The ray is padded the way vanilla pads its own entity pick, so a label does not flicker at the silhouette. */
    private static final double PICK_INFLATE = 0.3;

    public static void clear() {
        lookedAt = -1;
        occluded.clear();
    }

    /**
     * Rebuilds both answers. Called once per client tick from {@code BreedTimerClient.onClientTick} with the
     * entity list it has already collected -- there is no second world query here.
     */
    public static void update(Player player, Level level, BreedTimerConfig config, List<Entity> loadedEntities) {
        lookedAt = -1;
        occluded.clear();
        boolean pick = config.labelLookAtOnly;
        boolean occlude = !config.labelThroughWalls;
        if (!pick && !occlude) return;

        Vec3 eye = player.getEyePosition(1.0f);
        double range = config.fadeEndDistance;
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        double rangeSq = range * range;
        double bestSq = Double.MAX_VALUE;

        for (Entity entity : loadedEntities) {
            if (entity == player) continue;
            Vec3 centre = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
            if (centre.distanceToSqr(eye) > rangeSq) continue;

            if (pick) {
                var hit = entity.getBoundingBox().inflate(PICK_INFLATE).clip(eye, end);
                if (hit.isPresent()) {
                    double distSq = hit.get().distanceToSqr(eye);
                    if (distSq < bestSq) {
                        bestSq = distSq;
                        lookedAt = entity.getId();
                    }
                }
            }
            // When the pick is on, only the entity that wins it can be labelled at all, so testing every entity
            // for occlusion would be up to sixty wasted raycasts per tick. That case is handled below instead.
            if (occlude && !pick && !BreedCooldownHelper.hasLineOfSight(level, eye, centre, player)) {
                occluded.add(entity.getId());
            }
        }

        // Look-at plus through-walls off: exactly one raycast, against the entity that won the pick.
        if (pick && occlude && lookedAt >= 0) {
            Entity target = level.getEntity(lookedAt);
            if (target != null) {
                Vec3 centre = target.position().add(0, target.getBbHeight() / 2.0, 0);
                if (!BreedCooldownHelper.hasLineOfSight(level, eye, centre, player)) occluded.add(lookedAt);
            }
        }
    }

    /** The gate the label path asks. Two field reads for a player who has changed neither setting. */
    public static boolean isLabelled(Entity entity) {
        BreedTimerConfig config = BreedTimerConfig.get();
        if (config.labelLookAtOnly && entity.getId() != lookedAt) return false;
        return config.labelThroughWalls || !occluded.contains(entity.getId());
    }

    /** Whether a block label at this point is behind terrain. Asked by {@code BlockLabelScanner}, once per tick. */
    public static boolean blockVisible(Level level, Player player, double x, double y, double z) {
        return BreedCooldownHelper.hasLineOfSight(level, player.getEyePosition(1.0f), new Vec3(x, y, z), player);
    }
}
