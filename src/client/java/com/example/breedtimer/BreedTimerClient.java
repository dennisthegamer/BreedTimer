package com.example.breedtimer;

import com.example.breedtimer.config.BreedTimerConfig;
import com.example.breedtimer.render.BreedTimerHud;
import com.example.breedtimer.render.TurtleEggRenderer;
import com.example.breedtimer.util.BreedCooldownHelper;
import com.example.breedtimer.util.BreedCooldownHelper.AnimalState;
import com.example.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import com.example.breedtimer.util.VillagerCooldownHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BreedTimerClient implements ClientModInitializer {

    private static KeyBinding toggleEnabledKey;
    private static KeyBinding toggleCompactKey;

    private final Set<UUID> previouslyReady   = new HashSet<>();
    private final Set<UUID> previouslyWilling = new HashSet<>();

    @Override
    public void onInitializeClient() {
        BreedTimerConfig.HANDLER.load();

        KeyBinding.Category keybindCategory = KeyBinding.Category.create(
                Identifier.of("breedtimer", "breedtimer")
        );

        toggleEnabledKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.breedtimer.toggleEnabled",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                keybindCategory
        ));

        toggleCompactKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.breedtimer.toggleCompact",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                keybindCategory
        ));

        HudElementRegistry.addLast(
                Identifier.of("breedtimer", "compact_hud"),
                BreedTimerHud::render
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        WorldRenderEvents.AFTER_ENTITIES.register(TurtleEggRenderer::render);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String worldId = getWorldId(handler);
            BreedCooldownHelper.onWorldJoin(worldId);
            VillagerCooldownHelper.onWorldJoin(worldId);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BreedCooldownHelper.onWorldLeave();
            VillagerCooldownHelper.onWorldLeave();
        });
    }

    private String getWorldId(ClientPlayNetworkHandler handler) {
        var serverInfo = handler.getServerInfo();
        if (serverInfo != null) return sanitize(serverInfo.address);
        // Singleplayer: use "local" as a stable fallback
        return "local";
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void onClientTick(MinecraftClient mc) {
        if (toggleEnabledKey.wasPressed()) {
            BreedTimerConfig config = BreedTimerConfig.get();
            config.enabled = !config.enabled;
            BreedTimerConfig.HANDLER.save();
        }

        if (toggleCompactKey.wasPressed()) {
            BreedTimerConfig config = BreedTimerConfig.get();
            config.compactMode = !config.compactMode;
            BreedTimerConfig.HANDLER.save();
        }

        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled) return;

        PlayerEntity player = mc.player;
        World world = mc.world;
        if (player == null || world == null) return;

        if (config.showAnimals)   BreedCooldownHelper.tick(world, player, mc.isPaused());
        if (config.showAnimals)   TurtleEggRenderer.tick(player, world, config);
        if (config.showVillagers) VillagerCooldownHelper.tick(world, player, mc.isPaused());

        if (!config.playSound) return;

        if (config.showAnimals) {
            List<AnimalTimerInfo> animals = BreedCooldownHelper.getVisibleAnimals(player, world);
            Set<UUID> currentlyReady = new HashSet<>();
            for (AnimalTimerInfo info : animals) {
                UUID uuid = info.animal().getUuid();
                if (info.state() == AnimalState.READY) {
                    currentlyReady.add(uuid);
                    if (!previouslyReady.contains(uuid)) {
                        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.5f, 1.0f);
                    }
                }
            }
            previouslyReady.clear();
            previouslyReady.addAll(currentlyReady);
        }

        if (config.showVillagers) {
            List<VillagerCooldownHelper.VillagerTimerInfo> villagers = VillagerCooldownHelper.getVisibleVillagers(player, world);
            Set<UUID> currentlyVillagerReady = new HashSet<>();
            for (VillagerCooldownHelper.VillagerTimerInfo info : villagers) {
                UUID uuid = info.villager().getUuid();
                if (info.state() == VillagerCooldownHelper.VillagerState.READY) {
                    currentlyVillagerReady.add(uuid);
                    if (!previouslyWilling.contains(uuid)) {
                        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.5f, 1.0f);
                    }
                }
            }
            previouslyWilling.clear();
            previouslyWilling.addAll(currentlyVillagerReady);
        }
    }
}
