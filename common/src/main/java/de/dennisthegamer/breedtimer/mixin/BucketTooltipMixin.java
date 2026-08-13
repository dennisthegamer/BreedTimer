package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.render.StatePalette;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import de.dennisthegamer.breedtimer.util.BucketedAge;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds a growth-time line to a bucketed axolotl, tadpole or (26.2) sulfur cube's tooltip -- the only exact age
 * a client-only mod can ever read, and only while the mob is still in the bucket. See {@link BucketedAge} for
 * where the number comes from.
 *
 * <p>Targets {@code ItemStack.getTooltipLines}, whose descriptor is identical on 1.21, 1.21.5, 1.21.8, 1.21.11,
 * 26.1 and 26.2. {@code addDetailsToTooltip(..., TooltipDisplay, ..., Consumer)} looks like a cleaner hook and is
 * not portable -- it does not exist before 1.21.5.
 */
@Mixin(ItemStack.class)
public abstract class BucketTooltipMixin {

    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;"
                   + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)"
                   + "Ljava/util/List;",
            at = @At("RETURN")
    )
    private void breedtimer$bucketAge(Item.TooltipContext context, Player player, TooltipFlag flag,
                                      CallbackInfoReturnable<List<Component>> cir) {
        List<Component> lines = cir.getReturnValue();
        // getTooltipLines has two return sites: the normal one hands back a Lists.newArrayList, the early
        // one (hidden tooltip, non-creative) hands back List.of() -- immutable on every version. Adding to
        // that one throws an UnsupportedOperationException.
        if (!(lines instanceof ArrayList)) return;
        ItemStack self = (ItemStack) (Object) this;
        BucketedAge.Info info = BucketedAge.read(self);
        if (!info.known()) return;

        StatePalette p = StatePalette.current();
        if (info.ageLocked()) {
            lines.add(Component.translatable("breedtimer.state.age_locked").withColor(p.inert));
        } else if (info.remainingTicks() > 0) {
            lines.add(Component.translatable("breedtimer.bucket.grows_in",
                            BreedCooldownHelper.formatTime(info.remainingTicks()))
                    .withColor(p.young));
        }
        // An adult (remainingTicks() <= 0, not locked) gets no line at all here -- controller ruling OQ4(b),
        // consistent with the adult-dolphin precedent: AgeableTracking gives an adult no label either
        // (see its class javadoc). There is deliberately no "breedtimer.bucket.adult" lang key.
    }
}
