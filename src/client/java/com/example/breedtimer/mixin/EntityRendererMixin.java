package com.example.breedtimer.mixin;

import com.example.breedtimer.config.BreedTimerConfig;
import com.example.breedtimer.render.TimerLabelRenderer;
import com.example.breedtimer.util.BreedCooldownHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "submit", at = @At("TAIL"))
    private void breedtimer$onSubmit(S state, PoseStack poseStack,
                                      SubmitNodeCollector collector, CameraRenderState camera,
                                      CallbackInfo ci) {
        if (!BreedTimerConfig.get().enabled) return;
        if (!(state instanceof LivingEntityRenderState livingState)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        double x = livingState.x;
        double y = livingState.y;
        double z = livingState.z;
        Vec3 statePos = new Vec3(x, y, z);

        AABB searchBox = new AABB(x - 1.0, y - 1.0, z - 1.0, x + 1.0, y + 1.0, z + 1.0);

        List<Animal> nearby = mc.level.getEntitiesOfClass(Animal.class, searchBox,
                BreedCooldownHelper::isSupportedAnimal);

        if (nearby.isEmpty()) return;

        // Find the closest animal to the render state position
        Animal closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Animal animal : nearby) {
            double dist = animal.position().distanceToSqr(statePos);
            if (dist < closestDist) {
                closestDist = dist;
                closest = animal;
            }
        }

        if (closest != null) {
            TimerLabelRenderer.renderLabel(state, closest, poseStack, collector, camera);
        }
    }
}
