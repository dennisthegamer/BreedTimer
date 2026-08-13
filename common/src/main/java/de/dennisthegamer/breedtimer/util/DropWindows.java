package de.dennisthegamer.breedtimer.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ticks-since-last-heard-drop for chickens and armadillos, fed by {@link
 * de.dennisthegamer.breedtimer.mixin.DropSoundMixin}. Both {@code Chicken.eggTime} and {@code
 * Armadillo.scuteTime} are server-only fields; the sound the game plays on a successful drop is the
 * only trace either leaves on the client, and even that sound is not guaranteed -- see the four
 * caveats in the Task 33 brief. This class only ever produces a window, never a countdown: the true
 * remaining time is a 6000-tick range around the last heard drop, not a single number.
 */
public final class DropWindows {

    private DropWindows() {}

    private static final Identifier CHICKEN_EGG_SOUND = Identifier.withDefaultNamespace("entity.chicken.egg");
    private static final Identifier ARMADILLO_SCUTE_DROP_SOUND =
            Identifier.withDefaultNamespace("entity.armadillo.scute_drop");

    /** Both timers reset to {@code 6000 + random.nextInt(6000)}. */
    public static final int MIN_TICKS = 6000;
    public static final int MAX_TICKS = 11999;

    /** How far from the packet's position a same-species adult still counts as a candidate. */
    private static final double MATCH_RADIUS = 2.0;
    /** Two candidates this close to *each other* make the drop's owner ambiguous; record nothing. */
    private static final double AMBIGUITY_RADIUS_SQ = 1.0 * 1.0;

    /**
     * Ticks since we last heard this mob's drop sound, counted up by {@link #tick}. Not saved to
     * disk and not carried across a world change -- unlike {@code cooldownMap}, an entry here is
     * worthless without the sound that started it, and that sound is never replayed from a save
     * file. Cleared by {@link #clear}, called from the same {@code onWorldJoin}/{@code onWorldLeave}
     * pair every other tracker in {@link BreedCooldownHelper} uses.
     */
    private static final Map<UUID, Integer> windows = new HashMap<>();

    /**
     * Called from {@link de.dennisthegamer.breedtimer.mixin.DropSoundMixin} on every sound packet.
     * The two ids this cares about are compared first and cheaply, because this runs for every sound
     * in the game, not just these two.
     */
    public static void onSound(ClientboundSoundPacket packet) {
        Holder<SoundEvent> sound = packet.getSound();
        boolean chickenEgg = sound.is(CHICKEN_EGG_SOUND);
        if (!chickenEgg && !sound.is(ARMADILLO_SCUTE_DROP_SOUND)) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        Vec3 pos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        Animal target = chickenEgg
                ? resolveNearest(level, Chicken.class, pos)
                : resolveNearest(level, Armadillo.class, pos);
        if (target == null) return;

        windows.put(target.getUUID(), 0);
    }

    /**
     * The nearest adult of {@code type} within {@link #MATCH_RADIUS} of the packet position, or
     * {@code null} if there is no candidate or the winner cannot be told apart from some other
     * candidate. Adults only: a chick never lays and, up to 1.21.10, a cub's {@code scuteTime} is
     * frozen -- either would be a false attribution.
     *
     * <p>Delegates the actual geometry to {@link #nearestUnambiguousIndex}, which is unit-testable
     * without a running level -- see that method's javadoc for why the ambiguity check has to compare
     * the winner against <em>every</em> other candidate, not just the second-nearest-to-the-packet.
     */
    private static <T extends Animal> T resolveNearest(Level level, Class<T> type, Vec3 pos) {
        List<T> candidates = level.getEntitiesOfClass(type,
                new AABB(pos, pos).inflate(MATCH_RADIUS), animal -> !animal.isBaby());
        if (candidates.isEmpty()) return null;
        List<Vec3> positions = new ArrayList<>(candidates.size());
        for (T candidate : candidates) positions.add(candidate.position());
        int winner = nearestUnambiguousIndex(positions, pos);
        return winner < 0 ? null : candidates.get(winner);
    }

