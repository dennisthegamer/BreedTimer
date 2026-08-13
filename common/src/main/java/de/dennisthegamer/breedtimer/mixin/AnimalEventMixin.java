package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Animal.class)
public abstract class AnimalEventMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void breedtimer$onEntityEvent(byte id, CallbackInfo ci) {
        if (id == 18) {
            Animal self = (Animal) (Object) this;
            if (self.level().isClientSide()) {
                BreedCooldownHelper.onLoveEvent(self);
            }
        }
    }

    /**
     * Fires when a baby is fed and vanilla shortens its remaining growth time.
     *
     * <p>Unlike the love branch above it, the feeding branch of {@code mobInteract} is not
     * server-only — it is skipped merely because the interacting player is no {@code ServerPlayer},
     * and it returns {@code SUCCESS} rather than {@code SUCCESS_SERVER}. The client therefore
     * walks the same path, and injecting on the {@code ageUp} call means we react to exactly the
     * feeds that count: the food check and the baby check have already passed.
     */
    /*
     * The owner must be Animal, and it must be spelled out. ageUp is declared on AgeableMob, but
     * javac writes the methodref with the qualifying type, so this call site's constant pool says
     * Animal.ageUp(IZ)V -- naming AgeableMob would not match. Leaving the owner off entirely does
     * not work either: loom only remaps fully qualified member references, so an unqualified
     * target ships as the literal string "ageUp(IZ)V" while the game runs on intermediary names,
     * and the injector finds nothing. With defaultRequire=1 that takes the game down on startup.
     */
    @Inject(
            method = "mobInteract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Animal;ageUp(IZ)V")
    )
    private void breedtimer$onBabyFed(Player player, InteractionHand hand,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        Animal self = (Animal) (Object) this;
        if (self.level().isClientSide()) {
            BreedCooldownHelper.onBabyFed(self);
        }
    }
}
