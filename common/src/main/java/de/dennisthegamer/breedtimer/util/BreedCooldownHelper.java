package de.dennisthegamer.breedtimer.util;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BreedCooldownHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("BreedTimer");

    public static final int BREED_COOLDOWN_TICKS = 6000;
    public static final int LOVE_MODE_TICKS = 600;
    public static final int BABY_GROW_TICKS = 24000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_DIR = Platforms.get().getConfigDir().resolve("breedtimer");

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

    public record AnimalTimerInfo(Animal animal, AnimalState state, int remainingTicks, int color) {}

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

    public static void onLoveEvent(Animal animal) {
        UUID uuid = animal.getUUID();

        if (loveMap.containsKey(uuid)) {
            loveMap.remove(uuid);
            cooldownMap.put(uuid, BREED_COOLDOWN_TICKS);

            Level world = animal.level();
            AABB searchBox = animal.getBoundingBox().inflate(8);
            List<Animal> nearby = world.getEntitiesOfClass(Animal.class, searchBox,
                    a -> a != animal && a.getClass() == animal.getClass());
            for (Animal mate : nearby) {
                UUID mateUuid = mate.getUUID();
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

    /**
     * Called every client tick to update timers.
     * Timers only count down for entities that are currently loaded — unloaded
     * entities don't tick server-side, so their real cooldowns pause too.
     * @param paused true if the game is paused (singleplayer ESC menu)
     * @param loadedEntities all entities currently loaded on the client
     */
    public static void tick(Level world, boolean paused, List<Entity> loadedEntities) {
        if (paused) return;

        long currentGameTime = world.getGameTime();
        int delta = lastGameTime < 0 ? 1 : (int) Math.min(currentGameTime - lastGameTime, 6000);
        lastGameTime = currentGameTime;

        Set<UUID> loadedUuids = new HashSet<>();
        for (Entity entity : loadedEntities) {
            loadedUuids.add(entity.getUUID());
        }

        // Tick down love timers (paused while the entity is unloaded)
        loveMap.entrySet().removeIf(entry -> {
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

        // Track baby growth for all loaded animals
        for (Entity entity : loadedEntities) {
            if (!(entity instanceof Animal animal) || !isSupportedAnimal(animal)) continue;
            UUID uuid = animal.getUUID();

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

    public static List<AnimalTimerInfo> getVisibleAnimals(Player player, Level world) {
        BreedTimerConfig config = BreedTimerConfig.get();
        int radius = config.scanRadius;

        AABB scanBox = player.getBoundingBox().inflate(radius);
        List<Animal> animals = world.getEntitiesOfClass(Animal.class, scanBox, a -> true);
        List<AnimalTimerInfo> result = new ArrayList<>();

        Vec3 eyePos = player.getEyePosition();

        for (Animal animal : animals) {
            if (!isSupportedAnimal(animal)) continue;
            if (isOutOfFieldOfView(player, animal)) continue;

            Vec3 animalPos = new Vec3(animal.getX(), animal.getY() + animal.getBbHeight() / 2.0, animal.getZ());
            double dist = eyePos.distanceTo(animalPos);
            if (dist > config.fadeEndDistance) continue;

            if (!hasLineOfSight(world, eyePos, animalPos, player)) continue;

            result.add(createTimerInfo(animal));
        }

        return result;
    }

    private static boolean hasLineOfSight(Level world, Vec3 from, Vec3 to, Entity entity) {
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        HitResult hit = world.clip(ctx);
        return hit.getType() == HitResult.Type.MISS ||
                hit.getLocation().distanceToSqr(to) < 1.0;
    }

    public static AnimalTimerInfo createTimerInfo(Animal animal) {
        UUID uuid = animal.getUUID();

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

    public static boolean isOutOfFieldOfView(Player player, Entity entity) {
        BreedTimerConfig config = BreedTimerConfig.get();
        double fovCos = Math.cos(Math.toRadians(config.fovAngle / 2.0));

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle().normalize();
        Vec3 entityPos = new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ());
        Vec3 toEntity = entityPos.subtract(eyePos).normalize();

        return lookDir.dot(toEntity) < fovCos;
    }

    public static float getDistanceFade(Player player, Entity entity) {
        BreedTimerConfig config = BreedTimerConfig.get();
        Vec3 playerPos = new Vec3(player.getX(), player.getY(), player.getZ());
        Vec3 entityPos = new Vec3(entity.getX(), entity.getY(), entity.getZ());
        double dist = playerPos.distanceTo(entityPos);

        if (dist <= config.fadeStartDistance) return 1.0f;
        if (dist >= config.fadeEndDistance) return 0.0f;

        return 1.0f - (float) ((dist - config.fadeStartDistance) /
                (config.fadeEndDistance - config.fadeStartDistance));
    }

    public static boolean isSupportedAnimal(Entity entity) {
        // Cow got a fresh intermediary class name when AbstractCow was introduced in
        // 1.21.5, so `instanceof Cow` NoClassDefFoundErrors on 1.21.2-1.21.4. Match by the
        // stable EntityType instead (Mooshrooms are still caught by MushroomCow below).
        return entity.getType() == EntityType.COW
                || entity.getType() == EntityType.SHEEP
                || entity instanceof Pig
                || entity instanceof Chicken
                || entity instanceof Rabbit
                || entity instanceof Horse
                || entity instanceof Donkey
                || entity instanceof Mule
                || entity instanceof Llama
                || entity instanceof Fox
                || entity.getType() == EntityType.WOLF
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
                || isNautilus(entity);
    }

    // Nautilus was added in 1.21.11. Match it by registry id instead of the entity
    // class so the mod still loads on 1.21.9 / 1.21.10, where that class does not exist.
    private static boolean isNautilus(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                .getPath().equals("nautilus");
    }
}
