package com.niklas2233.minescriptaddon.mixin;

import com.niklas2233.minescriptaddon.MinescriptKeys;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Names for the Minescript keybinds in Options -> Controls, in every language. A KeyMapping's name is also the
 * key its binding is saved under in options.txt, so the config label can't go into the name itself: the name
 * stays "key.minescriptaddon.slot.N" and its translation is supplied here instead.
 */
@Mixin(ClientLanguage.class)
abstract class ClientLanguageMixin {
	@Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
	private void minescriptaddon$getOrDefault(String key, String fallback, CallbackInfoReturnable<String> cir) {
		String name = MinescriptKeys.translate(key);
		if (name != null) cir.setReturnValue(name);
	}

	@Inject(method = "has(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
	private void minescriptaddon$has(String key, CallbackInfoReturnable<Boolean> cir) {
		if (MinescriptKeys.translate(key) != null) cir.setReturnValue(true);
	}
}
