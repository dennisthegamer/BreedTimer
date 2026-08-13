package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import de.dennisthegamer.breedtimer.util.BucketedAge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries a bucketed baby's exact age across the release, so the mob that comes out starts with a real
 * countdown instead of a fresh 20:00 guess.
 *
 * <p>The client runs {@code checkExtraContent} too. {@code BucketItem.use} calls it unconditionally once
 * {@code emptyContents} succeeds, and {@code MobBucketItem.checkExtraContent} only spawns anything when the
 * level is a {@code ServerLevel} -- so on the client it is a free notification carrying both the stack (with
 * the exact age) and the block the mob is about to appear at.
 *
 * <p>No descriptor is spelled out on the injection, even though one would be unambiguous here:
 * {@code checkExtraContent} has exactly one overload on this version and its first parameter is
 * {@code Player} -- it only widens to {@code LivingEntity} at 1.21.5, a version this branch never
 * reaches, so the bare name is enough.
 *
 * <p>{@code type} is shadowed rather than exposed through a separate accessor mixin: {@code MobBucketItem} has
 * no public getter for it, but the value is only ever needed inside this one injection, and a mixin has the
 * same field access its target class does.
 */
@Mixin(MobBucketItem.class)
public abstract class BucketReleaseMixin {

    @Shadow
    @Final
    private EntityType<? extends Mob> type;

    @Inject(method = "checkExtraContent", at = @At("HEAD"))
    private void breedtimer$onRelease(Player user, Level level, ItemStack stack, BlockPos pos,
                                      CallbackInfo ci) {
        if (!level.isClientSide()) return;
        if (user != Minecraft.getInstance().player) return;
        BucketedAge.Info info = BucketedAge.read(stack);
        if (!info.known() || info.remainingTicks() <= 0) return;
        BreedCooldownHelper.onBucketReleased(this.type, pos, info.remainingTicks());
    }
}
