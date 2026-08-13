package de.dennisthegamer.breedtimer.util;

import de.dennisthegamer.breedtimer.render.StatePalette;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The mobs that grow on {@code AgeableMob}'s clock but are not {@code Animal}s.
 *
 * <p>Every other query in the mod is written against {@code Animal}: the scan box asks for
 * {@code Animal.class}, {@code isSupportedAnimal} is an {@code instanceof} chain of animals, and
 * {@code createTimerInfo} takes an {@code Animal}. A dolphin is none of those things and was
 * therefore invisible end to end, despite growing on the standard 24000-tick clock, being feedable
 * and being age-lockable. Rather than widen a dozen signatures — or grow a second
 * {@code instanceof} chain at every call site — the whole special case lives here, and the call
 * sites ask this class two questions: "is this one of yours?" and "what does it say?".
 *
 * <p>These mobs cannot breed. {@code AgeableMob.canBreed()} is a hard false, neither species
 * registers a {@code BreedGoal}, and love mode, the 6000-tick cooldown and entity event 18 are all
 * {@code Animal} machinery. An adult therefore has nothing to display at all, which is why the only
 * states here are "growing" and "age locked".
 */
public final class AgeableTracking {

    private AgeableTracking() {}

    /**
     * How long babyhood lasts for everything tracked here. {@code AgeableMob.getBabyStartAge()}
     * returns -24000 and neither {@code Dolphin} nor {@code AbstractCubeMob} overrides it — unlike
     * the sniffer, which is why {@code BreedCooldownHelper} needs a per-species figure and this
     * class does not.
     */
    public static final int GROW_TICKS = BabyGrowthTracker.BABY_GROW_TICKS;

    /**
     * Estimates for these mobs are kept apart from the animal ones deliberately: this class owns
     * the whole feature, so {@code BreedCooldownHelper} gains no field, no import and no third
     * meaning for its existing tracker. Persisted under its own save-file key.
     */
    private static final BabyGrowthTracker growth = new BabyGrowthTracker(GROW_TICKS);

    /**
     * Memoised {@link #isTracked} answers, keyed by entity type — the same device, and for the same
     * reason, as {@code BreedCooldownHelper.supportedByType}: this runs once per rendered entity per
     * frame. The answer depends only on the runtime type, so like that map it survives a world
     * change.
     */
    private static final Map<EntityType<?>, Boolean> trackedByType = new HashMap<>();

    /**
     * What one of these mobs currently says. Deliberately the same shape as
     * {@code BreedCooldownHelper.TadpoleTimerInfo}: a tadpole is the other non-{@code Animal} mob
     * whose only state is a growth estimate, and one pattern is easier to review than two.
     */
    public record Info(int remainingTicks, boolean ageLocked, int color) {}

    public static boolean isTracked(Entity entity) {
        Boolean known = trackedByType.get(entity.getType());
        if (known != null) return known;
        boolean result = computeIsTracked(entity);
        trackedByType.put(entity.getType(), result);
        return result;
    }

    /**
     * A positive whitelist, not "an {@code AgeableMob} that is not an {@code Animal}". Every animal
     * in the game is an {@code AgeableMob}, so a negative test would hand the whole animal
     * population to this path the moment somebody reorders a call site.
     */
    private static boolean computeIsTracked(Entity entity) {
        return entity instanceof Dolphin
                // 26.2 only: the cubemob package does not exist in 26.1 or on any 1.21 branch.
                // Remove this line, its import and SulfurCubeFeedMixin when porting.
                || entity instanceof SulfurCube;
    }

    /** The predicate the compact HUD hands to its scan-box query. */
    public static boolean isTrackedBaby(AgeableMob mob) {
        return isTracked(mob) && mob.isBaby();
    }

    /**
     * Advances the estimate for one loaded entity, or reports that this entity is none of ours.
     *
     * @return true if the entity was handled here, so the caller can skip its own branches
     */
    public static boolean tick(Entity entity, int delta) {
        if (!(entity instanceof AgeableMob mob) || !isTracked(mob)) return false;
        UUID uuid = mob.getUUID();
        if (mob.isBaby()) {
            growth.tick(uuid, mob.isAgeLocked(), delta, GROW_TICKS);
        } else {
            growth.forget(uuid);
        }
        return true;
    }

    /**
     * Only meaningful for a baby; an adult has nothing to show (see the class javadoc), and the
     * renderer returns before calling this.
     */
    public static Info infoFor(AgeableMob mob) {
        int remaining = growth.remainingFor(mob.getUUID(), GROW_TICKS);
        StatePalette p = StatePalette.current();
        // Grey for the frozen case, cyan for a running countdown -- the same convention the animal
        // and tadpole labels use.
        return mob.isAgeLocked()
                ? new Info(remaining, true, p.inert)
                : new Info(remaining, false, p.young);
    }

    /**
     * The local player fed one of these. Vanilla takes the same proportional cut it takes from an
     * animal — {@code ageUp(getSpeedUpSecondsWhenFeeding(-age), true)} — so
     * {@link BabyGrowthTracker#onFed} reproduces it exactly, truncation included.
     *
     * <p>The guard duplicates the {@code canAgeUp()} the injection point already sits behind. That
     * is deliberate: the same hook on the 1.21 branches sits behind {@code isBaby()} alone.
     */
    public static void onFed(AgeableMob mob) {
        if (!isTracked(mob) || !mob.isBaby() || mob.isAgeLocked()) return;
        growth.onFed(mob.getUUID(), GROW_TICKS);
    }

    /** The food line for one of these, or {@code null} if the tag resolves to nothing. */
    public static List<Component> foodHintFor(AgeableMob mob) {
        TagKey<Item> tag = foodTagFor(mob);
        return tag == null ? null : BreedingFoodHelper.hintForTag(mob.getType(), tag);
    }

    /**
     * Read off each species' own {@code mobInteract} rather than assumed. A dolphin takes
     * {@code ItemTags.FISHES}; the test is inlined in its {@code mobInteract} rather than living in
     * an {@code isFood} override, which is why the probe cannot be shared with the animals.
     */
    private static TagKey<Item> foodTagFor(AgeableMob mob) {
        if (mob instanceof Dolphin) return ItemTags.FISHES;
        // 26.2 only, as above. SulfurCube.isFood is private, so the tag is read directly.
        if (mob instanceof SulfurCube) return ItemTags.SULFUR_CUBE_FOOD;
        return null;
    }

    /** Hands {@link BabyGrowthTracker#set} an exact figure for a sulfur cube released from a bucket. */
    public static void set(UUID uuid, int remainingTicks) {
        growth.set(uuid, remainingTicks);
    }

    /** See {@link BabyGrowthTracker#has}. */
    public static boolean has(UUID uuid) {
        return growth.has(uuid);
    }

    public static void clear() {
        growth.clear();
    }

    public static Map<UUID, Integer> snapshot() {
        return growth.snapshot();
    }

    public static void restore(Map<UUID, Integer> saved) {
        growth.restore(saved);
    }
}
