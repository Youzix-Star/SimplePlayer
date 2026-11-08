package com.simpleplayer.client;

import java.util.ArrayList;
import java.util.List;

import com.simpleplayer.audio.AudioEngine;
import com.simpleplayer.config.ClientConfig;
import com.simpleplayer.music.MusicSource;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.Track;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Material 3 style player: navigation rail, list surface and a bottom player sheet.
 */
public class MainScreen extends Screen {

	private record Hit(int x, int y, int w, int h, int kind, int index) {
		boolean inside(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final int RAIL = 74;
	private static final int SHEET = 86;
	private static final String[] NAV = {"搜索", "队列", "登录", "设置"};

	private EditBox searchBox;
	private MusicSource source = MusicSource.NETEASE;
	private List<Track> results = List.of();
	private String status = "";
	private int tab;
	private int scroll;

	private final List<Hit> hits = new ArrayList<>();

	public MainScreen() {
		super(Component.literal("SimplePlayer"));
	}

	@Override
	protected void init() {
		searchBox = new EditBox(font, RAIL + 16, 22, width - RAIL - 128, 18, Component.literal("搜索"));
		searchBox.setBordered(false);
		addRenderableWidget(searchBox);
		addRenderableWidget(Button.builder(Component.literal("搜索"), b -> search())
				.bounds(width - 106, 18, 48, 24).build());
		addRenderableWidget(Button.builder(Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站"), b -> {
			source = source == MusicSource.NETEASE ? MusicSource.BILIBILI : MusicSource.NETEASE;
			b.setMessage(Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站"));
			results = List.of();
		}).bounds(width - 54, 18, 44, 24).build());
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
				scroll = 0;
				tab = 0;
				status = tracks.isEmpty() ? "没有结果" : "共 " + tracks.size() + " 条";
			});
		});
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		renderBackground(g, mouseX, mouseY, partial);
		g.fill(0, 0, width, height, Md3.SURFACE);
		hits.clear();

		drawRail(g, mouseX, mouseY);
		drawTopBar(g);
		if (tab == 1) {
			drawList(g, PlaybackService.INSTANCE.queue(), mouseX, mouseY, true);
		} else {
			drawList(g, results, mouseX, mouseY, false);
		}
		drawSheet(g, mouseX, mouseY);

		for (var renderable : renderables) {
			renderable.render(g, mouseX, mouseY, partial);
		}
	}

	private void drawTopBar(GuiGraphics g) {
		g.fill(RAIL, 0, width, 56, Md3.SURFACE_CONTAINER);
		Md3.text(g, font, tab == 1 ? "播放队列" : (source == MusicSource.NETEASE ? "网易云音乐" : "哔哩哔哩"),
				RAIL + 16, 52, Md3.ON_SURFACE_VARIANT, 1F);
		Md3.roundRect(g, RAIL + 12, 20, width - RAIL - 132, 22, 11, Md3.SURFACE_CONTAINER_HIGH);
	}

	private void drawRail(GuiGraphics g, int mouseX, int mouseY) {
		g.fill(0, 0, RAIL, height, Md3.SURFACE_CONTAINER);
		Md3.roundRect(g, 14, 16, 46, 46, 14, Md3.PRIMARY_CONTAINER);
		Md3.text(g, font, "SP", 26, 33, Md3.ON_PRIMARY_CONTAINER, 1F);

		for (int i = 0; i < NAV.length; i++) {
			int y = 84 + i * 52;
			boolean active = tab == i;
			Hit hit = new Hit(0, y, RAIL, 48, 0, i);
			hits.add(hit);
			if (active || hit.inside(mouseX, mouseY)) {
				Md3.roundRect(g, 9, y, 56, 48, 14, active ? Md3.SECONDARY_CONTAINER : Md3.SURFACE_CONTAINER_HIGH);
			}
			g.drawString(font, NAV[i], 15, y + 28, active ? Md3.ON_PRIMARY_CONTAINER : Md3.ON_SURFACE_VARIANT, false);
			Md3.roundRect(g, 24, y + 8, 26, 16, 6, active ? Md3.PRIMARY : Md3.OUTLINE);
		}
	}

	private void drawList(GuiGraphics g, List<Track> tracks, int mouseX, int mouseY, boolean queue) {
		int left = RAIL + 12;
		int right = width - 12;
		int top = 64;
		int rowHeight = 56;
		int bottom = height - SHEET - 8;

		if (tracks.isEmpty()) {
			g.drawString(font, queue ? "队列是空的" : (status.isEmpty() ? "搜索一首歌开始" : status),
					left + 8, top + 16, Md3.ON_SURFACE_VARIANT, false);
			return;
		}

		for (int i = scroll; i < tracks.size(); i++) {
			int y = top + (i - scroll) * (rowHeight + 6);
			if (y + rowHeight > bottom) {
				break;
			}
			Track track = tracks.get(i);
			boolean playing = track.equals(PlaybackService.INSTANCE.current());
			Hit hit = new Hit(left, y, right - left, rowHeight, 1, i);
			hits.add(hit);
			boolean hovered = hit.inside(mouseX, mouseY);
			Md3.roundRect(g, left, y, right - left, rowHeight, 14, playing ? Md3.SECONDARY_CONTAINER
					: hovered ? Md3.SURFACE_CONTAINER_HIGH : Md3.SURFACE_CONTAINER);

			int cover = 40;
			int cx = left + 8;
			int cy = y + 8;
			CoverCache.Cover art = CoverCache.get(track.coverUrl());
			if (art != null) {
				g.blit(art.id(), cx, cy, cover, cover, 0F, 0F, art.width(), art.height(), art.width(), art.height());
			} else {
				Md3.roundRect(g, cx, cy, cover, cover, 10, Md3.SURFACE_CONTAINER_HIGH);
				g.drawString(font, "♪", cx + 16, cy + 16, Md3.ON_SURFACE_VARIANT, false);
			}
			g.drawString(font, trim(track.title(), right - left - cover - 60), cx + cover + 10, y + 12,
					playing ? Md3.PRIMARY : Md3.ON_SURFACE, false);
			g.drawString(font, trim(track.artists(), right - left - cover - 60), cx + cover + 10, y + 30,
					Md3.ON_SURFACE_VARIANT, false);
			String time = fmt(track.durationMillis() / 1000.0);
			g.drawString(font, time, right - font.width(time) - 12, y + 22, Md3.ON_SURFACE_VARIANT, false);
		}
	}

	private void drawSheet(GuiGraphics g, int mouseX, int mouseY) {
		int top = height - SHEET;
		Md3.roundRect(g, 0, top, width, SHEET + 16, 22, Md3.SURFACE_CONTAINER_HIGH);

		Track current = PlaybackService.INSTANCE.current();
		int cover = 52;
		int cx = 14;
		int cy = top + 10;
		CoverCache.Cover art = current == null ? null : CoverCache.get(current.coverUrl());
		if (art != null) {
			g.blit(art.id(), cx, cy, cover, cover, 0F, 0F, art.width(), art.height(), art.width(), art.height());
		} else {
			Md3.roundRect(g, cx, cy, cover, cover, 14, Md3.SURFACE_CONTAINER);
			g.drawString(font, "♪", cx + 22, cy + 22, Md3.ON_SURFACE_VARIANT, false);
		}

		int textX = cx + cover + 12;
		int textMax = width - 250 - textX;
		g.drawString(font, current == null ? "未在播放" : trim(current.title(), Math.max(40, textMax)),
				textX, top + 14, current == null ? Md3.ON_SURFACE_VARIANT : Md3.ON_SURFACE, false);
		g.drawString(font, current == null ? "选一首歌开始" : trim(current.artists(), Math.max(40, textMax)),
				textX, top + 30, Md3.ON_SURFACE_VARIANT, false);

		double position = AudioEngine.INSTANCE.positionSeconds();
		double total = current == null ? 0 : current.durationMillis() / 1000.0;
		int barLeft = textX;
		int barRight = Math.max(barLeft + 40, width - 250);
		g.fill(barLeft, top + 48, barRight, top + 50, Md3.OUTLINE);
		if (total > 0) {
			g.fill(barLeft, top + 48, barLeft + (int) ((barRight - barLeft) * Math.min(1, position / total)), top + 50, Md3.PRIMARY);
		}
		g.drawString(font, fmt(position) + " / " + fmt(total), barLeft, top + 58, Md3.ON_SURFACE_VARIANT, false);

		String[] labels = {"<", AudioEngine.INSTANCE.isPaused() ? "▶" : "||", ">", "■"};
		int[] widths = {30, 40, 30, 30};
		int totalWidth = 178;
		int x = width - totalWidth - 16;
		int y = top + 30;
		for (int i = 0; i < 4; i++) {
			Hit hit = new Hit(x, y, widths[i], 30, 2, i);
			hits.add(hit);
			boolean hovered = hit.inside(mouseX, mouseY);
			int color = i == 1 ? (hovered ? Md3.PRIMARY : Md3.PRIMARY_CONTAINER) : (hovered ? Md3.SURFACE_CONTAINER_HIGH : Md3.SURFACE_CONTAINER);
			Md3.pill(g, x, y, widths[i], 30, color, labels[i], i == 1 ? Md3.ON_PRIMARY : Md3.ON_SURFACE, font);
			x += widths[i] + 6;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Hit hit : hits) {
			if (!hit.inside(mouseX, mouseY)) {
				continue;
			}
			switch (hit.kind()) {
				case 0 -> nav(hit.index());
				case 1 -> {
					List<Track> list = tab == 1 ? PlaybackService.INSTANCE.queue() : results;
					if (hit.index() < list.size()) {
						PlaybackService.INSTANCE.playQueue(list, hit.index());
					}
				}
				default -> {
					switch (hit.index()) {
						case 0 -> PlaybackService.INSTANCE.previous();
						case 1 -> PlaybackService.INSTANCE.togglePause();
						case 2 -> PlaybackService.INSTANCE.next();
						default -> PlaybackService.INSTANCE.stop();
					}
				}
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void nav(int index) {
		if (minecraft == null) {
			return;
		}
		if (index == 2) {
			minecraft.setScreen(new LoginScreen(this));
		} else if (index == 3) {
			minecraft.setScreen(new SimplePlayerConfigScreen(this));
		} else {
			tab = index;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		List<Track> list = tab == 1 ? PlaybackService.INSTANCE.queue() : results;
		if (!list.isEmpty()) {
			scroll = Math.max(0, Math.min(list.size() - 1, scroll - (int) Math.signum(deltaY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
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

	private String trim(String text, int maxWidth) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		StringBuilder builder = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (font.width(builder.toString() + c + "…") > maxWidth) {
				break;
			}
			builder.append(c);
		}
		return builder + "…";
	}

	private static String fmt(double seconds) {
		int total = (int) Math.max(0, seconds);
		return String.format("%d:%02d", total / 60, total % 60);
	}
}
