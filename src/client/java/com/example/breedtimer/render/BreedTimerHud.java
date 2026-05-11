package com.example.breedtimer.render;

import com.example.breedtimer.config.BreedTimerConfig;
import com.example.breedtimer.util.BreedCooldownHelper;
import com.example.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import com.example.breedtimer.util.VillagerCooldownHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
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

        AABB scanBox = player.getBoundingBox().inflate(config.scanRadius);

        // ── Animal counts ────────────────────────────────────────────────────
        int ready = 0, cooldown = 0, babies = 0, inLove = 0;
        if (config.showAnimals) {
            List<Animal> animals = level.getEntitiesOfClass(Animal.class, scanBox,
                    BreedCooldownHelper::isSupportedAnimal);
            for (Animal animal : animals) {
                if (BreedCooldownHelper.isOutOfFieldOfView(player, animal)) continue;
                AnimalTimerInfo info = BreedCooldownHelper.createTimerInfo(animal);
                switch (info.state()) {
                    case READY    -> ready++;
                    case IN_LOVE  -> inLove++;
                    case COOLDOWN -> cooldown++;
                    case BABY     -> babies++;
                }
            }
        }

        // ── Villager counts ──────────────────────────────────────────────────
        int vReady = 0, vCooldown = 0, vBabies = 0;
        List<Villager> villagers = List.of();
        if (config.showVillagers) {
            villagers = level.getEntitiesOfClass(Villager.class, scanBox);
            for (Villager villager : villagers) {
                if (BreedCooldownHelper.isOutOfFieldOfView(player, villager)) continue;
                VillagerCooldownHelper.VillagerTimerInfo vInfo = VillagerCooldownHelper.createTimerInfo(villager);
                switch (vInfo.state()) {
                    case READY    -> vReady++;
                    case COOLDOWN -> vCooldown++;
                    case BABY     -> vBabies++;
                }
            }
        }

        // ── Render ───────────────────────────────────────────────────────────
        int x = 5;
        int currentY = 5;
        int bgColor = ((int) (config.backgroundOpacity * 255) << 24);

        if (config.showAnimals) {
            MutableComponent animalText = Component.empty()
                    .append(Component.literal("Animals  ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(ready + " ready ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(cooldown + " cooldown ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(babies + " babies ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(inLove + " in love").withStyle(ChatFormatting.LIGHT_PURPLE));
            graphics.fill(x - 2, currentY - 2, x + mc.font.width(animalText) + 2, currentY + mc.font.lineHeight + 2, bgColor);
            graphics.text(mc.font, animalText, x, currentY, 0xFFFFFFFF, true);
            currentY += mc.font.lineHeight + 4;

            int[] eggCounts = TurtleEggRenderer.getEggCounts();
            int totalEggs = eggCounts[0] + eggCounts[1] + eggCounts[2];
            if (totalEggs > 0) {
                MutableComponent eggText = Component.empty()
                        .append(Component.literal("Turtle Eggs  ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(eggCounts[0] + " fresh ").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(eggCounts[1] + " cracking ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(eggCounts[2] + " hatching").withStyle(ChatFormatting.GREEN));
                graphics.fill(x - 2, currentY - 2, x + mc.font.width(eggText) + 2, currentY + mc.font.lineHeight + 2, bgColor);
                graphics.text(mc.font, eggText, x, currentY, 0xFFFFFFFF, true);
                currentY += mc.font.lineHeight + 4;
            }
        }

        if (config.showVillagers && !villagers.isEmpty()) {
            MutableComponent villagerText = Component.empty()
                    .append(Component.literal("Villagers  ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(vReady + " ready ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(vCooldown + " cooldown ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(vBabies + " babies").withStyle(ChatFormatting.AQUA));
            graphics.fill(x - 2, currentY - 2, x + mc.font.width(villagerText) + 2, currentY + mc.font.lineHeight + 2, bgColor);
            graphics.text(mc.font, villagerText, x, currentY, 0xFFFFFFFF, true);
        }
    }
}
