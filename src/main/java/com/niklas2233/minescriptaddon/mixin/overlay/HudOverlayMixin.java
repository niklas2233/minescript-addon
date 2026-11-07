package com.niklas2233.minescriptaddon.mixin.overlay;

import com.niklas2233.minescriptaddon.MinescriptChat;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every action bar message ends in setOverlayMessage(Component, boolean): the /title actionbar packet,
 * system messages with overlay=true, and other mods. 26.2+ has it on Hud; 26.1.x on Gui (GuiOverlayMixin).
 * String targets, and MixinVersionPlugin applies only the variant for the running version (the config is
 * also non-required, so a mismatch only logs a warning).
 */
@Mixin(targets = "net.minecraft.client.gui.Hud")
abstract class HudOverlayMixin {
	@Inject(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), require = 0)
	private void minescriptaddon$setOverlayMessage(Component message, boolean animateColor, CallbackInfo ci) {
		MinescriptChat.onOverlayMessage(message);
	}
}
