package com.niklas2233.minescriptaddon.mixin.overlay;

import com.niklas2233.minescriptaddon.MinescriptChat;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.1.x: setOverlayMessage is on Gui (moved to Hud in 26.2, see HudOverlayMixin). No-op on 26.2+. */
@Mixin(targets = "net.minecraft.client.gui.Gui")
abstract class GuiOverlayMixin {
	@Inject(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), require = 0)
	private void minescriptaddon$setOverlayMessage(Component message, boolean animateColor, CallbackInfo ci) {
		MinescriptChat.onOverlayMessage(message);
	}
}
