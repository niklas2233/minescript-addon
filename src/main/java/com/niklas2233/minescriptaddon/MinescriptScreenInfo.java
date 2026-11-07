package com.niklas2233.minescriptaddon;

import com.niklas2233.minescriptaddon.ScreenInfo.Text;
import com.niklas2233.minescriptaddon.mixin.overlay.TabListAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;

import java.util.List;

/**
 * Sidebar scoreboard, tab list and on-screen title for screen_info.py, read on the render thread as one
 * snapshot. Empty values outside a world. Each returns ASCII-only JSON (see MinescriptChat.ascii).
 */
public class MinescriptScreenInfo {
	private static final String EMPTY_SIDEBAR = ScreenInfo.sidebarJson(Text.EMPTY, List.of());
	private static final String EMPTY_TAB_LIST = ScreenInfo.tabListJson(Text.EMPTY, Text.EMPTY, List.of());

	public static String sidebar() {
		return RenderThread.read(() -> {
			var level = Minecraft.getInstance().level;
			if (level == null) return EMPTY_SIDEBAR;
			var board = level.getScoreboard();
			// ponytail: the main sidebar slot only; per-team-color sidebars changed type in 26.3 (TeamColor)
			var objective = board.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (objective == null) return EMPTY_SIDEBAR;
			var format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
			var entries = board.listPlayerScores(objective).stream().filter(e -> !e.isHidden())
					.map(e -> new ScreenInfo.SidebarEntry(e.owner(), e.value(),
							text(PlayerTeam.formatNameForTeam(board.getPlayersTeam(e.owner()), e.ownerName())),
							MinescriptChat.plain(e.formatValue(format))))
					.toList();
			return ScreenInfo.sidebarJson(text(objective.getDisplayName()), entries);
		}, EMPTY_SIDEBAR);
	}

	public static String tabList() {
		return RenderThread.read(() -> {
			var mc = Minecraft.getInstance();
			if (mc.player == null || mc.getConnection() == null) return EMPTY_TAB_LIST;
			var tab = Compat.tabList(mc);
			var access = (TabListAccessor) tab;
			var players = access.minescriptaddon$playerInfos().stream().map(p -> new ScreenInfo.Player(
					p.getProfile().name(), text(tab.getNameForDisplay(p)), p.getLatency(),
					p.getGameMode() == null ? "" : p.getGameMode().getName())).toList();
			return ScreenInfo.tabListJson(text(access.minescriptaddon$header()), text(access.minescriptaddon$footer()), players);
		}, EMPTY_TAB_LIST);
	}

	public static String title() {
		return RenderThread.read(() -> {
			var mc = Minecraft.getInstance();
			if (mc.player == null || !(Compat.hud(mc) instanceof TitleView t)) return "null";
			return ScreenInfo.titleJson(t.minescriptaddon$title() == null ? null : text(t.minescriptaddon$title()),
					text(t.minescriptaddon$subtitle()), t.minescriptaddon$titleTime());
		}, "null");
	}

	private static Text text(Component c) {
		return c == null ? Text.EMPTY : new Text(MinescriptChat.plain(c), MinescriptChat.formatted(c));
	}
}
