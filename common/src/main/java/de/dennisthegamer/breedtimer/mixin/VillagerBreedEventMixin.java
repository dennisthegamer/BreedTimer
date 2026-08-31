package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Villager courtship, read from the two events that actually describe it.
 *
 * <p>{@code VillagerMakeLove.start()} broadcasts entity event <b>18</b> to both partners exactly
 * once, and {@code tryToGiveBirth} broadcasts <b>13</b> to both when it finds no free bed and gives
 * up. Together they bracket a courtship precisely: one signal to start it, one to cancel it.
 *
 * <p>This has to sit on {@code Villager} rather than on {@code AbstractVillager} — the latter does
 * not declare {@code handleEntityEvent}, and injecting there fails to resolve.
 */
@Mixin(Villager.class)
public abstract class VillagerBreedEventMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void breedtimer$onVillagerEvent(byte id, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (!self.level().isClientSide()) return;

        // A birth, announced on the child itself: breed() broadcasts 12 to it alone, right after
        // snapTo has put it on one of its parents. This is the only event either parent is not told
        // about, and it is the one that says a courtship really produced something.
        if (self.isBaby()) {
            if (id == 12) VillagerCooldownHelper.onNewbornVillager(self);
            return;
        }

        if (id == 18) {
            VillagerCooldownHelper.onCourtshipStart(self);
        } else if (id == 13 && VillagerCooldownHelper.isCourting(self.getUUID())) {
            // Byte 13 is not exclusive to "no free bed": Villager.setLastHurtByMob broadcasts it
            // whenever a player hits a living villager. Only treat it as a failed courtship when
            // we already believe one is running -- a hit outside a courtship is then ignored, and
            // a hit during one costs at most a cooldown we would have shown optimistically.
            VillagerCooldownHelper.onCourtshipFailed(self);
        }
    }
}
