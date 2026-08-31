package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Animal.class)
public abstract class AnimalEventMixin {

    /**
     * Fires when the local player offers food to an animal, before the game has decided anything.
     *
     * <p>{@code HEAD} rather than a branch inside: for an adult the client walks the whole method
     * and returns {@code CONSUME} without ever calling anything species-specific, so there is no
     * later instruction that means "this feed was offered". Whether the server accepts is answered
     * by the hearts that do or do not arrive afterwards -- see {@code FeedProbe}.
     *
     * <p>Not gated on {@code isFood} here: {@code BreedCooldownHelper.onFeedAttempt} needs the
     * animal anyway, and an unanswered probe on an animal that was never fed food costs nothing
     * because a wrong item cannot produce hearts either.
     */
    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void breedtimer$onFeedOffered(Player player, InteractionHand hand,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        Animal self = (Animal) (Object) this;
        if (!self.level().isClientSide()) return;
        if (player != Minecraft.getInstance().player) return;
        if (!self.isFood(player.getItemInHand(hand))) return;
        BreedCooldownHelper.onFeedAttempt(self);
    }

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
