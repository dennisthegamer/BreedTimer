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
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {

    // HEAD, not TAIL: on 1.21/1.21.1 EntityRenderer#render returns early when
    // shouldShowName() is false, so TAIL only fires for entities that carry a name
    // tag — every plain animal/villager silently skipped the label. The method got a
    // single exit again in 1.21.2, which is why the later branches can use TAIL.
    @Inject(method = "render", at = @At("HEAD"))
    private void breedtimer$onRender(T entity, float entityYaw, float partialTick, PoseStack matrices,
                                      MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled) return;
        // Everything below runs once per rendered entity per frame. Reject here on the conditions
        // every branch would reject on anyway: all label entry points bail out immediately in
        // compact mode, and with neither category shown nothing can be drawn at all.
        if (config.compactMode) return;
        if (!config.showAnimals && !config.showVillagers) return;

        // No render state to read a pre-computed camera distance off of on this branch -- the
        // entity is already the parameter, so distanceToSqr is called on it directly instead. The
        // margin is deliberately generous: this distance is camera-based while the exact test in
        // TimerLabelRenderer is player-based, and in third person the two differ by a few blocks.
        // Being over-inclusive here cannot drop a label the exact test would have kept.
        double cullRange = config.fadeEndDistance + 8.0;
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        if (entity.distanceToSqr(camPos) > cullRange * cullRange) return;

        if (config.showAnimals && entity instanceof Animal animal
                && BreedCooldownHelper.isSupportedAnimal(animal)) {
            TimerLabelRenderer.renderLabel(animal, matrices, bufferSource,
                    mc.gameRenderer.getMainCamera());
        } else if (config.showVillagers && entity instanceof Villager villager) {
            TimerLabelRenderer.renderVillagerLabel(villager, matrices, bufferSource,
                    mc.gameRenderer.getMainCamera());
        } else if (config.showAnimals && entity instanceof Allay allay) {
            TimerLabelRenderer.renderAllayLabel(allay, matrices, bufferSource,
                    mc.gameRenderer.getMainCamera());
        } else if (config.showAnimals && config.showBabyTimer && entity instanceof Tadpole tadpole) {
            // Tadpoles are not Animals, so the branch above never sees them. No AgeableMob/ageable
            // branch after this one: on this branch a dolphin extends WaterAnimal, not AgeableMob,
            // so there is nothing else that could ever reach a fifth branch here.
            TimerLabelRenderer.renderTadpoleLabel(tadpole, matrices, bufferSource,
                    mc.gameRenderer.getMainCamera());
        }
    }
}
