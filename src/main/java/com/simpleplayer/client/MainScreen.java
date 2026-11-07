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
 * Main screen with two selectable layouts (see {@link ClientConfig.UiLayout}).
 */
public class MainScreen extends Screen {

	private record Rect(int x, int y, int w, int h, int index) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final int ACCENT = 0xFFE0A458;
	private static final int BOTTOM_BAR = 52;
	private static final String[] NAV = {"搜索", "歌单", "登录", "设置"};

	private final ClientConfig.UiLayout layout = ClientConfig.get().uiLayout;
	private EditBox searchBox;
	private MusicSource source = MusicSource.NETEASE;
	private List<Track> results = List.of();
	private String status = "";
	private int selected;
	private int scroll;

	private final List<Rect> songRects = new ArrayList<>();
	private final List<Rect> navRects = new ArrayList<>();
	private final List<Rect> controlRects = new ArrayList<>();

	public MainScreen() {
		super(Component.literal("SimplePlayer"));
	}

	@Override
	protected void init() {
		searchBox = new EditBox(font, 12, 10, width - 120, 16, Component.literal("搜索"));
		addRenderableWidget(searchBox);
		addRenderableWidget(Button.builder(Component.literal("搜索"), b -> search())
				.bounds(width - 102, 9, 44, 18).build());
		addRenderableWidget(Button.builder(Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站"), b -> {
			source = source == MusicSource.NETEASE ? MusicSource.BILIBILI : MusicSource.NETEASE;
			b.setMessage(Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站"));
			results = List.of();
			status = "";
		}).bounds(width - 54, 9, 44, 18).build());
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
				status = tracks.isEmpty() ? "没有结果" : "共 " + tracks.size() + " 条";
			});
		});
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(0, 0, width, height, 0xFF0B0B0F);
		songRects.clear();
		navRects.clear();
		controlRects.clear();

		if (layout == ClientConfig.UiLayout.YSM) {
			renderSidebar(graphics, mouseX, mouseY);
			renderGrid(graphics, mouseX, mouseY);
		} else {
			renderBanner(graphics);
			renderList(graphics, mouseX, mouseY);
		}
		renderBottomBar(graphics, mouseX, mouseY);

		for (var renderable : renderables) {
			renderable.render(graphics, mouseX, mouseY, partialTick);
		}
	}

	// ── YSM layout ──────────────────────────────────────────────────────────

	private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.fill(0, 0, 104, height, 0xEE111116);
		graphics.fill(103, 0, 104, height, 0x33FFFFFF);
		graphics.drawString(font, "SimplePlayer", 12, 12, ACCENT, false);
		graphics.drawString(font, "MUSIC", 12, 26, 0xFF55555C, false);

		for (int i = 0; i < NAV.length; i++) {
			int y = 44 + i * 24;
			Rect rect = new Rect(0, y, 104, 22, i);
			navRects.add(rect);
			boolean hovered = rect.contains(mouseX, mouseY);
			if (selected == i) {
				graphics.fill(0, y, 3, y + 22, ACCENT);
				graphics.fill(3, y, 104, y + 22, 0x1FFFFFFF);
			} else if (hovered) {
				graphics.fill(3, y, 104, y + 22, 0x12FFFFFF);
			}
			graphics.drawString(font, NAV[i], 14, y + 7, selected == i ? 0xFFFFFFFF : 0xFF9A9AA2, false);
		}
	}

	private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
		int left = 112;
		int top = 40;
		int contentWidth = width - left - 10;
		int columns = contentWidth > 380 ? 2 : 1;
		int gap = 6;
		int cardWidth = (contentWidth - gap * (columns - 1)) / columns;
		int cardHeight = 62;
		int bottom = height - BOTTOM_BAR - 10;

		graphics.drawString(font, source == MusicSource.NETEASE ? "网易云音乐" : "哔哩哔哩", left, 22, 0xFFFFFFFF, false);

		for (int i = scroll; i < results.size(); i++) {
			int slot = i - scroll;
			int col = slot % columns;
			int row = slot / columns;
			int x = left + col * (cardWidth + gap);
			int y = top + row * (cardHeight + gap);
			if (y + cardHeight > bottom) {
				break;
			}
			Rect rect = new Rect(x, y, cardWidth, cardHeight, i);
			songRects.add(rect);
			drawCard(graphics, results.get(i), rect, rect.contains(mouseX, mouseY));
		}

		if (!status.isEmpty()) {
			graphics.drawString(font, status, left, height - BOTTOM_BAR - 16, 0xFF808088, false);
		}
	}

	private void drawCard(GuiGraphics graphics, Track track, Rect r, boolean hovered) {
		boolean playing = track.equals(PlaybackService.INSTANCE.current());
		graphics.fillGradient(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), 0xFF1B1B22, 0xFF141419);
		int border = playing ? ACCENT : hovered ? 0x55FFFFFF : 0x1AFFFFFF;
		outline(graphics, r, border);

		int cover = 46;
		int cx = r.x() + 8;
		int cy = r.y() + (r.h() - cover) / 2;
		drawCover(graphics, track, cx, cy, cover);

		graphics.drawString(font, truncate(track.title(), r.w() - cover - 26), cx + cover + 8, r.y() + 14,
				playing ? ACCENT : 0xFFFFFFFF, false);
		graphics.drawString(font, truncate(track.artists(), r.w() - cover - 26), cx + cover + 8, r.y() + 27, 0xFF808088, false);
		graphics.drawString(font, format(track.durationMillis() / 1000.0), cx + cover + 8, r.y() + 40, 0xFF5A5A62, false);
	}

	// ── STREAM layout ───────────────────────────────────────────────────────

	private void renderBanner(GuiGraphics graphics) {
		Track current = PlaybackService.INSTANCE.current();
		graphics.fillGradient(0, 0, width, 92, 0xFF3A2A1C, 0xFF0B0B0F);
		graphics.drawString(font, "为你播放", 16, 12, 0x99FFFFFF, false);
		graphics.drawString(font, current == null ? "还没有播放任何歌曲" : current.title(), 16, 44, 0xFFFFFFFF, false);
		graphics.drawString(font, current == null ? "搜索并播放一首歌" : current.artists(), 16, 60, 0xCCFFFFFF, false);
	}

	private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
		int left = 12;
		int top = 100;
		int width = this.width - 24;
		int rowHeight = 44;
		int bottom = height - BOTTOM_BAR - 10;

		for (int i = scroll; i < results.size(); i++) {
			int y = top + (i - scroll) * (rowHeight + 4);
			if (y + rowHeight > bottom) {
				break;
			}
			Rect rect = new Rect(left, y, width, rowHeight, i);
			songRects.add(rect);
			Track track = results.get(i);
			boolean playing = track.equals(PlaybackService.INSTANCE.current());
			boolean hovered = rect.contains(mouseX, mouseY);
			if (hovered) {
				graphics.fill(left, y, left + width, y + rowHeight, 0x14FFFFFF);
			}
			drawCover(graphics, track, left + 4, y + 4, rowHeight - 8);
			graphics.drawString(font, truncate(track.title(), width - 140), left + rowHeight + 4, y + 8,
					playing ? ACCENT : 0xFFFFFFFF, false);
			graphics.drawString(font, truncate(track.artists(), width - 140), left + rowHeight + 4, y + 24, 0xFF808088, false);
			String time = format(track.durationMillis() / 1000.0);
			graphics.drawString(font, time, left + width - font.width(time) - 8, y + 16, 0xFF5A5A62, false);
		}

		if (!status.isEmpty()) {
			graphics.drawString(font, status, left, height - BOTTOM_BAR - 16, 0xFF808088, false);
		}
	}

	// ── Shared pieces ───────────────────────────────────────────────────────

	private void renderBottomBar(GuiGraphics graphics, int mouseX, int mouseY) {
		int top = height - BOTTOM_BAR;
		graphics.fill(0, top, width, height, 0xF2121216);
		graphics.fill(0, top, width, top + 1, 0x22FFFFFF);

		Track current = PlaybackService.INSTANCE.current();
		graphics.drawString(font, current == null ? "未在播放" : truncate(current.displayName(), width / 2 - 60), 14, top + 10,
				current == null ? 0xFF6A6A72 : 0xFFFFFFFF, false);

		double position = AudioEngine.INSTANCE.positionSeconds();
		double total = current == null ? 0 : current.durationMillis() / 1000.0;
		graphics.drawString(font, format(position) + " / " + format(total), 14, top + 26, 0xFF808088, false);

		int barLeft = 14;
		int barRight = width - 170;
		graphics.fill(barLeft, top + 38, barRight, top + 40, 0x22FFFFFF);
		if (current != null && total > 0) {
			int filled = barLeft + (int) ((barRight - barLeft) * Math.min(1, position / total));
			graphics.fill(barLeft, top + 38, filled, top + 40, ACCENT);
		}

		String[] labels = {"|<", AudioEngine.INSTANCE.isPaused() ? "继续" : "暂停", ">|", "停止"};
		int[] widths = {28, 40, 28, 40};
		int totalWidth = widths[0] + widths[1] + widths[2] + widths[3] + 18;
		int x = width - totalWidth - 12;
		int y = top + 16;
		for (int i = 0; i < 4; i++) {
			Rect rect = new Rect(x, y, widths[i], 20, i);
			controlRects.add(rect);
			boolean hovered = rect.contains(mouseX, mouseY);
			graphics.fill(x, y, x + widths[i], y + 20, hovered ? 0x33FFFFFF : 0x14FFFFFF);
			graphics.drawString(font, labels[i], x + (widths[i] - font.width(labels[i])) / 2, y + 6, 0xFFFFFFFF, false);
			x += widths[i] + 6;
		}
	}

	private void drawCover(GuiGraphics graphics, Track track, int x, int y, int size) {
		CoverCache.Cover cover = CoverCache.get(track.coverUrl());
		if (cover != null) {
			graphics.blit(cover.id(), x, y, size, size, 0F, 0F, cover.width(), cover.height(), cover.width(), cover.height());
		} else {
			graphics.fill(x, y, x + size, y + size, 0xFF26262E);
			graphics.drawString(font, "♪", x + size / 2 - 3, y + size / 2 - 4, 0x55FFFFFF, false);
		}
	}

	private static void outline(GuiGraphics graphics, Rect r, int color) {
		graphics.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, color);
		graphics.fill(r.x(), r.y() + r.h() - 1, r.x() + r.w(), r.y() + r.h(), color);
		graphics.fill(r.x(), r.y(), r.x() + 1, r.y() + r.h(), color);
		graphics.fill(r.x() + r.w() - 1, r.y(), r.x() + r.w(), r.y() + r.h(), color);
	}

	private String truncate(String text, int maxWidth) {
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

	private static String format(double seconds) {
		int total = (int) Math.max(0, seconds);
		return String.format("%d:%02d", total / 60, total % 60);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Rect rect : controlRects) {
			if (rect.contains(mouseX, mouseY)) {
				switch (rect.index()) {
					case 0 -> PlaybackService.INSTANCE.previous();
					case 1 -> PlaybackService.INSTANCE.togglePause();
					case 2 -> PlaybackService.INSTANCE.next();
					default -> PlaybackService.INSTANCE.stop();
				}
				return true;
			}
		}
		for (Rect rect : navRects) {
			if (rect.contains(mouseX, mouseY)) {
				openNav(rect.index());
				return true;
			}
		}
		for (Rect rect : songRects) {
			if (rect.contains(mouseX, mouseY)) {
				PlaybackService.INSTANCE.playQueue(results, rect.index());
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void openNav(int index) {
		if (minecraft == null) {
			return;
		}
		if (index == 2) {
			minecraft.setScreen(new LoginScreen(this));
		} else if (index == 3) {
			minecraft.setScreen(new SimplePlayerConfigScreen(this));
		} else {
			selected = index;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		if (!results.isEmpty()) {
			scroll = Math.max(0, Math.min(results.size() - 1, scroll - (int) Math.signum(deltaY)));
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
}
