package com.niklas2233.minescriptaddon.mixin;

import com.niklas2233.minescriptaddon.MinescriptChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every chat-box line goes through the private addMessage in 26.1–26.3 (addClientSystemMessage,
 * addServerSystemMessage and addPlayerMessage all call it), so lines other mods add are caught here.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
			+ "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), cancellable = true)
	private void minescriptaddon$addMessage(Component message, MessageSignature signature, GuiMessageSource source,
			GuiMessageTag tag, CallbackInfo ci) {
		if (MinescriptChat.chatBox(message)) ci.cancel();
	}
}
