package de.dennisthegamer.breedtimer.fabric;

import de.dennisthegamer.breedtimer.BreedTimerClient;
import de.dennisthegamer.breedtimer.render.BreedTimerHud;
import de.dennisthegamer.breedtimer.render.BlockLabelScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class BreedTimerFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BreedTimerClient.init();

        KeyBindingHelper.registerKeyBinding(BreedTimerClient.toggleEnabledKey);
        KeyBindingHelper.registerKeyBinding(BreedTimerClient.toggleCompactKey);

        HudRenderCallback.EVENT.register(BreedTimerHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(BreedTimerClient::onClientTick);

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.matrixStack() == null || context.consumers() == null) return;
            BlockLabelScanner.render(context.matrixStack(), context.consumers(), context.camera());
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                BreedTimerClient.onWorldJoin(handler));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                BreedTimerClient.onWorldLeave());
    }
}
