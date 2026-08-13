package de.dennisthegamer.breedtimer.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out which items will actually feed an animal, by asking the animal itself rather than
 * keeping a hand-written table: every {@code isFood} override in the game is a tag test, and a
 * data pack is free to change what is in those tags, so a table would go stale silently.
 *
 * <p>The answer is cached per species because finding it means offering every registered item to
 * {@code isFood}, and the label renderer runs once per animal per frame.
 */
public final class BreedingFoodHelper {

    /** Items named on one line before the list wraps onto the next. */
    private static final int PER_LINE = 3;
    /** Lines the hint may fill before whatever is left is summarised as a "+n" count. */
    private static final int MAX_LINES = 2;
    /** Items named in full; anything past this is counted rather than listed. */
    private static final int MAX_LISTED = PER_LINE * MAX_LINES;

    /**
     * Answers per entity type, then per state variant. Keyed on the type object rather than on a
     * built-up string because this is read once per animal per frame while the option is on, and
     * assembling a key each time was pure garbage. The variant index boxes to a cached Integer,
     * so the lookup allocates nothing either.
     */
    private static final Map<EntityType<?>, Map<Integer, List<Component>>> CACHE = new HashMap<>();

    private BreedingFoodHelper() {}

    /**
     * Item tags arrive from the server and a data pack may redefine them, so nothing may outlive a
     * world. Called from {@link BreedCooldownHelper#onWorldJoin} and {@code onWorldLeave}.
     */
    public static void clear() {
        CACHE.clear();
    }

    /** The food lines for this animal, or {@code null} if nothing feeds it. */
    public static List<Component> hintFor(Animal animal) {
        int variant = variantOf(animal);
        Map<Integer, List<Component>> byVariant = CACHE.get(animal.getType());
        if (byVariant != null) {
            List<Component> cached = byVariant.get(variant);
            if (cached != null) return cached;
        }

        List<Component> named = new ArrayList<>();
        int unlisted = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!animal.isFood(item.getDefaultInstance())) continue;
            if (named.size() < MAX_LISTED) {
                named.add(Component.translatable(item.getDescriptionId()));
            } else {
                unlisted++;
            }
        }

        // Deliberately not cached when empty. Item tags only exist once the server has sent them,
        // so an early call answers "nothing feeds this" for every animal alive — caching that would
        // freeze the wrong answer in for the rest of the session.
        if (named.isEmpty()) return null;

        List<Component> hint = format(named, unlisted);
        if (byVariant == null) {
            byVariant = new HashMap<>();
            CACHE.put(animal.getType(), byVariant);
        }
        byVariant.put(variant, hint);
        return hint;
    }

    /**
     * As {@link #hintFor(Animal)}, for mobs whose food test is not an {@code isFood} override that
     * can be probed. A dolphin has no {@code isFood} at all — the {@code ItemTags.FISHES} test is
     * inlined in its {@code mobInteract} — and {@code SulfurCube.isFood} is private. Both are plain
     * tag tests, so the tag is asked directly, which keeps the promise the class javadoc makes: a
     * data pack that redefines the tag changes the hint.
     *
     * <p>Shares the cache with {@link #hintFor(Animal)} at variant 0. Neither species is a
     * {@code TamableAnimal}, so no other variant can exist for them, and no entity type can reach
     * both entry points.
     */
    public static List<Component> hintForTag(EntityType<?> type, TagKey<Item> tag) {
        Map<Integer, List<Component>> byVariant = CACHE.get(type);
        if (byVariant != null) {
            List<Component> cached = byVariant.get(0);
            if (cached != null) return cached;
        }

        List<Component> named = new ArrayList<>();
        int unlisted = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!item.getDefaultInstance().is(tag)) continue;
            if (named.size() < MAX_LISTED) {
                named.add(Component.translatable(item.getDescriptionId()));
            } else {
                unlisted++;
            }
        }

        // Not cached when empty, for the same reason hintFor does not: item tags only exist once the
        // server has sent them, and freezing "nothing feeds this" would last the whole session.
        if (named.isEmpty()) return null;

        List<Component> hint = format(named, unlisted);
        if (byVariant == null) {
            byVariant = new HashMap<>();
            CACHE.put(type, byVariant);
        }
        byVariant.put(0, hint);
        return hint;
    }

    /**
     * Lays resolved names out over at most {@link #MAX_LINES} lines. Wrapping rather than cutting
     * off after a couple of entries matters because several animals accept a real choice — a
     * chicken takes six kinds of seed — and "two items and an ellipsis" told you almost nothing.
     * Only what will not fit at all is reduced to a "+n" count, so the label stays a label.
     *
     * <p>Shared with the villager hint, which gets its items from {@code Villager.FOOD_POINTS}
     * instead of from an {@code isFood} probe.
     */
    public static List<Component> format(List<Component> named, int unlisted) {
        List<MutableComponent> lines = new ArrayList<>();
        for (int i = 0; i < named.size(); i += PER_LINE) {
            MutableComponent line = Component.empty();
            for (int j = i; j < Math.min(i + PER_LINE, named.size()); j++) {
                // Translatable rather than ", ": a locale that separates list items differently
                // has no other way to say so, and Minecraft offers no list-formatting API.
                if (j > i) line.append(Component.translatable("breedtimer.food_hint.separator"));
                line.append(named.get(j));
            }
            lines.add(line);
        }
        if (unlisted > 0) {
            lines.get(lines.size() - 1).append(Component.translatable("breedtimer.food_hint.more", unlisted));
        }

        // Only the first line is prefixed; the rest read as a continuation of it.
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("breedtimer.food_hint", lines.get(0)));
        for (int i = 1; i < lines.size(); i++) out.add(lines.get(i));
        return out;
    }

    /** Splits a fixed item list into the first {@link #MAX_LISTED} names plus a remainder count. */
    public static List<Component> fromItems(Iterable<Item> items) {
        List<Component> named = new ArrayList<>();
        int unlisted = 0;
        for (Item item : items) {
            if (named.size() < MAX_LISTED) named.add(Component.translatable(item.getDescriptionId()));
            else unlisted++;
        }
        return named.isEmpty() ? null : format(named, unlisted);
    }

    /**
     * Species alone is not always enough. Of everything the mod tracks, exactly one {@code isFood}
     * looks at the animal rather than only at the stack: {@code AbstractNautilus} offers the taming
     * items while it is an untamed adult and the breeding food once tamed, or while still a baby.
     * Giving the tamables a state-aware key keeps that honest; for wolves and cats, whose
     * {@code isFood} is a plain tag test, the extra buckets simply hold the same answer.
     */
    private static int variantOf(Animal animal) {
        if (!(animal instanceof TamableAnimal tamable)) return 0;
        return (tamable.isTame() ? 1 : 0) | (animal.isBaby() ? 2 : 0);
    }
}
