package com.simpleplayer.client.panel;

import java.util.List;
import java.util.concurrent.CompletionException;

import com.simpleplayer.client.CoverCache;
import com.simpleplayer.client.PlayerScreen;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.Track;
import com.simpleplayer.ui.Md3;
import com.simpleplayer.ui.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/** Daily recommendation page with a hero card for the first track. */
public final class RecommendPanel extends PlayerPanel {

	private final TrackListView list;
	private List<Track> tracks = List.of();
	private String status = "";
	private boolean loading;
	private boolean loaded;
	private boolean error;
	private int generation;

	public RecommendPanel(PlayerScreen screen) {
		super(screen);
		this.list = new TrackListView(screen.scale());
		this.list.setOnPick(index -> PlaybackService.INSTANCE.playQueue(tracks, index));
	}

	@Override
	public String title() {
		return I18n.get("gui.simpleplayer.recommend.title");
	}

	@Override
	public String subtitle() {
		return I18n.get("gui.simpleplayer.recommend.subtitle");
	}

	@Override
	public void onShown() {
		ensureLoaded();
	}

	@Override
	public void onLoginChanged() {
		tracks = List.of();
		loaded = false;
		loading = false;
		generation++;
		ensureLoaded();
	}

	private void ensureLoaded() {
		if (loading || !PlaybackService.INSTANCE.netease().loggedIn()) {
			return;
		}
		if (loaded && !tracks.isEmpty()) {
			return;
		}
		load();
	}

	private void load() {
		if (loading) {
			return;
		}
		if (!PlaybackService.INSTANCE.netease().loggedIn()) {
			status = I18n.get("gui.simpleplayer.recommend.login.title");
			loaded = false;
			return;
		}
		final int token = ++generation;
		loading = true;
		error = false;
		status = "";
		PlaybackService.INSTANCE.dailyRecommendations().whenComplete((loadedTracks, failure) -> {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.execute(() -> {
				if (token != generation) {
					return;
				}
				loading = false;
				loaded = true;
				if (failure != null) {
					error = true;
					status = I18n.get("gui.simpleplayer.recommend.failed", message(failure));
					return;
				}
				tracks = loadedTracks;
				if (tracks.isEmpty()) {
					status = I18n.get("gui.simpleplayer.recommend.empty");
				}
			});
		});
	}

	@Override
	protected void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		boolean compact = height < 560;
		int pad = compact ? scale().dp(12) : scale().dp(20);
		int available = width - pad * 2;
		int contentWidth = Math.min(available, scale().dp(920));
		int contentX = x + pad + (available - contentWidth) / 2;
		int contentY = y + pad;

		if (!PlaybackService.INSTANCE.netease().loggedIn()) {
			drawLoginPrompt(graphics, mouseX, mouseY, contentX, y, contentWidth, height);
			return;
		}
		if (loading && tracks.isEmpty()) {
			drawLoading(graphics, contentX, y, contentWidth, height);
			return;
		}
		if (tracks.isEmpty()) {
			drawEmpty(graphics, mouseX, mouseY, contentX, y, contentWidth, height);
			return;
		}

		boolean showHero = height > 600 && contentWidth > scale().dp(360);
		int listTop = contentY;
		if (showHero) {
			int heroHeight = Math.min(scale().dp(172), Math.max(scale().dp(120), height / 3));
			drawHero(graphics, mouseX, mouseY, contentX, contentY, contentWidth, heroHeight);
			listTop = contentY + heroHeight + scale().dp(18);
		}

		int listHeaderY = listTop;
		String header = I18n.get("gui.simpleplayer.recommend.today", tracks.size());
		Md3.text(graphics, font(), header, contentX, listHeaderY, Theme.onSurface, bodyText());
		boolean notice = loading || (error && !status.isEmpty());
		int listY = listHeaderY + scale().dp(notice ? 42 : 24);
		int listHeight = Math.max(scale().dp(40), y + height - scale().dp(12) - listY);
		list.setRowHeight(compact ? scale().dp(40) : scale().dp(60));
		list.setBounds(contentX, listY, contentWidth, listHeight);
		list.setTracks(tracks);
		list.setPlayingIndex(-1);
		list.render(graphics, font(), bodyText(), mouseX, mouseY);

