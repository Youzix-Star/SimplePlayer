package com.simpleplayer.client.panel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import com.simpleplayer.client.CoverCache;
import com.simpleplayer.client.PlayerScreen;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.Playlist;
import com.simpleplayer.music.Track;
import com.simpleplayer.ui.Md3;
import com.simpleplayer.ui.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/**
 * NetEase playlist browser: a cover grid backed by the account's playlists, plus an in-panel detail
 * view with the playlist's tracks.
 */
public final class PlaylistPanel extends PlayerPanel {

	private final TrackListView list;
	private final Map<String, List<Track>> trackCache = new HashMap<>();

	private List<Playlist> playlists = List.of();
	private Playlist selected;
	private List<Track> detailTracks = List.of();
	private String status = "";
	private String detailStatus = "";
	private boolean loading;
	private boolean loaded;
	private boolean error;
	private boolean detailLoading;
	private boolean detailError;
	private int generation;
	private int detailGeneration;

	private boolean compact;
	private double gridScroll;
	private int gridX;
	private int gridY;
	private int gridWidth;
	private int gridHeight;
	private int gridContentHeight;

	public PlaylistPanel(PlayerScreen screen) {
		super(screen);
		this.list = new TrackListView(screen.scale());
		this.list.setShowIndex(true);
		this.list.setOnPick(index -> {
			if (!detailTracks.isEmpty()) {
				PlaybackService.INSTANCE.playQueue(detailTracks, index);
			}
		});
	}

	@Override
	public String title() {
		return I18n.get("gui.simpleplayer.playlist.title");
	}

	@Override
	public String subtitle() {
		if (!PlaybackService.INSTANCE.netease().loggedIn()) {
			return I18n.get("gui.simpleplayer.playlist.subtitle_guest");
		}
		return I18n.get("gui.simpleplayer.playlist.subtitle", loaded ? String.valueOf(playlists.size()) : "…");
	}

	@Override
	public void onShown() {
		ensureLoaded();
	}

	@Override
	public void onLoginChanged() {
		playlists = List.of();
		loaded = false;
		selected = null;
		detailTracks = List.of();
		trackCache.clear();
		generation++;
		detailGeneration++;
		error = false;
		detailError = false;
		loading = false;
		detailLoading = false;
		gridScroll = 0D;
		list.resetScroll();
		ensureLoaded();
	}

	private void ensureLoaded() {
		if (loading || !PlaybackService.INSTANCE.netease().loggedIn()) {
			return;
		}
		if (loaded) {
			return;
		}
		loadPlaylists();
	}

	private void loadPlaylists() {
		if (loading) {
			return;
		}
		if (!PlaybackService.INSTANCE.netease().loggedIn()) {
			playlists = List.of();
			return;
		}
		final int token = ++generation;
		loading = true;
		error = false;
		status = "";
		PlaybackService.INSTANCE.playlists().whenComplete((loadedPlaylists, failure) -> {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.execute(() -> {
				if (token != generation) {
					return;
				}
				loading = false;
				loaded = true;
				if (failure != null) {
					error = true;
					status = I18n.get("gui.simpleplayer.playlist.failed", message(failure));
					return;
				}
				playlists = loadedPlaylists;
				if (playlists.isEmpty()) {
					status = I18n.get("gui.simpleplayer.playlist.empty");
				}
			});
		});
	}

	private void openPlaylist(Playlist playlist) {
		selected = playlist;
		detailError = false;
		detailStatus = "";
		list.resetScroll();
		List<Track> cached = trackCache.get(playlist.id());
		if (cached != null) {
			detailTracks = cached;
			detailLoading = false;
			return;
		}
		detailTracks = List.of();
		loadDetail(playlist);
	}

