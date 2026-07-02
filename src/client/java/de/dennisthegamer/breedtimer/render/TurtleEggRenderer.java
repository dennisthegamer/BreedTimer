package de.dennisthegamer.breedtimer.render;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class TurtleEggRenderer {

    // [x, y, z, hatch (0–2), egg_count (1–4)] – updated per tick, read per frame
    private static final List<int[]> eggCache = new ArrayList<>();

    public static void tick(PlayerEntity player, World world, BreedTimerConfig config) {
        eggCache.clear();
        int radius = config.scanRadius;
        BlockPos origin = player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -5; dy <= 5; dy++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    if (!state.isOf(Blocks.TURTLE_EGG)) continue;
                    eggCache.add(new int[]{
                        mutable.getX(), mutable.getY(), mutable.getZ(),
                        state.get(TurtleEggBlock.HATCH),
                        state.get(TurtleEggBlock.EGGS)
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

    /** Called from WorldRenderEvents.AFTER_ENTITIES – renders floating labels above turtle egg blocks. */
    public static void render(WorldRenderContext context) {
        if (eggCache.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled || config.compactMode || !config.showAnimals) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        org.joml.Quaternionf orientation = camera.getRotation();

        Vec3d eyePos = player.getEyePos();
        Vec3d lookDir = player.getRotationVec(1.0f).normalize();
        double fovCos = Math.cos(Math.toRadians(config.fovAngle / 2.0));

        MatrixStack matrices = context.matrices();
        OrderedRenderCommandQueue queue = context.commandQueue();

        for (int[] egg : eggCache) {
            int bx = egg[0], by = egg[1], bz = egg[2];
            int hatch = egg[3], eggCount = egg[4];

            Vec3d labelPos = new Vec3d(bx + 0.5, by + 1.15, bz + 0.5);

            // FOV check
            Vec3d toBlock = labelPos.subtract(eyePos).normalize();
            if (lookDir.dotProduct(toBlock) < fovCos) continue;

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

            Text text = Text.translatable(key, eggCount);
            int textAlpha = (int)(fade * 255.0f);
            int textColor = (textAlpha << 24) | labelColor;
            int bgAlpha   = (int)(config.backgroundOpacity * fade * 255.0f);
            int bgColor   = bgAlpha << 24;

            matrices.push();
            matrices.translate(
                labelPos.x - camPos.x,
                labelPos.y - camPos.y,
                labelPos.z - camPos.z
            );
            matrices.multiply(orientation);
            matrices.scale(0.025F, -0.025F, 0.025F);

            queue.submitText(
                matrices,
                -mc.textRenderer.getWidth(text) / 2.0F,
                0.0F,
                text.asOrderedText(),
                false,
                TextRenderer.TextLayerType.NORMAL,
                0xF000F0,
                textColor,
                bgColor,
                0
            );

            matrices.pop();
        }
    }
}
