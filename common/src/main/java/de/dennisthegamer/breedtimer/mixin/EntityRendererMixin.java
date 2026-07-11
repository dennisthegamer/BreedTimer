package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.render.TimerLabelRenderer;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
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
        if (!(state instanceof LivingEntityRenderState livingState)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BreedTimerConfig config = BreedTimerConfig.get();
        if (!config.enabled) return;

        double x = livingState.x;
        double y = livingState.y;
        double z = livingState.z;
        Vec3 statePos = new Vec3(x, y, z);

        AABB searchBox = new AABB(x - 1.0, y - 1.0, z - 1.0, x + 1.0, y + 1.0, z + 1.0);

        if (config.showAnimals) {
            List<Animal> nearby = mc.level.getEntitiesOfClass(Animal.class, searchBox,
                    BreedCooldownHelper::isSupportedAnimal);
            if (!nearby.isEmpty()) {
                Animal closest = findClosest(nearby, statePos);
                if (closest != null) {
                    TimerLabelRenderer.renderLabel(state, closest, poseStack, collector, camera);
                    return;
                }
            }
        }

        if (config.showVillagers) {
            List<Villager> nearbyVillagers = mc.level.getEntitiesOfClass(Villager.class, searchBox);
            if (!nearbyVillagers.isEmpty()) {
                Villager closest = findClosest(nearbyVillagers, statePos);
                if (closest != null) {
                    TimerLabelRenderer.renderVillagerLabel(state, closest, poseStack, collector, camera);
                }
            }
        }
    }

    private static <E extends Entity> E findClosest(List<E> entities, Vec3 pos) {
        E closest = null;
        double closestDist = Double.MAX_VALUE;
        for (E e : entities) {
            double dist = e.position().distanceToSqr(pos);
            if (dist < closestDist) {
                closestDist = dist;
                closest = e;
            }
        }
        return closest;
    }
}
