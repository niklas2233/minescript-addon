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
 * Titles for screen_info.title() and chat.py (sources "title"/"subtitle"). 26.2+ keeps them on Hud; 26.1.x on
 * Gui (GuiTitleMixin, identical). MixinVersionPlugin applies the one for the running version.
 */
@Mixin(targets = "net.minecraft.client.gui.Hud")
abstract class HudTitleMixin implements TitleView {
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
