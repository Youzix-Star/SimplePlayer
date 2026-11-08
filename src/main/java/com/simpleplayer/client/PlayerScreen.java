package com.simpleplayer.client;

import java.util.ArrayList;
import java.util.List;

import com.simpleplayer.audio.AudioEngine;
import com.simpleplayer.client.panel.PlayerPanel;
import com.simpleplayer.client.panel.PlaylistPanel;
import com.simpleplayer.client.panel.RecommendPanel;
import com.simpleplayer.client.panel.SearchPanel;
import com.simpleplayer.client.sheet.BottomSheet;
import com.simpleplayer.client.sheet.LoginSheet;
import com.simpleplayer.client.sheet.QueueSheet;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.Track;
import com.simpleplayer.ui.Hit;
import com.simpleplayer.ui.Md3;
import com.simpleplayer.ui.Theme;
import com.simpleplayer.ui.UiScale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Player shell. Mirrors Muonium's NCMScreen/NcmSheet split: the screen owns the navigation rail,
 * app bar, mini player and modal sheets, while each page is a {@link PlayerPanel} and every overlay
 * is a {@link BottomSheet}. All colours come from {@link Theme}, which is retinted from the current
 * cover by {@link #applyCoverTheme()}.
 */
public final class PlayerScreen extends Screen {

	private enum Tab {
		SEARCH,
		RECOMMEND,
		PLAYLIST
	}

	private final List<Hit> hits = new ArrayList<>();

	private UiScale scale;
	private boolean railMode;
	private boolean compactChrome;
	private int topBarHeight;
	private int playerBarHeight;
	private int navHeight;
	private int railWidth;
	private int contentX;
	private int contentY;
	private int contentWidth;
	private int contentHeight;
	private int playerBarY;
	private int navY;

	private Tab tab = Tab.SEARCH;
	private SearchPanel searchPanel;
	private RecommendPanel recommendPanel;
	private PlaylistPanel playlistPanel;
	private PlayerPanel activePanel;

	private BottomSheet sheet;
	private QueueSheet queueSheet;
	private LoginSheet loginSheet;

	private String lastCoverUrl;
	private String accountName;
	private boolean accountLoading;
	private String toast;
	private long toastUntil;

	public PlayerScreen() {
		super(Component.translatable("screen.simpleplayer.title"));
	}

	@Override
	protected void init() {
		scale = new UiScale(width, height);
		railMode = width >= scale.dp(600) && height >= scale.dp(420);
		compactChrome = height < 600;
		if (compactChrome) {
			// On short windows (720p at GUI scale 2 leaves only 360 logical pixels) a dp-scaled
			// chrome would eat most of the viewport, so the shell falls back to fixed compact bars.
			topBarHeight = 48;
			playerBarHeight = 64;
			navHeight = railMode ? 0 : 52;
			railWidth = railMode ? 64 : 0;
		} else {
			topBarHeight = Math.round(56F * Math.min(scale.f(), 1.35F));
			playerBarHeight = Math.round(72F * Math.min(scale.f(), 1.35F));
			navHeight = railMode ? 0 : Math.round(56F * Math.min(scale.f(), 1.15F));
			railWidth = railMode ? Math.round(72F * Math.min(scale.f(), 1.2F)) : 0;
		}
		contentX = railWidth;
		contentY = topBarHeight;
		contentWidth = width - railWidth;
		contentHeight = height - topBarHeight - playerBarHeight - navHeight;
		playerBarY = height - navHeight - playerBarHeight;
		navY = height - navHeight;

		if (searchPanel == null) {
			searchPanel = new SearchPanel(this);
			recommendPanel = new RecommendPanel(this);
			playlistPanel = new PlaylistPanel(this);
			activePanel = searchPanel;
		}
		if (sheet != null) {
			sheet.close();
			sheet = null;
		}
		if (queueSheet != null) {
			queueSheet.close();
			queueSheet = null;
		}
		if (loginSheet != null) {
			loginSheet.close();
			loginSheet = null;
		}
		searchPanel.layout(contentX, contentY, contentWidth, contentHeight);
		recommendPanel.layout(contentX, contentY, contentWidth, contentHeight);
		playlistPanel.layout(contentX, contentY, contentWidth, contentHeight);
		activePanel = switch (tab) {
			case SEARCH -> searchPanel;
			case RECOMMEND -> recommendPanel;
			case PLAYLIST -> playlistPanel;
		};
		refreshAccount();
		activePanel.onShown();
	}

	private void setTab(Tab target) {
		if (tab == target) {
			return;
		}
		setFocused(null);
		activePanel.onHidden();
		tab = target;
		activePanel = switch (target) {
			case SEARCH -> searchPanel;
			case RECOMMEND -> recommendPanel;
			case PLAYLIST -> playlistPanel;
		};
		activePanel.layout(contentX, contentY, contentWidth, contentHeight);
		activePanel.onShown();
	}

	public void openLogin() {
		if (loginSheet == null) {
			loginSheet = new LoginSheet(this);
		}
		openSheet(loginSheet);
	}

	public void openQueue() {
		if (queueSheet == null) {
			queueSheet = new QueueSheet(this);
		}
		openSheet(queueSheet);
	}

	public void openSheet(BottomSheet target) {
		if (sheet == target && !target.isClosing()) {
			return;
		}
		if (sheet != null && sheet != target) {
			sheet.close();
		}
		setFocused(null);
		sheet = target;
		target.open();
	}

	public boolean hasSheet() {
		return sheet != null && !sheet.isFinished();
	}

	/** Called by the login sheet after a successful login, logout or manual cookie save. */
	public void onLoginChanged() {
		accountName = null;
		accountLoading = false;
		refreshAccount();
		recommendPanel.onLoginChanged();
		playlistPanel.onLoginChanged();
	}

	public void toast(String message) {
		toast = message;
		toastUntil = System.currentTimeMillis() + 2200L;
	}

	public Font font() {
		return font;
	}

	public UiScale scale() {
		return scale;
	}

	/** Text scale for body copy: Minecraft's 9 px font grows slightly with the layout scale. */
	public float bodyText() {
		return Math.min(1.16F, 1F + (scale.f() - 1F) * 0.22F);
	}

	public float titleText() {
		return bodyText() * 1.55F;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Theme.tick();
		applyCoverTheme();

		graphics.fill(0, 0, width, height, Theme.surface);
		Md3.verticalGradient(graphics, contentX, contentY, contentWidth, scale.dp(180),
				Theme.alpha(Theme.primary, 24), Theme.alpha(Theme.primary, 0));
		hits.clear();

		if (railMode) {
			drawRail(graphics, mouseX, mouseY);
		} else {
			drawBottomNav(graphics, mouseX, mouseY);
		}
		drawTopBar(graphics, mouseX, mouseY);

		if (activePanel != null) {
			searchPanel.setInputVisible(activePanel == searchPanel && !hasSheet());
			activePanel.render(graphics, mouseX, mouseY, partialTick);
		}
		drawPlayerBar(graphics, mouseX, mouseY);

		if (sheet != null) {
			sheet.render(graphics, mouseX, mouseY, partialTick);
			if (sheet.isFinished()) {
				sheet = null;
			}
		}
		drawToast(graphics);
	}

	private void drawTopBar(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.fill(contentX, 0, width, topBarHeight, Theme.surface);
		graphics.fill(contentX, topBarHeight - 1, width, topBarHeight, Theme.outlineVariant);

		String heading = activePanel == null ? title.getString() : activePanel.title();
		String subtitle = activePanel == null ? "" : activePanel.subtitle();
		int textX = contentX + (compactChrome ? scale.dp(12) : scale.dp(24));
		float headingScale = compactChrome ? bodyText() * 1.15F : titleText();
		float subtitleScale = compactChrome ? bodyText() * 0.78F : bodyText() * 0.9F;
		int titleWidth = Math.max(scale.dp(48), contentWidth - (compactChrome ? scale.dp(190) : scale.dp(240)));
		int headingY = compactChrome ? 5 : scale.dp(10);
		int subtitleY = compactChrome ? 24 : scale.dp(36);
		Md3.text(graphics, font, Md3.ellipsize(font, heading, titleWidth, headingScale),
				textX, headingY, Theme.onSurface, headingScale);
		Md3.text(graphics, font, Md3.ellipsize(font, subtitle, titleWidth, subtitleScale),
				textX, subtitleY, Theme.onSurfaceVariant, subtitleScale);

		int chipHeight = compactChrome ? 28 : Math.min(scale.dp(32), topBarHeight - 8);
		drawAccountChip(graphics, mouseX, mouseY, contentX + contentWidth - scale.dp(12),
				(topBarHeight - chipHeight) / 2, chipHeight);
	}

	private void drawAccountChip(GuiGraphics graphics, int mouseX, int mouseY, int right, int y, int height) {
		String label = accountLabel();
		float labelScale = bodyText() * (compactChrome ? 0.8F : 0.9F);
		int textWidth = Md3.textWidth(font, label, labelScale);
		int pad = compactChrome ? scale.dp(6) : scale.dp(8);
		int width = Math.min(Math.min(scale.dp(200), contentWidth / 3), textWidth + scale.dp(46));
		width = Math.max(width, height + pad * 2);
		int x = right - width;
		boolean hovered = inside(mouseX, mouseY, x, y, width, height);
		Md3.roundRect(graphics, x, y, width, height, height / 2,
				hovered ? Theme.surfaceContainerHighest : Theme.surfaceContainerHigh);
		int avatar = height - scale.dp(8);
		Md3.roundRect(graphics, x + scale.dp(4), y + scale.dp(4), avatar, avatar, avatar / 2, Theme.primaryContainer);
		String initial = label.isBlank() ? "?" : label.substring(0, 1).toUpperCase();
		Md3.textCentered(graphics, font, initial, x + scale.dp(4) + avatar / 2,
				Md3.centeredTextY(font, y + scale.dp(4), avatar, labelScale), Theme.onPrimaryContainer, labelScale);
		Md3.text(graphics, font, Md3.ellipsize(font, label, width - avatar - pad * 2, labelScale),
				x + avatar + pad * 2, Md3.centeredTextY(font, y, height, labelScale),
				hovered ? Theme.onSurface : Theme.onSurfaceVariant, labelScale);
		hit(x, y, width, height, this::openLogin);
	}

	private String accountLabel() {
		if (!PlaybackService.INSTANCE.netease().loggedIn()) {
			return I18n.get("gui.simpleplayer.login.sign_in");
		}
		if (accountName != null && !accountName.isBlank()) {
			return accountName;
		}
		return I18n.get("gui.simpleplayer.login.account");
	}

	private void drawRail(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.fill(0, 0, railWidth, height, Theme.surfaceContainerLow);
		graphics.fill(railWidth - 1, 0, railWidth, height, Theme.outlineVariant);

		int brand = scale.dp(44);
		int brandX = (railWidth - brand) / 2;
		int brandY = scale.dp(18);
		Md3.roundRect(graphics, brandX, brandY, brand, brand, scale.dp(Theme.SHAPE_L), Theme.primaryContainer);
		Md3.textCentered(graphics, font, "SP", railWidth / 2,
				Md3.centeredTextY(font, brandY, brand, bodyText() * 1.05F), Theme.onPrimaryContainer,
				bodyText() * 1.05F);

		Tab[] tabs = {Tab.SEARCH, Tab.RECOMMEND, Tab.PLAYLIST};
		int itemHeight = scale.dp(58);
		int itemWidth = railWidth - scale.dp(16);
		for (int i = 0; i < tabs.length; i++) {
			drawRailItem(graphics, mouseX, mouseY, tabs[i], scale.dp(8), scale.dp(84) + i * scale.dp(70),
					itemWidth, itemHeight);
		}

		int settingsY = height - scale.dp(66);
		drawRailAction(graphics, mouseX, mouseY, I18n.get("gui.simpleplayer.nav.settings"), settingsY,
				itemWidth, scale.dp(50), false, this::openSettings);
		int accountY = height - scale.dp(122);
		drawRailAction(graphics, mouseX, mouseY, accountLabel(), accountY, itemWidth, scale.dp(50), true,
				this::openLogin);
	}

	private void drawRailItem(GuiGraphics graphics, int mouseX, int mouseY, Tab tab, int x, int y, int width,
			int height) {
		boolean active = this.tab == tab;
		boolean hovered = inside(mouseX, mouseY, x, y, width, height);
		if (active || hovered) {
			Md3.roundRect(graphics, x, y, width, height, scale.dp(Theme.SHAPE_L),
					active ? Theme.secondaryContainer : Theme.surfaceContainerHigh);
		}
		navIcon(graphics, tab, x + width / 2, y + scale.dp(18), scale.dp(17),
				active ? Theme.onSecondaryContainer : Theme.onSurfaceVariant);
		Md3.textCentered(graphics, font, navLabel(tab), x + width / 2, y + scale.dp(34),
				active ? Theme.onSecondaryContainer : Theme.onSurfaceVariant, bodyText() * 0.92F);
		hit(x, y, width, height, () -> setTab(tab));
	}

	private void drawRailAction(GuiGraphics graphics, int mouseX, int mouseY, String label, int y, int width,
			int height, boolean account, Runnable action) {
		int x = scale.dp(8);
		boolean hovered = inside(mouseX, mouseY, x, y, width, height);
		Md3.roundRect(graphics, x, y, width, height, scale.dp(Theme.SHAPE_L),
				hovered ? Theme.surfaceContainerHigh : Theme.surfaceContainerLow);
		int centerX = x + width / 2;
		if (account) {
			int avatar = scale.dp(22);
			Md3.roundRect(graphics, centerX - avatar / 2, y + scale.dp(6), avatar, avatar, avatar / 2,
					Theme.primaryContainer);
			String initial = label.isBlank() ? "?" : label.substring(0, 1).toUpperCase();
			Md3.textCentered(graphics, font, initial, centerX,
					Md3.centeredTextY(font, y + scale.dp(6), avatar, bodyText() * 0.8F),
					Theme.onPrimaryContainer, bodyText() * 0.8F);
		} else {
			Md3.settings(graphics, centerX, y + scale.dp(16), scale.dp(17),
					hovered ? Theme.onSurface : Theme.onSurfaceVariant);
		}
		Md3.textCentered(graphics, font, Md3.ellipsize(font, label, width - scale.dp(10), bodyText() * 0.9F),
				centerX, y + scale.dp(32), hovered ? Theme.onSurface : Theme.onSurfaceVariant,
				bodyText() * 0.9F);
		hit(x, y, width, height, action);
	}

	private void drawBottomNav(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.fill(0, navY, width, height, Theme.surfaceContainer);
		graphics.fill(0, navY, width, navY + 1, Theme.outlineVariant);
		int itemWidth = width / 4;
		Tab[] tabs = {Tab.SEARCH, Tab.RECOMMEND, Tab.PLAYLIST};
		for (int i = 0; i < tabs.length; i++) {
			drawBottomNavItem(graphics, mouseX, mouseY, tabs[i], i * itemWidth, itemWidth);
		}
		drawBottomSettings(graphics, mouseX, mouseY, 3 * itemWidth, itemWidth);
	}

	private void drawBottomNavItem(GuiGraphics graphics, int mouseX, int mouseY, Tab tab, int x, int width) {
		boolean active = this.tab == tab;
		int centerX = x + width / 2;
		int pillWidth = compactChrome ? 44 : scale.dp(52);
		int pillHeight = compactChrome ? 22 : Math.min(scale.dp(28), Math.round(navHeight * 0.44F));
		int pillY = navY + (compactChrome ? 5 : Math.round(navHeight * 0.10F));
		boolean hovered = inside(mouseX, mouseY, x, navY, width, navHeight);
		if (active || hovered) {
			Md3.roundRect(graphics, centerX - pillWidth / 2, pillY, pillWidth, pillHeight, pillHeight / 2,
					active ? Theme.secondaryContainer : Theme.surfaceContainerHigh);
		}
		navIcon(graphics, tab, centerX, pillY + pillHeight / 2, compactChrome ? 13 : Math.min(scale.dp(16), pillHeight - 6),
				active ? Theme.onSecondaryContainer : Theme.onSurfaceVariant);
		Md3.textCentered(graphics, font, navLabel(tab), centerX, pillY + pillHeight + (compactChrome ? 2 : 4),
				active ? Theme.onSurface : Theme.onSurfaceVariant, bodyText() * (compactChrome ? 0.78F : 0.9F));
		hit(x, navY, width, navHeight, () -> setTab(tab));
	}

	private void drawBottomSettings(GuiGraphics graphics, int mouseX, int mouseY, int x, int width) {
		int centerX = x + width / 2;
		int pillWidth = compactChrome ? 44 : scale.dp(52);
		int pillHeight = compactChrome ? 22 : Math.min(scale.dp(28), Math.round(navHeight * 0.44F));
		int pillY = navY + (compactChrome ? 5 : Math.round(navHeight * 0.10F));
		boolean hovered = inside(mouseX, mouseY, x, navY, width, navHeight);
		if (hovered) {
			Md3.roundRect(graphics, centerX - pillWidth / 2, pillY, pillWidth, pillHeight, pillHeight / 2,
					Theme.surfaceContainerHigh);
		}
		Md3.settings(graphics, centerX, pillY + pillHeight / 2, compactChrome ? 13 : Math.min(scale.dp(16), pillHeight - 6),
				hovered ? Theme.onSurface : Theme.onSurfaceVariant);
		Md3.textCentered(graphics, font, I18n.get("gui.simpleplayer.nav.settings"), centerX,
				pillY + pillHeight + (compactChrome ? 2 : 4), Theme.onSurfaceVariant,
				bodyText() * (compactChrome ? 0.78F : 0.9F));
		hit(x, navY, width, navHeight, this::openSettings);
	}

	private void navIcon(GuiGraphics graphics, Tab tab, int centerX, int centerY, int size, int color) {
		switch (tab) {
			case SEARCH -> Md3.search(graphics, centerX, centerY, size, color);
			case RECOMMEND -> Md3.refresh(graphics, centerX, centerY, size, color);
			case PLAYLIST -> Md3.queue(graphics, centerX, centerY, size, color);
		}
	}

	private String navLabel(Tab tab) {
		return switch (tab) {
			case SEARCH -> I18n.get("gui.simpleplayer.nav.search");
			case RECOMMEND -> I18n.get("gui.simpleplayer.nav.recommend");
			case PLAYLIST -> I18n.get("gui.simpleplayer.nav.playlist");
		};
	}

	private void drawPlayerBar(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.fill(contentX, playerBarY, width, playerBarY + playerBarHeight, Theme.surfaceContainer);
		graphics.fill(contentX, playerBarY, width, playerBarY + 1, Theme.outlineVariant);

		Track current = PlaybackService.INSTANCE.current();
		int padding = compactChrome ? scale.dp(8) : scale.dp(16);
		int coverSize = compactChrome ? Math.max(32, Math.min(42, playerBarHeight - 16))
				: Math.min(scale.dp(56), playerBarHeight - scale.dp(20));
		int coverX = contentX + padding;
		int coverY = playerBarY + (playerBarHeight - coverSize) / 2;
		CoverCache.Cover cover = current == null ? null : CoverCache.get(current.coverUrl());
		if (cover != null) {
			Md3.image(graphics, cover.id(), cover.width(), cover.height(), coverX, coverY, coverSize,
					scale.dp(Theme.SHAPE_S), Theme.surfaceContainer);
		} else {
			Md3.placeholderCover(graphics, coverX, coverY, coverSize, scale.dp(Theme.SHAPE_S),
					Theme.surfaceContainerHigh, Theme.onSurfaceVariant);
		}
		Runnable coverAction = current == null ? () -> setTab(Tab.SEARCH) : this::openQueue;
		hit(coverX, coverY, coverSize, coverSize, coverAction);

		boolean narrow = contentWidth < scale.dp(560);
		int queueSize = compactChrome ? 30 : scale.dp(38);
		int queueX = width - padding - queueSize;
		int queueY = playerBarY + (playerBarHeight - queueSize) / 2;
		int playSize = compactChrome ? Math.max(30, playerBarHeight - 24) : scale.dp(44);
		int playX = queueX - (compactChrome ? scale.dp(6) : scale.dp(12)) - playSize;
		int playY = playerBarY + (playerBarHeight - playSize) / 2;
		int smallSize = compactChrome ? 26 : scale.dp(32);
		int nextX = playX - (compactChrome ? scale.dp(4) : scale.dp(10)) - smallSize;
		int prevX = nextX - scale.dp(4) - smallSize;
		int smallY = playerBarY + (playerBarHeight - smallSize) / 2;

		int metaX = coverX + coverSize + (compactChrome ? scale.dp(8) : scale.dp(14));
		int metaRight = (narrow ? playX : prevX) - scale.dp(12);
		String title = current == null ? I18n.get("gui.simpleplayer.player.nothing") : current.title();
		String artist = current == null ? I18n.get("gui.simpleplayer.player.idle_hint") : current.artists();
		int textWidth = Math.max(scale.dp(40), metaRight - metaX);
		float titleScale = compactChrome ? bodyText() * 0.9F : bodyText();
		float artistScale = compactChrome ? bodyText() * 0.8F : bodyText() * 0.9F;
		int titleY = compactChrome ? playerBarY + 9 : playerBarY + scale.dp(23);
		int artistY = compactChrome ? playerBarY + 24 : playerBarY + scale.dp(41);
		Md3.text(graphics, font, Md3.ellipsize(font, title, textWidth, titleScale), metaX, titleY,
				current == null ? Theme.onSurfaceVariant : Theme.onSurface, titleScale);
		Md3.text(graphics, font, Md3.ellipsize(font, artist, textWidth, artistScale), metaX, artistY,
				Theme.onSurfaceVariant, artistScale);
		Runnable metaAction = current == null ? () -> setTab(Tab.SEARCH) : this::openQueue;
		hit(metaX, playerBarY + scale.dp(16), textWidth, scale.dp(36), metaAction);

		int playingColor = current == null ? Theme.surfaceContainerHigh : Theme.primary;
		boolean playHovered = current != null && inside(mouseX, mouseY, playX, playY, playSize, playSize);
		Md3.roundRect(graphics, playX, playY, playSize, playSize, playSize / 2,
				current == null ? Theme.surfaceContainerHigh : playHovered ? Theme.primary : playingColor);
		int playIcon = compactChrome ? 14 : scale.dp(18);
		if (current == null) {
			Md3.play(graphics, playX + playSize / 2, playY + playSize / 2, playIcon, Theme.onSurfaceVariant);
		} else if (AudioEngine.INSTANCE.isPaused()) {
			Md3.play(graphics, playX + playSize / 2, playY + playSize / 2, playIcon, Theme.onPrimary);
		} else {
			Md3.pause(graphics, playX + playSize / 2, playY + playSize / 2, playIcon, Theme.onPrimary);
		}
		hit(playX, playY, playSize, playSize, () -> {
			if (PlaybackService.INSTANCE.current() == null) {
				setTab(Tab.SEARCH);
			} else {
				PlaybackService.INSTANCE.togglePause();
			}
		});

		if (!narrow) {
			boolean nextHovered = inside(mouseX, mouseY, nextX, smallY, smallSize, smallSize);
			boolean prevHovered = inside(mouseX, mouseY, prevX, smallY, smallSize, smallSize);
			Md3.roundRect(graphics, nextX, smallY, smallSize, smallSize, smallSize / 2,
					nextHovered ? Theme.surfaceContainerHighest : Theme.surfaceContainerHigh);
			Md3.roundRect(graphics, prevX, smallY, smallSize, smallSize, smallSize / 2,
					prevHovered ? Theme.surfaceContainerHighest : Theme.surfaceContainerHigh);
			int smallIcon = compactChrome ? 8 : scale.dp(12);
			Md3.next(graphics, nextX + smallSize / 2, smallY + smallSize / 2, smallIcon,
					nextHovered ? Theme.onSurface : Theme.onSurfaceVariant);
			Md3.previous(graphics, prevX + smallSize / 2, smallY + smallSize / 2, smallIcon,
					prevHovered ? Theme.onSurface : Theme.onSurfaceVariant);
			hit(nextX, smallY, smallSize, smallSize, PlaybackService.INSTANCE::next);
			hit(prevX, smallY, smallSize, smallSize, PlaybackService.INSTANCE::previous);
		}

		boolean queueHovered = inside(mouseX, mouseY, queueX, queueY, queueSize, queueSize);
		Md3.roundRect(graphics, queueX, queueY, queueSize, queueSize, queueSize / 2,
				queueHovered ? Theme.surfaceContainerHighest : Theme.surfaceContainerHigh);
		Md3.queue(graphics, queueX + queueSize / 2, queueY + queueSize / 2,
				compactChrome ? 12 : scale.dp(16), queueHovered ? Theme.onSurface : Theme.onSurfaceVariant);
		int queueCount = PlaybackService.INSTANCE.queue().size();
		if (queueCount > 0) {
			int badge = compactChrome ? 14 : scale.dp(16);
			Md3.roundRect(graphics, queueX + queueSize - badge + scale.dp(1), queueY - scale.dp(2), badge, badge,
					badge / 2, Theme.primary);
			Md3.textCentered(graphics, font, String.valueOf(Math.min(99, queueCount)),
					queueX + queueSize - badge / 2 + scale.dp(1), queueY + scale.dp(1) + (badge - 8) / 2,
					Theme.onPrimary, bodyText() * 0.72F);
		}
		hit(queueX, queueY, queueSize, queueSize, this::openQueue);

		double position = AudioEngine.INSTANCE.positionSeconds();
		double total = current == null ? 0D : current.durationMillis() / 1000D;
		float fraction = total <= 0D ? 0F : (float) Math.max(0D, Math.min(1D, position / total));
		int lineY = playerBarY + playerBarHeight - scale.dp(6);
		Md3.linearProgress(graphics, contentX + padding, lineY, contentWidth - padding * 2, 2, fraction,
				Theme.surfaceContainerHighest, Theme.primary);
		if (current != null && !compactChrome) {
			String time = Md3.duration((long) (position * 1000D)) + " / " + Md3.duration(current.durationMillis());
			Md3.textRight(graphics, font, time, width - padding, lineY - scale.dp(14), Theme.onSurfaceVariant,
					bodyText() * 0.85F);
		}
	}

	private void drawToast(GuiGraphics graphics) {
		if (toast == null || System.currentTimeMillis() >= toastUntil) {
			return;
		}
		int height = scale.dp(32);
		int width = Md3.textWidth(font, toast, bodyText() * 0.9F) + scale.dp(32);
		int x = (this.width - width) / 2;
		int y = playerBarY - height - scale.dp(10);
		Md3.roundRect(graphics, x, y, width, height, height / 2, Theme.surfaceContainerHighest);
		Md3.textCentered(graphics, font, toast, this.width / 2,
				Md3.centeredTextY(font, y, height, bodyText() * 0.9F), Theme.onSurface, bodyText() * 0.9F);
	}

	private void applyCoverTheme() {
		Track current = PlaybackService.INSTANCE.current();
		if (current == null) {
			if (lastCoverUrl != null) {
				lastCoverUrl = null;
				Theme.reset();
			}
			return;
		}
		String url = current.coverUrl();
		if (url == null || url.isBlank() || url.equals(lastCoverUrl)) {
			return;
		}
		CoverCache.Cover cover = CoverCache.get(url);
		if (cover != null) {
			lastCoverUrl = url;
			Theme.applyCover(cover.palette());
		}
	}

	private void refreshAccount() {
		if (!PlaybackService.INSTANCE.netease().loggedIn()) {
			accountName = null;
			accountLoading = false;
			return;
		}
		if (accountName != null || accountLoading) {
			return;
		}
		accountLoading = true;
		Thread thread = new Thread(() -> {
			String name = PlaybackService.INSTANCE.netease().accountName();
			Minecraft.getInstance().execute(() -> {
				accountName = name;
				accountLoading = false;
			});
		}, "SimplePlayer-Account");
		thread.setDaemon(true);
		thread.start();
	}

	private void openSettings() {
		if (minecraft != null) {
			minecraft.setScreen(new SimplePlayerConfigScreen(this));
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		if (sheet != null) {
			if (!sheet.mouseClicked(mouseX, mouseY, button)) {
				setFocused(null);
			}
			return true;
		}
		setFocused(null);
		if (Hit.dispatch(hits, mouseX, mouseY)) {
			return true;
		}
		if (activePanel != null && activePanel.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (sheet != null) {
			sheet.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
			return true;
		}
		return activePanel != null && activePanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (sheet != null) {
			if (sheet.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				sheet.close();
				return true;
			}
			return true;
		}
		if (activePanel == searchPanel && searchPanel.inputFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				setFocused(null);
				return true;
			}
			if (searchPanel.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (sheet != null) {
			return sheet.charTyped(codePoint, modifiers);
		}
		if (activePanel == searchPanel && searchPanel.charTyped(codePoint, modifiers)) {
			return true;
		}
		return super.charTyped(codePoint, modifiers);
	}

	@Override
	public void tick() {
		super.tick();
		if (activePanel != null) {
			activePanel.tick();
		}
	}

	@Override
	public void onClose() {
		if (sheet != null) {
			sheet.close();
		}
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void hit(int x, int y, int width, int height, Runnable action) {
		hits.add(new Hit(x, y, width, height, action));
	}

	private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
