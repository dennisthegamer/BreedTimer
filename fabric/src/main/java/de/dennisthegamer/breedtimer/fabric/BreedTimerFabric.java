package de.dennisthegamer.breedtimer.fabric;

import de.dennisthegamer.breedtimer.BreedTimerClient;
import de.dennisthegamer.breedtimer.render.BreedTimerHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public final class BreedTimerFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BreedTimerClient.init();

        KeyBindingHelper.registerKeyBinding(BreedTimerClient.toggleEnabledKey);
        KeyBindingHelper.registerKeyBinding(BreedTimerClient.toggleCompactKey);

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(BreedTimerClient.MOD_ID, "compact_hud"),
                BreedTimerHud::render
        );

        ClientTickEvents.END_CLIENT_TICK.register(BreedTimerClient::onClientTick);

        // Turtle egg labels are rendered via the shared LevelRendererSubmitMixin
        // (no common level-render event exists across both loaders).

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                BreedTimerClient.onWorldJoin(handler));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                BreedTimerClient.onWorldLeave());
    }
}
