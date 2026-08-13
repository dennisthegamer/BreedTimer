package de.dennisthegamer.breedtimer.mixin;

import net.minecraft.world.entity.animal.allay.Allay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches {@code Allay.canDuplicate()}, which is private.
 *
 * <p>The value behind it is genuinely synced — the body is nothing but
 * {@code entityData.get(DATA_CAN_DUPLICATE)}, and that accessor is written by
 * {@code setDuplicationCooldown} as {@code cooldown == 0}. So the flag is exactly "ready to
 * duplicate again", broadcast to the client.
 *
 * <p>An invoker is used only because there is no public mirror, unlike the sniffer's state which
 * is reflected onto public animation fields. The countdown itself ({@code duplicationCooldown})
 * stays server-side, so the remaining time is derived from watching the flag flip instead.
 */
@Mixin(Allay.class)
public interface AllayDuplicationAccessor {

    @Invoker("canDuplicate")
    boolean breedtimer$canDuplicate();
}
