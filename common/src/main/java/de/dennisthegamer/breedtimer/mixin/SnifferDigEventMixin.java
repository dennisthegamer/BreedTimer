package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches entity event 63 — a sniffer starting to dig.
 *
 * <p>This is the one entity event in the mod that cannot be caught on the entity. {@code
 * ClientPacketListener.handleEntityEvent} switches on the event id and case 63 plays a {@code
 * SnifferSoundInstance} and returns; only the {@code default} arm forwards to {@code
 * Entity.handleEntityEvent}. {@code Sniffer} does not override that method at all, so nothing on the
 * entity side ever sees a 63.
 *
 * <p>{@code TAIL}, not {@code HEAD}, and the difference matters: the method opens with {@code
 * PacketUtils.ensureRunningOnSameThread}, which throws to reschedule the packet onto the main
 * thread. A HEAD injection would therefore run once on the netty thread — touching client state off
 * the client thread — and then a second time for real. TAIL is unambiguous here because the method
 * compiles to exactly one {@code return}.
 *
 * <p>The descriptor is spelled out rather than left as a bare method name. Loom only remaps fully
 * qualified member references, and on the intermediary-mapped branches a bare name would ship as a
 * literal string the injector cannot resolve — with {@code defaultRequire: 1} that is a startup
 * crash, not a warning.
 */
@Mixin(ClientPacketListener.class)
public abstract class SnifferDigEventMixin {

    @Inject(
            method = "handleEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;)V",
            at = @At("TAIL")
    )
    private void breedtimer$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != 63) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        if (packet.getEntity(level) instanceof Sniffer sniffer) {
            BreedCooldownHelper.onSnifferDiggingStarted(sniffer);
        }
    }
}
