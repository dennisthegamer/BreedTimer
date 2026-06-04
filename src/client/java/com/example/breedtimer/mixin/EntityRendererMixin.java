package com.example.breedtimer.mixin;

import com.example.breedtimer.config.BreedTimerConfig;
import com.example.breedtimer.render.TimerLabelRenderer;
import com.example.breedtimer.util.BreedCooldownHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "render", at = @At("TAIL"))
    private void breedtimer$onRender(S state, MatrixStack matrices,
                                      OrderedRenderCommandQueue queue, CameraRenderState camera,
                                      CallbackInfo ci) {
        if (!(state instanceof LivingEntityRenderState livingState)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled) return;

        double x = livingState.x;
        double y = livingState.y;
        double z = livingState.z;
        Vec3d statePos = new Vec3d(x, y, z);

        Box searchBox = new Box(x - 1.0, y - 1.0, z - 1.0, x + 1.0, y + 1.0, z + 1.0);

        if (config.showAnimals) {
            List<AnimalEntity> nearby = mc.world.getEntitiesByClass(AnimalEntity.class, searchBox,
                    BreedCooldownHelper::isSupportedAnimal);
            if (!nearby.isEmpty()) {
                AnimalEntity closest = findClosest(nearby, statePos);
                if (closest != null) {
                    TimerLabelRenderer.renderLabel(state, closest, matrices, queue, camera);
                    return;
                }
            }
        }

        if (config.showVillagers) {
            List<VillagerEntity> nearbyVillagers = mc.world.getEntitiesByClass(VillagerEntity.class, searchBox, v -> true);
            if (!nearbyVillagers.isEmpty()) {
                VillagerEntity closest = findClosest(nearbyVillagers, statePos);
                if (closest != null) {
                    TimerLabelRenderer.renderVillagerLabel(state, closest, matrices, queue, camera);
                }
            }
        }
    }

    private static <E extends Entity> E findClosest(List<E> entities, Vec3d pos) {
        E closest = null;
        double closestDist = Double.MAX_VALUE;
        for (E e : entities) {
            double dist = new Vec3d(e.getX(), e.getY(), e.getZ()).squaredDistanceTo(pos);
            if (dist < closestDist) {
                closestDist = dist;
                closest = e;
            }
        }
        return closest;
    }
}
