package de.dennisthegamer.breedtimer.render;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class TurtleEggRenderer {

    // [x, y, z, hatch (0–2), egg_count (1–4)] – updated per tick, read per frame
    private static final List<int[]> eggCache = new ArrayList<>();

    public static void tick(Player player, Level level, BreedTimerConfig config) {
        eggCache.clear();
        int radius = config.scanRadius;
        BlockPos origin = player.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -5; dy <= 5; dy++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(mutable);
                    if (!state.is(Blocks.TURTLE_EGG)) continue;
                    eggCache.add(new int[]{
                        mutable.getX(), mutable.getY(), mutable.getZ(),
                        state.getValue(TurtleEggBlock.HATCH),
                        state.getValue(TurtleEggBlock.EGGS)
                    });
                }
            }
        }
    }

    /** Returns [fresh, cracking, hatching] total individual egg counts for the compact HUD. */
    public static int[] getEggCounts() {
        int fresh = 0, cracking = 0, hatching = 0;
        for (int[] egg : eggCache) {
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
    public static void render(PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera) {
        if (eggCache.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled || config.compactMode || !config.showAnimals) return;

        Vec3 camPos = camera.pos;
        org.joml.Quaternionf orientation = camera.orientation;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle().normalize();
        double fovCos = Math.cos(Math.toRadians(config.fovAngle / 2.0));

        for (int[] egg : eggCache) {
            int bx = egg[0], by = egg[1], bz = egg[2];
            int hatch = egg[3], eggCount = egg[4];

            Vec3 labelPos = new Vec3(bx + 0.5, by + 1.15, bz + 0.5);

            // FOV check
            Vec3 toBlock = labelPos.subtract(eyePos).normalize();
            if (lookDir.dot(toBlock) < fovCos) continue;

            // Distance + fade
            double dist = eyePos.distanceTo(labelPos);
            if (dist > config.fadeEndDistance) continue;
            float fade;
            if (dist <= config.fadeStartDistance) {
                fade = 1.0f;
            } else {
                fade = 1.0f - (float)((dist - config.fadeStartDistance) /
                        (config.fadeEndDistance - config.fadeStartDistance));
            }
            if (fade <= 0.0f) continue;

            int labelColor = switch (hatch) {
                case 1  -> 0xFFAA00;
                case 2  -> 0x55FF55;
                default -> 0x55FFFF;
            };
            String key = switch (hatch) {
                case 1  -> "breedtimer.turtle_egg.cracking";
                case 2  -> "breedtimer.turtle_egg.hatching";
                default -> "breedtimer.turtle_egg.fresh";
            };

            Component text = Component.translatable(key, eggCount);
            int textAlpha = (int)(fade * 255.0f);
            int textColor = (textAlpha << 24) | labelColor;
            int bgAlpha   = (int)(config.backgroundOpacity * fade * 255.0f);
            int bgColor   = bgAlpha << 24;

            matrices.pushPose();
            matrices.translate(
                labelPos.x - camPos.x,
                labelPos.y - camPos.y,
                labelPos.z - camPos.z
            );
            matrices.mulPose(orientation);
            matrices.scale(0.025F, -0.025F, 0.025F);

            queue.submitText(
                matrices,
                -mc.font.width(text) / 2.0F,
                0.0F,
                text.getVisualOrderText(),
                false,
                Font.DisplayMode.NORMAL,
                LightTexture.FULL_BRIGHT,
                textColor,
                bgColor,
                0
            );

            matrices.popPose();
        }
    }
}
