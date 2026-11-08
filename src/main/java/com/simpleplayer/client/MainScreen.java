package com.simpleplayer.client;

import java.util.ArrayList;
import java.util.List;

import com.simpleplayer.audio.AudioEngine;
import com.simpleplayer.music.MusicSource;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.Track;
import com.simpleplayer.ui.Theme;
import com.simpleplayer.ui.UiScale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** MD3 shell: top app bar, navigation rail, content list and bottom player bar. */
public class MainScreen extends Screen {

	private record Hit(int x, int y, int w, int h, int kind, int index) {
		boolean inside(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private enum Tab { SEARCH, RECOMMEND }

	private UiScale scale;
	private EditBox searchBox;
	private MusicSource source = MusicSource.NETEASE;
	private List<Track> results = List.of();
	private String status = "";
	private Tab tab = Tab.SEARCH;
	private final List<Hit> hits = new ArrayList<>();

	public MainScreen() {
		super(Component.literal("SimplePlayer"));
	}

	@Override
	protected void init() {
		scale = new UiScale(width, height);
		int top = scale.dp(56);
		searchBox = new EditBox(font, scale.dp(76), top / 2 - 9, Math.max(60, width - scale.dp(240)), 18,
				Component.literal("搜索"));
		searchBox.setBordered(false);
		addRenderableWidget(searchBox);
		addRenderableWidget(Button.builder(Component.literal("搜索"), b -> search())
				.bounds(width - scale.dp(158), top / 2 - 10, scale.dp(48), 20).build());
		addRenderableWidget(Button.builder(Component.literal("推荐"), b -> recommend())
				.bounds(width - scale.dp(106), top / 2 - 10, scale.dp(44), 20).build());
		addRenderableWidget(Button.builder(Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站"), b -> {
			source = source == MusicSource.NETEASE ? MusicSource.BILIBILI : MusicSource.NETEASE;
			b.setMessage(Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站"));
			results = List.of();
		}).bounds(width - scale.dp(58), top / 2 - 10, scale.dp(48), 20).build());
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
				results = error == null ? tracks : List.of();
				status = error == null ? (tracks.isEmpty() ? "没有结果" : "") : "搜索失败";
				tab = Tab.SEARCH;
			});
		});
	}

	private void recommend() {
		status = "加载推荐…";
		Thread thread = new Thread(() -> {
			try {
				List<Track> tracks = PlaybackService.INSTANCE.netease().dailyRecommendations();
				if (minecraft != null) {
					minecraft.execute(() -> {
						results = tracks;
						status = tracks.isEmpty() ? "登录后可见每日推荐" : "";
						tab = Tab.RECOMMEND;
					});
				}
			} catch (Exception e) {
				if (minecraft != null) {
					minecraft.execute(() -> status = "推荐加载失败");
				}
			}
		}, "SimplePlayer-Recommend");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		renderBackground(g, mouseX, mouseY, partial);
		g.fill(0, 0, width, height, Theme.surface);
		hits.clear();

		int topBar = scale.dp(56);
		int rail = scale.dp(64);
		int bottom = scale.dp(72);

		// Top app bar
		g.fill(0, 0, width, topBar, Theme.surfaceContainer);
		g.drawString(font, tab == Tab.RECOMMEND ? "每日推荐" : "SimplePlayer", scale.dp(18), scale.dp(10),
				Theme.onSurface, false);
		g.drawString(font, "音乐", scale.dp(18), scale.dp(24), Theme.onSurfaceVariant, false);

		// Navigation rail
		g.fill(0, topBar, rail, height - topBar, Theme.surfaceContainerLow);
		drawRailItem(g, "搜索", 0, topBar + scale.dp(12), mouseX, mouseY, tab == Tab.SEARCH);

		drawList(g, mouseX, mouseY, rail, topBar, bottom);
		drawPlayerBar(g, mouseX, mouseY, bottom);

		for (var renderable : renderables) {
			renderable.render(g, mouseX, mouseY, partial);
		}
	}

	private void drawRailItem(GuiGraphics g, String label, int x, int y, int mouseX, int mouseY, boolean active) {
		Hit hit = new Hit(x, y, scale.dp(64), scale.dp(48), 3, 0);
		hits.add(hit);
		boolean hovered = hit.inside(mouseX, mouseY);
		if (active || hovered) {
			g.fill(x + 4, y, x + scale.dp(60), y + scale.dp(48), Theme.surfaceContainerHigh);
		}
		g.drawString(font, label, x + 12, y + scale.dp(16), active ? Theme.onPrimaryContainer : Theme.onSurfaceVariant, false);
	}

	private void drawList(GuiGraphics g, int mouseX, int mouseY, int rail, int topBar, int bottom) {
		int left = rail + scale.dp(10);
		int right = width - scale.dp(10);
		int rowHeight = scale.dp(56);
		int y = topBar + scale.dp(8);

		if (results.isEmpty()) {
			g.drawString(font, status.isEmpty() ? "搜索或获取每日推荐" : status, left + scale.dp(6), y + scale.dp(8),
					Theme.onSurfaceVariant, false);
			return;
		}
		for (int i = 0; i < results.size(); i++) {
			if (y + rowHeight > height - bottom) {
				break;
			}
			Track track = results.get(i);
			boolean playing = track.equals(PlaybackService.INSTANCE.current());
			Hit hit = new Hit(left, y, right - left, rowHeight, 1, i);
			hits.add(hit);
			boolean hovered = hit.inside(mouseX, mouseY);
			g.fill(left, y, right, y + rowHeight - 2, playing ? Theme.surfaceContainerHigh
					: hovered ? Theme.surfaceContainer : Theme.surfaceContainerLow);

			CoverCache.Cover cover = CoverCache.get(track.coverUrl());
			int coverSize = rowHeight - scale.dp(12);
			int cx = left + scale.dp(6);
			int cy = y + scale.dp(5);
			if (cover != null) {
				g.blit(cover.id(), cx, cy, coverSize, coverSize, 0F, 0F, cover.width(), cover.height(), cover.width(),
						cover.height());
			} else {
				g.fill(cx, cy, cx + coverSize, cy + coverSize, Theme.surfaceContainerHigh);
			}
			g.drawString(font, track.title(), cx + coverSize + scale.dp(8), y + scale.dp(10),
					playing ? Theme.primary : Theme.onSurface, false);
			g.drawString(font, track.artists(), cx + coverSize + scale.dp(8), y + scale.dp(26), Theme.onSurfaceVariant, false);
			y += rowHeight;
		}
	}

	private void drawPlayerBar(GuiGraphics g, int mouseX, int mouseY, int bottom) {
		int top = height - bottom;
		g.fill(0, top, width, height, Theme.surfaceContainer);
		Track current = PlaybackService.INSTANCE.current();
		g.drawString(font, current == null ? "未在播放" : current.displayName(), scale.dp(16), top + scale.dp(10),
				current == null ? Theme.onSurfaceVariant : Theme.onSurface, false);

		String[] labels = {"|<", AudioEngine.INSTANCE.isPaused() ? "▶" : "||", ">|", "■"};
		int x = width - scale.dp(200);
		int y = top + scale.dp(8);
		for (int i = 0; i < 4; i++) {
			int w = i == 1 ? scale.dp(44) : scale.dp(34);
			Hit hit = new Hit(x, y, w, scale.dp(28), 2, i);
			hits.add(hit);
			boolean hovered = hit.inside(mouseX, mouseY);
			int color = i == 1 ? (hovered ? Theme.primary : Theme.primaryContainer) : (hovered ? Theme.surfaceContainerHigh : Theme.surfaceContainerLow);
			g.fill(x, y, x + w, y + scale.dp(28), color);
			g.drawString(font, labels[i], x + (w - font.width(labels[i])) / 2, y + scale.dp(10),
					i == 1 ? Theme.onPrimaryContainer : Theme.onSurface, false);
			x += w + scale.dp(6);
		}

		double pos = AudioEngine.INSTANCE.positionSeconds();
		double total = current == null ? 0 : current.durationMillis() / 1000.0;
		g.fill(scale.dp(16), top + scale.dp(48), width - scale.dp(16), top + scale.dp(50), Theme.outline);
		if (total > 0) {
			int filled = scale.dp(16) + (int) ((width - scale.dp(32)) * Math.min(1, pos / total));
			g.fill(scale.dp(16), top + scale.dp(48), filled, top + scale.dp(50), Theme.primary);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Hit hit : hits) {
			if (!hit.inside(mouseX, mouseY)) {
				continue;
			}
			if (hit.kind() == 1) {
				PlaybackService.INSTANCE.playQueue(results, hit.index());
			} else if (hit.kind() == 2) {
				switch (hit.index()) {
					case 0 -> PlaybackService.INSTANCE.previous();
					case 1 -> PlaybackService.INSTANCE.togglePause();
					case 2 -> PlaybackService.INSTANCE.next();
					default -> PlaybackService.INSTANCE.stop();
				}
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
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
