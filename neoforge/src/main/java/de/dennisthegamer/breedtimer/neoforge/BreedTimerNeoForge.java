package de.dennisthegamer.breedtimer.neoforge;

import de.dennisthegamer.breedtimer.BreedTimerClient;
import de.dennisthegamer.breedtimer.config.BreedTimerConfigScreen;
import de.dennisthegamer.breedtimer.render.BreedTimerHud;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = BreedTimerClient.MOD_ID, dist = Dist.CLIENT)
public final class BreedTimerNeoForge {

    public BreedTimerNeoForge(ModContainer container, IEventBus modBus) {
        BreedTimerClient.init();

        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(BreedTimerClient.toggleEnabledKey);
            event.register(BreedTimerClient.toggleCompactKey);
        });

        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(BreedTimerClient.MOD_ID, "compact_hud"),
                BreedTimerHud::render));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event ->
                BreedTimerClient.onClientTick(Minecraft.getInstance()));

        // Turtle egg labels are rendered via the shared LevelRendererSubmitMixin
        // (no common level-render event exists across both loaders).

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event ->
                BreedTimerClient.onWorldJoin(event.getPlayer().connection));

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event ->
                BreedTimerClient.onWorldLeave());

        if (ModList.get().isLoaded("yet_another_config_lib_v3")) {
            container.registerExtensionPoint(IConfigScreenFactory.class,
                    (c, parent) -> BreedTimerConfigScreen.createConfigScreen(parent));
        }
    }
}
