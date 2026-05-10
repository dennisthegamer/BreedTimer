package com.example.breedtimer.render;

import com.example.breedtimer.config.BreedTimerConfig;
import com.example.breedtimer.util.BreedCooldownHelper;
import com.example.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import com.example.breedtimer.util.VillagerCooldownHelper;
import com.example.breedtimer.util.VillagerCooldownHelper.VillagerTimerInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class TimerLabelRenderer {

    public static void renderLabel(EntityRenderState state, Animal animal, PoseStack poseStack,
                                   SubmitNodeCollector collector, CameraRenderState camera) {
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

        renderText(state, animal.getBbHeight(), poseStack, collector, camera, mc,
                getDisplayComponent(info), fade, info.color(), config.backgroundOpacity);
    }

    public static void renderVillagerLabel(EntityRenderState state, Villager villager, PoseStack poseStack,
                                           SubmitNodeCollector collector, CameraRenderState camera) {
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

        renderText(state, villager.getBbHeight(), poseStack, collector, camera, mc,
                getVillagerDisplayComponent(info), fade, info.color(), config.backgroundOpacity);
    }

    private static void renderText(EntityRenderState state, float bbHeight, PoseStack poseStack,
                                   SubmitNodeCollector collector, CameraRenderState camera,
                                   Minecraft mc, Component text, float fade, int color, float bgOpacity) {
        int textAlpha = (int) (fade * 255.0f);
        int textColor = (textAlpha << 24) | color;
        int bgAlpha = (int) (bgOpacity * fade * 255.0f);
        int backgroundColor = bgAlpha << 24;

        Vec3 attachment = state.nameTagAttachment != null
                ? state.nameTagAttachment.add(0, 0.3, 0)
                : new Vec3(0, bbHeight + 0.5, 0);
        int yOffset = state.nameTag != null ? 1 : 0;

        poseStack.pushPose();
        poseStack.translate(attachment.x, attachment.y + 0.5, attachment.z);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(0.025F, -0.025F, 0.025F);

        collector.submitText(
                poseStack,
                -mc.font.width(text) / 2.0F,
                (float) yOffset,
                text.getVisualOrderText(),
                false,
                Font.DisplayMode.NORMAL,
                state.lightCoords,
                textColor,
                backgroundColor,
                0
        );

        poseStack.popPose();
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
