package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import net.minecraft.entity.passive.AnimalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimalEntity.class)
public abstract class AnimalEventMixin {

    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void breedtimer$onEntityEvent(byte id, CallbackInfo ci) {
        if (id == 18) {
            AnimalEntity self = (AnimalEntity) (Object) this;
            if (self.getEntityWorld().isClient()) {
                BreedCooldownHelper.onLoveEvent(self);
            }
        }
    }
}