    /**
     * Pure geometry, extracted from {@link #resolveNearest} purely so it is unit-testable without a
     * running level. Returns the index of the position in {@code positions} nearest to {@code pos},
     * or {@code -1} if {@code positions} is empty or the winner is ambiguous.
     *
     * <p>Ambiguous means some <em>other</em> position in the list -- not necessarily the
     * second-nearest to {@code pos} -- sits within {@link #AMBIGUITY_RADIUS_SQ} of the winner. Those
     * are two different candidates in general: with a packet at the origin and three same-species
     * adults at {@code (0.5, 0, 0)}, {@code (0, 1.0, 0)} and {@code (1.4, 0, 0)}, the second-nearest
     * to the packet is the one at {@code (0, 1.0, 0)} (distance 1.0), which sits a safe ~1.118 blocks
     * from the winner -- but the <em>third</em>-nearest to the packet, at {@code (1.4, 0, 0)}
     * (distance 1.4), sits only 0.9 blocks from the winner and is exactly the ambiguous case the rule
     * exists to catch. An earlier version of this method only compared the winner against the
     * second-nearest-<em>to-the-packet</em> candidate and missed that third one entirely, silently
     * picking a winner a real 3-bird pen would make ambiguous. Checking every other candidate against
     * the winner, as this does, is the only version of the rule that cannot miss a case like that.
     */
    static int nearestUnambiguousIndex(List<Vec3> positions, Vec3 pos) {
        int nearest = -1;
        double nearestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < positions.size(); i++) {
            double distSq = positions.get(i).distanceToSqr(pos);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = i;
            }
        }
        if (nearest < 0) return -1;

        Vec3 winner = positions.get(nearest);
        for (int i = 0; i < positions.size(); i++) {
            if (i == nearest) continue;
            if (positions.get(i).distanceToSqr(winner) <= AMBIGUITY_RADIUS_SQ) return -1;
        }
        return nearest;
    }

    /** Ticks since the last drop we heard from this mob, or -1 if we have never heard one. */
    public static int sinceDrop(UUID uuid) {
        Integer since = windows.get(uuid);
        return since == null ? -1 : since;
    }

    /**
     * Evicts every entry for a mob {@code loadedUuids} no longer contains, then counts the rest up by
     * {@code delta} and drops any that have run past {@link #MAX_TICKS} -- past that point the true
     * remaining time is empty, which means a completion was missed (out of earshot, silenced, or the
     * loot-table caveat), and there is nothing left worth showing.
     *
     * <p>Evicted on unload, the same rule {@code snifferSeed}/{@code turtleLaying} use and
     * deliberately <em>not</em> the one {@code cooldownMap} uses. {@code loadedUuids} reflects client
     * entity <em>tracking</em>, which is a distance-based radius around the player, not server
     * chunk-ticking -- a mob can fall out of tracking range while the server keeps simulating it
     * (still resetting {@code eggTime}/{@code scuteTime} on schedule), so pausing here on the client
     * would let a stale window silently overstate how much time is actually left, or keep narrowing
     * toward a drop that already happened while we were not tracking it -- exactly the kind of
     * confidently-wrong answer this mod's one inferred display must never give. {@code cooldownMap}
     * pausing instead is an accepted, bounded imprecision for an <em>exact</em> synced figure; here it
     * would manufacture a specific new lie. Losing the window every time the mob leaves tracking range
     * is a minor inconvenience -- it recovers the next time we hear this mob drop again with it in
     * range -- and is the price of never showing a window we are not still sure of.
     */
    public static void tick(int delta, Set<UUID> loadedUuids) {
        windows.keySet().removeIf(uuid -> !loadedUuids.contains(uuid));
        windows.entrySet().removeIf(entry -> {
            int since = entry.getValue() + delta;
            entry.setValue(since);
            return since >= MAX_TICKS;
        });
    }

    /** Called from {@code onWorldJoin}/{@code onWorldLeave}; never written to a save file. */
    public static void clear() {
        windows.clear();
    }
}
