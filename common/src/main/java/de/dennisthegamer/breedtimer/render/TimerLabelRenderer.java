package de.dennisthegamer.breedtimer.render;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper.VillagerTimerInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

public class TimerLabelRenderer {

    public static void renderLabel(Animal animal, PoseStack matrices,
                                   MultiBufferSource bufferSource, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (config.compactMode) return;
        if (BreedCooldownHelper.isOutOfFieldOfView(player, animal)) return;

        float fade = BreedCooldownHelper.getDistanceFade(player, animal);
        if (fade <= 0.0f) return;

        AnimalTimerInfo info = BreedCooldownHelper.createTimerInfo(animal);
        if (info.state() == BreedCooldownHelper.AnimalState.BABY && !config.showBabyTimer) return;

        renderText(animal.getBbHeight(), matrices, bufferSource, camera, mc,
                getDisplayComponent(info), fade, info.color(), config.backgroundOpacity);
    }

    public static void renderVillagerLabel(Villager villager, PoseStack matrices,
                                           MultiBufferSource bufferSource, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (config.compactMode) return;
        if (BreedCooldownHelper.isOutOfFieldOfView(player, villager)) return;

        float fade = BreedCooldownHelper.getDistanceFade(player, villager);
        if (fade <= 0.0f) return;

        VillagerTimerInfo info = VillagerCooldownHelper.createTimerInfo(villager);
        if (info.state() == VillagerCooldownHelper.VillagerState.BABY && !config.showBabyTimer) return;

        renderText(villager.getBbHeight(), matrices, bufferSource, camera, mc,
                getVillagerDisplayComponent(info), fade, info.color(), config.backgroundOpacity);
    }

    private static void renderText(float bbHeight, PoseStack matrices,
                                   MultiBufferSource bufferSource, Camera camera,
                                   Minecraft mc, Component text, float fade, int color, float bgOpacity) {
        int textAlpha = (int) (fade * 255.0f);
        int textColor = (textAlpha << 24) | color;
        int bgAlpha = (int) (bgOpacity * fade * 255.0f);
        int backgroundColor = bgAlpha << 24;

        matrices.pushPose();
        // Position above the entity — offset higher if it has a vanilla name tag
        matrices.translate(0.0, bbHeight + 1.0, 0.0);
        matrices.mulPose(camera.rotation());
        matrices.scale(0.025F, -0.025F, 0.025F);

        mc.font.drawInBatch(
                text.getVisualOrderText(),
                -mc.font.width(text) / 2.0F,
                0.0F,
                textColor,
                false,
                matrices.last().pose(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                backgroundColor,
                LightTexture.FULL_BRIGHT
        );

        matrices.popPose();
    }

    private static Component getDisplayComponent(AnimalTimerInfo info) {
        return switch (info.state()) {
            case READY -> Component.translatable("breedtimer.state.ready");
            case COOLDOWN -> Component.literal(BreedCooldownHelper.formatTime(info.remainingTicks()));
            case IN_LOVE -> Component.translatable("breedtimer.state.in_love");
            case BABY -> Component.translatable("breedtimer.state.growing",
                    BreedCooldownHelper.formatTime(info.remainingTicks()));
        };
    }

    private static Component getVillagerDisplayComponent(VillagerTimerInfo info) {
        return switch (info.state()) {
            case READY    -> Component.translatable("breedtimer.state.ready");
            case COOLDOWN -> Component.literal(BreedCooldownHelper.formatTime(info.remainingTicks()));
            case BABY     -> Component.translatable("breedtimer.state.growing",
                    BreedCooldownHelper.formatTime(info.remainingTicks()));
        };
    }
}
