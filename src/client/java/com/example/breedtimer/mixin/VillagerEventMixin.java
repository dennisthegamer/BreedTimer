package com.example.breedtimer.mixin;

import com.example.breedtimer.util.VillagerCooldownHelper;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerEventMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void breedtimer$onVillagerEvent(byte id, CallbackInfo ci) {
        if (id == 12) {
            AbstractVillager self = (AbstractVillager)(Object) this;
            if (self.level().isClientSide()) {
                VillagerCooldownHelper.onWillingEvent(self);
            }
        }
    }
}
