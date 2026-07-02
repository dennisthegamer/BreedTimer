package de.dennisthegamer.breedtimer.render;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper.VillagerTimerInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class TimerLabelRenderer {

    public static void renderLabel(EntityRenderState state, AnimalEntity animal, MatrixStack matrices,
                                   OrderedRenderCommandQueue queue, CameraRenderState camera) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (config.compactMode) return;
        if (BreedCooldownHelper.isOutOfFieldOfView(player, animal)) return;

        float fade = BreedCooldownHelper.getDistanceFade(player, animal);
        if (fade <= 0.0f) return;

        AnimalTimerInfo info = BreedCooldownHelper.createTimerInfo(animal);
        if (info.state() == BreedCooldownHelper.AnimalState.BABY && !config.showBabyTimer) return;

        renderText(state, animal.getHeight(), matrices, queue, camera, mc,
                getDisplayComponent(info), fade, info.color(), config.backgroundOpacity);
    }

    public static void renderVillagerLabel(EntityRenderState state, VillagerEntity villager, MatrixStack matrices,
                                           OrderedRenderCommandQueue queue, CameraRenderState camera) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (config.compactMode) return;
        if (BreedCooldownHelper.isOutOfFieldOfView(player, villager)) return;

        float fade = BreedCooldownHelper.getDistanceFade(player, villager);
        if (fade <= 0.0f) return;

        VillagerTimerInfo info = VillagerCooldownHelper.createTimerInfo(villager);
        if (info.state() == VillagerCooldownHelper.VillagerState.BABY && !config.showBabyTimer) return;

        renderText(state, villager.getHeight(), matrices, queue, camera, mc,
                getVillagerDisplayComponent(info), fade, info.color(), config.backgroundOpacity);
    }

    private static void renderText(EntityRenderState state, float bbHeight, MatrixStack matrices,
                                   OrderedRenderCommandQueue queue, CameraRenderState camera,
                                   MinecraftClient mc, Text text, float fade, int color, float bgOpacity) {
        int textAlpha = (int) (fade * 255.0f);
        int textColor = (textAlpha << 24) | color;
        int bgAlpha = (int) (bgOpacity * fade * 255.0f);
        int backgroundColor = bgAlpha << 24;

        // Position above the entity — offset higher if it has a vanilla name tag
        Vec3d attachment = new Vec3d(0, bbHeight + 0.5, 0);

        matrices.push();
        matrices.translate(attachment.x, attachment.y + 0.5, attachment.z);
        matrices.multiply(camera.orientation);
        matrices.scale(0.025F, -0.025F, 0.025F);

        queue.submitText(
                matrices,
                -mc.textRenderer.getWidth(text) / 2.0F,
                0.0F,
                text.asOrderedText(),
                false,
                TextRenderer.TextLayerType.NORMAL,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                textColor,
                backgroundColor,
                0
        );

        matrices.pop();
    }

    private static Text getDisplayComponent(AnimalTimerInfo info) {
        return switch (info.state()) {
            case READY -> Text.translatable("breedtimer.state.ready");
            case COOLDOWN -> Text.literal(BreedCooldownHelper.formatTime(info.remainingTicks()));
            case IN_LOVE -> Text.translatable("breedtimer.state.in_love");
            case BABY -> Text.translatable("breedtimer.state.growing",
                    BreedCooldownHelper.formatTime(info.remainingTicks()));
        };
    }

    private static Text getVillagerDisplayComponent(VillagerTimerInfo info) {
        return switch (info.state()) {
            case READY    -> Text.translatable("breedtimer.state.ready");
            case COOLDOWN -> Text.literal(BreedCooldownHelper.formatTime(info.remainingTicks()));
            case BABY     -> Text.translatable("breedtimer.state.growing",
                    BreedCooldownHelper.formatTime(info.remainingTicks()));
        };
    }
}
