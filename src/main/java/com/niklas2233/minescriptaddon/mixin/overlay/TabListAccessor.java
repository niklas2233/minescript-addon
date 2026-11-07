package com.niklas2233.minescriptaddon.mixin.overlay;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Tab list header/footer and players in display order (vanilla's own sort and 80-player limit), for screen_info. */
@Mixin(PlayerTabOverlay.class)
public interface TabListAccessor {
	@Accessor("header")
	Component minescriptaddon$header();

	@Accessor("footer")
	Component minescriptaddon$footer();

	@Invoker("getPlayerInfos")
	List<PlayerInfo> minescriptaddon$playerInfos();
}
