package com.example.breedtimer.fabric;

import com.example.breedtimer.BreedTimerClient;
import com.example.breedtimer.render.BreedTimerHud;
import com.example.breedtimer.render.TurtleEggRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.resources.Identifier;

public final class BreedTimerFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BreedTimerClient.init();

        KeyMappingHelper.registerKeyMapping(BreedTimerClient.toggleEnabledKey);
        KeyMappingHelper.registerKeyMapping(BreedTimerClient.toggleCompactKey);

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(BreedTimerClient.MOD_ID, "compact_hud"),
                BreedTimerHud::extractRenderState
        );

        ClientTickEvents.END_CLIENT_TICK.register(BreedTimerClient::onClientTick);

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> TurtleEggRenderer.render(
                context.poseStack(), context.submitNodeCollector(), context.levelState().cameraRenderState));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                BreedTimerClient.onWorldJoin(handler));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                BreedTimerClient.onWorldLeave());
    }
}
