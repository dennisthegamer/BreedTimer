package com.example.breedtimer;

import com.example.breedtimer.config.BreedTimerConfig;
import com.example.breedtimer.render.TurtleEggRenderer;
import com.example.breedtimer.util.BreedCooldownHelper;
import com.example.breedtimer.util.BreedCooldownHelper.AnimalState;
import com.example.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import com.example.breedtimer.util.VillagerCooldownHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared client logic; loader-specific entrypoints (fabric/neoforge) call
 * {@link #init()} and wire these methods to their loader's events.
 */
public final class BreedTimerClient {

    public static final String MOD_ID = "breedtimer";

    private static final KeyMapping.Category KEYBIND_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "breedtimer")
    );
    public static KeyMapping toggleEnabledKey;
    public static KeyMapping toggleCompactKey;

    private static final Set<UUID> previouslyReady   = new HashSet<>();
    private static final Set<UUID> previouslyWilling = new HashSet<>();

    private BreedTimerClient() {}

    public static void init() {
        BreedTimerConfig.HANDLER.load();

        toggleEnabledKey = new KeyMapping(
                "key.breedtimer.toggleEnabled",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KEYBIND_CATEGORY
        );

        toggleCompactKey = new KeyMapping(
                "key.breedtimer.toggleCompact",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KEYBIND_CATEGORY
        );
    }

    public static void onWorldJoin(ClientPacketListener handler) {
        String worldId = getWorldId(handler);
        BreedCooldownHelper.onWorldJoin(worldId);
        VillagerCooldownHelper.onWorldJoin(worldId);
    }

    public static void onWorldLeave() {
        BreedCooldownHelper.onWorldLeave();
        VillagerCooldownHelper.onWorldLeave();
    }

    private static String getWorldId(ClientPacketListener handler) {
        var serverData = handler.getServerData();
        if (serverData != null) return sanitize(serverData.ip);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null)
            return sanitize(mc.getSingleplayerServer().getWorldData().getLevelSettings().levelName());
        return "unknown";
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static void onClientTick(Minecraft mc) {
        if (toggleEnabledKey.consumeClick()) {
            BreedTimerConfig config = BreedTimerConfig.get();
            config.enabled = !config.enabled;
            BreedTimerConfig.HANDLER.save();
        }

        if (toggleCompactKey.consumeClick()) {
            BreedTimerConfig config = BreedTimerConfig.get();
            config.compactMode = !config.compactMode;
            BreedTimerConfig.HANDLER.save();
        }

        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled) return;

        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        // Collect all client-loaded entities so the helpers can pause timers
        // for entities that are currently unloaded
        List<Entity> loadedEntities = new ArrayList<>();
        if (level instanceof ClientLevel clientLevel) {
            for (Entity entity : clientLevel.entitiesForRendering()) {
                loadedEntities.add(entity);
            }
        }

        if (config.showAnimals)   BreedCooldownHelper.tick(level, mc.isPaused(), loadedEntities);
        if (config.showAnimals)   TurtleEggRenderer.tick(player, level, config);
        if (config.showVillagers) VillagerCooldownHelper.tick(level, mc.isPaused(), loadedEntities);

        if (!config.playSound) return;

        if (config.showAnimals) {
            // Animal ready sound
            List<AnimalTimerInfo> animals = BreedCooldownHelper.getVisibleAnimals(player, level);
            Set<UUID> currentlyReady = new HashSet<>();
            for (AnimalTimerInfo info : animals) {
                UUID uuid = info.animal().getUUID();
                if (info.state() == AnimalState.READY) {
                    currentlyReady.add(uuid);
                    if (!previouslyReady.contains(uuid)) {
                        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.0f);
                    }
                }
            }
            previouslyReady.clear();
            previouslyReady.addAll(currentlyReady);
        }

        if (config.showVillagers) {
            // Villager ready sound (plays when villager comes back from cooldown)
            List<VillagerCooldownHelper.VillagerTimerInfo> villagers = VillagerCooldownHelper.getVisibleVillagers(player, level);
            Set<UUID> currentlyVillagerReady = new HashSet<>();
            for (VillagerCooldownHelper.VillagerTimerInfo info : villagers) {
                UUID uuid = info.villager().getUUID();
                if (info.state() == VillagerCooldownHelper.VillagerState.READY) {
                    currentlyVillagerReady.add(uuid);
                    if (!previouslyWilling.contains(uuid)) {
                        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.0f);
                    }
                }
            }
            previouslyWilling.clear();
            previouslyWilling.addAll(currentlyVillagerReady);
        }
    }
}