		if (loading) {
			Md3.indeterminate(graphics, contentX, listHeaderY + scale().dp(16), contentWidth, 2,
					Theme.surfaceContainerHighest, Theme.primary, System.currentTimeMillis());
		}
		if (error && !status.isEmpty()) {
			Md3.text(graphics, font(), Md3.ellipsize(font(), status, contentWidth, bodyText() * 0.9F), contentX,
					listHeaderY + scale().dp(18), Theme.error, bodyText() * 0.9F);
		}

		drawRefresh(graphics, mouseX, mouseY, contentX + contentWidth, contentY);
	}

	private void drawHero(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
		Md3.roundRect(graphics, x, y, width, height, scale().dp(Theme.SHAPE_L), Theme.surfaceContainerLow);
		Md3.verticalGradient(graphics, x, y, width, height, Theme.alpha(Theme.primary, 30),
				Theme.alpha(Theme.primary, 0));
		int coverSize = height - scale().dp(24);
		int coverX = x + scale().dp(12);
		int coverY = y + scale().dp(12);
		Track first = tracks.get(0);
		CoverCache.Cover cover = CoverCache.get(first.coverUrl());
		if (cover != null) {
			Md3.image(graphics, cover.id(), cover.width(), cover.height(), coverX, coverY, coverSize,
					scale().dp(Theme.SHAPE_M), Theme.surfaceContainerLow);
		} else {
			Md3.placeholderCover(graphics, coverX, coverY, coverSize, scale().dp(Theme.SHAPE_M),
					Theme.primaryContainer, Theme.onPrimaryContainer);
		}

		int textX = coverX + coverSize + scale().dp(18);
		int textRight = x + width - scale().dp(18);
		Md3.text(graphics, font(), I18n.get("gui.simpleplayer.recommend.first"), textX,
				y + scale().dp(20), Theme.primary, bodyText() * 0.9F);
		Md3.text(graphics, font(),
				Md3.ellipsize(font(), first.title(), textRight - textX, titleText() * 0.8F), textX,
				y + scale().dp(38), Theme.onSurface, titleText() * 0.8F);
		Md3.text(graphics, font(),
				Md3.ellipsize(font(), first.artists(), textRight - textX, bodyText() * 0.95F), textX,
				y + scale().dp(64), Theme.onSurfaceVariant, bodyText() * 0.95F);

		String play = I18n.get("gui.simpleplayer.search.play_all");
		int playWidth = Md3.textWidth(font(), play, bodyText() * 0.92F) + scale().dp(30);
		int playHeight = scale().dp(34);
		int playX = textX;
		int playY = y + height - playHeight - scale().dp(18);
		boolean hovered = inside(mouseX, mouseY, playX, playY, playWidth, playHeight);
		Md3.roundRect(graphics, playX, playY, playWidth, playHeight, playHeight / 2,
				hovered ? Theme.primary : Theme.primaryContainer);
		Md3.play(graphics, playX + scale().dp(15), playY + playHeight / 2, scale().dp(10),
				hovered ? Theme.onPrimary : Theme.onPrimaryContainer);
		Md3.text(graphics, font(), play, playX + scale().dp(26),
				Md3.centeredTextY(font(), playY, playHeight, bodyText() * 0.92F),
				hovered ? Theme.onPrimary : Theme.onPrimaryContainer, bodyText() * 0.92F);
		hit(x, y, width, height, () -> PlaybackService.INSTANCE.playQueue(tracks, 0));
	}

	private void drawRefresh(GuiGraphics graphics, int mouseX, int mouseY, int right, int top) {
		int size = scale().dp(36);
		int x = right - size;
		int y = top - scale().dp(4);
		boolean hovered = inside(mouseX, mouseY, x, y, size, size);
		Md3.roundRect(graphics, x, y, size, size, size / 2,
				hovered ? Theme.surfaceContainerHighest : Theme.surfaceContainerHigh);
		Md3.refresh(graphics, x + size / 2, y + size / 2, scale().dp(14),
				hovered ? Theme.onSurface : Theme.onSurfaceVariant);
		hit(x, y, size, size, this::load);
	}

	private void drawLoading(GuiGraphics graphics, int x, int y, int width, int height) {
		int centerX = x + width / 2;
		int centerY = y + height / 2 - scale().dp(20);
		Md3.refresh(graphics, centerX, centerY, scale().dp(30), Theme.primary);
		Md3.textCentered(graphics, font(), I18n.get("gui.simpleplayer.recommend.loading"), centerX,
				centerY + scale().dp(30), Theme.onSurfaceVariant, bodyText());
	}

	private void drawLoginPrompt(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
		int centerX = x + width / 2;
		int centerY = y + height / 2 - scale().dp(30);
		int circle = scale().dp(76);
		Md3.roundRect(graphics, centerX - circle / 2, centerY - circle / 2, circle, circle, circle / 2,
				Theme.primaryContainer);
		Md3.settings(graphics, centerX, centerY, scale().dp(28), Theme.onPrimaryContainer);
		Md3.textCentered(graphics, font(), I18n.get("gui.simpleplayer.recommend.login.title"), centerX,
				centerY + circle / 2 + scale().dp(18), Theme.onSurface, titleText() * 0.72F);
		Md3.textCentered(graphics, font(), I18n.get("gui.simpleplayer.recommend.login.subtitle"), centerX,
				centerY + circle / 2 + scale().dp(40), Theme.onSurfaceVariant, bodyText() * 0.92F);

		String label = I18n.get("gui.simpleplayer.recommend.login_button");
		int buttonWidth = Md3.textWidth(font(), label, bodyText()) + scale().dp(40);
		int buttonHeight = scale().dp(40);
		int buttonX = centerX - buttonWidth / 2;
		int buttonY = centerY + circle / 2 + scale().dp(62);
		boolean hovered = inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);
		Md3.roundRect(graphics, buttonX, buttonY, buttonWidth, buttonHeight, buttonHeight / 2,
				hovered ? Theme.primary : Theme.primaryContainer);
		Md3.textCentered(graphics, font(), label, centerX,
				Md3.centeredTextY(font(), buttonY, buttonHeight, bodyText()),
				hovered ? Theme.onPrimary : Theme.onPrimaryContainer, bodyText());
		hit(buttonX, buttonY, buttonWidth, buttonHeight, screen::openLogin);
	}

	private void drawEmpty(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
		int centerX = x + width / 2;
		int centerY = y + height / 2 - scale().dp(20);
		Md3.roundRect(graphics, centerX - scale().dp(34), centerY - scale().dp(34), scale().dp(68), scale().dp(68),
			scale().dp(34), Theme.surfaceContainerHigh);
		Md3.refresh(graphics, centerX, centerY, scale().dp(26), error ? Theme.error : Theme.onSurfaceVariant);
		Md3.textCentered(graphics, font(), status, centerX, centerY + scale().dp(50),
				error ? Theme.error : Theme.onSurface, bodyText());
		String retry = I18n.get("gui.simpleplayer.common.retry");
		int buttonWidth = Md3.textWidth(font(), retry, bodyText()) + scale().dp(36);
		int buttonHeight = scale().dp(36);
		int buttonX = centerX - buttonWidth / 2;
		int buttonY = centerY + scale().dp(70);
		boolean hovered = inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);
		Md3.roundRect(graphics, buttonX, buttonY, buttonWidth, buttonHeight, buttonHeight / 2,
				hovered ? Theme.primary : Theme.surfaceContainerHigh);
		Md3.textCentered(graphics, font(), retry, centerX,
				Md3.centeredTextY(font(), buttonY, buttonHeight, bodyText()),
				hovered ? Theme.onPrimary : Theme.onSurface, bodyText());
		hit(buttonX, buttonY, buttonWidth, buttonHeight, this::load);
	}

	private static String message(Throwable error) {
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		String message = cause.getMessage();
		if (message == null || message.isBlank()) {
			message = cause.getClass().getSimpleName();
		}
		return message.length() > 80 ? message.substring(0, 77) + "…" : message;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (list.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
}
