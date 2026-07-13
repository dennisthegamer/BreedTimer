package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.render.TimerLabelRenderer;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {

    @Inject(method = "render", at = @At("TAIL"))
    private void breedtimer$onRender(T entity, float entityYaw, float partialTick, PoseStack matrices,
                                      MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled) return;

        if (config.showAnimals && entity instanceof Animal animal
                && BreedCooldownHelper.isSupportedAnimal(animal)) {
            TimerLabelRenderer.renderLabel(animal, matrices, bufferSource,
                    mc.gameRenderer.getMainCamera());
        } else if (config.showVillagers && entity instanceof Villager villager) {
            TimerLabelRenderer.renderVillagerLabel(villager, matrices, bufferSource,
                    mc.gameRenderer.getMainCamera());
        }
    }
}
