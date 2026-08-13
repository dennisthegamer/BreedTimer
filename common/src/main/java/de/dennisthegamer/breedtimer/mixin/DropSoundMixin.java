package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.DropWindows;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches the two sounds that mark a timed drop finishing: a chicken laying an egg and an armadillo
 * shedding a scute. Both timers are server-only fields; the sound is the only trace either leaves on
 * a client.
 *
 * <p>{@code TAIL}, not {@code HEAD}, and the difference is a correctness bug rather than a style
 * choice: the method opens with {@code PacketUtils.ensureRunningOnSameThread}, which throws to
 * reschedule the packet onto the main thread. A HEAD injection would run once on the netty IO thread
 * -- touching client state off the client thread -- and then a second time for real. TAIL is
 * unambiguous here because the method compiles to exactly one {@code return}.
 *
 * <p>No {@code @At("INVOKE")} target is used, deliberately. Two calls inside this method change
 * signature within the supported range: {@code ensureRunningOnSameThread}'s third parameter becomes
 * {@code PacketProcessor} at 1.21.9, and {@code playSeededSound}'s first becomes {@code Entity} at
 * 1.21.5. TAIL is descriptor-free and survives both.
 *
 * <p>The sound is matched by {@code Identifier}, not by comparing against {@code
 * SoundEvents.CHICKEN_EGG}: the packet carries a {@code Holder<SoundEvent>}, and an id comparison is
 * what stays stable across the range.
 */
@Mixin(ClientPacketListener.class)
public abstract class DropSoundMixin {

    @Inject(
            method = "handleSoundEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;)V",
            at = @At("TAIL")
    )
    private void breedtimer$onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        DropWindows.onSound(packet);
    }
}
