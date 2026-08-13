package de.dennisthegamer.breedtimer.render;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.util.LookTarget;
import de.dennisthegamer.breedtimer.util.Visibility;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DriedGhastBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SnifferEggBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-tick block scan behind every floating block label the mod draws.
 *
 * <p>It was written for turtle eggs, then grew sniffer eggs, dried ghasts and beehives, so the old
 * name had stopped describing it. What the four have in common is the shape of the problem: a block
 * whose interesting state lives in its block state, is fully readable on the client, and is either
 * invisible in the model or only distinguishable by squinting at it.
 */
public class BlockLabelScanner {

    // [x, y, z, stage, count, kind, flag, visible] – updated per tick, read per frame.
    // kind: 0 turtle egg (stage 0-2), 1 sniffer egg (stage 0-2), 2 dried ghast (stage 0-3),
    // 3 beehive/bee nest (stage = honey level 0-5), 4 torchflower crop (stage 0-1),
    // 5 pitcher crop (stage = age 0-4, lower half only).
    // flag: meaning depends on kind – sniffer: 1 when the egg sits on moss and hatches at
    // double rate; dried ghast: 1 when waterlogged (hydration only rises while true); turtle
    // egg: 1 when the block satisfies onSand() (HATCH only advances while true); beehive:
    // 1 when a campfire below makes harvesting safe; torchflower/pitcher: unused, always 0.
    // visible: 0 when the through-walls setting is off and terrain stands in the way. Always 1 while the
    // setting is on (the default), so the raycast is only paid by players who asked for it.
    private static final int KIND_TURTLE = 0, KIND_SNIFFER = 1, KIND_DRIED_GHAST = 2,
                             KIND_BEEHIVE = 3, KIND_TORCHFLOWER = 4, KIND_PITCHER = 5;
    private static final List<int[]> blockCache = new ArrayList<>();

    /** Blocks from the world we just left must not be counted or drawn in the next one. */
    public static void clear() {
        blockCache.clear();
    }

    /**
     * The blocks this scan is looking for, as a palette test.
     *
     * <p>A chunk section stores its distinct block states in a small palette, so asking whether it
     * could possibly contain one of these is a handful of comparisons rather than 4096 lookups.
     * The test is conservative — it may answer yes for a section that does not actually hold one —
     * so skipping on a "no" can never miss an egg.
     */
    private static final java.util.function.Predicate<BlockState> BLOCK_PALETTE_TEST =
            state -> state.is(Blocks.TURTLE_EGG) || state.is(Blocks.SNIFFER_EGG)
                    || state.is(Blocks.DRIED_GHAST)
                    || state.is(Blocks.BEEHIVE) || state.is(Blocks.BEE_NEST)
                    || state.is(Blocks.TORCHFLOWER_CROP) || state.is(Blocks.PITCHER_CROP);

