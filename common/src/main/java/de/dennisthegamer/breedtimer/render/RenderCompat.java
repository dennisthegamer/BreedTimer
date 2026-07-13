package de.dennisthegamer.breedtimer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

/** Version-compat shims for the client render path across MC 1.21.2–1.21.5. */
public final class RenderCompat {

    private RenderCompat() {}

    /**
     * {@code PoseStack.mulPose(Quaternionf)} became {@code mulPose(Quaternionfc)} in the
     * 1.21.5 render refactor, so a jar compiled against 1.21.5 throws
     * {@code NoSuchMethodError} on 1.21.2–1.21.4. Rotating the pose matrix directly — which
     * is exactly what {@code mulPose} does for the transform used by billboarded labels —
     * emits a JOML call whose signature is identical across the whole range.
     */
    public static void rotate(PoseStack matrices, Quaternionf rotation) {
        matrices.last().pose().rotate(rotation);
    }
}
