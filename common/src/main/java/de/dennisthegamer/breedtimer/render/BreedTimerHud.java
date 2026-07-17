package de.dennisthegamer.breedtimer.render;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import de.dennisthegamer.hudlib.position.HudPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class BreedTimerHud {

    /**
     * Box-Maße einer Zeilenliste; von {@link #render}, {@link #measureBox} und {@link #draw}
     * geteilt, damit Vermessung und Zeichnung nie auseinanderdriften (dieselbe Invariante wie bei
     * FishingStats: computeLayout() und draw() müssen dieselben Strings sehen).
     */
    private record Layout(List<MutableComponent> lines, int width, int height) {}

    // ── Textbau (unverändert gegenüber dem alten Renderer) ──────────────────

    private static MutableComponent animalText(int ready, int cooldown, int babies, int inLove) {
        return Component.empty()
                .append(Component.literal("Animals  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(ready + " ready ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(cooldown + " cooldown ").withStyle(ChatFormatting.RED))
                .append(Component.literal(babies + " babies ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(inLove + " in love").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static MutableComponent eggText(int[] eggCounts) {
        return Component.empty()
                .append(Component.literal("Turtle Eggs  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(eggCounts[0] + " fresh ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(eggCounts[1] + " cracking ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(eggCounts[2] + " hatching").withStyle(ChatFormatting.GREEN));
    }

    private static MutableComponent villagerText(int vReady, int vCooldown, int vBabies) {
        return Component.empty()
                .append(Component.literal("Villagers  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(vReady + " ready ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(vCooldown + " cooldown ").withStyle(ChatFormatting.RED))
                .append(Component.literal(vBabies + " babies").withStyle(ChatFormatting.AQUA));
    }

    /** Baut die sichtbaren Zeilen aus den aktuellen Zählwerten (Live-Zustand). */
    private static List<MutableComponent> buildLines(BreedTimerConfig config, int ready, int cooldown, int babies,
                                                       int inLove, int[] eggCounts, int vReady, int vCooldown,
                                                       int vBabies, boolean hasVillagers) {
        List<MutableComponent> lines = new ArrayList<>();
        if (config.showAnimals) {
            lines.add(animalText(ready, cooldown, babies, inLove));
            int totalEggs = eggCounts[0] + eggCounts[1] + eggCounts[2];
            if (totalEggs > 0) {
                lines.add(eggText(eggCounts));
            }
        }
        if (config.showVillagers && hasVillagers) {
            lines.add(villagerText(vReady, vCooldown, vBabies));
        }
        return lines;
    }

    /**
     * Fester Beispiel-Zustand, damit die Editor-Box stabil ist (Live-HUD ist oft leer):
     * eine Animals-Zeile + eine Villagers-Zeile mit typischen Zählwerten.
     */
    private static List<MutableComponent> sampleLines() {
        return List.of(
                animalText(3, 2, 1, 1),
                villagerText(2, 1, 1)
        );
    }

    // ── Vermessen + Zeichnen (von render() UND drawPreview()/measureBox() geteilt) ──────────

    /** Bounding-Box der Zeilenliste: max. Zeilenbreite × Summe der Zeilenhöhen (+2/+4-Padding). */
    private static Layout computeLayout(List<MutableComponent> lines, Font font) {
        int width = 0;
        for (MutableComponent line : lines) {
            width = Math.max(width, font.width(line) + 4);
        }
        int height = lines.size() * (font.lineHeight + 4);
        return new Layout(lines, width, height);
    }

    /**
     * Zeichnet die Zeilen so, dass die gemalte Bounding-Box exakt bei (x,y) beginnt — (x,y) ist die
     * Box-Ecke oben-links (wie von computeLayout()/measureBox() vermessen und vom HudEditorScreen-
     * Rahmen erwartet), nicht der Text-Ursprung. Je Zeile eigener Hintergrund, wie im alten Renderer.
     */
    private static void draw(GuiGraphics graphics, int x, int y, Layout layout, Font font, int bgColor) {
        int textX = x + 2;   // 2px-Inset, das die Zeilen-Fills unten voraussetzen
        int textY = y + 2;
        int currentY = textY;
        for (MutableComponent line : layout.lines()) {
            graphics.fill(textX - 2, currentY - 2, textX + font.width(line) + 2, currentY + font.lineHeight + 2, bgColor);
            graphics.drawString(font, line, textX, currentY, 0xFFFFFFFF, true);
            currentY += font.lineHeight + 4;
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
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
            villagers = level.getEntitiesOfClass(Villager.class, scanBox, v -> true);
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

        int[] eggCounts = config.showAnimals ? TurtleEggRenderer.getEggCounts() : new int[] {0, 0, 0};

        // ── Vermessen dann zeichnen ──────────────────────────────────────────
        List<MutableComponent> lines = buildLines(config, ready, cooldown, babies, inLove, eggCounts,
                vReady, vCooldown, vBabies, !villagers.isEmpty());
        Font font = mc.font;
        Layout layout = computeLayout(lines, font);

        HudPlacement placement = config.hudPlacement; // nach der Migration nie null
        int x = placement.resolveX(graphics.guiWidth(), layout.width());
        int currentY = placement.resolveY(graphics.guiHeight(), layout.height());
        int bgColor = ((int) (config.backgroundOpacity * 255) << 24);

        draw(graphics, x, currentY, layout, font, bgColor);
    }

    // --- Editor-Vorschau -----------------------------------------------------

    /** Box-Maße {width,height} der Beispiel-Box (scale-unabhängig, Font-basiert). */
    public static int[] measureBox() {
        Font font = Minecraft.getInstance().font;
        Layout layout = computeLayout(sampleLines(), font);
        return new int[] { layout.width(), layout.height() };
    }

    /** Zeichnet die Beispiel-Box im Editor an (x,y). BreedTimer skaliert nicht, {@code scale} bleibt ungenutzt. */
    public static void drawPreview(GuiGraphics graphics, int x, int y, float scale) {
        BreedTimerConfig config = BreedTimerConfig.get();
        Font font = Minecraft.getInstance().font;
        Layout layout = computeLayout(sampleLines(), font);
        int bgColor = ((int) (config.backgroundOpacity * 255) << 24);
        draw(graphics, x, y, layout, font, bgColor);
    }
}
