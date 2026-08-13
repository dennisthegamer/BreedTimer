package de.dennisthegamer.breedtimer.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The exact remaining growth time of a mob sitting in a bucket -- the only exact age a client-only mod can get.
 *
 * <p>{@code DataComponents.BUCKET_ENTITY_DATA} is {@code networkSynchronized}, so the client holds the same
 * {@code CompoundTag} the server wrote when the mob was scooped up, and each bucketable mob's own
 * {@code saveToBucketTag} puts its age in it. {@code Bucketable}'s shared helper does not -- it writes only
 * NoAI, Silent, NoGravity, Glowing, Invulnerable, PersistenceRequired and Health.
 *
 * <p>Three things differ per species and would each be a silent wrong answer:
 * <ul>
 *   <li>a sulfur cube writes <b>lowercase</b> {@code age} / {@code age_locked}; everything else writes
 *       {@code Age} / {@code AgeLocked};</li>
 *   <li>a tadpole's age counts <b>up</b> towards {@code Tadpole.ticksToBeFrog}, while an
 *       {@code AgeableMob}'s counts up towards zero from a negative start;</li>
 *   <li>{@code AgeLocked} was only added in 26.1, so on older versions its absence means "unknown", not
 *       "unlocked" -- which is harmless, because the golden dandelion that sets it is 26.x-only.</li>
 * </ul>
 *
 * <p>{@code CustomData.copyTag()} deep-copies the whole tag and is the only reader left on 1.21.11 and later
 * ({@code getUnsafe}, {@code contains} and {@code read} were all removed), so callers must not put it on a
 * per-frame path without caching -- see {@code BucketTooltipMixin}.
 */
public final class BucketedAge {

    private BucketedAge() {}

    /** What one bucket says. {@code remainingTicks} is 0 for an adult; {@code known} is false when there is no age. */
    public record Info(boolean known, int remainingTicks, boolean ageLocked) {
        public static final Info UNKNOWN = new Info(false, 0, false);
    }

    public static Info read(ItemStack stack) {
        if (!stack.has(DataComponents.BUCKET_ENTITY_DATA)) return Info.UNKNOWN;
        CompoundTag tag = stack.get(DataComponents.BUCKET_ENTITY_DATA).copyTag();
        boolean sulfur = stack.is(Items.SULFUR_CUBE_BUCKET);   // 26.2 only; drop on every other branch
        String ageKey = sulfur ? "age" : "Age";
        String lockKey = sulfur ? "age_locked" : "AgeLocked";
        // 1.21-1.21.4 have `int getInt(String)` instead; see the porting notes.
        if (tag.getInt(ageKey).isEmpty()) return Info.UNKNOWN;
        int age = tag.getIntOr(ageKey, 0);
        boolean locked = tag.getBooleanOr(lockKey, false);

        // A tadpole counts up to ticksToBeFrog; an AgeableMob counts a negative age up to zero. The field is
        // public and non-final, so read it rather than the -24000 it is initialised from.
        int remaining = stack.is(Items.TADPOLE_BUCKET)
                ? Math.max(0, Tadpole.ticksToBeFrog - age)
                : Math.max(0, -age);
        return new Info(true, remaining, locked);
    }
}
