package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.render.BlockLabelScanner;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the submit-collection phase to draw block labels. A shared mixin
 * is used because there is no common level-render event across both loaders.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSubmitMixin {

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void breedtimer$afterSubmitEntities(PoseStack poseStack, LevelRenderState levelRenderState,
                                                SubmitNodeCollector collector, CallbackInfo ci) {
        BlockLabelScanner.render(poseStack, collector, levelRenderState.cameraRenderState);
    }
}
