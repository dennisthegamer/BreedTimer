package de.dennisthegamer.breedtimer.render;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import de.dennisthegamer.breedtimer.util.Visibility;
import de.dennisthegamer.hudlib.position.HudPlacement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class BreedTimerHud {

    /**
     * Box-Maße einer Zeilenliste; von {@link #render}, {@link #measureBox} und {@link #draw}
     * geteilt, damit Vermessung und Zeichnung nie auseinanderdriften (dieselbe Invariante wie bei
     * FishingStats: computeLayout() und draw() müssen dieselben Strings sehen).
     */
    private record Layout(List<MutableComponent> lines, int width, int height) {}

    // ── Textbau (unverändert gegenüber dem alten Renderer) ──────────────────

    /**
     * Each segment is appended only if its state filter is on. This is the HUD-line half of the
     * filter: {@link de.dennisthegamer.breedtimer.util.BreedCooldownHelper#isShown} already keeps a
     * filtered-out count at zero, but without this a switched-off segment would still print "0
     * blocked" forever -- a permanent, meaningless number the filter was supposed to remove.
     */
    private static MutableComponent animalText(BreedTimerConfig config, int ready, int cooldown, int babies,
                                                int inLove, int blocked) {
        StatePalette p = StatePalette.current();
        MutableComponent line = Component.empty()
                .append(Component.translatable("breedtimer.hud.animals").withColor(p.neutral));
        if (config.showReady)
            line.append(Component.translatable("breedtimer.hud.ready", ready).withColor(p.ready));
        if (config.showCooldown)
            line.append(Component.translatable("breedtimer.hud.cooldown", cooldown).withColor(p.coolFar));
        if (config.showBabyTimer)
            line.append(Component.translatable("breedtimer.hud.babies", babies).withColor(p.young));
        if (config.showInLove)
            line.append(Component.translatable("breedtimer.hud.in_love", inLove).withColor(p.love));
        if (config.showBlocked)
            line.append(Component.translatable("breedtimer.hud.blocked", blocked).withColor(p.inert));
        return line;
    }

    /** As {@link #animalText}: an allay has exactly the two states {@code showReady}/{@code showCooldown} cover. */
    private static MutableComponent allayText(BreedTimerConfig config, int ready, int cooldown) {
        StatePalette p = StatePalette.current();
        MutableComponent line = Component.empty()
                .append(Component.translatable("breedtimer.hud.allays").withColor(p.neutral));
        if (config.showReady)
            line.append(Component.translatable("breedtimer.hud.ready", ready).withColor(p.ready));
        if (config.showCooldown)
            line.append(Component.translatable("breedtimer.hud.cooldown", cooldown).withColor(p.coolFar));
        return line;
    }

    private static MutableComponent eggText(int[] eggCounts) {
        StatePalette p = StatePalette.current();
        return Component.empty()
                .append(Component.translatable("breedtimer.hud.turtle_eggs").withColor(p.neutral))
                .append(Component.translatable("breedtimer.hud.fresh", eggCounts[0]).withColor(p.young))
                .append(Component.translatable("breedtimer.hud.cracking", eggCounts[1]).withColor(p.coolMid))
                .append(Component.translatable("breedtimer.hud.hatching", eggCounts[2]).withColor(p.ready));
    }

    private static MutableComponent snifferEggText(int[] eggCounts) {
        StatePalette p = StatePalette.current();
        return Component.empty()
                .append(Component.translatable("breedtimer.hud.sniffer_eggs").withColor(p.neutral))
                .append(Component.translatable("breedtimer.hud.fresh", eggCounts[0]).withColor(p.young))
                .append(Component.translatable("breedtimer.hud.cracking", eggCounts[1]).withColor(p.coolMid))
                .append(Component.translatable("breedtimer.hud.hatching", eggCounts[2]).withColor(p.ready));
    }

    private static MutableComponent beehiveText(int[] counts) {
        StatePalette p = StatePalette.current();
        return Component.empty()
                .append(Component.translatable("breedtimer.hud.beehives").withColor(p.neutral))
                .append(Component.translatable("breedtimer.hud.hive_filling", counts[0]).withColor(p.coolMid))
                .append(Component.translatable("breedtimer.hud.hive_full", counts[1]).withColor(p.ready));
    }

    /** One line for both crops, not two: they are grown by the same player for the same reason. */
    private static MutableComponent cropText(int[] counts) {
        StatePalette p = StatePalette.current();
        return Component.empty()
                .append(Component.translatable("breedtimer.hud.crops").withColor(p.neutral))
                .append(Component.translatable("breedtimer.hud.crop_growing", counts[0]).withColor(p.coolMid))
                .append(Component.translatable("breedtimer.hud.crop_ready", counts[1]).withColor(p.ready));
    }

    private static MutableComponent villagerText(BreedTimerConfig config, int vReady, int vCooldown, int vBabies,
                                                  int vBlocked) {
        StatePalette p = StatePalette.current();
        MutableComponent line = Component.empty()
                .append(Component.translatable("breedtimer.hud.villagers").withColor(p.neutral));
        if (config.showReady)
            line.append(Component.translatable("breedtimer.hud.ready", vReady).withColor(p.ready));
        if (config.showCooldown)
            line.append(Component.translatable("breedtimer.hud.cooldown", vCooldown).withColor(p.coolFar));
        if (config.showBabyTimer)
            line.append(Component.translatable("breedtimer.hud.babies", vBabies).withColor(p.young));
        // A villager's only synced BLOCKED cause is sleeping, hence showBlocked rather than a
        // dedicated flag -- see VillagerCooldownHelper.isShown.
        if (config.showBlocked)
            line.append(Component.translatable("breedtimer.hud.asleep", vBlocked).withColor(p.inert));
        return line;
    }

    /** The soonest cooldown in the counted population. One line, because a farm has one next thing to do. */
    private static MutableComponent nextReadyText(int ticks) {
        StatePalette p = StatePalette.current();
        return Component.empty()
                .append(Component.translatable("breedtimer.hud.next_ready").withColor(p.neutral))
                .append(Component.literal(BreedCooldownHelper.formatTime(ticks)).withColor(p.coolNear));
    }

    /** Baut die sichtbaren Zeilen aus den aktuellen Zählwerten (Live-Zustand). */
    private static List<MutableComponent> buildLines(BreedTimerConfig config, int ready, int cooldown, int babies,
                                                       int inLove, int blocked, int[] eggCounts, int[] snifferCounts,
                                                       int[] hiveCounts, int[] cropCounts, int allayReady, int allayCooldown, int vReady, int vCooldown,
                                                       int vBabies, int vBlocked, int nextReady) {
        List<MutableComponent> lines = new ArrayList<>();
        if (config.showAnimals) {
            // Was unconditional, so the HUD read "Animals 0 ready 0 cooldown ..." permanently in
            // the Nether, in caves, everywhere.
            if (ready + cooldown + babies + inLove + blocked > 0) {
                lines.add(animalText(config, ready, cooldown, babies, inLove, blocked));
            }
            if (allayReady + allayCooldown > 0) {
                lines.add(allayText(config, allayReady, allayCooldown));
            }
            int totalEggs = eggCounts[0] + eggCounts[1] + eggCounts[2];
            if (totalEggs > 0) {
                lines.add(eggText(eggCounts));
            }
            int totalSniffer = snifferCounts[0] + snifferCounts[1] + snifferCounts[2];
            if (totalSniffer > 0) {
                lines.add(snifferEggText(snifferCounts));
            }
            int totalHives = hiveCounts[0] + hiveCounts[1];
            if (totalHives > 0) {
                lines.add(beehiveText(hiveCounts));
            }
            int totalCrops = cropCounts[0] + cropCounts[1];
            if (totalCrops > 0) {
                lines.add(cropText(cropCounts));
            }
        }
        // hasVillagers used to come from the unfiltered query, so this line could appear with every
        // counter at zero; the sum below is derived from the same filtered counts it displays.
        if (config.showVillagers && (vReady + vCooldown + vBabies + vBlocked) > 0) {
            lines.add(villagerText(config, vReady, vCooldown, vBabies, vBlocked));
        }
        // Last, not folded under the Animals line: it summarises every counted cooldown -- animal, allay and
        // villager alike -- so it belongs under all of them rather than inside one of their groups.
        if (nextReady < Integer.MAX_VALUE) {
            lines.add(nextReadyText(nextReady));
        }
        return lines;
    }

    /**
     * The editor preview deliberately ignores the state filter: the box has to reserve the widest
     * line the HUD can ever draw, and a filtered preview would let a saved placement drift the next
     * time the filter changes. A default-constructed config is all-true by construction -- the same
     * device {@code BreedTimerConfigScreen} already uses for its YACL defaults.
     */
    private static final BreedTimerConfig PREVIEW_CONFIG = new BreedTimerConfig();

    /**
     * Fixed sample state so the editor box is stable (the live HUD is often empty): one Animals,
     * one Allays and one Villagers line with typical counts, so the measured box reserves space
     * for every line that can actually turn up at runtime.
     */
    private static List<MutableComponent> sampleLines() {
        return List.of(
                animalText(PREVIEW_CONFIG, 3, 2, 1, 1, 1),
                allayText(PREVIEW_CONFIG, 1, 1),
                villagerText(PREVIEW_CONFIG, 2, 1, 1, 1),
                nextReadyText(2400)
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

    /**
     * Draws {@code layout} at the unscaled coordinates (x,y) under a {@code scale} transform.
     * {@code GuiGraphics.pose()} returns a {@code PoseStack} on this branch (it becomes
     * {@code Matrix3x2fStack} only from 1.21.6), so this pushes/pops a full pose rather than a 2D
     * matrix stack.
     */
    private static void drawScaled(GuiGraphics graphics, int x, int y, Layout layout,
                                   Font font, int bgColor, float scale) {
        if (scale == 1.0f) { draw(graphics, x, y, layout, font, bgColor); return; }
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        draw(graphics, x, y, layout, font, bgColor);
        graphics.pose().popPose();
    }

    public static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled || !config.compactMode) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        AABB scanBox = player.getBoundingBox().inflate(config.effectiveScanRadius());

        boolean inViewOnly = config.hudInViewOnly;
        // Ambient by default: the compact HUD exists to replace labels when there are too many of
        // them, so it reports the state of the farm, not of the view cone. Egg counts were already
        // ambient, so this also removes two different semantics living in one panel. Stable counts
        // are also what makes a later "next ready" line meaningful — a schedule that changes when
        // you look away is not a schedule.
        // One Visibility for the whole call: eye position, look vector and fovCos do not change
        // between the animal, villager, tadpole and allay loops below, so they are worked out once
        // here instead of once per entity per loop.
        Visibility visibility = new Visibility(player, config);
        Predicate<Entity> visible = entity -> {
            if (!inViewOnly) return true;
            if (!visibility.inView(entity)) return false;
            return visibility.fade(entity) > 0.0f;
        };

        // Ticks until the soonest cooldown in the counted population expires. Deliberately taken from the same
        // loops the counters come from rather than from cooldownMap: that map also holds unloaded animals, whose
        // timers are paused on purpose, and animals outside the scan radius -- neither is what the panel is
        // talking about.
        int nextReady = Integer.MAX_VALUE;

        // ── Animal counts ────────────────────────────────────────────────────
        int ready = 0, cooldown = 0, babies = 0, inLove = 0, blocked = 0;
        if (config.showAnimals) {
            List<Animal> animals = level.getEntitiesOfClass(Animal.class, scanBox,
                    BreedCooldownHelper::isSupportedAnimal);
            for (Animal animal : animals) {
                if (!visible.test(animal)) continue;
                AnimalTimerInfo info = BreedCooldownHelper.createTimerInfo(animal);
                if (!BreedCooldownHelper.isShown(info.state())) continue;
                switch (info.state()) {
                    case READY      -> ready++;
                    case IN_LOVE    -> inLove++;
                    case COOLDOWN   -> { cooldown++; nextReady = Math.min(nextReady, info.remainingTicks()); }
                    case BABY, NEVER_GROWS -> babies++;
                    case BLOCKED  -> blocked++;
                }
            }
        }

        // ── Villager counts ──────────────────────────────────────────────────
        int vReady = 0, vCooldown = 0, vBabies = 0, vBlocked = 0;
        if (config.showVillagers) {
            for (Villager villager : level.getEntitiesOfClass(Villager.class, scanBox, v -> true)) {
                if (!visible.test(villager)) continue;
                VillagerCooldownHelper.VillagerTimerInfo vInfo = VillagerCooldownHelper.createTimerInfo(villager);
                if (!VillagerCooldownHelper.isShown(vInfo.state())) continue;
                switch (vInfo.state()) {
                    case READY    -> vReady++;
                    case COOLDOWN -> { vCooldown++; nextReady = Math.min(nextReady, vInfo.remainingTicks()); }
                    case BABY     -> vBabies++;
                    case BLOCKED  -> vBlocked++;
                }
            }
        }

        // Tadpoles are not Animals, so the loop above never sees them; they still belong in the
        // babies count, which is the only place compact mode would show them at all — gated on
        // showBabyTimer like every other baby count, which this used to ignore.
        if (config.showAnimals) {
            for (Tadpole tadpole : level.getEntitiesOfClass(Tadpole.class, scanBox)) {
                if (!visible.test(tadpole)) continue;
                if (config.showBabyTimer) babies++;
            }
        }

        // Allays were labelled but never counted; they get their own line rather than folding into
        // the animal counts, since they are not an Animal and duplicate instead of breeding.
        int allayReady = 0, allayCooldown = 0;
        if (config.showAnimals) {
            for (Allay allay : level.getEntitiesOfClass(Allay.class, scanBox)) {
                if (!visible.test(allay)) continue;
                BreedCooldownHelper.AllayTimerInfo info = BreedCooldownHelper.createAllayInfo(allay);
                if (info.ready()) {
                    if (config.showReady) allayReady++;
                } else if (config.showCooldown) {
                    allayCooldown++;
                    // remainingTicks() is -1 for an allay we never watched duplicate: the flag says "not yet"
                    // without saying for how long, and -1 would win every min().
                    if (info.remainingTicks() > 0) nextReady = Math.min(nextReady, info.remainingTicks());
                }
            }
        }

        // showBlocks is the master switch for all six block-kind labels; see BreedTimerConfig.
        // No dried-ghast counts here: that block does not exist on this branch's MC range.
        boolean showBlocks = config.showAnimals && config.showBlocks;
        int[] eggCounts = showBlocks ? BlockLabelScanner.getEggCounts() : new int[] {0, 0, 0};
        int[] snifferCounts = showBlocks ? BlockLabelScanner.getSnifferEggCounts() : new int[] {0, 0, 0};
        int[] hiveCounts = showBlocks ? BlockLabelScanner.getBeehiveCounts() : new int[] {0, 0};
        int[] cropCounts = showBlocks ? BlockLabelScanner.getCropCounts() : new int[] {0, 0};

        // ── Vermessen dann zeichnen ──────────────────────────────────────────
        List<MutableComponent> lines = buildLines(config, ready, cooldown, babies, inLove, blocked, eggCounts, snifferCounts,
                hiveCounts, cropCounts, allayReady, allayCooldown, vReady, vCooldown, vBabies, vBlocked, nextReady);
        if (lines.isEmpty()) return;
        Font font = mc.font;
        Layout layout = computeLayout(lines, font);

        HudPlacement placement = config.hudPlacement; // nach der Migration nie null
        // Resolved in the same scaled space HudEditorScreen used (V3): guiWidth()/guiHeight() divided
        // by scale, not raw, or a saved placement and the live HUD disagree about where the box is.
        float scale = Math.max(0.5f, config.hudScale);
        int x = placement.resolveX((int) (graphics.guiWidth()  / scale), layout.width());
        int y = placement.resolveY((int) (graphics.guiHeight() / scale), layout.height());
        int bgColor = ((int) (config.hudOpacity * 255) << 24);
        drawScaled(graphics, x, y, layout, font, bgColor, scale);
    }

    // --- Editor-Vorschau -----------------------------------------------------

    /** Box-Maße {width,height} der Beispiel-Box (scale-unabhängig, Font-basiert). */
    public static int[] measureBox() {
        Font font = Minecraft.getInstance().font;
        Layout layout = computeLayout(sampleLines(), font);
        return new int[] { layout.width(), layout.height() };
    }

    /** Zeichnet die Beispiel-Box im Editor an (x,y), unter demselben {@code scale}-Transform wie das Live-HUD. */
    public static void drawPreview(GuiGraphics graphics, int x, int y, float scale) {
        BreedTimerConfig config = BreedTimerConfig.get();
        Font font = Minecraft.getInstance().font;
        Layout layout = computeLayout(sampleLines(), font);
        int bgColor = ((int) (config.hudOpacity * 255) << 24);
        drawScaled(graphics, x, y, layout, font, bgColor, scale);
    }
}
