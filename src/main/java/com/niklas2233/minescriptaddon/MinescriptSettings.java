package com.niklas2233.minescriptaddon;

import com.google.gson.JsonElement;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** /msettings &lt;script&gt;: an editor for a settings.py file. Scripts see saved edits through changed()/reload(). */
public class MinescriptSettings implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("minescriptaddon");
	static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("minescriptaddon-settings");
	private static Screen pending; // opened next tick: the chat screen the command was typed in is still closing

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
				ClientCommands.literal("msettings").then(ClientCommands.argument("script", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Settings.names(DIR), b))
						.executes(c -> open(c.getSource(), StringArgumentType.getString(c, "script"))))));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (pending != null) Compat.setScreen(mc, pending);
			pending = null;
		});
	}

	private static int open(FabricClientCommandSource source, String name) {
		try {
			Path file = Settings.file(DIR, name);
			pending = new Editor(name, file, Settings.parse(Files.readString(file)));
			return 1;
		} catch (NoSuchFileException e) {
			source.sendError(Component.literal("No settings for " + name + " yet; a script creates them with settings.load(\"" + name + "\", ...)"));
		} catch (Exception e) {
			source.sendError(Component.literal("Can't open settings for " + name + ": " + e.getMessage()));
		}
		return 0;
	}

	static final class Editor extends Screen {
		private static final int OK = 0xFFE0E0E0, BAD = 0xFFFF5555;
		private final Path file;
		private final List<Settings.Entry> entries;
		private final Map<String, String> text = new HashMap<>(); // survives re-init on resize
		private final Map<String, EditBox> boxes = new HashMap<>();
		private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
		private ScrollableLayout scroll;
		private Button save;

		Editor(String name, Path file, List<Settings.Entry> entries) {
			super(Component.literal("Settings: " + name));
			this.file = file;
			this.entries = entries;
			entries.forEach(e -> text.put(e.key(), e.text()));
		}

		@Override
		protected void init() {
			boxes.clear();
			layout.addTitleHeader(title, font);
			var grid = new GridLayout().columnSpacing(8).rowSpacing(4);
			int row = 0;
			for (var e : entries) {
				grid.addChild(new StringWidget(Component.literal(e.key()), font).setMaxWidth(140), row, 0, s -> s.alignVerticallyMiddle());
				grid.addChild(field(e), row++, 1);
			}
			if (entries.isEmpty()) grid.addChild(new StringWidget(Component.literal("This file has no settings."), font), 0, 0);
			scroll = layout.addToContents(new ScrollableLayout(minecraft, grid, height));
			var footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
			save = footer.addChild(Button.builder(Component.literal("Save"), b -> save()).width(100).build());
			footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).width(100).build());
			layout.visitWidgets(this::addRenderableWidget);
			repositionElements();
			validate();
		}

		private net.minecraft.client.gui.layouts.LayoutElement field(Settings.Entry e) {
			String key = e.key();
			switch (e.kind()) {
				case BOOL -> {
					return CycleButton.onOffBuilder(Boolean.parseBoolean(text.get(key))).displayOnlyValue()
							.create(0, 0, 160, 20, Component.literal(key), (b, v) -> text.put(key, v.toString()));
				}
				case OTHER -> {
					return new StringWidget(160, 20, Component.literal("(not editable here)"), font);
				}
				default -> {
					var box = new EditBox(font, 0, 0, 160, 20, Component.literal(key));
					box.setMaxLength(10_000);
					box.setValue(text.get(key));
					if (e.kind() == Settings.Kind.LIST) box.setHint(Component.literal("comma-separated"));
					box.setResponder(v -> {
						text.put(key, v);
						validate();
					});
					boxes.put(key, box);
					return box;
				}
			}
		}

		/** Marks invalid fields red (with the reason as a tooltip) and disables Save while any is. */
		private void validate() {
			boolean ok = true;
			for (var e : entries) {
				var box = boxes.get(e.key());
				if (box == null) continue;
				String error = null;
				try {
					e.read(text.get(e.key()));
				} catch (IllegalArgumentException ex) {
					error = ex.getMessage();
					ok = false;
				}
				box.setTextColor(error == null ? OK : BAD);
				box.setTooltip(error == null ? null : Tooltip.create(Component.literal(error)));
			}
			if (save != null) save.active = ok;
		}

		private void save() {
			Map<String, JsonElement> changes = new LinkedHashMap<>();
			try {
				for (var e : entries) if (e.kind() != Settings.Kind.OTHER) changes.put(e.key(), e.read(text.get(e.key())));
				Settings.save(file, changes);
				onClose();
			} catch (Exception ex) {
				LOG.warn("Couldn't save {}", file, ex);
				save.setTooltip(Tooltip.create(Component.literal("Couldn't save: " + ex.getMessage())));
			}
		}

		@Override
		protected void repositionElements() {
			layout.arrangeElements(); // the content height is only known once the header and footer are placed
			scroll.setMinHeight(layout.getContentHeight());
			scroll.setMaxHeight(layout.getContentHeight());
			layout.arrangeElements();
		}
	}
}
