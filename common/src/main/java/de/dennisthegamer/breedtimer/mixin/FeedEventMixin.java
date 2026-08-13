package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The growth shortcuts that never reach {@code Animal.mobInteract}, and so were invisible to the
 * feed hook in {@link AnimalEventMixin}. Each one leaves the growth estimate running long.
 */
public final class FeedEventMixin {

    private FeedEventMixin() {}

    /**
     * A panda inlines the whole speed-up calculation in its own {@code mobInteract} and jumps past
     * the {@code super} call, so feeding a cub never touched the ordinary hook. The owner is spelled
     * out for the same reason as in {@link AnimalEventMixin}: javac writes the methodref with the
     * qualifying type, and loom only remaps fully qualified member references.
     */
    @Mixin(Panda.class)
    public abstract static class PandaFed {
        @Inject(
                method = "mobInteract",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Panda;ageUp(IZ)V")
        )
        private void breedtimer$onPandaFed(Player player, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResult> cir) {
            Panda self = (Panda) (Object) this;
            if (self.level().isClientSide()) {
                BreedCooldownHelper.onBabyFed(self);
            }
        }
    }

    /** A tadpole is not an {@code Animal} at all; {@code feed} calls its own {@code ageUp}. */
    @Mixin(Tadpole.class)
    public abstract static class TadpoleFed {
        @Inject(method = "feed", at = @At("HEAD"))
        private void breedtimer$onTadpoleFed(Player player, ItemStack stack, CallbackInfo ci) {
            Tadpole self = (Tadpole) (Object) this;
            if (self.level().isClientSide()) {
                BreedCooldownHelper.onTadpoleFed(self);
            }
        }
    }

    /**
     * A lamb eating a grass block gains a flat 1200 ticks through {@code Sheep.ate()}, which runs
     * server-side. Entity event 10 is the eating animation, broadcast from
     * {@code EatBlockGoal.start()} — not from {@code ate()} itself — so this fires on intent, not
     * completion. {@link BreedCooldownHelper#onSheepStartedEatingGrass} only records the start; the
     * growth estimate is not touched until the meal actually finishes.
     */
    @Mixin(Sheep.class)
    public abstract static class SheepAte {
        @Inject(method = "handleEntityEvent", at = @At("HEAD"))
        private void breedtimer$onSheepEat(byte id, CallbackInfo ci) {
            if (id != 10) return;
            Sheep self = (Sheep) (Object) this;
            if (self.level().isClientSide()) {
                BreedCooldownHelper.onSheepStartedEatingGrass(self);
            }
        }
    }
}
