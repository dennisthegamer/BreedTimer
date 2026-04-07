package com.example.breedtimer;

import com.example.breedtimer.config.BreedTimerConfig;
import com.example.breedtimer.render.BreedTimerHud;
import com.example.breedtimer.util.BreedCooldownHelper;
import com.example.breedtimer.util.BreedCooldownHelper.AnimalState;
import com.example.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BreedTimerClient implements ClientModInitializer {

    private static final KeyMapping.Category KEYBIND_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("breedtimer", "breedtimer")
    );
    private static KeyMapping toggleEnabledKey;
    private static KeyMapping toggleCompactKey;
    private final Set<UUID> previouslyReady = new HashSet<>();

    @Override
    public void onInitializeClient() {
        BreedTimerConfig.HANDLER.load();

        toggleEnabledKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.breedtimer.toggleEnabled",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KEYBIND_CATEGORY
        ));

        toggleCompactKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.breedtimer.toggleCompact",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KEYBIND_CATEGORY
        ));

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("breedtimer", "compact_hud"),
                BreedTimerHud::extractRenderState
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String worldId = getWorldId(handler);
            BreedCooldownHelper.onWorldJoin(worldId);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BreedCooldownHelper.onWorldLeave());
    }

    private String getWorldId(ClientPacketListener handler) {
        var serverData = handler.getServerData();
        if (serverData != null) {
            return sanitize(serverData.ip);
        }
        // Singleplayer — use level name
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return sanitize(mc.getSingleplayerServer().getWorldData().getLevelSettings().levelName());
        }
        return "unknown";
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void onClientTick(Minecraft mc) {
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

        if (!BreedTimerConfig.get().enabled) return;

        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        // Update client-side cooldown tracking every tick (skip when paused)
        BreedCooldownHelper.tick(level, player, mc.isPaused());

        if (!BreedTimerConfig.get().playSound) return;

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
}
