package com.example.breedtimer.render;

import com.example.breedtimer.config.BreedTimerConfig;
import com.example.breedtimer.util.BreedCooldownHelper;
import com.example.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class BreedTimerHud {

    public static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled || !config.compactMode) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        int radius = config.scanRadius;
        AABB scanBox = player.getBoundingBox().inflate(radius);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, scanBox,
                BreedCooldownHelper::isSupportedAnimal);

        int ready = 0;
        int cooldown = 0;
        int babies = 0;
        int inLove = 0;

        for (Animal animal : animals) {
            if (BreedCooldownHelper.isOutOfFieldOfView(player, animal)) continue;
            AnimalTimerInfo info = BreedCooldownHelper.createTimerInfo(animal);
            switch (info.state()) {
                case READY -> ready++;
                case IN_LOVE -> inLove++;
                case COOLDOWN -> cooldown++;
                case BABY -> babies++;
            }
        }

        MutableComponent text = Component.empty()
                .append(Component.literal(ready + " ready ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(cooldown + " cooldown ").withStyle(ChatFormatting.RED))
                .append(Component.literal(babies + " babies ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(inLove + " in love").withStyle(ChatFormatting.LIGHT_PURPLE));

        int x = 5;
        int y = 5;
        int bgColor = ((int) (config.backgroundOpacity * 255) << 24);

        graphics.fill(x - 2, y - 2, x + mc.font.width(text) + 2, y + mc.font.lineHeight + 2, bgColor);
        graphics.text(mc.font, text, x, y, 0xFFFFFFFF, true);
    }
}
