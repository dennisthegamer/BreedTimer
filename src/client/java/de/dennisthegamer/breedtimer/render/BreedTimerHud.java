package de.dennisthegamer.breedtimer.render;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

public class BreedTimerHud {

    public static void render(DrawContext graphics, RenderTickCounter tickCounter) {
        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled || !config.compactMode) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        World world = mc.world;
        if (player == null || world == null) return;

        Box scanBox = player.getBoundingBox().expand(config.scanRadius);

        // ── Animal counts ────────────────────────────────────────────────────
        int ready = 0, cooldown = 0, babies = 0, inLove = 0;
        if (config.showAnimals) {
            List<AnimalEntity> animals = world.getEntitiesByClass(AnimalEntity.class, scanBox,
                    BreedCooldownHelper::isSupportedAnimal);
            for (AnimalEntity animal : animals) {
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
        List<VillagerEntity> villagers = List.of();
        if (config.showVillagers) {
            villagers = world.getEntitiesByClass(VillagerEntity.class, scanBox, v -> true);
            for (VillagerEntity villager : villagers) {
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
            MutableText animalText = Text.empty()
                    .append(Text.literal("Animals  ").formatted(Formatting.WHITE))
                    .append(Text.literal(ready + " ready ").formatted(Formatting.GREEN))
                    .append(Text.literal(cooldown + " cooldown ").formatted(Formatting.RED))
                    .append(Text.literal(babies + " babies ").formatted(Formatting.AQUA))
                    .append(Text.literal(inLove + " in love").formatted(Formatting.LIGHT_PURPLE));
            graphics.fill(x - 2, currentY - 2, x + mc.textRenderer.getWidth(animalText) + 2, currentY + mc.textRenderer.fontHeight + 2, bgColor);
            graphics.drawText(mc.textRenderer, animalText, x, currentY, 0xFFFFFFFF, true);
            currentY += mc.textRenderer.fontHeight + 4;

            int[] eggCounts = TurtleEggRenderer.getEggCounts();
            int totalEggs = eggCounts[0] + eggCounts[1] + eggCounts[2];
            if (totalEggs > 0) {
                MutableText eggText = Text.empty()
                        .append(Text.literal("Turtle Eggs  ").formatted(Formatting.WHITE))
                        .append(Text.literal(eggCounts[0] + " fresh ").formatted(Formatting.AQUA))
                        .append(Text.literal(eggCounts[1] + " cracking ").formatted(Formatting.GOLD))
                        .append(Text.literal(eggCounts[2] + " hatching").formatted(Formatting.GREEN));
                graphics.fill(x - 2, currentY - 2, x + mc.textRenderer.getWidth(eggText) + 2, currentY + mc.textRenderer.fontHeight + 2, bgColor);
                graphics.drawText(mc.textRenderer, eggText, x, currentY, 0xFFFFFFFF, true);
                currentY += mc.textRenderer.fontHeight + 4;
            }
        }

        if (config.showVillagers && !villagers.isEmpty()) {
            MutableText villagerText = Text.empty()
                    .append(Text.literal("Villagers  ").formatted(Formatting.WHITE))
                    .append(Text.literal(vReady + " ready ").formatted(Formatting.GREEN))
                    .append(Text.literal(vCooldown + " cooldown ").formatted(Formatting.RED))
                    .append(Text.literal(vBabies + " babies").formatted(Formatting.AQUA));
            graphics.fill(x - 2, currentY - 2, x + mc.textRenderer.getWidth(villagerText) + 2, currentY + mc.textRenderer.fontHeight + 2, bgColor);
            graphics.drawText(mc.textRenderer, villagerText, x, currentY, 0xFFFFFFFF, true);
        }
    }
}
