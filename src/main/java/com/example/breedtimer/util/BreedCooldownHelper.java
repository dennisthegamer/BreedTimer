package com.example.breedtimer.util;

import com.example.breedtimer.config.BreedTimerConfig;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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

    public static final int BREED_COOLDOWN_TICKS = 6000; // 5 minutes
    public static final int LOVE_MODE_TICKS = 600; // 30 seconds
    public static final int BABY_GROW_TICKS = 24000; // 20 minutes

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_DIR = FabricLoader.getInstance().getConfigDir().resolve("breedtimer");

    // Client-side tracking using UUID (persistent across sessions)
    private static final Map<UUID, Integer> cooldownMap = new HashMap<>();
    private static final Map<UUID, Integer> loveMap = new HashMap<>();
    private static final Map<UUID, Integer> babyTimerMap = new HashMap<>();
    private static String currentWorldId = null;

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
    }

    private static Path getSaveFile() {
        return SAVE_DIR.resolve(currentWorldId + ".json");
    }

    private static void saveData() {
        try {
            Files.createDirectories(SAVE_DIR);
            // Convert UUID keys to strings for JSON
            Map<String, Map<String, Integer>> data = new HashMap<>();
            data.put("cooldown", uuidMapToString(cooldownMap));
            data.put("baby", uuidMapToString(babyTimerMap));
            // Don't save love mode — it's too short-lived to persist
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
            Level level = animal.level();
            AABB searchBox = animal.getBoundingBox().inflate(8);
            List<Animal> nearby = level.getEntitiesOfClass(Animal.class, searchBox,
                    a -> a != animal && a.getClass() == animal.getClass());
            for (Animal mate : nearby) {
                UUID mateUuid = mate.getUUID();
                if (loveMap.containsKey(mateUuid)) {
                    loveMap.remove(mateUuid);
                    cooldownMap.put(mateUuid, BREED_COOLDOWN_TICKS);
                    break; // only one mate
                }
            }
        } else if (!cooldownMap.containsKey(uuid)) {
            // Not in love, not on cooldown → entering love mode
            loveMap.put(uuid, LOVE_MODE_TICKS);
        }
    }

    /**
     * Called every client tick to update timers.
     * @param paused true if the game is paused (singleplayer ESC menu)
     */
    public static void tick(Level level, Player player, boolean paused) {
        if (paused) return;

        // Tick down love timers
        loveMap.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - 1);
            return entry.getValue() <= 0;
        });

        // Tick down cooldowns
        cooldownMap.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - 1);
            return entry.getValue() <= 0;
        });

        // Track baby growth
        BreedTimerConfig config = BreedTimerConfig.get();
        int radius = config.scanRadius;
        AABB scanBox = player.getBoundingBox().inflate(radius);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, scanBox);

        for (Animal animal : animals) {
            if (!isSupportedAnimal(animal)) continue;
            UUID uuid = animal.getUUID();

            if (animal.isBaby()) {
                if (!babyTimerMap.containsKey(uuid)) {
                    babyTimerMap.put(uuid, BABY_GROW_TICKS);
                } else {
                    int remaining = babyTimerMap.get(uuid) - 1;
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

    public static List<AnimalTimerInfo> getVisibleAnimals(Player player, Level level) {
        BreedTimerConfig config = BreedTimerConfig.get();
        int radius = config.scanRadius;

        AABB scanBox = player.getBoundingBox().inflate(radius);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, scanBox);
        List<AnimalTimerInfo> result = new ArrayList<>();

        Vec3 eyePos = player.getEyePosition(1.0f);

        for (Animal animal : animals) {
            if (!isSupportedAnimal(animal)) continue;
            if (isOutOfFieldOfView(player, animal)) continue;

            Vec3 animalPos = animal.position().add(0, animal.getBbHeight() / 2.0, 0);
            double dist = eyePos.distanceTo(animalPos);
            if (dist > config.fadeEndDistance) continue;

            if (!hasLineOfSight(level, eyePos, animalPos, player)) continue;

            result.add(createTimerInfo(animal));
        }

        return result;
    }

    private static boolean hasLineOfSight(Level level, Vec3 from, Vec3 to, Entity entity) {
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        HitResult hit = level.clip(ctx);
        return hit.getType() == HitResult.Type.MISS ||
                hit.getLocation().distanceToSqr(to) < 1.0;
    }

    public static AnimalTimerInfo createTimerInfo(Animal animal) {
        UUID uuid = animal.getUUID();

        // Check love mode
        if (loveMap.containsKey(uuid)) {
            return new AnimalTimerInfo(animal, AnimalState.IN_LOVE, loveMap.get(uuid), 0xFF55FF);
        }

        // Check baby
        if (animal.isBaby()) {
            int remaining = babyTimerMap.getOrDefault(uuid, BABY_GROW_TICKS);
            return new AnimalTimerInfo(animal, AnimalState.BABY, remaining, 0x55FFFF);
        }

        // Check our tracked cooldown
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

    public static boolean isOutOfFieldOfView(Player player, Animal animal) {
        BreedTimerConfig config = BreedTimerConfig.get();
        double fovCos = Math.cos(Math.toRadians(config.fovAngle / 2.0));

        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookDir = player.getLookAngle().normalize();
        Vec3 animalPos = animal.position().add(0, animal.getBbHeight() / 2.0, 0);
        Vec3 toAnimal = animalPos.subtract(eyePos).normalize();

        return lookDir.dot(toAnimal) < fovCos;
    }

    public static float getDistanceFade(Player player, Animal animal) {
        BreedTimerConfig config = BreedTimerConfig.get();
        double dist = player.position().distanceTo(animal.position());

        if (dist <= config.fadeStartDistance) return 1.0f;
        if (dist >= config.fadeEndDistance) return 0.0f;

        return 1.0f - (float) ((dist - config.fadeStartDistance) /
                (config.fadeEndDistance - config.fadeStartDistance));
    }

    public static boolean isSupportedAnimal(Entity entity) {
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
                || entity instanceof Frog;
    }
}
