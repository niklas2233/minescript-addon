package com.niklas2233.minescriptaddon.mixin.overlay;

import com.niklas2233.minescriptaddon.MinescriptChat;
import com.niklas2233.minescriptaddon.TitleView;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1.x copy of HudTitleMixin (titles moved from Gui to Hud in 26.2).
 */
@Mixin(targets = "net.minecraft.client.gui.Gui")
abstract class GuiTitleMixin implements TitleView {
	@Shadow private Component title;
	@Shadow private Component subtitle;
	@Shadow private int titleTime;

	@Override
	public Component minescriptaddon$title() {
		return title;
	}

	@Override
	public Component minescriptaddon$subtitle() {
		return subtitle;
	}

	@Override
	public int minescriptaddon$titleTime() {
		return titleTime;
	}

	@Inject(method = "setTitle(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), require = 0)
	private void minescriptaddon$setTitle(Component message, CallbackInfo ci) {
		MinescriptChat.onTitle("title", message, title, title != null && titleTime > 0);
	}

	@Inject(method = "setSubtitle(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), require = 0)
	private void minescriptaddon$setSubtitle(Component message, CallbackInfo ci) {
		MinescriptChat.onTitle("subtitle", message, subtitle, title != null && titleTime > 0);
	}
}
