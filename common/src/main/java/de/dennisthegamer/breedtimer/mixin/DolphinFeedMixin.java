package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.AgeableTracking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeding hook for dolphins, which {@link AnimalEventMixin} cannot reach: a dolphin is not an
 * {@code Animal}, so it never touches {@code Animal.mobInteract} at all.
 *
 * <p>{@code Dolphin.mobInteract} handles the fish itself — {@code ItemTags.FISHES}, then
 * {@code canAgeUp()}, then {@code ageUp(getSpeedUpSecondsWhenFeeding(-age), true)} — and only falls
 * through to {@code super} when the stack is not a fish. Injecting on that {@code ageUp} call means
 * this fires on exactly the feeds that count: both gates have already passed.
 *
 * <p>Only the eating sound sits behind {@code isClientSide}; the branch returns {@code SUCCESS}
 * rather than {@code SUCCESS_SERVER}, so the client walks it too. The size of the cut still has to
 * come from our own estimate, because vanilla computes it from the {@code age} field, which the
 * client never receives.
 *
 * <p>The owner in the target is spelled {@code Dolphin} for the same reason it is spelled
 * {@code Animal} in {@link AnimalEventMixin}: javac writes the methodref with the qualifying type,
 * and loom remaps only fully qualified member references.
 */
@Mixin(Dolphin.class)
public abstract class DolphinFeedMixin {

    @Inject(
            method = "mobInteract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Dolphin;ageUp(IZ)V")
    )
    private void breedtimer$onDolphinFed(Player player, InteractionHand hand,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        Dolphin self = (Dolphin) (Object) this;
        if (self.level().isClientSide()) {
            AgeableTracking.onFed(self);
        }
    }
}
