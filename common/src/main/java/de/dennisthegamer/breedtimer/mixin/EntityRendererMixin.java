package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.render.TimerLabelRenderer;
import de.dennisthegamer.breedtimer.util.AgeableTracking;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import de.dennisthegamer.breedtimer.util.RenderStateEntityId;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "render", at = @At("TAIL"))
    private void breedtimer$onRender(S state, PoseStack matrices, MultiBufferSource bufferSource,
                                      int packedLight, CallbackInfo ci) {
        if (!(state instanceof LivingEntityRenderState livingState)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled) return;
        // Everything below runs once per rendered living entity per frame. Reject here on the
        // conditions every branch would reject on anyway: all label entry points bail out
        // immediately in compact mode, and with neither category shown nothing can be drawn.
        if (config.compactMode) return;
        if (!config.showAnimals && !config.showVillagers) return;
        // The render state already carries the squared camera distance, so this costs nothing.
        // The margin is deliberately generous: this distance is camera-based while the exact test
        // in TimerLabelRenderer is player-based, and in third person the two differ by a few
        // blocks. Being over-inclusive here cannot drop a label the exact test would have kept.
        double cullRange = config.fadeEndDistance + 8.0;
        if (livingState.distanceToCameraSq > cullRange * cullRange) return;

        // The id was stamped onto the state during extraction, so the entity comes straight back
        // out of the level's id table. This used to be a world query for every living entity in a
        // 2x2x2 box around the render position, followed by a nearest-match -- once per rendered
        // living entity per frame. In a herd every one of those queries walked the whole cluster,
        // so the cost grew with the square of the herd size, which is exactly when labels are on
        // screen. The exact id also removes the failure it was working around: two overlapping
        // mobs can no longer be mixed up.
        int entityId = ((RenderStateEntityId) state).breedtimer$getEntityId();
        if (entityId < 0) return;
        if (!(mc.level.getEntity(entityId) instanceof LivingEntity subject)) return;

        if (config.showAnimals && subject instanceof Animal animal
                && BreedCooldownHelper.isSupportedAnimal(animal)) {
            TimerLabelRenderer.renderLabel(animal, matrices, bufferSource, mc.gameRenderer.getMainCamera());
        } else if (config.showVillagers && subject instanceof Villager villager) {
            TimerLabelRenderer.renderVillagerLabel(villager, matrices, bufferSource, mc.gameRenderer.getMainCamera());
        } else if (config.showAnimals && subject instanceof Allay allay) {
            TimerLabelRenderer.renderAllayLabel(allay, matrices, bufferSource, mc.gameRenderer.getMainCamera());
        } else if (config.showAnimals && config.showBabyTimer && subject instanceof Tadpole tadpole) {
            TimerLabelRenderer.renderTadpoleLabel(tadpole, matrices, bufferSource, mc.gameRenderer.getMainCamera());
        } else if (config.showAnimals && config.showBabyTimer && subject instanceof AgeableMob ageable
                && AgeableTracking.isTracked(ageable)) {
            // Dolphins: AgeableMobs, but not Animals, so the first branch above never sees them.
            // Placed last so an Animal -- which is also an AgeableMob -- can only ever reach the
            // branch written for it.
            TimerLabelRenderer.renderAgeableLabel(ageable, matrices, bufferSource, mc.gameRenderer.getMainCamera());
        }
    }

    /**
     * Records which entity a render state belongs to, so the label hook does not have to work it
     * out from the position. Hooked on this overload because it is {@code final}: subclasses
     * cannot bypass it, so every render state passes through exactly once per frame.
     */
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void breedtimer$tagRenderState(T entity, float partialTick, CallbackInfoReturnable<S> cir) {
        S rendered = cir.getReturnValue();
        if (rendered != null) {
            ((RenderStateEntityId) rendered).breedtimer$setEntityId(entity.getId());
        }
    }
}
