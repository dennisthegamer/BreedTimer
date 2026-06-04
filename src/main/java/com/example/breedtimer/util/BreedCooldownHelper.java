package com.example.breedtimer.util;

import com.example.breedtimer.config.BreedTimerConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.CamelEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.DonkeyEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.MuleEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.OcelotEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.SnifferEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BreedCooldownHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("BreedTimer");

    public static final int BREED_COOLDOWN_TICKS = 6000;
    public static final int LOVE_MODE_TICKS = 600;
    public static final int BABY_GROW_TICKS = 24000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_DIR = FabricLoader.getInstance().getConfigDir().resolve("breedtimer");

    private static final Map<UUID, Integer> cooldownMap = new HashMap<>();
    private static final Map<UUID, Integer> loveMap = new HashMap<>();
    private static final Map<UUID, Integer> babyTimerMap = new HashMap<>();
    private static String currentWorldId = null;
    private static long lastGameTime = -1;

    public enum AnimalState {
        READY,
        COOLDOWN,
        IN_LOVE,
        BABY
    }

    public record AnimalTimerInfo(AnimalEntity animal, AnimalState state, int remainingTicks, int color) {}

    public static void onWorldJoin(String worldId) {
        currentWorldId = worldId;
        cooldownMap.clear();
        loveMap.clear();
        babyTimerMap.clear();
        lastGameTime = -1;
        loadData();
    }

    public static void onWorldLeave() {
        if (currentWorldId != null) {
            saveData();
        }
        currentWorldId = null;
        cooldownMap.clear();
        loveMap.clear();
        babyTimerMap.clear();
        lastGameTime = -1;
    }

    private static Path getSaveFile() {
        return SAVE_DIR.resolve(currentWorldId + ".json");
    }

    private static void saveData() {
        try {
            Files.createDirectories(SAVE_DIR);
            Map<String, Map<String, Integer>> data = new HashMap<>();
            data.put("cooldown", uuidMapToString(cooldownMap));
            data.put("baby", uuidMapToString(babyTimerMap));
            Files.writeString(getSaveFile(), GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.error("BreedTimer data error", e);
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

    public static void onLoveEvent(AnimalEntity animal) {
        UUID uuid = animal.getUuid();

        if (loveMap.containsKey(uuid)) {
            loveMap.remove(uuid);
            cooldownMap.put(uuid, BREED_COOLDOWN_TICKS);

            World world = animal.getEntityWorld();
            Box searchBox = animal.getBoundingBox().expand(8);
            List<AnimalEntity> nearby = world.getEntitiesByClass(AnimalEntity.class, searchBox,
                    a -> a != animal && a.getClass() == animal.getClass());
            for (AnimalEntity mate : nearby) {
                UUID mateUuid = mate.getUuid();
                if (loveMap.containsKey(mateUuid)) {
                    loveMap.remove(mateUuid);
                    cooldownMap.put(mateUuid, BREED_COOLDOWN_TICKS);
                    break;
                }
            }
        } else if (!cooldownMap.containsKey(uuid)) {
            loveMap.put(uuid, LOVE_MODE_TICKS);
        }
    }

    public static void tick(World world, PlayerEntity player, boolean paused) {
        if (paused) return;

        long currentGameTime = world.getTime();
        int delta = lastGameTime < 0 ? 1 : (int) Math.min(currentGameTime - lastGameTime, 6000);
        lastGameTime = currentGameTime;

        loveMap.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - delta);
            return entry.getValue() <= 0;
        });

        cooldownMap.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - delta);
            return entry.getValue() <= 0;
        });

        BreedTimerConfig config = BreedTimerConfig.get();
        int radius = config.scanRadius;
        Box scanBox = player.getBoundingBox().expand(radius);
        List<AnimalEntity> animals = world.getEntitiesByClass(AnimalEntity.class, scanBox, a -> true);

        for (AnimalEntity animal : animals) {
            if (!isSupportedAnimal(animal)) continue;
            UUID uuid = animal.getUuid();

            if (animal.isBaby()) {
                if (!babyTimerMap.containsKey(uuid)) {
                    babyTimerMap.put(uuid, BABY_GROW_TICKS);
                } else {
                    int remaining = babyTimerMap.get(uuid) - delta;
                    if (remaining <= 0) {
                        babyTimerMap.remove(uuid);
                    } else {
                        babyTimerMap.put(uuid, remaining);
                    }
                }
            } else {
                babyTimerMap.remove(uuid);
            }
        }
    }

    public static List<AnimalTimerInfo> getVisibleAnimals(PlayerEntity player, World world) {
        BreedTimerConfig config = BreedTimerConfig.get();
        int radius = config.scanRadius;

        Box scanBox = player.getBoundingBox().expand(radius);
        List<AnimalEntity> animals = world.getEntitiesByClass(AnimalEntity.class, scanBox, a -> true);
        List<AnimalTimerInfo> result = new ArrayList<>();

        Vec3d eyePos = player.getEyePos();

        for (AnimalEntity animal : animals) {
            if (!isSupportedAnimal(animal)) continue;
            if (isOutOfFieldOfView(player, animal)) continue;

            Vec3d animalPos = new Vec3d(animal.getX(), animal.getY() + animal.getHeight() / 2.0, animal.getZ());
            double dist = eyePos.distanceTo(animalPos);
            if (dist > config.fadeEndDistance) continue;

            if (!hasLineOfSight(world, eyePos, animalPos, player)) continue;

            result.add(createTimerInfo(animal));
        }

        return result;
    }

    private static boolean hasLineOfSight(World world, Vec3d from, Vec3d to, Entity entity) {
        RaycastContext ctx = new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity);
        HitResult hit = world.raycast(ctx);
        return hit.getType() == HitResult.Type.MISS ||
                hit.getPos().squaredDistanceTo(to) < 1.0;
    }

    public static AnimalTimerInfo createTimerInfo(AnimalEntity animal) {
        UUID uuid = animal.getUuid();

        if (loveMap.containsKey(uuid)) {
            return new AnimalTimerInfo(animal, AnimalState.IN_LOVE, loveMap.get(uuid), 0xFF55FF);
        }

        if (animal.isBaby()) {
            int remaining = babyTimerMap.getOrDefault(uuid, BABY_GROW_TICKS);
            return new AnimalTimerInfo(animal, AnimalState.BABY, remaining, 0x55FFFF);
        }

        if (cooldownMap.containsKey(uuid)) {
            int remaining = cooldownMap.get(uuid);
            int color;
            int seconds = remaining / 20;
            if (seconds > 180) {
                color = 0xFF5555;
            } else if (seconds > 60) {
                color = 0xFFAA00;
            } else {
                color = 0xFFFF55;
            }
            return new AnimalTimerInfo(animal, AnimalState.COOLDOWN, remaining, color);
        }

        return new AnimalTimerInfo(animal, AnimalState.READY, 0, 0x55FF55);
    }

    public static String formatTime(int ticks) {
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public static boolean isOutOfFieldOfView(PlayerEntity player, Entity entity) {
        BreedTimerConfig config = BreedTimerConfig.get();
        double fovCos = Math.cos(Math.toRadians(config.fovAngle / 2.0));

        Vec3d eyePos = player.getEyePos();
        Vec3d lookDir = player.getRotationVec(1.0f).normalize();
        Vec3d entityPos = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ());
        Vec3d toEntity = entityPos.subtract(eyePos).normalize();

        return lookDir.dotProduct(toEntity) < fovCos;
    }

    public static float getDistanceFade(PlayerEntity player, Entity entity) {
        BreedTimerConfig config = BreedTimerConfig.get();
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        double dist = playerPos.distanceTo(entityPos);

        if (dist <= config.fadeStartDistance) return 1.0f;
        if (dist >= config.fadeEndDistance) return 0.0f;

        return 1.0f - (float) ((dist - config.fadeStartDistance) /
                (config.fadeEndDistance - config.fadeStartDistance));
    }

    public static boolean isSupportedAnimal(Entity entity) {
        return entity instanceof CowEntity
                || entity instanceof SheepEntity
                || entity instanceof PigEntity
                || entity instanceof ChickenEntity
                || entity instanceof RabbitEntity
                || entity instanceof HorseEntity
                || entity instanceof DonkeyEntity
                || entity instanceof MuleEntity
                || entity instanceof LlamaEntity
                || entity instanceof FoxEntity
                || entity instanceof WolfEntity
                || entity instanceof CatEntity
                || entity instanceof OcelotEntity
                || entity instanceof PandaEntity
                || entity instanceof GoatEntity
                || entity instanceof CamelEntity
                || entity instanceof SnifferEntity
                || entity instanceof BeeEntity
                || entity instanceof TurtleEntity
                || entity instanceof AxolotlEntity
                || entity instanceof FrogEntity
                || entity instanceof StriderEntity
                || entity instanceof HoglinEntity
                || entity instanceof MooshroomEntity
                || entity instanceof ArmadilloEntity
                || isNautilus(entity);
    }

    // Nautilus was added in 1.21.11. Match it by registry id instead of the entity
    // class so the mod still loads on 1.21.9 / 1.21.10, where that class does not exist.
    private static boolean isNautilus(Entity entity) {
        return Registries.ENTITY_TYPE.getId(entity.getType())
                .getPath().equals("nautilus");
    }
}
