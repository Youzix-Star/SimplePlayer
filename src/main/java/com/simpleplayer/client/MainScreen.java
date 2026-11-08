package com.simpleplayer.client;

import java.util.ArrayList;
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
 * YSM-style browsing panel: dark square panel, card grid and a page footer.
 */
public class MainScreen extends Screen {

	private record Hit(int x, int y, int w, int h, int kind, int index) {
		boolean inside(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final int PANEL_BG = 0xF2262626;
	private static final int HEADER_BG = 0xFF3A3A3A;
	private static final int CARD_BG = 0xFF323232;
	private static final int LABEL_BG = 0xFF4A4A4A;
	private static final int BORDER = 0xFF5A5A5A;
	private static final int TEXT = 0xFFE6E6E6;
	private static final int TEXT_DIM = 0xFFA0A0A0;
	private static final int ACCENT = 0xFFFFC46B;

	private static final int PAGE_SIZE = 9;

	private EditBox searchBox;
	private MusicSource source = MusicSource.NETEASE;
	private List<Track> results = List.of();
	private String status = "";
	private int page;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private final List<Hit> hits = new ArrayList<>();

	public MainScreen() {
		super(Component.literal("SimplePlayer"));
	}

	@Override
	protected void init() {
		panelW = Math.min(width - 24, 640);
		panelH = Math.min(height - 24, 420);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
		searchBox = new EditBox(font, panelX + 16, panelY + 10, panelW - 120, 16, Component.literal("搜索"));
		searchBox.setBordered(false);
		addRenderableWidget(searchBox);
		addRenderableWidget(Button.builder(Component.literal("搜索"), b -> search())
				.bounds(panelX + panelW - 92, panelY + 8, 40, 20).build());
		addRenderableWidget(Button.builder(Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站"), b -> {
			source = source == MusicSource.NETEASE ? MusicSource.BILIBILI : MusicSource.NETEASE;
			b.setMessage(Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站"));
			results = List.of();
			page = 0;
			status = "";
		}).bounds(panelX + panelW - 48, panelY + 8, 40, 20).build());
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
				results = error != null ? List.of() : tracks;
				status = error != null ? "搜索失败" : "";
				page = 0;
			});
		});
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		renderBackground(g, mouseX, mouseY, partial);
		g.fill(0, 0, width, height, 0x66000000);
		hits.clear();

		// Panel
		g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
		border(g, panelX, panelY, panelW, panelH, BORDER);
		g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 34, HEADER_BG);
		g.fill(panelX + 1, panelY + 34, panelX + panelW - 1, panelY + 35, BORDER);

		// Search field background
		g.fill(panelX + 12, panelY + 6, panelX + panelW - 96, panelY + 28, 0xFF1E1E1E);
		border(g, panelX + 12, panelY + 6, panelW - 108, 22, BORDER);

		drawGrid(g, mouseX, mouseY);
		drawFooter(g, mouseX, mouseY);
		drawPlayerBar(g, mouseX, mouseY);

		for (var renderable : renderables) {
			renderable.render(g, mouseX, mouseY, partial);
		}
	}

	private void drawGrid(GuiGraphics g, int mouseX, int mouseY) {
		int columns = 3;
		int gap = 8;
		int gridLeft = panelX + 12;
		int gridTop = panelY + 42;
		int gridWidth = panelW - 24;
		int cardWidth = (gridWidth - gap * (columns - 1)) / columns;
		int cardHeight = Math.min(112, (panelH - 120) / 2);
		int start = page * PAGE_SIZE;

		for (int i = start; i < Math.min(results.size(), start + PAGE_SIZE); i++) {
			int slot = i - start;
			int col = slot % columns;
			int row = slot / columns;
			int x = gridLeft + col * (cardWidth + gap);
			int y = gridTop + row * (cardHeight + gap + 14);
			if (y + cardHeight + 14 > panelY + panelH - 44) {
				break;
			}
			Track track = results.get(i);
			boolean playing = track.equals(PlaybackService.INSTANCE.current());
			Hit hit = new Hit(x, y, cardWidth, cardHeight + 14, 1, i);
			hits.add(hit);
			boolean hovered = hit.inside(mouseX, mouseY);

			g.fill(x, y, x + cardWidth, y + cardHeight, CARD_BG);
			CoverCache.Cover cover = CoverCache.get(track.coverUrl());
			if (cover != null) {
				g.blit(cover.id(), x + 1, y + 1, cardWidth - 2, cardHeight - 2, 0F, 0F, cover.width(), cover.height(),
						cover.width(), cover.height());
			} else {
				g.fill(x + 1, y + 1, x + cardWidth - 1, y + cardHeight - 1, 0xFF3C3C3C);
			}
			int labelColor = playing ? 0xFFD0A050 : LABEL_BG;
			g.fill(x, y + cardHeight, x + cardWidth, y + cardHeight + 14, labelColor);
			g.drawString(font, trim(track.title(), cardWidth - 8), x + 4, y + cardHeight + 3,
					playing ? 0xFFFFFFFF : TEXT, false);
			if (hovered) {
				border(g, x, y, cardWidth, cardHeight + 14, 0xFFEDEDED);
			} else if (playing) {
				border(g, x, y, cardWidth, cardHeight + 14, ACCENT);
			}
		}

		if (results.isEmpty()) {
			g.drawString(font, status.isEmpty() ? "搜索音乐开始播放" : status, gridLeft + 4, gridTop + 8, TEXT_DIM, false);
		}
	}

	private void drawFooter(GuiGraphics g, int mouseX, int mouseY) {
		int y = panelY + panelH - 36;
		int pages = Math.max(1, (results.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int centerX = panelX + panelW / 2;

		drawButton(g, centerX - 96, y, 60, 18, "上一页", mouseX, mouseY, 0, -1);
		drawButton(g, centerX + 36, y, 60, 18, "下一页", mouseX, mouseY, 0, 1);
		String label = (page + 1) + " / " + pages;
		g.drawString(font, label, centerX - font.width(label) / 2, y + 5, TEXT_DIM, false);
	}

	private void drawPlayerBar(GuiGraphics g, int mouseX, int mouseY) {
		int barTop = panelY + panelH - 16;
		int textY = barTop - 14;
		Track current = PlaybackService.INSTANCE.current();
		String title = current == null ? "未在播放" : current.displayName();
		g.drawString(font, trim(title, panelW - 160), panelX + 12, textY, current == null ? TEXT_DIM : TEXT, false);

		String[] labels = {"|<", AudioEngine.INSTANCE.isPaused() ? ">" : "||", ">|", "[]"};
		int x = panelX + panelW - 112;
		for (int i = 0; i < 4; i++) {
			drawButton(g, x, textY - 4, 22, 16, labels[i], mouseX, mouseY, 2, i);
			x += 26;
		}

		double position = AudioEngine.INSTANCE.positionSeconds();
		double total = current == null ? 0 : current.durationMillis() / 1000.0;
		g.fill(panelX + 1, panelY + panelH - 3, panelX + panelW - 1, panelY + panelH - 1, 0xFF3A3A3A);
		if (total > 0) {
			int filled = panelX + 1 + (int) ((panelW - 2) * Math.min(1, position / total));
			g.fill(panelX + 1, panelY + panelH - 3, filled, panelY + panelH - 1, ACCENT);
		}
	}

	private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int mouseX, int mouseY, int kind, int index) {
		Hit hit = new Hit(x, y, w, h, kind, index);
		hits.add(hit);
		boolean hovered = hit.inside(mouseX, mouseY);
		g.fill(x, y, x + w, y + h, hovered ? 0xFF5C5C5C : 0xFF3C3C3C);
		border(g, x, y, w, h, hovered ? 0xFFEDEDED : BORDER);
		g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, TEXT, false);
	}

	private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Hit hit : hits) {
			if (!hit.inside(mouseX, mouseY)) {
				continue;
			}
			if (hit.kind() == 0) {
				int pages = Math.max(1, (results.size() + PAGE_SIZE - 1) / PAGE_SIZE);
				page = Math.max(0, Math.min(pages - 1, page + hit.index()));
			} else if (hit.kind() == 1) {
				PlaybackService.INSTANCE.playQueue(results, hit.index());
			} else {
				switch (hit.index()) {
					case 0 -> PlaybackService.INSTANCE.previous();
					case 1 -> PlaybackService.INSTANCE.togglePause();
					case 2 -> PlaybackService.INSTANCE.next();
					default -> PlaybackService.INSTANCE.stop();
				}
			}
			return true;
		}
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchBox != null && searchBox.isFocused() && keyCode == 257) {
			search();
			return true;
		}
		if (keyCode == 81) { // Q toggles login/config? keep simple: ESC handled by screen
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
}
