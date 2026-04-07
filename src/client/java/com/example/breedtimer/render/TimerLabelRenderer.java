package com.example.breedtimer.render;

import com.example.breedtimer.config.BreedTimerConfig;
import com.example.breedtimer.util.BreedCooldownHelper;
import com.example.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Animal;
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

        Component text = getDisplayComponent(info);

        int textAlpha = (int) (fade * 255.0f);
        int textColor = (textAlpha << 24) | info.color();

        int bgAlpha = (int) (config.backgroundOpacity * fade * 255.0f);
        int backgroundColor = bgAlpha << 24;

        Vec3 attachment = state.nameTagAttachment != null
                ? state.nameTagAttachment.add(0, 0.3, 0)
                : new Vec3(0, animal.getBbHeight() + 0.5, 0);

        int yOffset = state.nameTag != null ? 1 : 0;

        poseStack.pushPose();
        poseStack.translate(attachment.x, attachment.y + 0.5, attachment.z);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(0.025F, -0.025F, 0.025F);

        float x = -mc.font.width(text) / 2.0F;

        collector.submitText(
                poseStack,
                x,
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
}
