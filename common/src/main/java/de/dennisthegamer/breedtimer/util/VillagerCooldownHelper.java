package de.dennisthegamer.breedtimer.util;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.dennisthegamer.breedtimer.platform.Platforms;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
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

    public static final int WILLING_TIMEOUT_TICKS = 120;
    public static final int BREED_COOLDOWN_TICKS = 6000;
    public static final int BABY_GROW_TICKS = 24_000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_DIR = Platforms.get().getConfigDir().resolve("breedtimer");

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

    private static Path getSaveFile() {
        return SAVE_DIR.resolve(currentWorldId + "_villagers.json");
    }

    private static void saveData() {
        try {
            Files.createDirectories(SAVE_DIR);
            Map<String, Map<String, Integer>> data = new HashMap<>();
            data.put("cooldown", uuidMapToString(cooldownMap));
            data.put("baby", uuidMapToString(babyTimerMap));
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

    public static void onWillingEvent(AbstractVillager villager) {
        willingTimeoutMap.put(villager.getUUID(), WILLING_TIMEOUT_TICKS);
    }

    /**
     * Cooldown and baby timers only count down for entities that are currently
     * loaded — unloaded entities don't tick server-side, so their real cooldowns
     * pause too. The willing timeout is a client-side detection window and keeps
     * ticking regardless of load state.
     * @param loadedEntities all entities currently loaded on the client
     */
    public static void tick(Level world, boolean paused, List<Entity> loadedEntities) {
        if (paused) return;

        // Via LevelData: Level.getGameTime() got a fresh intermediary name in 1.21.11,
        // so this multi-version jar NoSuchMethodErrors on 1.21.9/1.21.10. LevelData.getGameTime()
        // (which Level.getGameTime() delegates to) keeps a stable name across all three.
        long currentGameTime = world.getLevelData().getGameTime();
        int delta = lastGameTime < 0 ? 1 : (int) Math.min(currentGameTime - lastGameTime, 6000);
        lastGameTime = currentGameTime;

        Set<UUID> loadedUuids = new HashSet<>();
        for (Entity entity : loadedEntities) {
            loadedUuids.add(entity.getUUID());
        }

        // Willing timeout: when it expires, willing phase ended → start breed cooldown
        willingTimeoutMap.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - delta);
            if (entry.getValue() <= 0) {
                cooldownMap.put(entry.getKey(), BREED_COOLDOWN_TICKS);
                return true;
            }
            return false;
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

    public static List<VillagerTimerInfo> getVisibleVillagers(Player player, Level world) {
        BreedTimerConfig config = BreedTimerConfig.get();
        AABB scanBox = player.getBoundingBox().inflate(config.scanRadius);
        List<Villager> villagers = world.getEntitiesOfClass(Villager.class, scanBox, v -> true);
        List<VillagerTimerInfo> result = new ArrayList<>();

        Vec3 eyePos = player.getEyePosition();

        for (Villager villager : villagers) {
            if (BreedCooldownHelper.isOutOfFieldOfView(player, villager)) continue;

            Vec3 villagerPos = new Vec3(villager.getX(), villager.getY() + villager.getBbHeight() / 2.0, villager.getZ());
            if (eyePos.distanceTo(villagerPos) > config.fadeEndDistance) continue;

            if (!hasLineOfSight(world, eyePos, villagerPos, player)) continue;

            result.add(createTimerInfo(villager));
        }

        return result;
    }

    private static boolean hasLineOfSight(Level world, Vec3 from, Vec3 to, Entity entity) {
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        HitResult hit = world.clip(ctx);
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 1.0;
    }
}
