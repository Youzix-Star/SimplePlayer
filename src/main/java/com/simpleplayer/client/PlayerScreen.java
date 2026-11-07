package com.simpleplayer.client;

import java.util.List;

import com.simpleplayer.music.MusicSource;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.Track;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Minimal search-and-play screen. Deliberately plain: the polished UI comes later.
 */
public class PlayerScreen extends Screen {

	private static final int ROW_HEIGHT = 14;
	private static final int LIST_TOP = 60;

	private EditBox searchBox;
	private MusicSource source = MusicSource.NETEASE;
	private List<Track> results = List.of();
	private String status = "";
	private Button sourceButton;

	public PlayerScreen() {
		super(Component.translatable("screen.simpleplayer.title"));
	}

	@Override
	protected void init() {
		searchBox = new EditBox(font, width / 2 - 130, 32, 160, 18, Component.translatable("screen.simpleplayer.search"));
		addRenderableWidget(searchBox);

		addRenderableWidget(Button.builder(Component.translatable("screen.simpleplayer.search"), button -> search())
				.bounds(width / 2 + 36, 32, 56, 18).build());
		sourceButton = addRenderableWidget(Button.builder(sourceLabel(), button -> {
			source = source == MusicSource.NETEASE ? MusicSource.BILIBILI : MusicSource.NETEASE;
			button.setMessage(sourceLabel());
			results = List.of();
		}).bounds(width / 2 + 96, 32, 40, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.simpleplayer.stop"), button -> {
			PlaybackService.INSTANCE.stop();
			status = "";
		}).bounds(width / 2 - 30, height - 28, 60, 18).build());
	}

	private Component sourceLabel() {
		return Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站");
	}

	private void search() {
		String query = searchBox.getValue().trim();
		if (query.isEmpty()) {
			return;
		}
		status = "搜索中…";
		PlaybackService.INSTANCE.search(source, query).whenComplete((tracks, error) -> {
			if (minecraft == null) {
				return;
			}
			minecraft.execute(() -> {
				if (error != null) {
					status = "搜索失败：" + error.getMessage();
					return;
				}
				results = tracks;
				status = "找到 " + tracks.size() + " 首";
			});
		});
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);

		int y = LIST_TOP;
		for (Track track : results) {
			if (y > height - 40) {
				break;
			}
			boolean hovered = mouseX >= 30 && mouseX <= width - 30 && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2;
			if (hovered) {
				graphics.fill(30, y - 2, width - 30, y + ROW_HEIGHT - 2, 0x44FFFFFF);
			}
			graphics.drawString(font, track.displayName(), 36, y, hovered ? 0xFFFF80 : 0xFFFFFF);
			y += ROW_HEIGHT;
		}

		Track current = PlaybackService.INSTANCE.current();
		String footer = current == null ? status : "正在播放：" + current.displayName();
		graphics.drawString(font, footer, 12, height - 18, 0xAAAAAA);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int y = LIST_TOP;
		for (Track track : results) {
			if (mouseX >= 30 && mouseX <= width - 30 && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2) {
				PlaybackService.INSTANCE.play(track);
				return true;
			}
			y += ROW_HEIGHT;
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
