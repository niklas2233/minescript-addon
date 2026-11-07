package com.niklas2233.minescriptaddon.mixin;

import com.niklas2233.minescriptaddon.MinescriptWorldEvents;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every sound the client plays starts here: sounds from server packets (ClientLevel.playSeededSound and the
 * entity sound packet end in play/playDelayed), and the client's own (UI clicks, footsteps, music). Hooking the
 * packet handlers too would record those sounds twice. Recorded before the engine runs, so muted ones count.
 */
@Mixin(SoundManager.class)
abstract class SoundManagerMixin {
	@Inject(method = "play", at = @At("HEAD"))
	private void minescriptaddon$play(SoundInstance sound, CallbackInfoReturnable<?> cir) {
		record(sound);
	}

	@Inject(method = "playDelayed", at = @At("HEAD"))
	private void minescriptaddon$playDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
		record(sound);
	}

	private static void record(SoundInstance sound) {
		// The resolved volume (getVolume()) needs the sound loaded first; the requested one is in the fields.
		if (sound instanceof SoundInstanceAccessor a) MinescriptWorldEvents.onSound(sound, a.minescriptaddon$volume(), a.minescriptaddon$pitch());
		else MinescriptWorldEvents.onSound(sound, 1, 1);
	}
}
