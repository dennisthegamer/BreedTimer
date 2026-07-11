package de.dennisthegamer.breedtimer.neoforge;

import de.dennisthegamer.breedtimer.BreedTimerClient;
import de.dennisthegamer.breedtimer.config.BreedTimerConfigScreen;
import de.dennisthegamer.breedtimer.render.BreedTimerHud;
import de.dennisthegamer.breedtimer.render.TurtleEggRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
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
                BreedTimerHud::extractRenderState));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event ->
                BreedTimerClient.onClientTick(Minecraft.getInstance()));

        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, event -> TurtleEggRenderer.render(
                event.getPoseStack(), event.getSubmitNodeCollector(), event.getLevelRenderState().cameraRenderState));

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event ->
                BreedTimerClient.onWorldJoin(event.getPlayer().connection));

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event ->
                BreedTimerClient.onWorldLeave());

        container.registerExtensionPoint(IConfigScreenFactory.class,
                (c, parent) -> BreedTimerConfigScreen.createConfigScreen(parent));
    }
}