    /**
     * Rebuilds the cache of nearby eggs.
     *
     * <p>The volume is (2r+1)² × 11 blocks, which at the default radius is nearly twelve thousand
     * block lookups — and this runs every client tick. Each lookup is not a plain array read: it
     * resolves the chunk through a volatile array reference before it can touch the section. So
     * rather than walking blocks directly, this walks the handful of chunk sections the volume
     * covers and asks each one whether it could contain an egg at all. In the overwhelmingly
     * common case — no eggs anywhere near — the whole tick collapses to a few palette tests.
     */
    public static void tick(Player player, Level level, BreedTimerConfig config) {
        blockCache.clear();
        boolean throughWalls = config.labelThroughWalls;
        int radius = config.effectiveScanRadius();
        BlockPos origin = player.blockPosition();
        int minX = origin.getX() - radius, maxX = origin.getX() + radius;
        int minZ = origin.getZ() - radius, maxZ = origin.getZ() + radius;
        int minY = origin.getY() - 5, maxY = origin.getY() + 5;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int cx = minX >> 4; cx <= (maxX >> 4); cx++) {
            for (int cz = minZ >> 4; cz <= (maxZ >> 4); cz++) {
                var chunk = level.getChunk(cx, cz);
                if (chunk == null) continue;
                int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
                int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);

                for (int y = minY; y <= maxY; ) {
                    int sectionTop = (((y >> 4) + 1) << 4) - 1;
                    int yEnd = Math.min(maxY, sectionTop);
                    int index = chunk.getSectionIndex(y);
                    if (index < 0 || index >= chunk.getSections().length
                            || !chunk.getSection(index).maybeHas(BLOCK_PALETTE_TEST)) {
                        y = yEnd + 1;
                        continue;
                    }
                    scanSlice(level, player, mutable, x0, x1, z0, z1, y, yEnd, throughWalls);
                    y = yEnd + 1;
                }
            }
        }
    }

    /** Walks the blocks of one chunk section that fall inside the scan volume. */
    private static void scanSlice(Level level, Player player, BlockPos.MutableBlockPos mutable,
                                  int x0, int x1, int z0, int z1, int y0, int y1, boolean throughWalls) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                // x innermost: a section's storage is indexed y<<8 | z<<4 | x, so this walks it
                // contiguously instead of striding through it.
                for (int x = x0; x <= x1; x++) {
                    mutable.set(x, y, z);
                    BlockState state = level.getBlockState(mutable);
                    if (state.is(Blocks.TURTLE_EGG)) {
                        // randomTick advances HATCH only when onSand() passes, so an egg on any
                        // other block will sit there forever. The client can answer that exactly.
                        boolean onSand = TurtleEggBlock.onSand(level, mutable);
                        blockCache.add(new int[]{
                            mutable.getX(), mutable.getY(), mutable.getZ(),
                            state.getValue(TurtleEggBlock.HATCH),
                            state.getValue(TurtleEggBlock.EGGS), KIND_TURTLE, onSand ? 1 : 0,
                            losFlag(level, player, mutable, throughWalls)
                        });
                    } else if (state.is(Blocks.DRIED_GHAST)) {
                        // Not an egg, but the same shape of thing: a block that ripens in visible
                        // steps and then spawns a mob. Hydration runs 0-3 and only rises while the
                        // block is waterlogged, ending in a ghastling.
                        boolean waterlogged = state.getValue(DriedGhastBlock.WATERLOGGED);
                        blockCache.add(new int[]{
                            mutable.getX(), mutable.getY(), mutable.getZ(),
                            state.getValue(DriedGhastBlock.HYDRATION_LEVEL), 1, KIND_DRIED_GHAST,
                            waterlogged ? 1 : 0,
                            losFlag(level, player, mutable, throughWalls)
                        });
                    } else if (state.is(Blocks.SNIFFER_EGG)) {
                        // Sniffer eggs are one egg per block, so the count is always 1. The moss
                        // boost gets its own slot: it used to sit in slot 4, which countByStage
                        // reads as a count, so an unboosted egg counted as zero.
                        // hatchBoost is public static and takes a BlockGetter, so the client can
                        // ask it directly: a sniffer egg on moss hatches in half the time.
                        blockCache.add(new int[]{
                            mutable.getX(), mutable.getY(), mutable.getZ(),
                            state.getValue(SnifferEggBlock.HATCH),
                            1, KIND_SNIFFER,
                            SnifferEggBlock.hatchBoost(level, mutable) ? 1 : 0,
                            losFlag(level, player, mutable, throughWalls)
                        });
                    } else if (state.is(Blocks.BEEHIVE) || state.is(Blocks.BEE_NEST)) {
                        // Levels 0-4 all render the same model, so this is the one thing about a
                        // hive the game gives no way to see. Both blocks are the same BeehiveBlock
                        // -- Blocks' clinit builds them from one factory -- so one property read
                        // covers both.
                        int honey = state.getValue(BeehiveBlock.HONEY_LEVEL);
                        // isSmokeyPos walks up to five blocks down and joins a collision shape at
                        // each one, so it is only asked where the answer can matter: BeehiveBlock
                        // .useItemOn returns before the shears branch unless HONEY_LEVEL is 5, and
                        // an empty hive is not something you harvest.
                        boolean smokey = honey >= BeehiveBlock.MAX_HONEY_LEVELS
                                && CampfireBlock.isSmokeyPos(level, mutable);
                        blockCache.add(new int[]{
                            mutable.getX(), mutable.getY(), mutable.getZ(),
                            honey, 1, KIND_BEEHIVE, smokey ? 1 : 0,
                            losFlag(level, player, mutable, throughWalls)
                        });
                    } else if (state.is(Blocks.TORCHFLOWER_CROP)) {
                        // AGE is AGE_1 (0-1) on every version. MAX_AGE is deliberately NOT used: it is 2 on
                        // 1.21-1.21.4 and 1 from 1.21.5, so the constant contradicts the property on the oldest
                        // branch, and because it is a compile-time constant javac inlines it -- a wrong value
                        // would ship silently rather than failing to link. getMaxAge() is 2 everywhere and
                        // describes the *plant*, not the crop block -- growing out of AGE 1 replaces the block
                        // with Blocks.TORCHFLOWER, so there is no "ready" crop state to render.
                        blockCache.add(new int[]{
                            mutable.getX(), mutable.getY(), mutable.getZ(),
                            state.getValue(TorchflowerCropBlock.AGE), 1, KIND_TORCHFLOWER, 0,
                            losFlag(level, player, mutable, throughWalls)
                        });
                    } else if (state.is(Blocks.PITCHER_CROP)) {
                        // Two blocks from age 3 up -- isDouble(a) is a >= 3 -- and both halves carry the same
                        // AGE, because grow() writes the upper one from the state it just wrote the lower one
                        // from. Counting the lower half only is what vanilla itself does: isRandomlyTicking
                        // and every loot-table entry are both gated on HALF == LOWER.
                        // BlockStateProperties.DOUBLE_BLOCK_HALF, not PitcherCropBlock.HALF: the latter is a
                        // plain alias (its <clinit> is `getstatic DoublePlantBlock.HALF; putstatic HALF`) and it
                        // does not exist at all before 1.21.5, which would split this file across branches for
                        // nothing. The shared property is present on every version.
                        if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) continue;
                        blockCache.add(new int[]{
                            mutable.getX(), mutable.getY(), mutable.getZ(),
                            state.getValue(PitcherCropBlock.AGE), 1, KIND_PITCHER, 0,
                            losFlag(level, player, mutable, throughWalls)
                        });
                    }
                }
            }
        }
    }

    /** 1 unless the see-through default was switched off and this block is behind terrain. */
    private static int losFlag(Level level, Player player, BlockPos pos, boolean throughWalls) {
        return throughWalls || LookTarget.blockVisible(level, player,
                pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5) ? 1 : 0;
    }

    /** Returns [fresh, cracking, hatching] total individual egg counts for the compact HUD. */
    public static int[] getEggCounts() {
        return countByStage(KIND_TURTLE);
    }

    /** Same three stages, counted over sniffer eggs. */
    public static int[] getSnifferEggCounts() {
        return countByStage(KIND_SNIFFER);
    }

    /**
     * Dried ghasts, bucketed as dry (0) / soaking-or-drying (1-2, or 3 while not waterlogged) /
     * ready (3 while waterlogged).
     *
     * <p>F-2: a ghast at max hydration that has since left the water is drying back down, not
     * ready -- the same distinction {@link #render} already draws with the orange ".drying" label
     * (Task 15). Bucketing it as "ready" here would be the same kind of lie that fix exists to stop,
     * just on the compact-HUD surface instead of the floating-label one. entry[6] carries the
     * waterlogged flag; see the class-top comment.
     */
    public static int[] getDriedGhastCounts() {
        int dry = 0, soaking = 0, ready = 0;
        for (int[] entry : blockCache) {
            if (entry[5] != KIND_DRIED_GHAST) continue;
            boolean waterlogged = entry[6] == 1;
            if (entry[3] == 0) dry++;
            else if (entry[3] >= DriedGhastBlock.MAX_HYDRATION_LEVEL && waterlogged) ready++;
            else soaking++;
        }
        return new int[]{dry, soaking, ready};
    }

    /** Hives bucketed as still filling (honey 0-4) and full (5). */
    public static int[] getBeehiveCounts() {
        int filling = 0, full = 0;
        for (int[] entry : blockCache) {
            if (entry[5] != KIND_BEEHIVE) continue;
            if (entry[3] >= BeehiveBlock.MAX_HONEY_LEVELS) full++;
            else filling++;
        }
        return new int[]{filling, full};
    }

    /** Crops bucketed as still growing and ready to harvest. A torchflower crop is never "ready". */
    public static int[] getCropCounts() {
        int growing = 0, ready = 0;
        for (int[] entry : blockCache) {
            if (entry[5] == KIND_PITCHER) {
                if (entry[3] >= 4) ready++; else growing++;
            } else if (entry[5] == KIND_TORCHFLOWER) {
                growing++;
            }
        }
        return new int[]{growing, ready};
    }

    private static int[] countByStage(int kind) {
        int fresh = 0, cracking = 0, hatching = 0;
        for (int[] egg : blockCache) {
            if (egg[5] != kind) continue;
            int count = egg[4];
            switch (egg[3]) {
                case 0 -> fresh    += count;
                case 1 -> cracking += count;
                case 2 -> hatching += count;
            }
        }
        return new int[]{fresh, cracking, hatching};
    }

    /** Called during level-render submit collection – renders floating labels above turtle egg blocks. */
    public static void render(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (blockCache.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled || config.compactMode || !config.showAnimals || !config.showBlocks) return;

        // Reuse the same CameraRenderState pattern as TimerLabelRenderer
        Vec3 camPos = camera.pos;

        // One Visibility for the whole batch: eye position, look vector, fovCos and the fade window
        // are the same for every egg, so they are worked out once instead of once per egg.
        Visibility visibility = new Visibility(player, config);
        StatePalette p = StatePalette.current();

        for (int[] entry : blockCache) {
            int bx = entry[0], by = entry[1], bz = entry[2];
            int hatch = entry[3], eggCount = entry[4];
            int kind = entry[5];

            Vec3 labelPos = new Vec3(bx + 0.5, by + 1.15, bz + 0.5);

            if (!visibility.inView(labelPos.x, labelPos.y, labelPos.z)) continue;
            if (entry[7] == 0) continue;

            float fade = visibility.fade(labelPos.x, labelPos.y, labelPos.z);
            if (fade <= 0.0f) continue;

            Component text;
            int labelColor;
            if (kind == KIND_BEEHIVE) {
                int honey = entry[3];
                boolean full = honey >= BeehiveBlock.MAX_HONEY_LEVELS;
                // Green only when the hive is both full and safe. A full hive with no campfire
                // under it is the amber "you can act on this" colour the blocked labels use --
                // harvesting it right now costs you a swarm.
                String key = !full ? "breedtimer.beehive.filling"
                        : entry[6] == 1 ? "breedtimer.beehive.full_smokey"
                        : "breedtimer.beehive.full";
                labelColor = !full ? p.young : entry[6] == 1 ? p.ready : p.coolMid;
                text = Component.translatable(key, honey, BeehiveBlock.MAX_HONEY_LEVELS);
            } else if (kind == KIND_TORCHFLOWER || kind == KIND_PITCHER) {
                int age = entry[3];
                if (kind == KIND_PITCHER) {
                    // Age 4 is the only state that drops a pitcher plant; 0-3 drop the pod back.
                    boolean ripe = age >= 4;
                    labelColor = ripe ? p.ready : age == 0 ? p.young : p.coolMid;
                    text = ripe ? Component.translatable("breedtimer.pitcher_crop.ready")
                                : Component.translatable("breedtimer.pitcher_crop.growing", age, 4);
                } else {
                    // A torchflower crop has no ripe state: growing past age 1 turns the block into
                    // Blocks.TORCHFLOWER, which is a flower and needs no label.
                    labelColor = age == 0 ? p.young : p.coolMid;
                    text = Component.translatable("breedtimer.torchflower_crop.growing", age, 1);
                }
            } else {
                boolean ripe = kind == KIND_DRIED_GHAST
                        ? hatch >= DriedGhastBlock.MAX_HYDRATION_LEVEL
                        : hatch >= 2;
                labelColor = ripe ? p.ready : hatch == 0 ? p.young : p.coolMid;
                String prefix = switch (kind) {
                    case KIND_SNIFFER      -> "breedtimer.sniffer_egg";
                    case KIND_DRIED_GHAST  -> "breedtimer.dried_ghast";
                    default                -> "breedtimer.turtle_egg";
                };
                String key = prefix + (ripe ? ".hatching" : hatch == 0 ? ".fresh" : ".cracking");
                if (kind == KIND_DRIED_GHAST && entry[6] == 0 && hatch > 0) {
                    // Hydration only rises while waterlogged; out of water it ticks back down, so
                    // "Soaking" would be a lie and "Ready to hatch!" doubly so.
                    key = prefix + ".drying";
                    // Orange, not the green "ready" color: a green "Drying out" would still half-
                    // promise a hatch. Orange matches the mod's actionable-gate color (re-waterlog
                    // it); grey is reserved for states that can never be resolved.
                    labelColor = p.coolMid;
                }
                // A boosted sniffer egg hatches in 12000 ticks instead of 24000; say so while it is
                // still fresh, where the difference actually matters.
                if (kind == KIND_SNIFFER && entry[6] == 1 && !ripe) {
                    key = prefix + (hatch == 0 ? ".fresh_boosted" : ".cracking_boosted");
                }
                if (kind == KIND_TURTLE && entry[6] == 0) {
                    // randomTick only advances HATCH while onSand() holds, so an egg off sand is
                    // cosmetic forever. Say that instead of promising a hatch that can't happen.
                    key = "breedtimer.turtle_egg.not_on_sand";
                    labelColor = p.inert;
                }
                // A turtle egg block holds one to four eggs and says how many; the others are one
                // per block, so their labels would only ever read "1x".
                text = kind == KIND_TURTLE
                        ? Component.translatable(key, eggCount)
                        : Component.translatable(key);
            }
            int textAlpha = (int)(fade * 255.0f);
            int textColor = (textAlpha << 24) | labelColor;
            int bgAlpha   = (int)(config.labelOpacity * fade * 255.0f);
            int bgColor   = bgAlpha << 24;
            int seeThroughColor = ((int)(fade * TimerLabelRenderer.SEE_THROUGH_ALPHA) << 24) | labelColor;
            // Same outline treatment as TimerLabelRenderer: floored at alpha 1 so it never hits the
            // renderer's own "no outline" sentinel, and only ever applied to the opaque pass below.
            int outlineColor = config.effectiveTextOutline() ? (Math.max(1, textAlpha) << 24) : 0;

            poseStack.pushPose();
            poseStack.translate(
                labelPos.x - camPos.x,
                labelPos.y - camPos.y,
                labelPos.z - camPos.z
            );
            // Same billboard pattern as TimerLabelRenderer
            poseStack.mulPose(camera.orientation);
            poseStack.scale(0.025F, -0.025F, 0.025F);

            // Same two passes as TimerLabelRenderer: see-through pass carries the background
            // box, the opaque pass then lands on top of it instead of underneath. The outline only
            // ever goes on the opaque pass -- see TimerLabelRenderer.submitLine for why.
            collector.submitText(poseStack, -mc.font.width(text) / 2.0F, 0.0F, text.getVisualOrderText(), false,
                    Font.DisplayMode.SEE_THROUGH, 0xF000F0, seeThroughColor, bgColor, 0);
            collector.submitText(poseStack, -mc.font.width(text) / 2.0F, 0.0F, text.getVisualOrderText(), false,
                    Font.DisplayMode.NORMAL, 0xF000F0, textColor, 0, outlineColor);

            poseStack.popPose();
        }
    }
}
