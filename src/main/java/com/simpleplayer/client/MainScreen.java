package com.simpleplayer.client;

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
 * Main screen: sidebar navigation, a content area and a persistent player bar.
 */
public class MainScreen extends Screen {

	private static final int SIDEBAR_WIDTH = 108;
	private static final int TOP_BAR = 34;
	private static final int BOTTOM_BAR = 46;
	private static final int ROW_HEIGHT = 22;
	private static final int ACCENT = 0xFFE0A458;
	private static final int PANEL = 0x99101014;
	private static final int PANEL_LIGHT = 0xCC1A1A20;

	private static final String[] NAV = {"搜索", "歌单", "登录", "设置"};

	private EditBox searchBox;
	private MusicSource source = MusicSource.NETEASE;
	private List<Track> results = List.of();
	private String status = "";
	private int selected = 0;
	private int scroll;

	public MainScreen() {
		super(Component.literal("SimplePlayer"));
	}

	public MainScreen(int selected) {
		this();
		this.selected = selected;
	}

	@Override
	protected void init() {
		searchBox = new EditBox(font, SIDEBAR_WIDTH + 14, 9, width - SIDEBAR_WIDTH - 130, 16,
				Component.literal("搜索"));
		addRenderableWidget(searchBox);
		addRenderableWidget(Button.builder(Component.literal("搜索"), button -> search())
				.bounds(width - 108, 8, 46, 18).build());
		addRenderableWidget(Button.builder(Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站"),
				button -> {
					source = source == MusicSource.NETEASE ? MusicSource.BILIBILI : MusicSource.NETEASE;
					button.setMessage(Component.literal(source == MusicSource.NETEASE ? "网易云" : "B站"));
					results = List.of();
					status = "";
				}).bounds(width - 58, 8, 46, 18).build());
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
				status = tracks.isEmpty() ? "没有结果" : "共 " + tracks.size() + " 条结果";
			});
		});
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Draw the (blurred) world background first: whatever we draw afterwards stays sharp.
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(0, 0, width, height, 0x88000000);

		// Sidebar
		graphics.fill(0, 0, SIDEBAR_WIDTH, height, PANEL);
		graphics.fill(SIDEBAR_WIDTH - 1, 0, SIDEBAR_WIDTH, height, 0x33FFFFFF);
		graphics.drawString(font, "SimplePlayer", 12, 12, ACCENT, false);
		graphics.drawString(font, "music player", 12, 24, 0xFF666666, false);

		for (int i = 0; i < NAV.length; i++) {
			int y = 48 + i * 22;
			boolean hovered = mouseX < SIDEBAR_WIDTH && mouseY >= y && mouseY < y + 20;
			if (selected == i) {
				graphics.fill(0, y, 3, y + 20, ACCENT);
				graphics.fill(3, y, SIDEBAR_WIDTH - 1, y + 20, 0x22FFFFFF);
			} else if (hovered) {
				graphics.fill(3, y, SIDEBAR_WIDTH - 1, y + 20, 0x14FFFFFF);
			}
			graphics.drawString(font, NAV[i], 16, y + 6, selected == i ? 0xFFFFFFFF : 0xFFAAAAAA, false);
		}

		String account = selectedAccount();
		graphics.drawString(font, account, 12, height - 18, 0xFF777777, false);

		// Content
		String header = switch (selected) {
			case 0 -> source == MusicSource.NETEASE ? "网易云音乐 · 搜索" : "哔哩哔哩 · 搜索";
			case 1 -> "歌单";
			case 2 -> "账号";
			default -> "设置";
		};
		graphics.drawString(font, header, SIDEBAR_WIDTH + 14, TOP_BAR + 2, 0xFFFFFFFF, false);

		if (selected == 0) {
			renderResults(graphics, mouseX, mouseY);
		} else if (selected == 1) {
			renderPlaceholder(graphics, "歌单功能还没做，先在「搜索」里听歌吧");
		} else if (selected == 2) {
			renderAccount(graphics);
		} else {
			renderPlaceholder(graphics, "设置请点左下角「设置」，或使用 Mods 列表里的 Config 按钮");
		}

		renderPlayerBar(graphics, mouseX, mouseY);

		for (var renderable : renderables) {
			renderable.render(graphics, mouseX, mouseY, partialTick);
		}
	}

	private void renderResults(GuiGraphics graphics, int mouseX, int mouseY) {
		int left = SIDEBAR_WIDTH + 10;
		int right = width - 10;
		int top = TOP_BAR + 22;
		int bottom = height - BOTTOM_BAR - 6;

		for (int i = scroll; i < results.size(); i++) {
			int y = top + (i - scroll) * ROW_HEIGHT;
			if (y + ROW_HEIGHT > bottom) {
				break;
			}
			Track track = results.get(i);
			boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
			boolean playing = track.equals(PlaybackService.INSTANCE.current());
			if (hovered) {
				graphics.fill(left, y, right, y + ROW_HEIGHT - 2, 0x22FFFFFF);
			} else if (playing) {
				graphics.fill(left, y, right, y + ROW_HEIGHT - 2, 0x22E0A458);
			}
			int titleColor = playing ? ACCENT : 0xFFFFFFFF;
			graphics.drawString(font, truncate(track.title(), right - left - 150), left + 6, y + 3, titleColor, false);
			graphics.drawString(font, truncate(track.artists(), 120), right - 130, y + 3, 0xFF999999, false);
			graphics.drawString(font, format(track.durationMillis() / 1000.0), right - 44, y + 3, 0xFF777777, false);
		}

		if (!status.isEmpty()) {
			graphics.drawString(font, status, left + 6, height - BOTTOM_BAR - 18, 0xFF888888, false);
		}
	}

	private void renderPlaceholder(GuiGraphics graphics, String text) {
		graphics.drawString(font, text, SIDEBAR_WIDTH + 24, TOP_BAR + 60, 0xFF777777, false);
	}

	private void renderAccount(GuiGraphics graphics) {
		ClientConfig config = ClientConfig.get();
		int x = SIDEBAR_WIDTH + 24;
		int y = TOP_BAR + 40;
		graphics.drawString(font, "网易云：" + (config.neteaseCookie.isBlank() ? "未登录" : "已保存 Cookie"), x, y, 0xFFCCCCCC, false);
		graphics.drawString(font, "哔哩哔哩：" + (config.bilibiliCookie.isBlank() ? "未登录" : "已保存 Cookie"), x, y + 14, 0xFFCCCCCC, false);
		graphics.drawString(font, "点左下角「登录」可以填写 Cookie", x, y + 34, 0xFF777777, false);
	}

	private void renderPlayerBar(GuiGraphics graphics, int mouseX, int mouseY) {
		int top = height - BOTTOM_BAR;
		graphics.fill(0, top, width, height, PANEL_LIGHT);
		graphics.fill(0, top, width, top + 1, 0x33FFFFFF);

		Track current = PlaybackService.INSTANCE.current();
		String title = current == null ? "没有正在播放的歌曲" : current.displayName();
		graphics.drawString(font, truncate(title, width / 2 - 120), 12, top + 8, current == null ? 0xFF777777 : 0xFFFFFFFF, false);

		double position = AudioEngine.INSTANCE.positionSeconds();
		double total = current == null ? 0 : current.durationMillis() / 1000.0;
		String time = format(position) + " / " + format(total);
		graphics.drawString(font, time, 12, top + 24, 0xFF888888, false);

		// Progress line
		int barLeft = 12;
		int barRight = width - 160;
		if (current != null && total > 0) {
			int filled = barLeft + (int) ((barRight - barLeft) * Math.min(1, position / total));
			graphics.fill(barLeft, top + 36, barRight, top + 38, 0x33FFFFFF);
			graphics.fill(barLeft, top + 36, filled, top + 38, ACCENT);
		}

		// Controls (drawn manually, clicked in mouseClicked)
		int cx = width / 2 + 40;
		int cy = top + 14;
		drawControl(graphics, cx, cy, "⏮", mouseX, mouseY);
		drawControl(graphics, cx + 30, cy, AudioEngine.INSTANCE.isPaused() ? "▶" : "⏸", mouseX, mouseY);
		drawControl(graphics, cx + 60, cy, "⏭", mouseX, mouseY);
		drawControl(graphics, cx + 96, cy, "■", mouseX, mouseY);
	}

	private void drawControl(GuiGraphics graphics, int x, int y, String label, int mouseX, int mouseY) {
		boolean hovered = mouseX >= x && mouseX < x + 24 && mouseY >= y && mouseY < y + 20;
		graphics.fill(x, y, x + 24, y + 20, hovered ? 0x33FFFFFF : 0x14FFFFFF);
		graphics.drawString(font, label, x + 7, y + 6, 0xFFFFFFFF, false);
	}

	private String selectedAccount() {
		return "";
	}

	private String truncate(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		StringBuilder builder = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (font.width(builder.toString() + c + ellipsis) > maxWidth) {
				break;
			}
			builder.append(c);
		}
		return builder + ellipsis;
	}

	private static String format(double seconds) {
		int total = (int) Math.max(0, seconds);
		return String.format("%d:%02d", total / 60, total % 60);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Sidebar navigation
		for (int i = 0; i < NAV.length; i++) {
			int y = 48 + i * 22;
			if (mouseX < SIDEBAR_WIDTH && mouseY >= y && mouseY < y + 20) {
				if (i == 3 && minecraft != null) {
					minecraft.setScreen(new SimplePlayerConfigScreen(this));
				} else if (i == 2 && minecraft != null) {
					minecraft.setScreen(new LoginScreen(this));
				} else {
					selected = i;
				}
				return true;
			}
		}

		// Player controls
		int top = height - BOTTOM_BAR;
		int cx = width / 2 + 40;
		int cy = top + 14;
		if (clicked(mouseX, mouseY, cx, cy)) {
			PlaybackService.INSTANCE.previous();
			return true;
		}
		if (clicked(mouseX, mouseY, cx + 30, cy)) {
			PlaybackService.INSTANCE.togglePause();
			return true;
		}
		if (clicked(mouseX, mouseY, cx + 60, cy)) {
			PlaybackService.INSTANCE.next();
			return true;
		}
		if (clicked(mouseX, mouseY, cx + 96, cy)) {
			PlaybackService.INSTANCE.stop();
			return true;
		}

		// Result rows
		if (selected == 0) {
			int left = SIDEBAR_WIDTH + 10;
			int right = width - 10;
			int listTop = TOP_BAR + 22;
			for (int i = scroll; i < results.size(); i++) {
				int y = listTop + (i - scroll) * ROW_HEIGHT;
				if (mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
					PlaybackService.INSTANCE.playQueue(results, i);
					return true;
				}
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	private static boolean clicked(double mouseX, double mouseY, int x, int y) {
		return mouseX >= x && mouseX < x + 24 && mouseY >= y && mouseY < y + 20;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		if (selected == 0 && !results.isEmpty()) {
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
