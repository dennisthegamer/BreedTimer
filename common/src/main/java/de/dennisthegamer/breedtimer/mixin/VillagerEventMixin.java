package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerEventMixin {

    // handleEntityEvent byte 12 = heart particles (willing to breed).
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void breedtimer$onVillagerStatus(byte status, CallbackInfo ci) {
        if (status != 12) return;
        Villager self = (Villager) (Object) this;
        if (self.isBaby()) return;
        if (!self.level().isClientSide()) return;
        VillagerCooldownHelper.onWillingEvent(self);
    }
}
