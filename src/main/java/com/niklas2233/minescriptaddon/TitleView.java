package com.niklas2233.minescriptaddon;

import net.minecraft.network.chat.Component;

/** The on-screen title, implemented by the title mixins on Hud (26.2+) or Gui (26.1.x). */
public interface TitleView {
	Component minescriptaddon$title();

	Component minescriptaddon$subtitle();

	/** Ticks left including fade-out; the title shows while > 0. */
	int minescriptaddon$titleTime();
}
