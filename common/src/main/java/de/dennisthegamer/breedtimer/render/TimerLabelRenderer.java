package de.dennisthegamer.breedtimer.render;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.util.AgeableTracking;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import de.dennisthegamer.breedtimer.util.BreedingFoodHelper;
import de.dennisthegamer.breedtimer.util.DropWindows;
import de.dennisthegamer.breedtimer.util.LookTarget;
import de.dennisthegamer.breedtimer.util.PandaGenes;
import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper.VillagerTimerInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public class TimerLabelRenderer {

    /**
     * Alpha of the see-through pass, matching the 0x20FFFFFF vanilla uses for name tags. It only
     * has to keep the label legible where geometry hides it; the opaque pass carries the reading.
     * Shared with {@link BlockLabelScanner}, which draws its labels the same way.
     */
    static final int SEE_THROUGH_ALPHA = 0x20;

    /** {@code breedtimer.panda.gene.<name>}, in {@link PandaGenes} gene-id order. */
    private static final String[] PANDA_GENE_KEYS =
            {"normal", "lazy", "worried", "playful", "brown", "weak", "aggressive"};

    /**
     * Below this the entry is noise, not information: the whole line is suppressed for an ordinary
     * pen precisely because every non-normal outcome sits under this floor for it.
     */
    private static final double CUB_ODDS_MIN_SHOWN = 0.01;
    /** "Cub: %s" names at most the two likeliest outcomes; seven lines under a label is not a label. */
    private static final int CUB_ODDS_MAX_ENTRIES = 2;

    public static void renderLabel(EntityRenderState state, Animal animal, PoseStack matrices,
                                   SubmitNodeCollector queue, CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (config.compactMode) return;
        if (BreedCooldownHelper.isOutOfFieldOfView(player, animal)) return;
        if (!LookTarget.isLabelled(animal)) return;

        float fade = BreedCooldownHelper.getDistanceFade(player, animal);
        if (fade <= 0.0f) return;

        AnimalTimerInfo info = BreedCooldownHelper.createTimerInfo(animal);
        if (!BreedCooldownHelper.isShown(info.state())) return;

        // Nothing worth suggesting to an animal that can never breed. A parrot does take seeds,
        // but only for taming, and "Feed: Wheat Seeds" under "Cannot breed" reads as a
        // contradiction.
        //
        // Asked by species rather than only by the BLOCKED state, which covers adults alone. A
        // polar bear cub is BABY, so it used to fall through to the probe below — and a polar
        // bear's isFood is a hard false, so that probe offered all 1177 registered items to it,
        // found nothing, declined to cache the empty answer, and did the whole thing again on the
        // next frame. It produced no hint either way; now it produces none without the search.
        boolean sterile = BreedCooldownHelper.neverBreeds(animal)
                || (info.state() == BreedCooldownHelper.AnimalState.BLOCKED
                    && info.blockReason() == BreedCooldownHelper.BlockReason.STERILE);
        List<Component> foodHint = config.showFoodHint && !sterile
                ? BreedingFoodHelper.hintFor(animal) : null;
        // A cub is a separate topic from what feeds this panda, so it gets its own line rather than
        // reusing the food hint's slot -- both are only ever one line long today, but nothing here
        // assumes that stays true.
        List<Component> cubHint = animal instanceof Panda panda ? cubOddsHint(panda, info) : null;
        // Supplementary, like the food hint: the breeding timer stays the line you read first. Only
        // ever shown for a mob we have heard drop once -- until then there is nothing to say, and
        // saying "5:00-10:00" for an unobserved chicken would be inventing an observation. Opt-in and
        // off by default, unlike the other sub-lines here: this is the mod's only inferred display.
        List<Component> dropHint = config.showDropWindows ? dropWindowLine(animal) : null;
        renderText(state, animal.getBbHeight(), matrices, queue, camera, mc,
                getDisplayComponent(info), mergeSubLines(mergeSubLines(dropHint, cubHint), foodHint),
                fade, info.color(), config);
    }

    /** Concatenates two optional sub-line lists, in order, tolerating either (or both) being {@code null}. */
    private static List<Component> mergeSubLines(List<Component> first, List<Component> second) {
        if (first == null) return second;
        if (second == null) return first;
        List<Component> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }

    /**
     * "Egg in 5:00-10:00" or "Scute in 5:00-10:00" -- the mod's only <em>inferred</em> display,
     * hedged accordingly. {@code DropWindows} records ticks since the sound the game plays on a
     * successful drop; the true remaining time is a range around that, not a single number, because
     * both {@code Chicken.eggTime} and {@code Armadillo.scuteTime} reset to a random {@code 6000 +
     * random.nextInt(6000)} rather than a fixed constant.
     *
     * <p>{@code null} until we have heard one drop from this exact mob ({@link
     * DropWindows#sinceDrop} returns -1), and {@code null} again once the window has run empty
     * ({@code high <= 0}) -- past that point a completion was missed (out of earshot, silenced, or
     * a data pack suppressing the loot table) and there is nothing left worth showing. Never clamped
     * to a stuck {@code 0:00-0:00}: that would read as an exact prediction, which this deliberately
     * is not.
     */
    private static List<Component> dropWindowLine(Animal animal) {
        int since = DropWindows.sinceDrop(animal.getUUID());
        if (since < 0) return null;
        int low = Math.max(0, DropWindows.MIN_TICKS - since);
        int high = DropWindows.MAX_TICKS - since;
        if (high <= 0) return null;
        String key = animal instanceof Chicken ? "breedtimer.drop.egg" : "breedtimer.drop.scute";
        return List.of(Component.translatable(key,
                BreedCooldownHelper.formatTime(low), BreedCooldownHelper.formatTime(high)));
    }

    /**
     * "Cub: 75% normal, 24% brown" -- the top {@link #CUB_ODDS_MAX_ENTRIES} likeliest personalities
     * this panda's next cub could have, given the nearest adult partner {@link BreedCooldownHelper
     * #tick} found for it this game tick. The odds themselves come from {@link PandaGenes}, which
     * keeps the full 7x7 joint of main and hidden gene rather than multiplying marginals -- see its
     * javadoc for why a hand-rolled table gets this wrong by tens of percentage points.
     *
     * <p>{@code null} in three cases: this panda cannot currently produce a cub (baby, never-grows
     * or blocked -- {@link BreedCooldownHelper.AnimalState#READY}, {@code COOLDOWN} and
     * {@code IN_LOVE} are the only states a real pairing exists for), no adult partner is in range,
     * or every non-normal outcome is under {@link #CUB_ODDS_MIN_SHOWN} -- an ordinary pen of two
     * normal parents mutates at well under 1% per gene, and a line that always shows seven
     * near-zero percentages would be noise dressed up as information.
     */
    private static List<Component> cubOddsHint(Panda panda, AnimalTimerInfo info) {
        if (info.state() != BreedCooldownHelper.AnimalState.READY
                && info.state() != BreedCooldownHelper.AnimalState.COOLDOWN
                && info.state() != BreedCooldownHelper.AnimalState.IN_LOVE) {
            return null;
        }
        int[] partner = BreedCooldownHelper.pandaPartnerGenesFor(panda.getUUID());
        if (partner == null) return null;

        double[] odds = PandaGenes.cubOdds(panda.getMainGene().getId(), panda.getHiddenGene().getId(),
                partner[0], partner[1]);

        // The top two gene ids by odds, found by hand rather than by boxing into a sortable list --
        // there are only seven of them, and this runs once per visible panda per frame.
        int best = -1;
        int second = -1;
        for (int gene = 0; gene < PandaGenes.GENES; gene++) {
            if (best == -1 || odds[gene] > odds[best]) {
                second = best;
                best = gene;
            } else if (second == -1 || odds[gene] > odds[second]) {
                second = gene;
            }
        }

        boolean anyMutationLikely = false;
        for (int gene = 0; gene < PandaGenes.GENES; gene++) {
            if (gene != PandaGenes.NORMAL && odds[gene] >= CUB_ODDS_MIN_SHOWN) {
                anyMutationLikely = true;
                break;
            }
        }
        if (!anyMutationLikely) return null;

        MutableComponent joined = Component.empty();
        int shown = 0;
        int[] top = {best, second};
        for (int i = 0; i < CUB_ODDS_MAX_ENTRIES; i++) {
            int gene = top[i];
            if (gene < 0 || odds[gene] < CUB_ODDS_MIN_SHOWN) continue;
            if (shown > 0) joined.append(Component.translatable("breedtimer.food_hint.separator"));
            joined.append(Component.translatable("breedtimer.panda.cub_entry",
                    Math.round(odds[gene] * 100.0),
                    Component.translatable("breedtimer.panda.gene." + PANDA_GENE_KEYS[gene])));
            shown++;
        }
        return List.of(Component.translatable("breedtimer.panda.cub_odds", joined));
    }

    public static void renderVillagerLabel(EntityRenderState state, Villager villager, PoseStack matrices,
                                           SubmitNodeCollector queue, CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (config.compactMode) return;
        if (BreedCooldownHelper.isOutOfFieldOfView(player, villager)) return;
        if (!LookTarget.isLabelled(villager)) return;

        float fade = BreedCooldownHelper.getDistanceFade(player, villager);
        if (fade <= 0.0f) return;

        VillagerTimerInfo info = VillagerCooldownHelper.createTimerInfo(villager);
        if (!VillagerCooldownHelper.isShown(info.state())) return;

        // Not for babies. Unlike an animal, a villager cannot be fed to grow up faster — its food
        // only counts towards an adult's willingness to breed — so the line would be misleading
        // exactly where it looks most helpful.
        List<Component> foodHint = config.showFoodHint
                && info.state() != VillagerCooldownHelper.VillagerState.BABY
                ? VillagerCooldownHelper.foodHint() : null;

        renderText(state, villager.getBbHeight(), matrices, queue, camera, mc,
                getVillagerDisplayComponent(info), foodHint, fade, info.color(), config);
    }

    private static void renderText(EntityRenderState state, float bbHeight, PoseStack matrices,
                                   SubmitNodeCollector queue, CameraRenderState camera,
                                   Minecraft mc, Component text, List<Component> subLines,
                                   float fade, int color, BreedTimerConfig config) {
        int bgAlpha = (int) (config.labelOpacity * fade * 255.0f);
        int backgroundColor = bgAlpha << 24;
        // Alpha is honoured verbatim by prepare8xTextOutline, so the fade has to live in the
        // outline colour too -- otherwise the outline stays solid while the text fades out.
        // Floored at 1, never 0: outlineColor == 0 is the renderer's own "no outline" sentinel,
        // so a pure black at zero alpha would silently switch the feature off.
        int outlineColor = config.effectiveTextOutline() ? (Math.max(1, (int) (fade * 255.0f)) << 24) : 0;

        // Anchor on the vanilla name tag position when the renderer computed one (offset a bit
        // further up so the label clears it), else fall back to a fixed height above the entity.
        Vec3 attachment = state.nameTagAttachment != null
                ? state.nameTagAttachment.add(0, 0.3, 0)
                : new Vec3(0, bbHeight + 0.5, 0);
        // Push the label down by one line when the entity also has a vanilla name tag, so the
        // two don't render on top of each other.
        int yOffset = state.nameTag != null ? 1 : 0;

        matrices.pushPose();
        matrices.translate(attachment.x, attachment.y + 0.5, attachment.z);
        matrices.mulPose(camera.orientation);
        matrices.scale(0.025F, -0.025F, 0.025F);

        submitLine(matrices, queue, mc, text, (float) yOffset, fade, color,
                backgroundColor, outlineColor);
        if (subLines != null) {
            // Stacked under the timer, grey (the palette's "inert" slot) so the countdown stays
            // the thing you read first.
            int subLineColor = StatePalette.current().inert;
            for (int i = 0; i < subLines.size(); i++) {
                submitLine(matrices, queue, mc, subLines.get(i),
                        yOffset + (i + 1) * (float) mc.font.lineHeight,
                        fade, subLineColor, backgroundColor, outlineColor);
            }
        }

        matrices.popPose();
    }

    /**
     * Two passes, the way vanilla draws name tags. The first is depth-ignoring and carries the
     * background box, so the label never disappears behind the animal or the terrain. The second
     * redraws the glyphs opaque on top of that box.
     *
     * <p>Drawing only the first would leave the text sitting under the background quad, i.e.
     * multiplied by it: at the default 50% opacity the glyphs come out at exactly half brightness
     * (#55FF55 measured as #2A7E2A) and contrast against the box falls to 1.4:1.
     */
    private static void submitLine(PoseStack matrices, SubmitNodeCollector queue, Minecraft mc,
                                   Component line, float y, float fade, int color,
                                   int backgroundColor, int outlineColor) {
        int textColor = ((int) (fade * 255.0f) << 24) | color;
        int seeThroughColor = ((int) (fade * SEE_THROUGH_ALPHA) << 24) | color;
        float x = -mc.font.width(line) / 2.0F;

        // Outline stays 0 on this pass on purpose. TextFeatureRenderer.buildGroup branches on
        // outlineColor != 0 and then discards displayMode, dropShadow AND backgroundColor -- an
        // outline here would take the background box and the see-through behaviour with it, which
        // are the only two reasons this pass exists.
        queue.submitText(matrices, x, y, line.getVisualOrderText(), false,
                Font.DisplayMode.SEE_THROUGH, LightTexture.FULL_BRIGHT, seeThroughColor, backgroundColor, 0);
        // The opaque pass already passes NORMAL and a background of 0, which is exactly what the
        // outline branch forces anyway; the only visible change is that the fill is drawn with
        // POLYGON_OFFSET instead of NORMAL, which is what vanilla uses for outlined text.
        queue.submitText(matrices, x, y, line.getVisualOrderText(), false,
                Font.DisplayMode.NORMAL, LightTexture.FULL_BRIGHT, textColor, 0, outlineColor);
    }


    /**
     * Allays duplicate rather than breed, so they carry only a readiness state and, once we have
     * seen one duplicate, an exact countdown — the vanilla cooldown is a flat 6000 ticks.
     */
    public static void renderAllayLabel(EntityRenderState state, Allay allay, PoseStack matrices,
                                        SubmitNodeCollector queue, CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (config.compactMode) return;
        if (BreedCooldownHelper.isOutOfFieldOfView(player, allay)) return;
        if (!LookTarget.isLabelled(allay)) return;

        float fade = BreedCooldownHelper.getDistanceFade(player, allay);
        if (fade <= 0.0f) return;

        BreedCooldownHelper.AllayTimerInfo info = BreedCooldownHelper.createAllayInfo(allay);
        // An allay has exactly two states, and they are the two the compact HUD counts it under.
        if (!(info.ready() ? config.showReady : config.showCooldown)) return;

        Component text = info.ready()
                ? (info.dancing()
                        ? Component.translatable("breedtimer.state.ready")
                        // Ready, but Allay.mobInteract needs isDancing() before the shard counts,
                        // so "Ready" alone would promise something the game refuses.
                        : Component.translatable("breedtimer.state.allay_needs_jukebox"))
                : info.remainingTicks() < 0
                        ? Component.translatable("breedtimer.state.allay_not_ready")
                        : Component.literal(BreedCooldownHelper.formatTime(info.remainingTicks()));

        renderText(state, allay.getBbHeight(), matrices, queue, camera, mc,
                text, null, fade, info.color(), config);
    }

    /**
     * Tadpoles are not animals, so they get their own entry point. Only a growth estimate applies —
     * there is nothing to breed and no cooldown to track.
     */
    public static void renderTadpoleLabel(EntityRenderState state, Tadpole tadpole,
                                          PoseStack matrices, SubmitNodeCollector queue,
                                          CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (config.compactMode || !config.showBabyTimer) return;
        if (BreedCooldownHelper.isOutOfFieldOfView(player, tadpole)) return;
        if (!LookTarget.isLabelled(tadpole)) return;

        float fade = BreedCooldownHelper.getDistanceFade(player, tadpole);
        if (fade <= 0.0f) return;

        BreedCooldownHelper.TadpoleTimerInfo info = BreedCooldownHelper.createTadpoleInfo(tadpole);
        Component text = info.remainingTicks() <= 0
                ? Component.translatable("breedtimer.state.growing_soon")
                : Component.translatable("breedtimer.state.growing",
                        BreedCooldownHelper.formatTime(info.remainingTicks()));

        renderText(state, tadpole.getBbHeight(), matrices, queue, camera, mc,
                text, null, fade, info.color(), config);
    }

    /**
     * Dolphins. Like a tadpole they carry a growth estimate and nothing else — the species cannot
     * breed (no {@code BreedGoal}, and {@code AgeableMob.canBreed()} is a hard false), so an adult
     * is given no label at all rather than a permanent "Cannot breed".
     */
    public static void renderAgeableLabel(EntityRenderState state, AgeableMob mob, PoseStack matrices,
                                          SubmitNodeCollector queue, CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (config.compactMode || !config.showBabyTimer) return;
        // Checked before the visibility maths, not after: an adult can never produce a label, so
        // every ocean full of dolphins would otherwise pay for an FOV test and a fade computation
        // per dolphin per frame to reach the same return.
        if (!mob.isBaby()) return;
        if (BreedCooldownHelper.isOutOfFieldOfView(player, mob)) return;
        if (!LookTarget.isLabelled(mob)) return;

        float fade = BreedCooldownHelper.getDistanceFade(player, mob);
        if (fade <= 0.0f) return;

        AgeableTracking.Info info = AgeableTracking.infoFor(mob);
        Component text = info.remainingTicks() <= 0
                ? Component.translatable("breedtimer.state.growing_soon")
                : Component.translatable("breedtimer.state.growing",
                        BreedCooldownHelper.formatTime(info.remainingTicks()));

        // No sterility check here (unlike renderLabel): this entry point is only ever reached for a
        // baby, and the hint names what makes it grow, not what makes it breed.
        List<Component> foodHint = config.showFoodHint ? AgeableTracking.foodHintFor(mob) : null;
        renderText(state, mob.getBbHeight(), matrices, queue, camera, mc,
                text, foodHint, fade, info.color(), config);
    }

    private static Component getDisplayComponent(AnimalTimerInfo info) {
        return switch (info.state()) {
            case READY -> Component.translatable("breedtimer.state.ready");
            case COOLDOWN -> Component.literal(BreedCooldownHelper.formatTime(info.remainingTicks()));
            case IN_LOVE -> Component.translatable("breedtimer.state.in_love");
            // Our growth time is an estimate; once it runs out the animal is still a baby, so
            // say "almost grown" instead of showing a 0:00 that looks stuck.
            case BABY -> info.remainingTicks() <= 0
                    ? Component.translatable("breedtimer.state.growing_soon")
                    : Component.translatable("breedtimer.state.growing",
                            BreedCooldownHelper.formatTime(info.remainingTicks()));
            case BLOCKED -> blockedComponent(info);
            case NEVER_GROWS -> Component.translatable("breedtimer.state.never_grows");
        };
    }

    /**
     * Blocked labels are plain text, except for the two gates the game gives an exact countdown for.
     * Two keys rather than one with an ignored argument: a translatable that never uses its %s would
     * still build the formatted string on every frame for every blocked animal.
     */
    private static Component blockedComponent(AnimalTimerInfo info) {
        String key = blockedKey(info.blockReason());
        return info.remainingTicks() > 0
                ? Component.translatable(key + ".timed",
                        BreedCooldownHelper.formatTime(info.remainingTicks()))
                : Component.translatable(key);
    }

    /** The one place a {@link BreedCooldownHelper.BlockReason} turns into text. */
    private static String blockedKey(BreedCooldownHelper.BlockReason reason) {
        return switch (reason) {
            case STERILE -> "breedtimer.state.blocked.sterile";
            case UNTAMED -> "breedtimer.state.blocked.untamed";
            case HURT    -> "breedtimer.state.blocked.hurt";
            case MOUNTED -> "breedtimer.state.blocked.mounted";
            case BUSY    -> "breedtimer.state.blocked.busy";
            case NO_BAMBOO -> "breedtimer.state.blocked.no_bamboo";
            case SITTING -> "breedtimer.state.blocked.sitting";
            case LAYING_EGG -> "breedtimer.state.blocked.laying_egg";
            case DIGGING -> "breedtimer.state.blocked.digging";
            case SCARED  -> "breedtimer.state.blocked.scared";
            case REPELLED -> "breedtimer.state.blocked.repelled";
        };
    }

    private static Component getVillagerDisplayComponent(VillagerTimerInfo info) {
        return switch (info.state()) {
            case READY    -> Component.translatable("breedtimer.state.ready");
            case COOLDOWN -> Component.literal(BreedCooldownHelper.formatTime(info.remainingTicks()));
            case BABY     -> info.remainingTicks() <= 0
                    ? Component.translatable("breedtimer.state.growing_soon")
                    : Component.translatable("breedtimer.state.growing",
                            BreedCooldownHelper.formatTime(info.remainingTicks()));
            case BLOCKED  -> Component.translatable("breedtimer.state.blocked.sleeping");
        };
    }
}
