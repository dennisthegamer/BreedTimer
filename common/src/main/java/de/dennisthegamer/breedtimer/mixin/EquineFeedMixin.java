package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeding hook for the equines, which the {@link AnimalEventMixin} one cannot reach.
 *
 * <p>{@code Horse}, {@code AbstractChestedHorse} (donkey, mule, llama) and {@code Camel} all
 * override {@code mobInteract} and pass a food interaction to {@code fedFood} instead of to
 * {@code Animal.mobInteract}, so the injector that watches {@code Animal.ageUp} never fires for
 * them. Nothing overrides {@code fedFood} itself, which makes it the single point that covers the
 * whole family — including the trader llama and the camel husk, which inherit it two levels up.
 *
 * <p>Deliberately hooked at {@code HEAD} rather than on the {@code ageUp} call inside
 * {@code handleEating}: that call is guarded by {@code isClientSide}, so on the client there is
 * nothing to inject on.
 */
@Mixin(AbstractHorse.class)
public abstract class EquineFeedMixin {

    @Inject(method = "fedFood", at = @At("HEAD"))
    private void breedtimer$onEquineFed(Player player, ItemStack stack,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        AbstractHorse self = (AbstractHorse) (Object) this;
        if (self.level().isClientSide()) {
            BreedCooldownHelper.onEquineFed(self, stack);
        }
    }
}