	private void loadDetail(Playlist playlist) {
		final int token = ++detailGeneration;
		detailLoading = true;
		detailError = false;
		detailStatus = "";
		PlaybackService.INSTANCE.playlistTracks(playlist.id()).whenComplete((tracks, failure) -> {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.execute(() -> {
				if (token != detailGeneration || selected != playlist) {
					return;
				}
				detailLoading = false;
				if (failure != null) {
					detailError = true;
					detailStatus = I18n.get("gui.simpleplayer.playlist.detail_failed", message(failure));
					return;
				}
				detailTracks = tracks;
				trackCache.put(playlist.id(), tracks);
			});
		});
	}

	private void back() {
		selected = null;
		detailTracks = List.of();
		detailLoading = false;
		detailGeneration++;
		list.resetScroll();
	}

	@Override
	protected void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		compact = height < 560;
		if (!PlaybackService.INSTANCE.netease().loggedIn()) {
			drawLoginPrompt(graphics, mouseX, mouseY);
			return;
		}
		if (selected != null) {
			drawDetail(graphics, mouseX, mouseY);
		} else {
			drawGrid(graphics, mouseX, mouseY);
		}
	}

	private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY) {
		int pad = compact ? scale().dp(12) : scale().dp(20);
		int available = width - pad * 2;
		int contentWidth = Math.min(available, scale().dp(1100));
		int contentX = x + pad + (available - contentWidth) / 2;
		int contentY = y + pad;

		String count = I18n.get("gui.simpleplayer.playlist.count", playlists.size());
		Md3.text(graphics, font(), loading ? I18n.get("gui.simpleplayer.playlist.loading") : count, contentX, contentY,
				Theme.onSurfaceVariant, bodyText() * 0.92F);
		drawRefresh(graphics, mouseX, mouseY, contentX + contentWidth, contentY - scale().dp(8));

		if (!error && playlists.isEmpty()) {
			drawEmpty(graphics, mouseX, mouseY, contentX, contentY, contentWidth, height - pad * 2);
			return;
		}
		if (error && playlists.isEmpty()) {
			drawEmpty(graphics, mouseX, mouseY, contentX, contentY, contentWidth, height - pad * 2);
			return;
		}
		if (compact) {
			drawCompactGrid(graphics, mouseX, mouseY, contentX, contentY, contentWidth);
			return;
		}

		int gap = scale().dp(14);
		int minCard = scale().dp(158);
		int columns = Math.max(1, Math.min(5, (contentWidth + gap) / (minCard + gap)));
		int cardWidth = (contentWidth - gap * (columns - 1)) / columns;
		int coverSize = cardWidth - scale().dp(16);
		int cardHeight = coverSize + scale().dp(56);

		gridX = contentX;
		gridY = contentY + scale().dp(30);
		gridWidth = contentWidth;
		gridHeight = Math.max(scale().dp(40), y + height - scale().dp(12) - gridY);

		int rows = (playlists.size() + columns - 1) / columns;
		gridContentHeight = rows * (cardHeight + gap);
		double maxScroll = Math.max(0D, gridContentHeight - gridHeight);
		gridScroll = Math.max(0D, Math.min(gridScroll, maxScroll));

		graphics.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight);
		try {
			for (int i = 0; i < playlists.size(); i++) {
				int column = i % columns;
				int row = i / columns;
				int cardX = gridX + column * (cardWidth + gap);
				int cardY = gridY + row * (cardHeight + gap) - (int) Math.round(gridScroll);
				if (cardY + cardHeight < gridY || cardY > gridY + gridHeight) {
					continue;
				}
				drawCard(graphics, mouseX, mouseY, playlists.get(i), cardX, cardY, cardWidth, cardHeight,
						coverSize);
			}
		} finally {
			graphics.disableScissor();
		}
		drawScrollbar(graphics, gridX + gridWidth, gridY, gridHeight, maxScroll);
	}

	private void drawCompactGrid(GuiGraphics graphics, int mouseX, int mouseY, int contentX, int contentY,
			int contentWidth) {
		int rowHeight = scale().dp(54);
		gridX = contentX;
		gridY = contentY + scale().dp(26);
		gridWidth = contentWidth;
		gridHeight = Math.max(scale().dp(40), y + height - scale().dp(10) - gridY);
		gridContentHeight = playlists.size() * rowHeight;
		double maxScroll = Math.max(0D, gridContentHeight - gridHeight);
		gridScroll = Math.max(0D, Math.min(gridScroll, maxScroll));

		graphics.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight);
		try {
			int first = Math.max(0, (int) Math.floor(gridScroll / rowHeight));
			int last = Math.min(playlists.size() - 1, (int) Math.ceil((gridScroll + gridHeight) / rowHeight));
			for (int index = first; index <= last; index++) {
				Playlist playlist = playlists.get(index);
				int rowY = gridY + index * rowHeight - (int) Math.round(gridScroll);
				boolean hovered = inside(mouseX, mouseY, gridX, rowY, gridWidth, rowHeight - 3);
				int background = hovered ? Theme.surfaceContainerHigh : Theme.surfaceContainerLow;
				Md3.roundRect(graphics, gridX, rowY, gridWidth, rowHeight - 3, scale().dp(Theme.SHAPE_M), background);
				int coverSize = rowHeight - scale().dp(12);
				int coverX = gridX + scale().dp(7);
				int coverY = rowY + scale().dp(5);
				CoverCache.Cover cover = CoverCache.get(playlist.coverUrl());
				if (cover != null) {
					Md3.image(graphics, cover.id(), cover.width(), cover.height(), coverX, coverY, coverSize,
							scale().dp(Theme.SHAPE_S), background);
				} else {
					Md3.placeholderCover(graphics, coverX, coverY, coverSize, scale().dp(Theme.SHAPE_S),
							Theme.surfaceContainerHighest, Theme.onSurfaceVariant);
				}
				int textX = coverX + coverSize + scale().dp(12);
				int textRight = gridX + gridWidth - scale().dp(12);
				Md3.text(graphics, font(), Md3.ellipsize(font(), playlist.name(), textRight - textX, bodyText()),
						textX, rowY + scale().dp(12), Theme.onSurface, bodyText());
				String meta = playlist.creator() + " · " + I18n.get("gui.simpleplayer.playlist.track_count",
						playlist.trackCount());
				Md3.text(graphics, font(), Md3.ellipsize(font(), meta, textRight - textX, bodyText() * 0.85F),
						textX, rowY + scale().dp(30), Theme.onSurfaceVariant, bodyText() * 0.85F);
				hit(gridX, rowY, gridWidth, rowHeight - 3, () -> openPlaylist(playlist));
			}
		} finally {
			graphics.disableScissor();
		}
		drawScrollbar(graphics, gridX + gridWidth, gridY, gridHeight, maxScroll);
	}

	private void drawCard(GuiGraphics graphics, int mouseX, int mouseY, Playlist playlist, int x, int y, int width,
			int height, int coverSize) {
		boolean hovered = inside(mouseX, mouseY, x, y, width, height);
		int background = hovered ? Theme.surfaceContainerHigh : Theme.surfaceContainerLow;
		Md3.roundRect(graphics, x, y, width, height, scale().dp(Theme.SHAPE_M), background);
		int coverX = x + scale().dp(8);
		int coverY = y + scale().dp(8);
		CoverCache.Cover cover = CoverCache.get(playlist.coverUrl());
		if (cover != null) {
			Md3.image(graphics, cover.id(), cover.width(), cover.height(), coverX, coverY, coverSize,
					scale().dp(Theme.SHAPE_S), background);
		} else {
			Md3.placeholderCover(graphics, coverX, coverY, coverSize, scale().dp(Theme.SHAPE_S),
					Theme.surfaceContainerHighest, Theme.onSurfaceVariant);
		}

		String count = I18n.get("gui.simpleplayer.playlist.track_count", playlist.trackCount());
		int badgeHeight = scale().dp(18);
		int badgeWidth = Md3.textWidth(font(), count, bodyText() * 0.78F) + scale().dp(12);
		int badgeX = coverX + coverSize - badgeWidth - scale().dp(6);
		int badgeY = coverY + coverSize - badgeHeight - scale().dp(6);
		Md3.roundRect(graphics, badgeX, badgeY, badgeWidth, badgeHeight, badgeHeight / 2, 0xCC000000);
		Md3.textCentered(graphics, font(), count, badgeX + badgeWidth / 2,
				Md3.centeredTextY(font(), badgeY, badgeHeight, bodyText() * 0.78F), 0xFFFFFFFF,
				bodyText() * 0.78F);

		int textX = coverX;
		int textWidth = coverSize;
		Md3.text(graphics, font(), Md3.ellipsize(font(), playlist.name(), textWidth, bodyText()), textX,
				coverY + coverSize + scale().dp(10), Theme.onSurface, bodyText());
		Md3.text(graphics, font(), Md3.ellipsize(font(), playlist.creator(), textWidth, bodyText() * 0.85F),
				textX, coverY + coverSize + scale().dp(26), Theme.onSurfaceVariant, bodyText() * 0.85F);
		hit(x, y, width, height, () -> openPlaylist(playlist));
	}

	private void drawDetail(GuiGraphics graphics, int mouseX, int mouseY) {
		int pad = compact ? scale().dp(12) : scale().dp(20);
		int available = width - pad * 2;
		int contentWidth = Math.min(available, scale().dp(920));
		int contentX = x + pad + (available - contentWidth) / 2;
		int contentY = y + pad;

		int backHeight = scale().dp(32);
		int backWidth = Md3.textWidth(font(), I18n.get("gui.simpleplayer.playlist.back"), bodyText() * 0.92F)
				+ scale().dp(34);
		boolean backHovered = inside(mouseX, mouseY, contentX, contentY, backWidth, backHeight);
		Md3.roundRect(graphics, contentX, contentY, backWidth, backHeight, backHeight / 2,
				backHovered ? Theme.surfaceContainerHigh : Theme.surfaceContainerLow);
		Md3.back(graphics, contentX + scale().dp(14), contentY + backHeight / 2, scale().dp(10),
				backHovered ? Theme.onSurface : Theme.onSurfaceVariant);
		Md3.text(graphics, font(), I18n.get("gui.simpleplayer.playlist.back"), contentX + scale().dp(24),
				Md3.centeredTextY(font(), contentY, backHeight, bodyText() * 0.92F),
				backHovered ? Theme.onSurface : Theme.onSurfaceVariant, bodyText() * 0.92F);
		hit(contentX, contentY, backWidth, backHeight, this::back);

		int headerY = contentY + backHeight + scale().dp(10);
		int headerHeight = compact
				? Math.min(scale().dp(112), Math.max(scale().dp(76), height - backHeight - scale().dp(150)))
				: Math.min(scale().dp(160), Math.max(scale().dp(96), height - backHeight - scale().dp(98)));
		Md3.roundRect(graphics, contentX, headerY, contentWidth, headerHeight, scale().dp(Theme.SHAPE_L),
				Theme.surfaceContainerLow);
		Md3.verticalGradient(graphics, contentX, headerY, contentWidth, headerHeight,
				Theme.alpha(Theme.primary, 26), Theme.alpha(Theme.primary, 0));

		int coverSize = headerHeight - scale().dp(20);
		int coverX = contentX + scale().dp(10);
		int coverY = headerY + scale().dp(10);
		CoverCache.Cover cover = CoverCache.get(selected.coverUrl());
		if (cover != null) {
			Md3.image(graphics, cover.id(), cover.width(), cover.height(), coverX, coverY, coverSize,
					scale().dp(Theme.SHAPE_M), Theme.surfaceContainerLow);
		} else {
			Md3.placeholderCover(graphics, coverX, coverY, coverSize, scale().dp(Theme.SHAPE_M),
					Theme.primaryContainer, Theme.onPrimaryContainer);
		}

		int textX = coverX + coverSize + scale().dp(16);
		int textRight = contentX + contentWidth - scale().dp(16);
		Md3.text(graphics, font(),
				Md3.ellipsize(font(), selected.name(), textRight - textX, titleText() * 0.82F), textX,
				headerY + scale().dp(14), Theme.onSurface, titleText() * 0.82F);
		Md3.text(graphics, font(), I18n.get("gui.simpleplayer.playlist.by", selected.creator()), textX,
				headerY + scale().dp(38), Theme.onSurfaceVariant, bodyText() * 0.9F);
		String count = I18n.get("gui.simpleplayer.playlist.track_count", selected.trackCount());
		Md3.text(graphics, font(), count, textX, headerY + scale().dp(54), Theme.onSurfaceVariant,
				bodyText() * 0.9F);

		String playAll = I18n.get("gui.simpleplayer.search.play_all");
		int playWidth = Md3.textWidth(font(), playAll, bodyText() * 0.92F) + scale().dp(32);
		int playHeight = scale().dp(34);
		int playX = textX;
		int playY = headerY + headerHeight - playHeight - scale().dp(12);
		boolean playHovered = inside(mouseX, mouseY, playX, playY, playWidth, playHeight);
		Md3.roundRect(graphics, playX, playY, playWidth, playHeight, playHeight / 2,
				playHovered ? Theme.primary : Theme.primaryContainer);
		Md3.play(graphics, playX + scale().dp(15), playY + playHeight / 2, scale().dp(10),
				playHovered ? Theme.onPrimary : Theme.onPrimaryContainer);
		Md3.text(graphics, font(), playAll, playX + scale().dp(26),
				Md3.centeredTextY(font(), playY, playHeight, bodyText() * 0.92F),
				playHovered ? Theme.onPrimary : Theme.onPrimaryContainer, bodyText() * 0.92F);
		hit(playX, playY, playWidth, playHeight, () -> {
			if (!detailTracks.isEmpty()) {
				PlaybackService.INSTANCE.playQueue(detailTracks, 0);
			}
		});

		int listY = headerY + headerHeight + scale().dp(14);
		int listHeight = Math.max(scale().dp(40), y + height - scale().dp(12) - listY);
		list.setRowHeight(compact ? scale().dp(40) : scale().dp(60));
		list.setBounds(contentX, listY, contentWidth, listHeight);
		list.setTracks(detailTracks);
		list.setPlayingIndex(-1);
		list.render(graphics, font(), bodyText(), mouseX, mouseY);

		if (detailLoading) {
			Md3.textCentered(graphics, font(), I18n.get("gui.simpleplayer.playlist.detail_loading"),
					contentX + contentWidth / 2, listY + scale().dp(18), Theme.onSurfaceVariant, bodyText());
			Md3.indeterminate(graphics, contentX, listY + scale().dp(34), contentWidth, 2,
					Theme.surfaceContainerHighest, Theme.primary, System.currentTimeMillis());
		} else if (detailError) {
			Md3.textCentered(graphics, font(), detailStatus, contentX + contentWidth / 2, listY + scale().dp(18),
					Theme.error, bodyText() * 0.92F);
			String retry = I18n.get("gui.simpleplayer.common.retry");
			int retryWidth = Md3.textWidth(font(), retry, bodyText()) + scale().dp(32);
			int retryHeight = scale().dp(32);
			int retryX = contentX + (contentWidth - retryWidth) / 2;
			int retryY = listY + scale().dp(34);
			boolean hovered = inside(mouseX, mouseY, retryX, retryY, retryWidth, retryHeight);
			Md3.roundRect(graphics, retryX, retryY, retryWidth, retryHeight, retryHeight / 2,
					hovered ? Theme.primary : Theme.surfaceContainerHigh);
			Md3.textCentered(graphics, font(), retry, retryX + retryWidth / 2,
					Md3.centeredTextY(font(), retryY, retryHeight, bodyText()),
					hovered ? Theme.onPrimary : Theme.onSurface, bodyText());
			hit(retryX, retryY, retryWidth, retryHeight, () -> loadDetail(selected));
		} else if (detailTracks.isEmpty()) {
			Md3.textCentered(graphics, font(), I18n.get("gui.simpleplayer.playlist.detail_empty"),
					contentX + contentWidth / 2, listY + scale().dp(18), Theme.onSurfaceVariant, bodyText());
		}
	}

	private void drawRefresh(GuiGraphics graphics, int mouseX, int mouseY, int right, int top) {
		int size = scale().dp(36);
		int x = right - size;
		int y = top;
		boolean hovered = inside(mouseX, mouseY, x, y, size, size);
		Md3.roundRect(graphics, x, y, size, size, size / 2,
				hovered ? Theme.surfaceContainerHighest : Theme.surfaceContainerHigh);
		Md3.refresh(graphics, x + size / 2, y + size / 2, scale().dp(14),
				hovered ? Theme.onSurface : Theme.onSurfaceVariant);
		hit(x, y, size, size, () -> {
			loaded = false;
			loadPlaylists();
		});
	}

	private void drawLoginPrompt(GuiGraphics graphics, int mouseX, int mouseY) {
		int pad = scale().dp(20);
		int contentX = x + pad;
		int contentWidth = width - pad * 2;
		int centerX = contentX + contentWidth / 2;
		int centerY = y + height / 2 - scale().dp(30);
		int circle = scale().dp(76);
		Md3.roundRect(graphics, centerX - circle / 2, centerY - circle / 2, circle, circle, circle / 2,
				Theme.primaryContainer);
		Md3.settings(graphics, centerX, centerY, scale().dp(28), Theme.onPrimaryContainer);
		Md3.textCentered(graphics, font(), I18n.get("gui.simpleplayer.playlist.login.title"), centerX,
				centerY + circle / 2 + scale().dp(18), Theme.onSurface, titleText() * 0.72F);
		Md3.textCentered(graphics, font(), I18n.get("gui.simpleplayer.playlist.login.subtitle"), centerX,
				centerY + circle / 2 + scale().dp(40), Theme.onSurfaceVariant, bodyText() * 0.92F);
		String label = I18n.get("gui.simpleplayer.playlist.login_button");
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
		if (loading) {
			Md3.refresh(graphics, centerX, centerY, scale().dp(26), Theme.primary);
		} else {
			Md3.queue(graphics, centerX, centerY, scale().dp(26), Theme.onSurfaceVariant);
		}
		String text = loading ? I18n.get("gui.simpleplayer.playlist.loading") : status;
		Md3.textCentered(graphics, font(), text, centerX, centerY + scale().dp(50),
				error ? Theme.error : Theme.onSurface, bodyText());
		if (!loading) {
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
			hit(buttonX, buttonY, buttonWidth, buttonHeight, () -> {
				loaded = false;
				loadPlaylists();
			});
		}
	}

	private void drawScrollbar(GuiGraphics graphics, int right, int top, int height, double maxScroll) {
		if (maxScroll <= 0D) {
			return;
		}
		int barWidth = scale().dp(3);
		int trackHeight = height - scale().dp(8);
		int barHeight = Math.max(scale().dp(24), Math.round(trackHeight * height / (float) (height + maxScroll)));
		int barY = top + scale().dp(4)
				+ (int) Math.round((trackHeight - barHeight) * (gridScroll / maxScroll));
		Md3.roundRect(graphics, right - barWidth - scale().dp(2), barY, barWidth, barHeight, barWidth / 2,
				Theme.outline);
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
		if (selected != null && list.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (selected != null) {
			return list.mouseScrolled(mouseX, mouseY, scrollY);
		}
		if (mouseX < gridX || mouseX >= gridX + gridWidth || mouseY < gridY || mouseY >= gridY + gridHeight) {
			return false;
		}
		double maxScroll = Math.max(0D, gridContentHeight - gridHeight);
		if (maxScroll <= 0D) {
			return false;
		}
		gridScroll = Math.max(0D, Math.min(maxScroll, gridScroll - scrollY * scale().dp(48)));
		return true;
	}
}
