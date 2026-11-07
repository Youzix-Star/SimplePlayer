package com.simpleplayer.client;

import java.util.List;

import com.simpleplayer.audio.AudioEngine;
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
	private Button pauseButton;

	public PlayerScreen() {
		super(Component.translatable("screen.simpleplayer.title"));
	}

	@Override
	protected void init() {
		searchBox = new EditBox(font, width / 2 - 150, 32, 150, 18, Component.translatable("screen.simpleplayer.search"));
		addRenderableWidget(searchBox);

		addRenderableWidget(Button.builder(Component.translatable("screen.simpleplayer.search"), button -> search())
				.bounds(width / 2 + 4, 32, 50, 18).build());
		addRenderableWidget(Button.builder(sourceLabel(), button -> {
			source = source == MusicSource.NETEASE ? MusicSource.BILIBILI : MusicSource.NETEASE;
			button.setMessage(sourceLabel());
			results = List.of();
		}).bounds(width / 2 + 58, 32, 50, 18).build());

		int bottom = height - 26;
		addRenderableWidget(Button.builder(Component.literal("⏮"), button -> PlaybackService.INSTANCE.previous())
				.bounds(width / 2 - 60, bottom, 30, 18).build());
		pauseButton = addRenderableWidget(Button.builder(pauseLabel(), button -> {
			PlaybackService.INSTANCE.togglePause();
			button.setMessage(pauseLabel());
		}).bounds(width / 2 - 26, bottom, 52, 18).build());
		addRenderableWidget(Button.builder(Component.literal("⏭"), button -> PlaybackService.INSTANCE.next())
				.bounds(width / 2 + 30, bottom, 30, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.simpleplayer.stop"), button -> {
			PlaybackService.INSTANCE.stop();
			status = "";
		}).bounds(width / 2 + 66, bottom, 44, 18).build());
	}

	private Component sourceLabel() {
		return Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站");
	}

	private Component pauseLabel() {
		return Component.literal(AudioEngine.INSTANCE.isPaused() ? "▶" : "⏸");
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
				status = "找到 " + tracks.size() + " 首，点击播放";
			});
		});
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);

		int y = LIST_TOP;
		for (int i = 0; i < results.size(); i++) {
			if (y > height - 60) {
				break;
			}
			Track track = results.get(i);
			boolean hovered = mouseX >= 30 && mouseX <= width - 30 && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2;
			boolean playing = track.equals(PlaybackService.INSTANCE.current());
			int color = playing ? 0x55FF88 : hovered ? 0xFFFF80 : 0xFFFFFF;
			if (hovered || playing) {
				graphics.fill(30, y - 2, width - 30, y + ROW_HEIGHT - 2, 0x33FFFFFF);
			}
			graphics.drawString(font, track.displayName(), 36, y, color);
			y += ROW_HEIGHT;
		}

		if (pauseButton != null) {
			pauseButton.setMessage(pauseLabel());
		}
		Track current = PlaybackService.INSTANCE.current();
		String footer = current == null ? status : current.displayName() + "   " + timestamp();
		graphics.drawString(font, footer, 12, height - 44, 0xAAAAAA);
	}

	private String timestamp() {
		double position = AudioEngine.INSTANCE.positionSeconds();
		Track current = PlaybackService.INSTANCE.current();
		double total = current == null ? 0 : current.durationMillis() / 1000.0;
		return format(position) + " / " + format(total);
	}

	private static String format(double seconds) {
		int total = (int) Math.max(0, seconds);
		return String.format("%d:%02d", total / 60, total % 60);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int y = LIST_TOP;
		for (int i = 0; i < results.size(); i++) {
			if (mouseX >= 30 && mouseX <= width - 30 && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2) {
				PlaybackService.INSTANCE.playQueue(results, i);
				return true;
			}
			y += ROW_HEIGHT;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchBox != null && searchBox.isFocused() && keyCode == 257) {
			search();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
