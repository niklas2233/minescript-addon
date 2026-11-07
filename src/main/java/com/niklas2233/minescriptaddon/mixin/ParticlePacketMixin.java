package com.niklas2233.minescriptaddon.mixin;

import com.niklas2233.minescriptaddon.MinescriptWorldEvents;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particles from server packets. At RETURN, so only the render-thread run counts: on the network thread the
 * handler throws to re-run itself on the render thread and never returns.
 */
@Mixin(ClientPacketListener.class)
abstract class ParticlePacketMixin {
	@Inject(method = "handleParticleEvent", at = @At("RETURN"))
	private void minescriptaddon$particles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		MinescriptWorldEvents.onParticles(packet);
	}
}
