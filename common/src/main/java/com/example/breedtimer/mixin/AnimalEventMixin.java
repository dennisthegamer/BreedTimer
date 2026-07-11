package com.example.breedtimer.mixin;

import com.example.breedtimer.util.BreedCooldownHelper;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
}
