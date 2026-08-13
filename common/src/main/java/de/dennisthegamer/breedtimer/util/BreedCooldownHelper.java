package de.dennisthegamer.breedtimer.util;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.render.StatePalette;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.animal.Animal;
import de.dennisthegamer.breedtimer.mixin.AllayDuplicationAccessor;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.nautilus.Nautilus;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.dennisthegamer.breedtimer.platform.Platforms;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BreedCooldownHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("BreedTimer");

    public static final int BREED_COOLDOWN_TICKS = 6000; // 5 minutes
    /** Matches the inflate(8) in vanilla's {@code BreedGoal.getFreePartner}. */
    private static final int MATE_SEARCH_RADIUS = 8;
    public static final int LOVE_MODE_TICKS = 600; // 30 seconds
    public static final int BABY_GROW_TICKS = BabyGrowthTracker.BABY_GROW_TICKS; // 20 minutes

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_DIR = Platforms.get().getConfigDir().resolve("breedtimer");

    // Client-side tracking using UUID (persistent across sessions)
    private static final Map<UUID, Integer> cooldownMap = new HashMap<>();
    private static final Map<UUID, Integer> loveMap = new HashMap<>();
    private static final BabyGrowthTracker babyGrowth = new BabyGrowthTracker();
    /**
     * Tadpoles are not {@link Animal}s and so miss every path above, but their growth is the same
     * kind of estimate: {@code Tadpole.age} is server-side only, just like {@code AgeableMob.age}.
     * The target comes from vanilla's own tunable rather than a copied 24000.
     */
    private static final BabyGrowthTracker tadpoleGrowth =
            new BabyGrowthTracker(Math.abs(Tadpole.ticksToBeFrog));
    private static String currentWorldId = null;
    /** F-1 migration: the id this world's save file used to live under, or null if none applies. */
    private static String legacyWorldId = null;
    private static long lastGameTime = -1;

    /** Registry ids of every {@link Animal} the game refuses to breed; see {@link #neverBreeds}. */
    private static final Set<String> NEVER_BREEDS = Set.of(
            "mule", "camel_husk", "happy_ghast", "polar_bear",
            "parrot", "skeleton_horse", "zombie_horse", "zombie_nautilus");

    /**
     * Babies that never become adults: {@code SkeletonHorse.canAgeUp()} and
     * {@code ZombieHorse.canAgeUp()} are hard false, and {@code AgeableMob.aiStep} only advances a
     * negative age when {@code canAgeUp()} passes. A foal of either stays a foal forever, so a
     * countdown would just run out and lie.
     */
    private static final Set<String> NEVER_GROWS_UP = Set.of("skeleton_horse", "zombie_horse");

    /**
     * Pandas that have just told us they cannot find bamboo, and how long that report still stands.
     *
     * <p>This replaces a 15x3x15 block sweep with vanilla's own verdict. {@code PandaBreedGoal
     * .canUse()} calls {@code setUnhappyCounter(32)} on the bamboo miss and the value is synced, so
     * the client sees the game's answer instead of a re-implementation of it. The cost is that the
     * signal is a 32-tick pulse: the goal only runs while the panda is in love with a free partner,
     * and it throttles itself to {@code tickCount + 600} between reports. Latching for those same
     * 600 ticks turns the pulse back into a state without inventing anything -- if bamboo turns up,
     * the goal succeeds immediately, no new pulse arrives and the latch simply expires.
     */
    private static final Map<UUID, Integer> pandaNoBamboo = new HashMap<>();
    /** {@code PandaBreedGoal.canUse()} sets {@code unhappyCooldown = tickCount + 600} on each report. */
    private static final int PANDA_NO_BAMBOO_HOLD_TICKS = 600;
    /**
     * The nearest other adult panda's genes, per adult panda seen this tick: {@code [mainGeneId,
     * hiddenGeneId]}. Feeds {@link PandaGenes#cubOdds}, which is deliberately Minecraft-free and so
     * cannot read a synced field itself -- this is the thin adapter that does.
     *
     * <p>Rebuilt from scratch every tick rather than updated incrementally: either panda in a pair
     * can simply walk out of range, and there is nothing to gain from tracking a delta. An entry's
     * absence means "no adult partner in range this tick", which the label reads as "no sub-line",
     * exactly like an entity absent from {@code pandaNoBamboo}.
     */
    private static final Map<UUID, int[]> pandaPartnerGenes = new HashMap<>();
    /**
     * Last seen {@code hasEgg()} per turtle. Turtles are the one vanilla animal whose breeding
     * emits no entity event at all: {@code Turtle.TurtleBreedGoal.breed()} is a hand-written copy
     * that sets both parents to age 6000 and calls resetLove twice, but never broadcasts event 18
     * the way Fox's otherwise identical copy does. The one thing it does that IS synced is
     * {@code setHasEgg(true)}, so a false->true flip is the breeding moment.
     */
    private static final Map<UUID, Boolean> turtleHadEgg = new HashMap<>();
    /**
     * Last seen {@code isLayingEgg()} per turtle, so only a false->true flip starts the clock.
     * Reading the flag alone would be no use: a turtle that streams into view already laying has
     * no observable start, and seeding a full 201 ticks there would overstate the wait.
     */
    private static final Map<UUID, Boolean> turtleWasLaying = new HashMap<>();
    /** Ticks left before a turtle we watched start laying puts the egg down. */
    private static final Map<UUID, Integer> turtleLaying = new HashMap<>();
    /**
     * {@code TurtleLayEggGoal.tick()} sets LAYING_EGG on the tick its counter is 0, then lays on the
     * tick the counter exceeds {@code adjustedTickDelay(200)}. {@code MoveToBlockGoal
     * .requiresUpdateEveryTick()} is true, so that call returns 200 unchanged -- there is no
     * randomness in the path at all. Counting the flip tick itself gives 201.
     */
    private static final int TURTLE_LAY_TICKS = 201;
    /** Ticks left before a sniffer we watched start digging drops its seed. */
    private static final Map<UUID, Integer> snifferSeed = new HashMap<>();
    /**
     * {@code Sniffer.onDiggingStart()} schedules the drop at {@code tickCount + 120} and broadcasts
     * event 63 in the same instruction pair.
     *
     * <p>Only the constant is usable. The scheduled tick is written in <em>server</em> {@code
     * tickCount} units, and a client entity's {@code tickCount} restarts at 0 the moment it streams
     * into view — subtracting the synced field from the client's counter yields a large negative
     * number for exactly the sniffer a player is most likely to be looking at. Count 120 from the
     * event; never read {@code DATA_DROP_SEED_AT_TICK}.
     */
    private static final int SNIFFER_SEED_TICKS = 120;

    /** Called from {@link de.dennisthegamer.breedtimer.mixin.SnifferDigEventMixin} on event 63. */
    public static void onSnifferDiggingStarted(Sniffer sniffer) {
        snifferSeed.put(sniffer.getUUID(), SNIFFER_SEED_TICKS);
    }

    /**
     * Last seen duplication readiness per allay. The flag is synced but the countdown behind it is
     * not, so a true->false flip is taken as the moment of duplication and starts our own clock.
     * Unlike breeding this needs no estimate: {@code resetDuplicationCooldown()} is a flat 6000
     * ticks with no randomness at all.
     */
    private static final Map<UUID, Boolean> allayCouldDuplicate = new HashMap<>();
    private static final Map<UUID, Integer> allayCooldown = new HashMap<>();
    /** {@code Allay.resetDuplicationCooldown()} sets exactly 6000 ticks. */
    public static final int ALLAY_COOLDOWN_TICKS = 6000;

    /** A position with a countdown; both allay windows below have the same shape. */
    private static final class Sighting {
        final Vec3 pos;
        int ticksLeft;

        Sighting(Vec3 pos, int ticksLeft) {
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }

    /** Duplications we have just seen, still waiting for the child to stream in. */
    private static final List<Sighting> pendingDuplications = new ArrayList<>();
    /** Allays first seen already on cooldown, still waiting for a duplication to explain them. */
    private static final Map<UUID, Sighting> unexplainedAllays = new HashMap<>();
    /**
     * How long each side of that match stays open. {@code duplicateAllay()} calls {@code
     * addFreshEntity} and the parent's flag update goes out on the same chunk-tracker pass, so the
     * add packet and the entity-data packet normally land in one batch — but the order they are
     * processed in is not ours to choose, so both directions are held for a second.
     */
    private static final int ALLAY_CHILD_WINDOW_TICKS = 20;
    /** {@code duplicateAllay()} snaps the child onto the parent's exact position; 8 absorbs drift. */
    private static final double ALLAY_CHILD_RADIUS_SQ = 8.0 * 8.0;

    /**
     * A bucket release still waiting for the mob it explains to stream in, carrying the exact age read off
     * the item -- see {@link de.dennisthegamer.breedtimer.mixin.BucketReleaseMixin}. A plain mutable class
     * rather than a record, for the same reason {@link Sighting} above is one: {@code ticksLeft} is
     * decremented in place by the same {@code removeIf} idiom {@link #pendingDuplications} uses, which a
     * record's immutable component cannot support.
     */
    private static final class PendingRelease {
        final EntityType<?> type;
        final Vec3 pos;
        final int remainingTicks;
        int ticksLeft;

        PendingRelease(EntityType<?> type, Vec3 pos, int remainingTicks, int ticksLeft) {
            this.type = type;
            this.pos = pos;
            this.remainingTicks = remainingTicks;
            this.ticksLeft = ticksLeft;
        }
    }

    /** Buckets the local player has just emptied, waiting for the mob to stream in; see {@link #tick}. */
    private static final List<PendingRelease> pendingReleases = new ArrayList<>();
    /** Same window the allay child match uses, and for the same reason: one batch of packets, held for a second. */
    private static final int BUCKET_RELEASE_WINDOW_TICKS = 20;
    /** {@code spawn()} places the mob at the clicked block, and a fish drifts; 4 blocks absorbs that. */
    private static final double BUCKET_RELEASE_RADIUS_SQ = 4.0 * 4.0;

    /**
     * Ticks each tracked entity has gone unseen. Timers deliberately pause while an entity is
     * unloaded, which means an animal that dies out of render distance would otherwise keep its
     * entry forever — and that entry is written to disk on every world leave, so the save file
     * grew without bound. After an hour of play without sighting it, the value is worthless.
     */
    private static final Map<UUID, Integer> unseenTicks = new HashMap<>();
    private static final int FORGET_AFTER_UNSEEN_TICKS = 72000;

    public enum AnimalState {
        READY,
        COOLDOWN,
        IN_LOVE,
        BABY,
        /** Baby held at its current age by a golden dandelion — it will never grow up. */
        AGE_LOCKED,
        /** Adult that Minecraft refuses to breed right now; the reason is the {@link BlockReason}. */
        BLOCKED,
        /** Baby that can never grow up at all — see {@link #NEVER_GROWS_UP}. */
        NEVER_GROWS
    }

    /**
     * Why an adult cannot breed. This is a reason carried by a single {@link AnimalState#BLOCKED}
     * state instead of one state constant per condition, because every constant added to
     * {@code AnimalState} has to be handled at each exhaustive switch on every supported Minecraft
     * version — which is what made {@code AGE_LOCKED} expensive.
     */
    public enum BlockReason {
        /** Never breeds at all: mule, camel husk. */
        STERILE,
        /** Wolf, cat, nautilus and the equines all breed only once tamed. */
        UNTAMED,
        /** {@code AbstractHorse.canParent()} demands full health. */
        HURT,
        /** An equine being ridden, or riding something else, cannot breed. */
        MOUNTED,
        /** Temporary species condition: turtle carrying an egg, armadillo rolled up. */
        BUSY,
        /** Panda with no bamboo in reach of its breed goal. */
        NO_BAMBOO,
        /** Wolf that has been told to sit; {@code Wolf.canMate} refuses a sitting partner. */
        SITTING,
        /** Turtle that has reached its home beach and is laying; the countdown is exact. */
        LAYING_EGG,
        /** Sniffer mid-dig; the seed drops 120 ticks after the event we saw. */
        DIGGING,
        /** Worried panda during a thunderstorm; {@code Panda.mobInteract} refuses everything. */
        SCARED,
        /**
         * Hoglin with a warped fungus, nether portal or respawn anchor within 8 blocks horizontally and 4
         * vertically. Vanilla calls this PACIFIED and keeps it in the brain, which never reaches the client;
         * the scan is replayed instead. See {@link HoglinRepellents} for what that does and does not reproduce.
         */
        REPELLED
    }

    /**
     * A breeding gate and, where the game gives an exact figure, how long it still has to run.
     *
     * <p>A record rather than a second {@link AnimalState}: the {@link BlockReason} javadoc above
     * explains why a new state costs an arm at every exhaustive switch on six branches, and only two
     * gates in the game carry a number at all -- a turtle laying and a sniffer digging. Everything
     * else uses the single-argument constructor and reports zero, exactly as before.
     */
    private record Blocked(BlockReason reason, int remainingTicks) {
        Blocked(BlockReason reason) {
            this(reason, 0);
        }
    }

    public record AnimalTimerInfo(Animal animal, AnimalState state, int remainingTicks, int color,
                                  BlockReason blockReason) {
        /** For every state other than {@link AnimalState#BLOCKED}, which is the only one with a reason. */
        public AnimalTimerInfo(Animal animal, AnimalState state, int remainingTicks, int color) {
            this(animal, state, remainingTicks, color, null);
        }
    }

    /**
     * @param worldId       the world's current (save-folder, for singleplayer) id
     * @param legacyWorldId the id this world's save file used to live under before F-1, or null if
     *                      the two ids coincide -- see {@link WorldSaveMigration}
     */
    public static void onWorldJoin(String worldId, String legacyWorldId) {
        currentWorldId = worldId;
        BreedCooldownHelper.legacyWorldId = legacyWorldId;
        cooldownMap.clear();
        loveMap.clear();
        babyGrowth.clear();
        tadpoleGrowth.clear();
        AgeableTracking.clear();
        pandaNoBamboo.clear();
        pandaPartnerGenes.clear();
        HoglinRepellents.clear();
        turtleHadEgg.clear();
        turtleWasLaying.clear();
        turtleLaying.clear();
        snifferSeed.clear();
        allayCouldDuplicate.clear();
        allayCooldown.clear();
        pendingDuplications.clear();
        unexplainedAllays.clear();
        pendingReleases.clear();
        unseenTicks.clear();
        pendingGrassMeal.clear();
        BreedingFoodHelper.clear();
        DropWindows.clear();
        lastGameTime = -1;
        loadData();
    }

    public static void onWorldLeave() {
        if (currentWorldId != null) {
            saveData();
        }
        currentWorldId = null;
        legacyWorldId = null;
        cooldownMap.clear();
        loveMap.clear();
        babyGrowth.clear();
        tadpoleGrowth.clear();
        AgeableTracking.clear();
        pandaNoBamboo.clear();
        pandaPartnerGenes.clear();
        HoglinRepellents.clear();
        turtleHadEgg.clear();
        turtleWasLaying.clear();
        turtleLaying.clear();
        snifferSeed.clear();
        allayCouldDuplicate.clear();
        allayCooldown.clear();
        pendingDuplications.clear();
        unexplainedAllays.clear();
        pendingReleases.clear();
        unseenTicks.clear();
        pendingGrassMeal.clear();
        BreedingFoodHelper.clear();
        DropWindows.clear();
        lastGameTime = -1;
    }

    private static Path getSaveFile() {
        return SAVE_DIR.resolve(currentWorldId + ".json");
    }

    /** Null exactly when {@link #legacyWorldId} is, i.e. when there is nothing to migrate from. */
    private static Path getLegacySaveFile() {
        return legacyWorldId == null ? null : SAVE_DIR.resolve(legacyWorldId + ".json");
    }

    private static void saveData() {
        try {
            Files.createDirectories(SAVE_DIR);
            // Convert UUID keys to strings for JSON
            Map<String, Map<String, Integer>> data = new HashMap<>();
            data.put("cooldown", uuidMapToString(cooldownMap));
            data.put("baby", uuidMapToString(babyGrowth.snapshot()));
            data.put("tadpole", uuidMapToString(tadpoleGrowth.snapshot()));
            data.put("ageable", uuidMapToString(AgeableTracking.snapshot()));
            // Don't save love mode — it's too short-lived to persist
            Files.writeString(getSaveFile(), GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.error("BreedTimer data error", e);
        }
    }

    private static void loadData() {
        // F-1 migration: load the current id's file if it exists, else fall back to the legacy id's
        // file (see WorldSaveMigration). Writes always target getSaveFile() -- the legacy file, if
        // read, is left untouched.
        Path file = WorldSaveMigration.resolveLoadFile(getSaveFile(), getLegacySaveFile());
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file);
            Type type = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
            Map<String, Map<String, Integer>> data = GSON.fromJson(json, type);
            if (data == null) return;
            if (data.containsKey("cooldown")) stringMapToUuid(data.get("cooldown"), cooldownMap);
            if (data.containsKey("baby")) {
                Map<UUID, Integer> saved = new HashMap<>();
                stringMapToUuid(data.get("baby"), saved);
                babyGrowth.restore(saved);
            }
            // Absent in files written before 1.6.0; a missing key just means no tadpole estimates
            // to carry over, and they re-seed at full growth on first sighting as before.
            if (data.containsKey("tadpole")) {
                Map<UUID, Integer> saved = new HashMap<>();
                stringMapToUuid(data.get("tadpole"), saved);
                tadpoleGrowth.restore(saved);
            }
            // Absent in files written before 1.6.0, exactly like "tadpole": a missing key just means
            // no estimates to carry over, and they re-seed at full growth on first sighting.
            if (data.containsKey("ageable")) {
                Map<UUID, Integer> saved = new HashMap<>();
                stringMapToUuid(data.get("ageable"), saved);
                AgeableTracking.restore(saved);
            }
        } catch (Exception e) {
            LOGGER.error("BreedTimer data error", e);
        }
    }

    private static Map<String, Integer> uuidMapToString(Map<UUID, Integer> map) {
        Map<String, Integer> result = new HashMap<>();
        for (var entry : map.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }
        return result;
    }

    private static void stringMapToUuid(Map<String, Integer> source, Map<UUID, Integer> target) {
        for (var entry : source.entrySet()) {
            try {
                target.put(UUID.fromString(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /**
     * Called from AnimalEventMixin when entity event byte 18 (hearts) is received.
     * First event = entering love mode. Second event while in love = breeding happened.
     */
    public static void onLoveEvent(Animal animal) {
        UUID uuid = animal.getUUID();

        if (loveMap.containsKey(uuid)) {
            // Already in love → this is the breeding event → start cooldown
            loveMap.remove(uuid);
            cooldownMap.put(uuid, BREED_COOLDOWN_TICKS);

            // MC only sends event 18 for one parent — find the mate nearby
            // and put it on cooldown too
            // Nearest, not first: vanilla's BreedGoal.getFreePartner picks by distanceToSqr, and
            // getEntitiesOfClass returns in arbitrary order. With two pairs breeding in one herd,
            // taking the first match could put the cooldown on the wrong animal.
            Level level = animal.level();
            AABB searchBox = animal.getBoundingBox().inflate(MATE_SEARCH_RADIUS);
            List<Animal> nearby = level.getEntitiesOfClass(Animal.class, searchBox,
                    a -> a != animal && canPairWith(animal, a) && loveMap.containsKey(a.getUUID()));
            Animal mate = null;
            double closestDist = Double.MAX_VALUE;
            for (Animal candidate : nearby) {
                double dist = candidate.position().distanceToSqr(animal.position());
                if (dist < closestDist) {
                    closestDist = dist;
                    mate = candidate;
                }
            }
            if (mate != null) {
                loveMap.remove(mate.getUUID());
                cooldownMap.put(mate.getUUID(), BREED_COOLDOWN_TICKS);
            }
        } else {
            // Vanilla only broadcasts 18 from setInLove -- which requires age 0, i.e. no cooldown
            // left -- or from breeding itself. An event arriving while we believe this animal is on
            // cooldown proves our cooldown is wrong; drop it rather than swallow the event.
            cooldownMap.remove(uuid);
            loveMap.put(uuid, LOVE_MODE_TICKS);
        }
    }

    /**
     * A turtle just laid claim to an egg, which in vanilla only happens inside
     * {@code TurtleBreedGoal.breed()} — so both it and its partner were just put on the standard
     * cooldown. Without this the pair would sit at "ready" for the whole five minutes, because that
     * goal is the one breeding path in the game that broadcasts no entity event.
     */
    private static void onTurtleBred(Turtle turtle) {
        cooldownMap.put(turtle.getUUID(), BREED_COOLDOWN_TICKS);
        loveMap.remove(turtle.getUUID());

        Turtle mate = nearestInLoveMate(turtle);
        if (mate != null) {
            loveMap.remove(mate.getUUID());
            cooldownMap.put(mate.getUUID(), BREED_COOLDOWN_TICKS);
        }
    }

    /** The closest still-in-love turtle nearby, i.e. the other parent. */
    private static Turtle nearestInLoveMate(Turtle turtle) {
        List<Turtle> nearby = turtle.level().getEntitiesOfClass(Turtle.class,
                turtle.getBoundingBox().inflate(MATE_SEARCH_RADIUS),
                t -> t != turtle && loveMap.containsKey(t.getUUID()));
        Turtle closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Turtle candidate : nearby) {
            double dist = candidate.position().distanceToSqr(turtle.position());
            if (dist < closestDist) {
                closestDist = dist;
                closest = candidate;
            }
        }
        return closest;
    }

    /**
     * The closest other adult panda in {@code candidates}, within {@link #MATE_SEARCH_RADIUS} --
     * the same box-then-nearest shape {@link #nearestInLoveMate} uses, but run over an
     * already-collected list instead of a fresh world query, since {@link #tick} calls this for
     * every adult panda every tick rather than only at the moment of a love event.
     */
    private static Panda nearestAdultPanda(Panda panda, List<Panda> candidates) {
        AABB searchBox = panda.getBoundingBox().inflate(MATE_SEARCH_RADIUS);
        Panda closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Panda candidate : candidates) {
            if (candidate == panda) continue;
            if (!searchBox.intersects(candidate.getBoundingBox())) continue;
            double dist = candidate.position().distanceToSqr(panda.position());
            if (dist < closestDist) {
                closestDist = dist;
                closest = candidate;
            }
        }
        return closest;
    }

    /**
     * This tick's nearest adult partner's genes for {@code uuid} -- {@code [mainGeneId,
     * hiddenGeneId]} -- or {@code null} if none was in range. See {@link PandaGenes} for what the
     * renderer does with them.
     */
    public static int[] pandaPartnerGenesFor(UUID uuid) {
        return pandaPartnerGenes.get(uuid);
    }

    /**
     * Whether Minecraft would let these two breed together, ignoring whether they are in love.
     *
     * <p>Calling {@code canMate} directly is no use here: every implementation ends in
     * {@code isInLove()}, and {@code inLove} is a plain server-side field that always reads zero on
     * the client. So the pairing rule is mirrored instead.
     *
     * <p>Same class covers almost everything, but two vanilla pairings cross class boundaries and
     * used to be missed, leaving the second parent showing "ready" through its whole cooldown:
     * {@code Horse.canMate} and {@code Donkey.canMate} each accept the other (that is how you get a
     * mule), and {@code Llama.canMate} tests {@code instanceof Llama}, which a trader llama
     * satisfies.
     */
    private static boolean canPairWith(Animal animal, Animal other) {
        if (animal.getClass() == other.getClass()) return true;
        if (animal instanceof Llama && other instanceof Llama) return true;
        return isHorseOrDonkey(animal) && isHorseOrDonkey(other);
    }

    private static boolean isHorseOrDonkey(Animal animal) {
        return animal instanceof Horse || animal instanceof Donkey;
    }

    /**
     * Called every client tick to update timers.
     * Timers only count down for entities that are currently loaded — unloaded
     * entities don't tick server-side, so their real cooldowns pause too.
     * @param paused true if the game is paused (singleplayer ESC menu)
     * @param loadedEntities all entities currently loaded on the client
     */
    public static void tick(Level level, boolean paused, List<Entity> loadedEntities) {
        if (paused) return;

        long currentGameTime = level.getGameTime();
        // Clamped at both ends. The ceiling absorbs a long pause; the floor matters because a game
        // time that ever ran backwards would otherwise hand out a negative delta, and every timer
        // in here subtracts it — they would all count up instead of down.
        int delta = lastGameTime < 0 ? 1
                : (int) Math.max(0, Math.min(currentGameTime - lastGameTime, 6000));
        lastGameTime = currentGameTime;

        Set<UUID> loadedUuids = new HashSet<>();
        for (Entity entity : loadedEntities) {
            loadedUuids.add(entity.getUUID());
        }

        // Age out anything we have not seen for a long time, so dead entities stop accumulating
        // in memory and in the save file.
        // removeIf rather than iterating a defensive copy: the copy existed only to avoid a
        // ConcurrentModificationException, and it allocated a list plus an array every tick.
        unseenTicks.keySet().removeIf(loadedUuids::contains);
        for (UUID tracked : cooldownMap.keySet()) {
            if (!loadedUuids.contains(tracked)) {
                unseenTicks.merge(tracked, delta, Integer::sum);
            }
        }
        unseenTicks.entrySet().removeIf(entry -> {
            if (entry.getValue() < FORGET_AFTER_UNSEEN_TICKS) return false;
            cooldownMap.remove(entry.getKey());
            loveMap.remove(entry.getKey());
            return true;
        });

        // Tick down love timers (paused while the entity is unloaded)
        loveMap.entrySet().removeIf(entry -> {
            if (!loadedUuids.contains(entry.getKey())) return false;
            entry.setValue(entry.getValue() - delta);
            return entry.getValue() <= 0;
        });

        // Allay duplication cooldown, same pause-while-unloaded rule as breeding
        allayCooldown.entrySet().removeIf(entry -> {
            if (!loadedUuids.contains(entry.getKey())) return false;
            entry.setValue(entry.getValue() - delta);
            return entry.getValue() <= 0;
        });

        // Tick down cooldowns (paused while the entity is unloaded)
        cooldownMap.entrySet().removeIf(entry -> {
            if (!loadedUuids.contains(entry.getKey())) return false;
            entry.setValue(entry.getValue() - delta);
            return entry.getValue() <= 0;
        });

        // Same pause-while-unloaded rule as the timers above: a panda we cannot see is not being
        // asked about bamboo either, so its report should not expire behind our back.
        pandaNoBamboo.entrySet().removeIf(entry -> {
            if (!loadedUuids.contains(entry.getKey())) return false;
            entry.setValue(entry.getValue() - delta);
            return entry.getValue() <= 0;
        });

        // These are second-scale countdowns -- nothing worth preserving across an unload, unlike the
        // long-lived cooldown/love/growth maps. A frozen entry that survived an unload could re-attach
        // to a later, unrelated dig or lay the moment the entity streams back into view, handing the
        // HUD a countdown for an event we never actually saw -- exactly the invented number the
        // "we never saw it, so nothing is invented" rule forbids. turtleWasLaying is evicted alongside
        // turtleLaying: it is a plain boolean flip-detector with no expiry of its own, and a stale
        // value left over from before the unload would misread a genuinely new lay -- one that
        // started while the turtle was out of view -- as the same false->true flip already handled,
        // inventing a fresh countdown for a start we did not watch. Dropping it means a turtle
        // reappearing mid-lay is treated exactly like a turtle we are seeing for the first time: no
        // flip is detected until we observe both sides of one ourselves.
        turtleLaying.keySet().removeIf(u -> !loadedUuids.contains(u));
        turtleWasLaying.keySet().removeIf(u -> !loadedUuids.contains(u));
        snifferSeed.keySet().removeIf(u -> !loadedUuids.contains(u));

        // Evicted on unload exactly like the three trackers above, despite being minute-scale rather
        // than second-scale: a frozen entry here would not just lag a real timer the way a paused
        // cooldownMap entry does, it could outlive the very drop it is meant to explain. See
        // DropWindows.tick for the full reasoning.
        DropWindows.tick(delta, loadedUuids);

        // Floored at zero rather than removed, and cleared by the flag rather than by the number:
        // TurtleLayEggGoal only advances its counter while the turtle is out of the water and
        // standing on its target, so a turtle shoved into the sea mid-lay stalls the real countdown
        // while ours keeps running. Holding at 0:00 until LAYING_EGG clears says "any moment now"
        // instead of inventing a second countdown we have no way to observe.
        for (Map.Entry<UUID, Integer> entry : turtleLaying.entrySet()) {
            entry.setValue(Math.max(0, entry.getValue() - delta));
        }

        // Floored rather than removed, for the same reason the turtle countdown is: the dig can be
        // interrupted, and the digging animation state -- not the number -- is what says it is over.
        for (Map.Entry<UUID, Integer> entry : snifferSeed.entrySet()) {
            entry.setValue(Math.max(0, entry.getValue() - delta));
        }

        // A started meal only counts once it finishes. EatBlockGoal.stop() zeroes its counter with
        // no ate(), so panic, a tempt goal or the block being broken cancels the age-up -- and the
        // estimate would otherwise run a full minute short for nothing.
        pendingGrassMeal.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - delta);
            if (entry.getValue() > 0) return false;
            if (loadedUuids.contains(entry.getKey())) {
                babyGrowth.reduceBy(entry.getKey(), SHEEP_GRASS_AGE_UP_TICKS,
                        BabyGrowthTracker.BABY_GROW_TICKS);
            }
            return true;
        });

        // Unlike every map above, pandaPartnerGenes holds no running estimate: it is a pure render
        // cache, rebuilt from live synced gene fields every tick, so it loses nothing by standing
        // still. Gated here the same way BlockLabelScanner.tick is gated at its call site in
        // BreedTimerClient, and for the same reason -- skip the O(n^2) nearest-partner scan below
        // entirely while nothing would read its output (label off, compact mode, or animals hidden)
        // rather than compute something nobody sees.
        BreedTimerConfig config = BreedTimerConfig.get();
        boolean trackPandaPartners = config.enabled && !config.compactMode && config.showAnimals;

        // Collected in the loop below, then used to find each adult panda's nearest partner
        // without a second world query. null while empty, so the common non-panda tick pays for
        // nothing more than one comparison.
        List<Panda> loadedAdultPandas = null;

        // Bucket releases still waiting for the mob they explain, matched by species and proximity to
        // where the item was emptied -- the same consume-on-match shape pendingDuplications uses below.
        // This has to run before the growth loop beneath it, not after: that loop seeds a fresh 20:00 (or
        // 48000 for a sniffer, etc.) the moment it first sees any baby, exact age or not, so by the time a
        // release could be matched afterwards every candidate would already look "already tracked". Skipped
        // up front while empty, which is nearly always true -- this only holds entries for the twenty ticks
        // right after the local player empties a bucket.
        if (!pendingReleases.isEmpty()) {
            for (Entity entity : loadedEntities) {
                if (pendingReleases.isEmpty()) break;
                Iterator<PendingRelease> pending = pendingReleases.iterator();
                while (pending.hasNext()) {
                    PendingRelease release = pending.next();
                    if (entity.getType() != release.type) continue;
                    if (isAlreadyTracked(entity)) continue;
                    if (entity.position().distanceToSqr(release.pos) > BUCKET_RELEASE_RADIUS_SQ) continue;
                    onExactAgeKnown(entity, release.remainingTicks);
                    pending.remove();
                    break;
                }
            }
        }

        // Track baby growth for all loaded animals
        for (Entity entity : loadedEntities) {
            if (entity instanceof Allay allay) {
                UUID allayUuid = allay.getUUID();
                boolean ready = ((AllayDuplicationAccessor) allay).breedtimer$canDuplicate();
                boolean firstSighting = !allayCouldDuplicate.containsKey(allayUuid);
                Boolean was = allayCouldDuplicate.put(allayUuid, ready);
                if (was != null && was && !ready) {
                    allayCooldown.put(allayUuid, ALLAY_COOLDOWN_TICKS);
                    // The flip IS the duplication: setDuplicationCooldown has exactly two callers,
                    // resetDuplicationCooldown (6000) and the decrement, so nothing else can clear
                    // this flag. Remember where it happened -- the child spawns on that spot.
                    pendingDuplications.add(new Sighting(allay.position(), ALLAY_CHILD_WINDOW_TICKS));
                } else if (ready) {
                    allayCooldown.remove(allayUuid);
                } else if (firstSighting) {
                    // On cooldown the first time we ever see it. Either it is somebody's child born
                    // a moment ago, or it is an ordinary allay that just streamed into view; the
                    // match below decides, and until it does we say nothing.
                    unexplainedAllays.put(allayUuid, new Sighting(allay.position(), ALLAY_CHILD_WINDOW_TICKS));
                }
                continue;
            }
            if (entity instanceof Tadpole tadpole) {
                tadpoleGrowth.tick(tadpole.getUUID(), tadpole.isAgeLocked(), delta);
                continue;
            }
            // Dolphins are AgeableMobs but not Animals, so the check below never sees them.
            // AgeableTracking owns that whole special case and says when it has taken an entity.
            if (AgeableTracking.tick(entity, delta)) continue;
            if (!(entity instanceof Animal animal) || !isSupportedAnimal(animal)) continue;
            UUID uuid = animal.getUUID();

            if (animal.isBaby()) {
                babyGrowth.tick(uuid, animal.isAgeLocked(), delta, growTicksFor(animal));
            } else {
                babyGrowth.forget(uuid);
            }

            // Turtles: a false->true egg flip is the only observable trace of their breeding.
            if (animal instanceof Turtle turtle) {
                Boolean had = turtleHadEgg.put(uuid, turtle.hasEgg());
                if (had != null && !had && turtle.hasEgg()) {
                    onTurtleBred(turtle);
                }
                boolean laying = turtle.isLayingEgg();
                Boolean wasLaying = turtleWasLaying.put(uuid, laying);
                if (!laying) {
                    turtleLaying.remove(uuid);
                } else if (wasLaying != null && !wasLaying) {
                    turtleLaying.put(uuid, TURTLE_LAY_TICKS);
                }
            }

            // The dig ended -- successfully or not -- so the countdown has nothing left to count.
            // diggingAnimationState is the public mirror of the private DIGGING state; onSyncedData
            // Updated stops every animation and starts at most one on each state change.
            if (animal instanceof Sniffer sniffer && !sniffer.diggingAnimationState.isStarted()) {
                snifferSeed.remove(uuid);
            }

            if (animal instanceof Panda panda) {
                // Non-zero means the breed goal reported a bamboo miss within the last 32 ticks.
                // Re-arming the latch on every tick of the pulse is deliberate: it costs one map
                // write and it means a pulse that starts while the panda is out of view still
                // lands in full.
                if (panda.getUnhappyCounter() > 0) {
                    pandaNoBamboo.put(uuid, PANDA_NO_BAMBOO_HOLD_TICKS);
                }
                // Only adults are candidates for the cub-odds partner search below -- a baby
                // cannot be bred with, and it is never the subject of that label either. Skipped
                // entirely while trackPandaPartners is false, so a pen full of pandas costs nothing
                // extra with the label off.
                if (trackPandaPartners && !panda.isBaby()) {
                    if (loadedAdultPandas == null) loadedAdultPandas = new ArrayList<>();
                    loadedAdultPandas.add(panda);
                }
            }

            if (animal instanceof Hoglin hoglin) {
                HoglinRepellents.tick(hoglin, delta);
            }
        }

        // Nearest-partner lookup for the panda cub-odds label (Task 35). Always cleared -- even
        // while gated off -- so flipping compactMode or showAnimals back on mid-session cannot
        // briefly show a stale partner from before the flip; rebuilt from scratch every tick using
        // the pandas just collected above -- unlike onLoveEvent's search, this runs for every adult
        // panda every tick, so it must not cost a fresh AABB world query each time.
        pandaPartnerGenes.clear();
        if (trackPandaPartners && loadedAdultPandas != null && loadedAdultPandas.size() > 1) {
            for (Panda panda : loadedAdultPandas) {
                Panda nearest = nearestAdultPanda(panda, loadedAdultPandas);
                if (nearest != null) {
                    pandaPartnerGenes.put(panda.getUUID(), new int[] {
                            nearest.getMainGene().getId(), nearest.getHiddenGene().getId()});
                }
            }
        }

        // A freshly duplicated allay arrives with canDuplicate() already false and no earlier true
        // to flip from, so the loop above can never date its cooldown -- it used to read "Not ready"
        // for the whole five minutes. Its parent's flip is the one observable moment of the
        // duplication and duplicateAllay() snaps the child onto the parent's position, so an allay
        // first seen next to a flip we just recorded is that child. resetDuplicationCooldown() is a
        // flat 6000 with no randomness, which makes the result exact rather than an estimate.
        // A match consumes the pending entry: one duplication produces exactly one child, so once
        // a flip has explained a sighting it must not also seed some other allay that happens to
        // start up nearby in the same window -- in a dense allay farm that would hand out false
        // 6000s to unrelated allays streaming into view.
        if (!unexplainedAllays.isEmpty() && !pendingDuplications.isEmpty()) {
            unexplainedAllays.entrySet().removeIf(entry -> {
                Iterator<Sighting> duplications = pendingDuplications.iterator();
                while (duplications.hasNext()) {
                    Sighting duplication = duplications.next();
                    if (entry.getValue().pos.distanceToSqr(duplication.pos) <= ALLAY_CHILD_RADIUS_SQ) {
                        allayCooldown.put(entry.getKey(), ALLAY_COOLDOWN_TICKS);
                        duplications.remove();
                        return true;
                    }
                }
                return false;
            });
        }
        // Both windows are empty in every tick where no allay duplicated, which is almost all of
        // them, so this costs two empty-collection checks.
        pendingDuplications.removeIf(sighting -> (sighting.ticksLeft -= delta) <= 0);
        unexplainedAllays.values().removeIf(sighting -> (sighting.ticksLeft -= delta) <= 0);
        // A release nobody claimed within the window (the mob never streamed into range, or streamed in
        // as something the match above rejected) is simply dropped -- there is no second chance for it,
        // exactly like an unmatched allay sighting above.
        pendingReleases.removeIf(release -> (release.ticksLeft -= delta) <= 0);
    }

    /**
     * The local player just emptied a bucket whose stack carried a known, positive exact age -- see
     * {@link de.dennisthegamer.breedtimer.mixin.BucketReleaseMixin}. Queued rather than applied immediately:
     * the entity {@code checkExtraContent} hands us is the bucket item, not the mob that is about to spawn
     * from it, so the real target has to be found in {@link #tick} once it streams in.
     */
    public static void onBucketReleased(EntityType<?> type, BlockPos pos, int remainingTicks) {
        pendingReleases.add(new PendingRelease(type, Vec3.atCenterOf(pos), remainingTicks, BUCKET_RELEASE_WINDOW_TICKS));
    }

    /**
     * A mob just came out of a bucket and we read its real age off the item. Routes to whichever tracker owns
     * that species -- an axolotl is an {@link Animal}, a tadpole is not, and a dolphin is an
     * {@code AgeableMob} that is not an {@link Animal}.
     */
    private static void onExactAgeKnown(Entity entity, int remainingTicks) {
        UUID uuid = entity.getUUID();
        if (entity instanceof Tadpole) tadpoleGrowth.set(uuid, remainingTicks);
        else if (AgeableTracking.isTracked(entity)) AgeableTracking.set(uuid, remainingTicks);
        else babyGrowth.set(uuid, remainingTicks);
    }

    /**
     * Whether one of the three growth trackers already has an estimate for this entity -- the signal
     * {@link #tick}'s bucket-release match uses to tell a freshly spawned baby, which has none yet, from a
     * long-resident one of the same species that merely happens to be standing near the release point.
     */
    private static boolean isAlreadyTracked(Entity entity) {
        UUID uuid = entity.getUUID();
        if (entity instanceof Tadpole) return tadpoleGrowth.has(uuid);
        if (AgeableTracking.isTracked(entity)) return AgeableTracking.has(uuid);
        return babyGrowth.has(uuid);
    }

    /**
     * A tadpole was fed. {@code Tadpole.feed} calls its own {@code ageUp}, entirely outside the
     * {@code Animal} hierarchy, so the ordinary feed hook never sees it.
     */
    public static void onTadpoleFed(Tadpole tadpole) {
        tadpoleGrowth.onFed(tadpole.getUUID());
    }

    /**
     * Lambs that started eating grass, with the tick budget in which the meal must complete.
     * {@code EatBlockGoal} broadcasts entity event 10 from {@code start()}, but only calls
     * {@code Sheep.ate()} once its own counter reaches 4 of its 40-tick animation; {@code stop()}
     * — panic, a tempt goal, or the block being broken out from under the lamb — zeroes that
     * counter with no {@code ate()} at all. Crediting on the start event alone would hand a
     * scared lamb a phantom minute off its estimate, so the credit is deferred until this
     * countdown runs out; see the {@code tick} entry below.
     */
    private static final Map<UUID, Integer> pendingGrassMeal = new HashMap<>();
    /** EatBlockGoal broadcasts 10 at start and calls ate() when its counter reaches 4 of 40. */
    private static final int GRASS_MEAL_TICKS = 40;
    /** {@code Sheep.ate()} does {@code ageUp(60)}; ageUp multiplies its argument by 20. */
    private static final int SHEEP_GRASS_AGE_UP_TICKS = 1200;

    /**
     * A lamb started eating a grass block. {@code Sheep.ate()} gates on {@code canAgeUp()} =
     * {@code isBaby() && !isAgeLocked()} on 26.x before applying its flat {@code ageUp(60)}.
     * Without the lock test here an age-locked lamb would lose 1200 ticks off an estimate that is
     * meant to be frozen. This only records intent; {@link #tick} applies the actual reduction
     * once the meal finishes.
     */
    public static void onSheepStartedEatingGrass(Animal sheep) {
        if (!sheep.isBaby() || sheep.isAgeLocked()) return;
        pendingGrassMeal.put(sheep.getUUID(), GRASS_MEAL_TICKS);
    }

    /**
     * Called from AnimalEventMixin when the local player feeds a baby.
     *
     * <p>The client runs the feeding branch of {@code Animal.mobInteract} itself, so this fires on
     * exactly the same interactions that speed up the real animal. We cannot read the amount from
     * vanilla though: the age field is never synced, and client-side {@code getAge()} short-circuits
     * to -1 for a baby (and +1 for an adult), so vanilla's own calculation there yields a speed-up
     * of zero. Only the event is usable — the size of the cut has to come from our own estimate.
     */
    public static void onBabyFed(Animal animal) {
        if (!isSupportedAnimal(animal) || !animal.isBaby()) return;
        babyGrowth.onFed(animal.getUUID());
    }

    /**
     * A baby equine was fed. These never reach {@code Animal.mobInteract}, so the ordinary feed
     * hook cannot see them: {@code Horse}, {@code AbstractChestedHorse} and {@code Camel} each
     * override {@code mobInteract} and delegate to {@code super} only when the animal is a vehicle
     * or when a baby is offered a golden dandelion. Every real food falls past that branch onto
     * {@code AbstractHorse.fedFood} and returns from there. Nothing overrides {@code fedFood},
     * which is why it is the one hook that covers horse, donkey, mule, llama and camel alike.
     *
     * <p>Injecting on the {@code ageUp} call the way the {@code Animal} path does would not work
     * either: in {@code handleEating} that call sits behind an {@code isClientSide} guard, so the
     * client never runs it. The size of the cut therefore has to be mirrored rather than observed.
     */
    public static void onEquineFed(Animal animal, ItemStack stack) {
        // canAgeUp() is isBaby() && !isAgeLocked(), and handleEating checks both before ageing up.
        if (!isSupportedAnimal(animal) || !animal.isBaby() || animal.isAgeLocked()) return;
        int seconds = equineAgeUpSeconds(animal, stack);
        if (seconds <= 0) return;
        babyGrowth.reduceBy(animal.getUUID(), seconds * 20);
    }

    /**
     * Vanilla's growth reward for feeding an equine, in seconds. This is a flat amount rather than
     * the proportional cut {@link BabyGrowthTracker#onFed} applies, because {@code handleEating}
     * calls {@code ageUp(seconds)} directly — and {@code ageUp} multiplies its argument by 20.
     *
     * <p>Three separate rules, each read off 26.2 bytecode rather than assumed, because
     * {@code handleEating} is overridden twice and the numbers genuinely disagree: a llama gets
     * half a horse's reward for the same wheat.
     */
    private static int equineAgeUpSeconds(Animal animal, ItemStack stack) {
        // Camel.handleEating ignores which food it was and always ages up ten seconds.
        if (animal instanceof Camel) return 10;
        // Llama.handleEating knows only these two items; anything else feeds but does not grow.
        if (animal instanceof Llama) {
            if (stack.is(Items.WHEAT)) return 10;
            if (stack.is(Items.HAY_BLOCK)) return 90;
            return 0;
        }
        // AbstractHorse.handleEating. Red mushroom is deliberately absent: it heals three hearts
        // but its age-up figure is zero, so it must not shorten the estimate.
        if (stack.is(Items.WHEAT)) return 20;
        if (stack.is(Items.SUGAR)) return 30;
        if (stack.is(Items.HAY_BLOCK)) return 180;
        if (stack.is(Items.APPLE)) return 60;
        if (stack.is(Items.CARROT)) return 60;
        if (stack.is(Items.GOLDEN_CARROT)) return 60;
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return 240;
        return 0;
    }

    public static List<AnimalTimerInfo> getVisibleAnimals(Player player, Level level) {
        BreedTimerConfig config = BreedTimerConfig.get();
        int radius = config.effectiveScanRadius();

        AABB scanBox = player.getBoundingBox().inflate(radius);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, scanBox);
        List<AnimalTimerInfo> result = new ArrayList<>();

        Vec3 eyePos = player.getEyePosition(1.0f);
        Visibility visibility = new Visibility(player, config);

        for (Animal animal : animals) {
            if (!isSupportedAnimal(animal)) continue;
            if (!visibility.inView(animal)) continue;

            Vec3 animalPos = animal.position().add(0, animal.getBbHeight() / 2.0, 0);
            double dist = eyePos.distanceTo(animalPos);
            if (dist > config.fadeEndDistance) continue;

            if (!hasLineOfSight(level, eyePos, animalPos, player)) continue;

            result.add(createTimerInfo(animal));
        }

        return result;
    }

    /**
     * Whether nothing solid stands between these two points. Used by the ready chime, and from 1.6.0 by the
     * optional through-walls label gate. {@code ClipContext.Fluid.NONE} deliberately ignores water: a cow across
     * a pond is not hidden.
     *
     * <p>The 1.0 tolerance on the hit is what makes it usable against an entity's centre -- a ray aimed at the
     * middle of a cow legitimately stops on the cow's own neighbouring block at grazing angles.
     */
    public static boolean hasLineOfSight(Level level, Vec3 from, Vec3 to, Entity entity) {
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        HitResult hit = level.clip(ctx);
        return hit.getType() == HitResult.Type.MISS ||
                hit.getLocation().distanceToSqr(to) < 1.0;
    }

    /**
     * Whether the state filter admits this state. The single place the filter is evaluated for animals, so
     * the label path and the compact HUD can never disagree about what is on screen -- they could before,
     * because {@code showBabyTimer} was tested independently in four files.
     *
     * <p>{@code AGE_LOCKED} and {@code NEVER_GROWS} ride on the babies flag: both are babies, just
     * permanently so, which is how the HUD has always counted them.
     */
    public static boolean isShown(AnimalState state) {
        BreedTimerConfig config = BreedTimerConfig.get();
        return switch (state) {
            case READY -> config.showReady;
            case COOLDOWN -> config.showCooldown;
            case IN_LOVE -> config.showInLove;
            case BLOCKED -> config.showBlocked;
            case BABY, AGE_LOCKED, NEVER_GROWS -> config.showBabyTimer;
        };
    }

    public static AnimalTimerInfo createTimerInfo(Animal animal) {
        UUID uuid = animal.getUUID();
        StatePalette p = StatePalette.current();

        // Check baby
        if (animal.isBaby()) {
            // Skeleton and zombie horse foals can never age up, so a countdown would just run out
            // and be wrong forever; they get the same frozen treatment as an age-locked baby.
            if (neverGrowsUp(animal)) {
                return new AnimalTimerInfo(animal, AnimalState.NEVER_GROWS, 0, p.inert);
            }
            int remaining = babyGrowth.remainingFor(uuid, growTicksFor(animal));
            // canAgeUp() is isBaby() && !isAgeLocked(), so a locked baby stays a baby forever.
            if (animal.isAgeLocked()) {
                return new AnimalTimerInfo(animal, AnimalState.AGE_LOCKED, remaining, p.inert);
            }
            return new AnimalTimerInfo(animal, AnimalState.BABY, remaining, p.young);
        }

        // Before love mode, not after. Most vanilla gates live in canMate/canParent rather than in
        // canFallInLove, so an animal can legitimately enter love mode and still never breed: a
        // tamed mule fed a golden carrot, an injured or ridden horse, a panda with no bamboo, a
        // sitting wolf. Reporting "In Love" there promises something that cannot happen.
        Blocked blocked = blockedBy(animal);
        if (blocked != null) {
            // Grey for the permanent case, the same convention AGE_LOCKED uses; amber for the rest,
            // which the player can do something about.
            int color = blocked.reason() == BlockReason.STERILE ? p.inert : p.coolMid;
            return new AnimalTimerInfo(animal, AnimalState.BLOCKED, blocked.remainingTicks(),
                    color, blocked.reason());
        }

        // Check love mode
        if (loveMap.containsKey(uuid)) {
            return new AnimalTimerInfo(animal, AnimalState.IN_LOVE, loveMap.get(uuid), p.love);
        }

        // Check our tracked cooldown
        if (cooldownMap.containsKey(uuid)) {
            int remaining = cooldownMap.get(uuid);
            int color;
            int seconds = remaining / 20;
            if (seconds > 180) {
                color = p.coolFar;
            } else if (seconds > 60) {
                color = p.coolMid;
            } else {
                color = p.coolNear;
            }
            return new AnimalTimerInfo(animal, AnimalState.COOLDOWN, remaining, color);
        }

        return new AnimalTimerInfo(animal, AnimalState.READY, 0, p.ready);
    }

    /**
     * The condition Minecraft would itself reject this adult on, or {@code null} if it is free to
     * breed. Every value read here is synced to the client, so unlike the growth estimate this is
     * exact.
     *
     * <p>One vanilla gate is deliberately not modelled: a sniffer's state is synced, but
     * {@code Sniffer.getState()} is private, so reading it would need an accessor mixin against a
     * method that obfuscated multi-version jars may rename mid-range — a poor trade for a state that
     * lasts a few seconds. A hoglin's pacified flag looks the same on paper -- it lives in its brain
     * ({@code MemoryModuleType.PACIFIED}), and brains never leave the server -- but {@link
     * HoglinRepellents} replays the block scan that memory is derived from instead of reading it.
     *
     * <p>The return type carries a countdown for the two gates that have one; every other gate
     * reports zero, which is what {@code createTimerInfo} passed before this existed.
     */
    private static Blocked blockedBy(Animal animal) {
        if (isSterile(animal)) return new Blocked(BlockReason.STERILE);

        if (animal instanceof AbstractHorse horse) {
            // AbstractHorse.canParent() = !isVehicle() && !isPassenger() && isTamed() && !isBaby()
            //                             && getHealth() >= getMaxHealth() && isInLove()
            // Baby and in-love are already handled above. Taming is reported first: it is the one
            // the player has to fix before any of the others can even be reached.
            if (!horse.isTamed()) return new Blocked(BlockReason.UNTAMED);
            if (horse.getHealth() < horse.getMaxHealth()) return new Blocked(BlockReason.HURT);
            if (horse.isVehicle() || horse.isPassenger()) return new Blocked(BlockReason.MOUNTED);
        } else if (animal instanceof TamableAnimal tamable) {
            // Wolf and cat test isTame() on both sides in canMate. The nautilus gates one level
            // earlier, in isFood(): for an untamed adult only taming items count as food, so it
            // never enters love mode in the first place.
            if (!tamable.isTame()) return new Blocked(BlockReason.UNTAMED);
            // Wolf.canMate additionally rejects a partner in a sitting pose, so a wolf told to sit
            // cannot be bred with at all. Cats carry no such check.
            if (animal instanceof Wolf && tamable.isInSittingPose()) return new Blocked(BlockReason.SITTING);
            // Same outcome as AbstractHorse.canParent()'s full-health rule, reached the other way
            // round: each of these three mobInteract methods routes food into healing and returns
            // SUCCESS before setInLove is anywhere in reach, so a hurt one cannot enter love mode
            // at all. Health is synced, so this is exact.
            //
            // Reported after SITTING, not before, so a sitting wolf keeps the label 1.5.1 gave it.
            if (tamable.getHealth() < tamable.getMaxHealth() && divertsFoodToHealing(tamable)) {
                return new Blocked(BlockReason.HURT);
            }
        }

        if (animal instanceof Turtle turtle && turtle.hasEgg()) {
            // Laying is a strict sub-state of carrying: TurtleLayEggGoal.canUse() requires hasEgg().
            // isLayingEgg() is synced and true either way, so a turtle we did not watch start still
            // gets the plain "Laying egg" label -- just with no countdown, since only a flip we saw
            // gives us a number to show.
            if (turtle.isLayingEgg()) {
                Integer remaining = turtleLaying.get(animal.getUUID());
                return new Blocked(BlockReason.LAYING_EGG, remaining == null ? 0 : remaining);
            }
            return new Blocked(BlockReason.BUSY);
        }
        if (animal instanceof Sniffer sniffer && !snifferIsCalm(sniffer)) {
            // A dig we watched start carries an exact countdown; a sniffer that was already digging
            // when it came into view, or one that is only scenting or sniffing, keeps "Busy".
            Integer remaining = snifferSeed.get(animal.getUUID());
            if (remaining != null && sniffer.diggingAnimationState.isStarted()) {
                return new Blocked(BlockReason.DIGGING, remaining);
            }
            return new Blocked(BlockReason.BUSY);
        }
        if (animal instanceof Armadillo armadillo && armadillo.isScared()) return new Blocked(BlockReason.BUSY);

        // Hoglin.canFallInLove() is !HoglinAi.isPacified(this) && super, so a repelled hoglin cannot be put into
        // love mode at all -- it will not take the crimson fungus. It does not stop a breeding already in
        // flight (AnimalMakeLove never consults PACIFIED), which is why the label says what is refused rather
        // than promising the pair will not breed.
        if (animal instanceof Hoglin && HoglinRepellents.isRepelled(animal.getUUID())) {
            return new Blocked(BlockReason.REPELLED);
        }

        // Panda.mobInteract returns PASS on isScared() before it even reads the item, so a scared
        // panda takes no bamboo at all -- not to breed and not to grow a cub. isScared() is
        // isWorried() && level().isThundering(): the gene is synced and the thunder level is a
        // client value, which PandaRenderer.extractRenderState relies on every frame.
        if (animal instanceof Panda panda && panda.isScared()) return new Blocked(BlockReason.SCARED);

        // Two in-love pandas still will not breed without bamboo in reach -- the requirement sits
        // in the breed goal rather than in canMate, and the goal broadcasts its own verdict. This
        // therefore only appears once the pair has actually tried, which is later than the old
        // block sweep managed but is never wrong.
        if (animal instanceof Panda && pandaNoBamboo.containsKey(animal.getUUID())) {
            return new Blocked(BlockReason.NO_BAMBOO);
        }

        return null;
    }

    /**
     * Whether this tame animal's {@code mobInteract} would spend the food on healing rather than on
     * love mode.
     *
     * <p>Wolf and nautilus divert for any player. The cat only diverts for its owner: {@code Cat
     * .mobInteract} tests {@code isOwnedBy(player)} first and sends everyone else straight to
     * {@code super.mobInteract}, so a second player really can breed somebody else's injured cat.
     * The mod is a single-player-perspective overlay, so "the player" here is the local one.
     */
    private static boolean divertsFoodToHealing(TamableAnimal tamable) {
        if (tamable instanceof Wolf) return true;
        // 26.x only: Nautilus ships on both mc26.1 and mc26.2 but does not exist on any 1.21
        // branch. Remove this line and the Nautilus import when porting to a 1.21 branch.
        if (tamable instanceof Nautilus) return true;
        if (!(tamable instanceof Cat)) return false;
        Player local = Minecraft.getInstance().player;
        return local != null && tamable.isOwnedBy(local);
    }

    /**
     * Mules and camel husks inherit a {@code canMate} that always returns false.
     *
     * <p>Trader llamas are <em>not</em> sterile, despite overriding nothing: they inherit
     * {@code Llama.canMate}, whose partner test is {@code instanceof Llama}, which a trader llama
     * satisfies. A tamed pair breeds like any other llama.
     */
    private static boolean isSterile(Animal animal) {
        return neverBreeds(animal);
    }

    /**
     * True for the {@link Animal}s Minecraft never lets you breed, each verified against the game's
     * own bytecode rather than assumed:
     *
     * <ul>
     *   <li>{@code mule}, {@code skeleton_horse} — inherit {@code AbstractHorse.canMate}, a hard false</li>
     *   <li>{@code camel_husk}, {@code zombie_horse}, {@code happy_ghast} — {@code canFallInLove}
     *       is a hard false</li>
     *   <li>{@code parrot} — {@code canMate} is a hard false <em>and</em> {@code getBreedOffspring}
     *       returns null</li>
     *   <li>{@code polar_bear} — {@code getBreedOffspring} builds a real cub, but {@code isFood} is
     *       a hard false, so the love branch of {@code mobInteract} can never be reached and that
     *       code is unreachable</li>
     *   <li>{@code zombie_nautilus} — {@code getBreedOffspring} returns null, so no baby ever spawns</li>
     * </ul>
     *
     * <p>Matched by registry id rather than by class for two reasons: these mobs sit in packages
     * that moved between versions, and several of them do not exist on the older branches at all —
     * an id that never turns up simply never matches.
     */
    public static boolean neverBreeds(Entity entity) {
        return NEVER_BREEDS.contains(
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());
    }

    /**
     * An allay's duplication state. {@code remainingTicks} is -1 when the allay is on cooldown but
     * we never saw it duplicate — the flag says "not yet", without saying for how long.
     *
     * <p>{@code dancing} is a gate, not a timer: {@code Allay.mobInteract} tests {@code isDancing()}
     * before it will even look at the amethyst shard, so a ready allay with no jukebox playing
     * nearby cannot be duplicated at all. {@code ready()} stays truthful either way, because the
     * compact HUD counts on it.
     */
    public record AllayTimerInfo(int remainingTicks, boolean ready, boolean dancing, int color) {}

    public static AllayTimerInfo createAllayInfo(Allay allay) {
        StatePalette p = StatePalette.current();
        boolean dancing = allay.isDancing();
        if (((AllayDuplicationAccessor) allay).breedtimer$canDuplicate()) {
            // Amber, the mod's colour for a gate the player can act on: put a jukebox down.
            return new AllayTimerInfo(0, true, dancing, dancing ? p.ready : p.coolMid);
        }
        Integer remaining = allayCooldown.get(allay.getUUID());
        if (remaining == null) return new AllayTimerInfo(-1, false, dancing, p.inert);
        int seconds = remaining / 20;
        int color = seconds > 180 ? p.coolFar : seconds > 60 ? p.coolMid : p.coolNear;
        return new AllayTimerInfo(remaining, false, dancing, color);
    }

    /** A tadpole's estimated run to frog. Age-locked works the same way it does for babies. */
    public record TadpoleTimerInfo(int remainingTicks, boolean ageLocked, int color) {}

    public static TadpoleTimerInfo createTadpoleInfo(Tadpole tadpole) {
        int remaining = tadpoleGrowth.remainingFor(tadpole.getUUID());
        StatePalette p = StatePalette.current();
        return tadpole.isAgeLocked()
                ? new TadpoleTimerInfo(remaining, true, p.inert)
                : new TadpoleTimerInfo(remaining, false, p.young);
    }

    /**
     * How long this animal's babyhood lasts. Everything uses {@code AgeableMob.BABY_START_AGE}
     * (-24000) except the sniffer, whose {@code getBabyStartAge()} returns -48000 — verified to be
     * the only override of that method in the game.
     */
    private static int growTicksFor(Animal animal) {
        return animal instanceof Sniffer ? SNIFFER_GROW_TICKS : BabyGrowthTracker.BABY_GROW_TICKS;
    }

    /** Mirrors {@code Sniffer.SNIFFER_BABY_START_AGE} (-48000). */
    private static final int SNIFFER_GROW_TICKS = 48000;

    /**
     * Whether a sniffer is in one of the three states {@code Sniffer.canMate} accepts — IDLING,
     * SCENTING or FEELING_HAPPY of its seven.
     *
     * <p>{@code getState()} is private, but it does not need to be read: {@code onSyncedDataUpdated}
     * mirrors every state change onto the public animation fields, stopping all of them and then
     * starting at most one. Going through those avoids an accessor mixin against a private method
     * whose name obfuscated multi-version jars are free to change.
     */
    private static boolean snifferIsCalm(Sniffer sniffer) {
        return !sniffer.isSearching()
                && !sniffer.sniffingAnimationState.isStarted()
                && !sniffer.diggingAnimationState.isStarted()
                && !sniffer.risingAnimationState.isStarted();
    }

    private static boolean neverGrowsUp(Entity entity) {
        return NEVER_GROWS_UP.contains(
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());
    }

    /**
     * Hand-built rather than {@code String.format("%d:%02d", ...)}, which parses its format string
     * and spins up a Formatter, a StringBuilder and a varargs array on every call — and this runs
     * once per labelled animal per frame. The output is identical for every value it can be given:
     * ticks are never negative, so minutes and seconds are never negative either.
     */
    public static String formatTime(int ticks) {
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return seconds < 10 ? minutes + ":0" + seconds : minutes + ":" + seconds;
    }

    /** Last {@code fovAngle} the cosine below was computed for; the angle is a plain int slider. */
    private static int cachedFovAngle = Integer.MIN_VALUE;
    private static double cachedFovCos;

    /** Half-angle cosine for the configured field of view, recomputed only when the setting moves. */
    public static double fovCos() {
        int angle = BreedTimerConfig.get().fovAngle;
        if (angle != cachedFovAngle) {
            cachedFovAngle = angle;
            cachedFovCos = Math.cos(Math.toRadians(angle / 2.0));
        }
        return cachedFovCos;
    }

    public static boolean isOutOfFieldOfView(Player player, Entity entity) {
        return isOutOfFieldOfView(player.getEyePosition(1.0f), player.getLookAngle(), fovCos(), entity);
    }

    /**
     * As {@link #isOutOfFieldOfView(Player, Entity)}, but with the three player-dependent values
     * passed in. They do not vary between entities, so a caller looping over many of them should
     * work them out once instead of paying for a cosine and several {@code Vec3} allocations per
     * animal — this used to run six allocations deep, once per animal per frame.
     *
     * <p>{@code lookDir} is expected exactly as {@code getLookAngle()} returns it: normalising it
     * is pointless, because {@code calculateViewVector} already yields a unit vector.
     */
    public static boolean isOutOfFieldOfView(Vec3 eyePos, Vec3 lookDir, double fovCos, Entity entity) {
        Vec3 pos = entity.position();
        double dx = pos.x - eyePos.x;
        double dy = pos.y + entity.getBbHeight() / 2.0 - eyePos.y;
        double dz = pos.z - eyePos.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Mirrors Vec3.normalize(), which yields ZERO below this threshold rather than dividing.
        // Only reachable if the entity sits exactly on the camera, but the old code answered this
        // case as "0 < fovCos", so this one does too.
        if (len < 1.0E-4) return 0.0 < fovCos;
        // dot(look, delta)/len < fovCos, multiplied out. Deliberately NOT squared on both sides:
        // fovCos turns negative past a 180 degree slider setting, and squaring would flip the test.
        return lookDir.x * dx + lookDir.y * dy + lookDir.z * dz < fovCos * len;
    }

    public static float getDistanceFade(Player player, Entity entity) {
        BreedTimerConfig config = BreedTimerConfig.get();
        double dist = player.position().distanceTo(entity.position());

        if (dist <= config.fadeStartDistance) return 1.0f;
        if (dist >= config.fadeEndDistance) return 0.0f;

        return 1.0f - (float) ((dist - config.fadeStartDistance) /
                (config.fadeEndDistance - config.fadeStartDistance));
    }

    /**
     * Memoised {@link #isSupportedAnimal} answers, keyed by entity type.
     *
     * <p>The test is a chain of two dozen {@code instanceof} checks ending, for anything it does
     * not recognise, in a registry reverse lookup — and it runs once per rendered entity per frame
     * from the label mixin, so an unsupported mob standing in view paid the whole chain sixty times
     * a second. The answer depends only on the runtime type, and both the registry and
     * {@link #NEVER_BREEDS} are frozen once mods are loaded, so there is nothing to invalidate;
     * unlike the other caches here it deliberately survives a world change.
     *
     * <p>Assumes one Java class per entity type, which holds throughout vanilla — the same
     * assumption {@code BreedingFoodHelper} already makes when it keys on the type.
     */
    private static final Map<EntityType<?>, Boolean> supportedByType = new HashMap<>();

    public static boolean isSupportedAnimal(Entity entity) {
        Boolean known = supportedByType.get(entity.getType());
        if (known != null) return known;
        boolean result = computeIsSupportedAnimal(entity);
        supportedByType.put(entity.getType(), result);
        return result;
    }

    private static boolean computeIsSupportedAnimal(Entity entity) {
        return entity instanceof Cow
                || entity instanceof Sheep
                || entity instanceof Pig
                || entity instanceof Chicken
                || entity instanceof Rabbit
                || entity instanceof Horse
                || entity instanceof Donkey
                || entity instanceof Mule
                || entity instanceof Llama
                || entity instanceof Fox
                || entity instanceof Wolf
                || entity instanceof Cat
                || entity instanceof Ocelot
                || entity instanceof Panda
                || entity instanceof Goat
                || entity instanceof Camel
                || entity instanceof Sniffer
                || entity instanceof Bee
                || entity instanceof Turtle
                || entity instanceof Axolotl
                || entity instanceof Frog
                || entity instanceof Strider
                || entity instanceof Hoglin
                || entity instanceof MushroomCow
                || entity instanceof Armadillo
                || entity instanceof Nautilus
                // Tracked purely so the label can say they never breed. Their young are unaffected:
                // a ghastling or a polar bear cub still gets the normal growth timer, because the
                // baby check in createTimerInfo runs first.
                || neverBreeds(entity);
    }
}
