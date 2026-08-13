package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.AgeableTracking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeding hook for the sulfur cube. Like the dolphin it is an {@code AgeableMob} that is not an
 * {@code Animal}, so {@link AnimalEventMixin} never sees it.
 *
 * <p>{@code SulfurCube.mobInteract} tests {@code isBaby()}, then its private {@code isFood}
 * ({@code ItemTags.SULFUR_CUBE_FOOD}), then {@code canAgeUp()}, and only then calls
 * {@code ageUp(getSpeedUpSecondsWhenFeeding(-age), true)}. Nothing on that path is behind
 * {@code isClientSide}, and the adult branch below it — priming, shears, buckets — never reaches
 * the call being injected on.
 *
 * <p><b>26.2 only.</b> The {@code net.minecraft.world.entity.monster.cubemob} package does not exist
 * in 26.1 or on any 1.21 branch; this file and its {@code breedtimer.mixins.json} entry are deleted
 * when porting rather than guarded.
 */
@Mixin(SulfurCube.class)
public abstract class SulfurCubeFeedMixin {

    @Inject(
            method = "mobInteract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/cubemob/SulfurCube;ageUp(IZ)V")
    )
    private void breedtimer$onSulfurCubeFed(Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        SulfurCube self = (SulfurCube) (Object) this;
        if (self.level().isClientSide()) {
            AgeableTracking.onFed(self);
        }
    }
}
