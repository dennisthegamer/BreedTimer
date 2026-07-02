package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public abstract class VillagerEventMixin {

    // handleStatus byte 12 = heart particles (willing to breed).
    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void breedtimer$onVillagerStatus(byte status, CallbackInfo ci) {
        if (status != 12) return;
        VillagerEntity self = (VillagerEntity) (Object) this;
        if (self.isBaby()) return;
        if (!self.getEntityWorld().isClient()) return;
        VillagerCooldownHelper.onWillingEvent(self);
    }
}
