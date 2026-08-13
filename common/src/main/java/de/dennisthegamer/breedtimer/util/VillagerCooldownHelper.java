package de.dennisthegamer.breedtimer.util;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.render.StatePalette;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.dennisthegamer.breedtimer.platform.Platforms;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VillagerCooldownHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("BreedTimer");

    /**
     * How long a courtship runs before the child appears. {@code VillagerMakeLove} schedules birth
     * at start + 275 + random.nextInt(50), so 324 is its worst case; 325 keeps one tick of slack so
     * the timer never expires before the birth actually happens.
     */
    public static final int COURTSHIP_TICKS = 325;
    public static final int BREED_COOLDOWN_TICKS = 6000; // 5 minutes, same as animals
    public static final int BABY_GROW_TICKS = BabyGrowthTracker.BABY_GROW_TICKS; // 20 minutes

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_DIR = Platforms.get().getConfigDir().resolve("breedtimer");

    /** Villagers currently courting, counting down to the birth that starts their cooldown. */
    private static final Map<UUID, Integer> courtshipMap = new HashMap<>();
    private static final Map<UUID, Integer> cooldownMap = new HashMap<>();
    private static final BabyGrowthTracker babyGrowth = new BabyGrowthTracker();
    private static String currentWorldId = null;
    /** F-1 migration: the id this world's save file used to live under, or null if none applies. */
    private static String legacyWorldId = null;
    private static long lastGameTime = -1;

    /**
     * What to throw a villager so it will breed, straight out of {@code Villager.FOOD_POINTS} —
     * the same map {@code canBreed()} counts against its twelve-point threshold, so the list can
     * never drift from the rule it describes. Resolved once; the map is a compile-time constant.
     */
    private static List<Component> foodHint;

    public static List<Component> foodHint() {
        if (foodHint == null) {
            foodHint = BreedingFoodHelper.fromItems(Villager.FOOD_POINTS.keySet());
        }
        return foodHint;
    }

    public enum VillagerState {
        READY,
        COOLDOWN,
        BABY,
        /** Adult that cannot breed right now. Currently only one cause reaches the client: asleep. */
        BLOCKED
    }

    public record VillagerTimerInfo(AbstractVillager villager, VillagerState state, int remainingTicks, int color) {}

    // ── World lifecycle ──────────────────────────────────────────────────────

    /**
     * @param worldId       the world's current (save-folder, for singleplayer) id
     * @param legacyWorldId the id this world's save file used to live under before F-1, or null if
     *                      the two ids coincide -- see {@link WorldSaveMigration}
     */
    public static void onWorldJoin(String worldId, String legacyWorldId) {
        currentWorldId = worldId;
        VillagerCooldownHelper.legacyWorldId = legacyWorldId;
        courtshipMap.clear();
        cooldownMap.clear();
        babyGrowth.clear();
        lastGameTime = -1;
        loadData();
    }

    public static void onWorldLeave() {
        if (currentWorldId != null) saveData();
        currentWorldId = null;
        legacyWorldId = null;
        courtshipMap.clear();
        cooldownMap.clear();
        babyGrowth.clear();
        lastGameTime = -1;
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private static Path getSaveFile() {
        return SAVE_DIR.resolve(currentWorldId + "_villagers.json");
    }

    /** Null exactly when {@link #legacyWorldId} is, i.e. when there is nothing to migrate from. */
    private static Path getLegacySaveFile() {
        return legacyWorldId == null ? null : SAVE_DIR.resolve(legacyWorldId + "_villagers.json");
    }

    private static void saveData() {
        try {
            Files.createDirectories(SAVE_DIR);
            Map<String, Map<String, Integer>> data = new HashMap<>();
            data.put("cooldown", uuidMapToString(cooldownMap));
            data.put("baby", uuidMapToString(babyGrowth.snapshot()));
            // Don't save courtshipMap — a courtship is over in ~16 seconds
            Files.writeString(getSaveFile(), GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.error("BreedTimer villager data error", e);
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
        } catch (Exception e) {
            LOGGER.error("BreedTimer villager data error", e);
        }
    }

    private static Map<String, Integer> uuidMapToString(Map<UUID, Integer> map) {
        Map<String, Integer> result = new HashMap<>();
        for (var entry : map.entrySet()) result.put(entry.getKey().toString(), entry.getValue());
        return result;
    }

    private static void stringMapToUuid(Map<String, Integer> source, Map<UUID, Integer> target) {
        for (var entry : source.entrySet()) {
            try {
                target.put(UUID.fromString(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException ignored) {}
        }
    }

    // ── Event handler ────────────────────────────────────────────────────────

    // Called each time entity event 12 (heart particles) fires for a Villager.
    // Resets the willing timeout window. When the window expires with no new events,
    // tick() starts the breed cooldown — approximating the post-breed lock.
    /**
     * Courtship began. {@code VillagerMakeLove.start()} broadcasts entity event 18 to both partners
     * exactly once, which is a far better signal than the heart particles this used to infer from:
     * those are event 12, emitted on a 1-in-35 roll per tick, so a quiet stretch used to look like
     * the courtship had ended.
     */
    public static void onCourtshipStart(AbstractVillager villager) {
        courtshipMap.put(villager.getUUID(), COURTSHIP_TICKS);
    }

    /**
     * Heart particles. Only used as a fallback for coming into range mid-courtship, since event 18
     * would already have been missed by then.
     */
    public static void onWillingEvent(AbstractVillager villager) {
        courtshipMap.putIfAbsent(villager.getUUID(), COURTSHIP_TICKS);
    }

    /**
     * Courtship failed. When no bed is free {@code VillagerMakeLove.tryToGiveBirth} broadcasts the
     * angry-villager event and returns without setting any age, so there is no cooldown to show —
     * previously the mod invented a full five minutes here.
     */
    public static void onCourtshipFailed(AbstractVillager villager) {
        courtshipMap.remove(villager.getUUID());
    }

    /** Whether we currently believe this villager is courting. See {@code onCourtshipFailed}. */
    public static boolean isCourting(UUID uuid) {
        return courtshipMap.containsKey(uuid);
    }

    // ── Per-tick update ──────────────────────────────────────────────────────

    /**
     * Cooldown and baby timers only count down for entities that are currently
     * loaded — unloaded entities don't tick server-side, so their real cooldowns
     * pause too. The willing timeout is a client-side detection window and keeps
     * ticking regardless of load state.
     * @param loadedEntities all entities currently loaded on the client
     */
    public static void tick(Level level, boolean paused, List<Entity> loadedEntities) {
        if (paused) return;

        // Via LevelData: Level.getGameTime() got a fresh intermediary name in 1.21.11,
        // so this multi-version jar NoSuchMethodErrors on 1.21.9/1.21.10. LevelData.getGameTime()
        // (which Level.getGameTime() delegates to) keeps a stable name across all three.
        long currentGameTime = level.getLevelData().getGameTime();
        // Same clamp as BreedCooldownHelper.tick: the floor stops a backwards game time from
        // handing out a negative delta, which every timer here subtracts.
        int delta = lastGameTime < 0 ? 1
                : (int) Math.max(0, Math.min(currentGameTime - lastGameTime, 6000));
        lastGameTime = currentGameTime;

        Set<UUID> loadedUuids = new HashSet<>();
        for (Entity entity : loadedEntities) {
            loadedUuids.add(entity.getUUID());
        }

        // Courtship complete → a child should have been born, which is what puts both parents on
        // cooldown. But VillagerMakeLove.tick() bails out before the birth check when the pair is
        // more than sqrt(5) blocks apart, and canStillUse re-tests isBreedingPossible every tick —
        // so a partner that dies, is led away or never closes the distance ends the behaviour with
        // no child, no age change and no entity event. Inventing a cooldown there is worse than
        // missing one, because the villagers really are still willing.
        courtshipMap.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - delta);
            if (entry.getValue() > 0) return false;
            if (sawNewbornVillager(loadedEntities)) {
                cooldownMap.put(entry.getKey(), BREED_COOLDOWN_TICKS);
            }
            return true;
        });

        // Breed cooldown countdown (paused while the entity is unloaded)
        cooldownMap.entrySet().removeIf(entry -> {
            if (!loadedUuids.contains(entry.getKey())) return false;
            entry.setValue(entry.getValue() - delta);
            return entry.getValue() <= 0;
        });

        // Baby growth tracking for all loaded villagers
        for (Entity entity : loadedEntities) {
            if (!(entity instanceof Villager villager)) continue;
            UUID uuid = villager.getUUID();
            if (villager.isBaby()) {
                // No age lock in this MC line: the golden dandelion arrived in 26.1.
                babyGrowth.tick(uuid, false, delta);
            } else {
                babyGrowth.forget(uuid);
            }
        }
    }

    /** A baby villager young enough to be the child this courtship was heading towards. */
    private static final int NEWBORN_TICK_WINDOW = 40;

    /**
     * Whether a freshly spawned baby villager is loaded. Unlike the animal case, the child here
     * appears a full tick or more after the courtship timer runs out, so a generous window is safe:
     * VillagerMakeLove.breed() also broadcasts event 12 to the child alone, and no other path
     * creates a baby villager next to a pair we were tracking.
     */
    private static boolean sawNewbornVillager(List<Entity> loadedEntities) {
        for (Entity entity : loadedEntities) {
            if (entity instanceof Villager villager
                    && villager.isBaby()
                    && villager.tickCount <= NEWBORN_TICK_WINDOW) {
                return true;
            }
        }
        return false;
    }

    // ── State queries ────────────────────────────────────────────────────────

    /** As {@code BreedCooldownHelper.isShown(AnimalState)}; a villager's BLOCKED is always "asleep". */
    public static boolean isShown(VillagerState state) {
        BreedTimerConfig config = BreedTimerConfig.get();
        return switch (state) {
            case READY -> config.showReady;
            case COOLDOWN -> config.showCooldown;
            case BABY -> config.showBabyTimer;
            case BLOCKED -> config.showBlocked;
        };
    }

    /** Baby always shown first. Adults show COOLDOWN or READY. Never returns null. */
    public static VillagerTimerInfo createTimerInfo(AbstractVillager villager) {
        UUID uuid = villager.getUUID();
        StatePalette p = StatePalette.current();

        if (villager.isBaby()) {
            int remaining = babyGrowth.remainingFor(uuid);
            return new VillagerTimerInfo(villager, VillagerState.BABY, remaining, p.young);
        }

        if (cooldownMap.containsKey(uuid)) {
            int remaining = cooldownMap.get(uuid);
            int seconds = remaining / 20;
            int color = seconds > 180 ? p.coolFar : seconds > 60 ? p.coolMid : p.coolNear;
            return new VillagerTimerInfo(villager, VillagerState.COOLDOWN, remaining, color);
        }

        // Villager.canBreed() = foodLevel + food in inventory >= 12, && !isSleeping() && age == 0.
        // Only the sleep flag reaches the client: SLEEPING_POS_ID is synced, while foodLevel and the
        // inventory are plain server-side fields. So this catches the sleeping half only — a
        // villager shown as ready may still be short of the twelve food points. See the changelog.
        if (villager.isSleeping()) {
            return new VillagerTimerInfo(villager, VillagerState.BLOCKED, 0, p.coolMid);
        }

        return new VillagerTimerInfo(villager, VillagerState.READY, 0, p.ready);
    }

    public static List<VillagerTimerInfo> getVisibleVillagers(Player player, Level level) {
        BreedTimerConfig config = BreedTimerConfig.get();
        AABB scanBox = player.getBoundingBox().inflate(config.effectiveScanRadius());
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, scanBox, v -> true);
        List<VillagerTimerInfo> result = new ArrayList<>();

        Vec3 eyePos = player.getEyePosition();
        Visibility visibility = new Visibility(player, config);

        for (Villager villager : villagers) {
            if (!visibility.inView(villager)) continue;

            Vec3 villagerPos = new Vec3(villager.getX(), villager.getY() + villager.getBbHeight() / 2.0, villager.getZ());
            if (eyePos.distanceTo(villagerPos) > config.fadeEndDistance) continue;

            if (!hasLineOfSight(level, eyePos, villagerPos, player)) continue;

            result.add(createTimerInfo(villager));
        }

        return result;
    }

    private static boolean hasLineOfSight(Level level, Vec3 from, Vec3 to, Entity entity) {
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        HitResult hit = level.clip(ctx);
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 1.0;
    }
}
