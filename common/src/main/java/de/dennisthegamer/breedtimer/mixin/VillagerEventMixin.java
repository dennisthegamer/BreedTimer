package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractVillager.class)
public abstract class VillagerEventMixin {

    // addParticlesAroundSelf is defined in AbstractVillager and called when
    // Villager.handleEntityEvent receives byte 12 (heart particles = willing to breed).
    // Targeting this method avoids injection into handleEntityEvent which is not
    // declared in AbstractVillager and causes a Mixin resolution crash.
    @Inject(method = "addParticlesAroundSelf", at = @At("HEAD"))
    private void breedtimer$onVillagerParticles(ParticleOptions particle, CallbackInfo ci) {
        if (particle != ParticleTypes.HEART) return;
        AbstractVillager self = (AbstractVillager) (Object) this;
        if (!(self instanceof Villager)) return; // skip WanderingTrader
        if (self.isBaby()) return;               // skip baby birth event
        if (!self.level().isClientSide()) return;
        VillagerCooldownHelper.onWillingEvent(self);
    }
}
