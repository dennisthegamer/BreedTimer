package de.dennisthegamer.breedtimer.util;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VillagerCooldownHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("BreedTimer");

    public static final int WILLING_TIMEOUT_TICKS = 120; // 6 sec window — resets on each event 12
    public static final int BREED_COOLDOWN_TICKS = 6000; // 5 minutes, same as animals
    public static final int BABY_GROW_TICKS = 24_000;    // 20 minutes

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_DIR = FabricLoader.getInstance().getConfigDir().resolve("breedtimer");

    private static final Map<UUID, Integer> willingTimeoutMap = new HashMap<>();
    private static final Map<UUID, Integer> cooldownMap = new HashMap<>();
    private static final Map<UUID, Integer> babyTimerMap = new HashMap<>();
    private static String currentWorldId = null;
    private static long lastGameTime = -1;

    public enum VillagerState {
        READY,
        COOLDOWN,
        BABY
    }

    public record VillagerTimerInfo(AbstractVillager villager, VillagerState state, int remainingTicks, int color) {}

    // ── World lifecycle ──────────────────────────────────────────────────────

    public static void onWorldJoin(String worldId) {
        currentWorldId = worldId;
        willingTimeoutMap.clear();
        cooldownMap.clear();
        babyTimerMap.clear();
        lastGameTime = -1;
        loadData();
    }

    public static void onWorldLeave() {
        if (currentWorldId != null) saveData();
        currentWorldId = null;
        willingTimeoutMap.clear();
        cooldownMap.clear();
        babyTimerMap.clear();
        lastGameTime = -1;
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private static Path getSaveFile() {
        return SAVE_DIR.resolve(currentWorldId + "_villagers.json");
    }

    private static void saveData() {
        try {
            Files.createDirectories(SAVE_DIR);
            Map<String, Map<String, Integer>> data = new HashMap<>();
            data.put("cooldown", uuidMapToString(cooldownMap));
            data.put("baby", uuidMapToString(babyTimerMap));
            // Don't save willingTimeoutMap — too short-lived (120 ticks = 6 sec)
            Files.writeString(getSaveFile(), GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.error("BreedTimer villager data error", e);
        }
    }

    private static void loadData() {
        Path file = getSaveFile();
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file);
            Type type = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
            Map<String, Map<String, Integer>> data = GSON.fromJson(json, type);
            if (data == null) return;
            if (data.containsKey("cooldown")) stringMapToUuid(data.get("cooldown"), cooldownMap);
            if (data.containsKey("baby")) stringMapToUuid(data.get("baby"), babyTimerMap);
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
    public static void onWillingEvent(AbstractVillager villager) {
        willingTimeoutMap.put(villager.getUUID(), WILLING_TIMEOUT_TICKS);
    }

    // ── Per-tick update ──────────────────────────────────────────────────────

    public static void tick(Level level, Player player, boolean paused) {
        if (paused) return;

        long currentGameTime = level.getGameTime();
        int delta = lastGameTime < 0 ? 1 : (int) Math.min(currentGameTime - lastGameTime, 6000);
        lastGameTime = currentGameTime;

        // Willing timeout: when it expires, willing phase ended → start breed cooldown
        willingTimeoutMap.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - delta);
            if (entry.getValue() <= 0) {
                cooldownMap.put(entry.getKey(), BREED_COOLDOWN_TICKS);
                return true;
            }
            return false;
        });

        // Breed cooldown countdown
        cooldownMap.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - delta);
            return entry.getValue() <= 0;
        });

        // Baby growth tracking
        BreedTimerConfig config = BreedTimerConfig.get();
        AABB scanBox = player.getBoundingBox().inflate(config.scanRadius);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, scanBox);
        for (Villager villager : villagers) {
            UUID uuid = villager.getUUID();
            if (villager.isBaby()) {
                if (!babyTimerMap.containsKey(uuid)) {
                    babyTimerMap.put(uuid, BABY_GROW_TICKS);
                } else {
                    int remaining = babyTimerMap.get(uuid) - delta;
                    if (remaining <= 0) babyTimerMap.remove(uuid);
                    else babyTimerMap.put(uuid, remaining);
                }
            } else {
                babyTimerMap.remove(uuid);
            }
        }
    }

    // ── State queries ────────────────────────────────────────────────────────

    /** Baby always shown first. Adults show COOLDOWN or READY. Never returns null. */
    public static VillagerTimerInfo createTimerInfo(AbstractVillager villager) {
        UUID uuid = villager.getUUID();

        if (villager.isBaby()) {
            int remaining = babyTimerMap.getOrDefault(uuid, BABY_GROW_TICKS);
            return new VillagerTimerInfo(villager, VillagerState.BABY, remaining, 0x55FFFF);
        }

        if (cooldownMap.containsKey(uuid)) {
            int remaining = cooldownMap.get(uuid);
            int seconds = remaining / 20;
            int color = seconds > 180 ? 0xFF5555 : seconds > 60 ? 0xFFAA00 : 0xFFFF55;
            return new VillagerTimerInfo(villager, VillagerState.COOLDOWN, remaining, color);
        }

        return new VillagerTimerInfo(villager, VillagerState.READY, 0, 0x55FF55);
    }

    public static List<VillagerTimerInfo> getVisibleVillagers(Player player, Level level) {
        BreedTimerConfig config = BreedTimerConfig.get();
        AABB scanBox = player.getBoundingBox().inflate(config.scanRadius);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, scanBox);
        List<VillagerTimerInfo> result = new ArrayList<>();

        Vec3 eyePos = player.getEyePosition(1.0f);

        for (Villager villager : villagers) {
            if (BreedCooldownHelper.isOutOfFieldOfView(player, villager)) continue;

            Vec3 villagerPos = villager.position().add(0, villager.getBbHeight() / 2.0, 0);
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
