package com.simpleplayer.client.panel;

import java.util.List;
import java.util.function.IntConsumer;

import com.simpleplayer.client.CoverCache;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.Track;
import com.simpleplayer.ui.Theme;
import com.simpleplayer.ui.UiScale;
import com.simpleplayer.ui.Md3;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Scrollable, self-contained list of tracks. Panels position it, feed it a snapshot of the tracks
 * they currently show and optionally set the playing index; all hover, cover loading, clipping and
 * wheel handling lives here.
 */
public final class TrackListView {

	private final UiScale scale;

	private int x;
	private int y;
	private int width;
	private int height;
	private int rowHeight;
	private double scroll;

	private List<Track> tracks = List.of();
	private IntConsumer onPick;
	private boolean showIndex;
	private int playingIndex = -1;

	public TrackListView(UiScale scale) {
		this.scale = scale;
		this.rowHeight = scale.dp(60);
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		clampScroll();
	}

	public void setRowHeight(int rowHeight) {
		this.rowHeight = Math.max(scale.dp(38), rowHeight);
	}

	public void setTracks(List<Track> tracks) {
		this.tracks = tracks == null ? List.of() : tracks;
		clampScroll();
	}

	public void setOnPick(IntConsumer onPick) {
		this.onPick = onPick;
	}

	public void setShowIndex(boolean showIndex) {
		this.showIndex = showIndex;
	}

	public void setPlayingIndex(int playingIndex) {
		this.playingIndex = playingIndex;
	}

	public void resetScroll() {
		scroll = 0D;
	}

	public int rowHeight() {
		return rowHeight;
	}

	public double scroll() {
		return scroll;
	}

	public void render(GuiGraphics graphics, Font font, float textScale, int mouseX, int mouseY) {
		if (height <= 0 || width <= 0) {
			return;
		}
		graphics.enableScissor(x, y, x + width, y + height);
		try {
			int first = Math.max(0, (int) Math.floor(scroll / rowHeight));
			int last = Math.min(tracks.size() - 1, (int) Math.ceil((scroll + height) / rowHeight));
			for (int index = first; index <= last; index++) {
				drawRow(graphics, font, textScale, index, tracks.get(index), mouseX, mouseY);
			}
		} finally {
			graphics.disableScissor();
		}
		drawScrollbar(graphics);
	}

	private void drawRow(GuiGraphics graphics, Font font, float textScale, int index, Track track, int mouseX,
			int mouseY) {
		int rowY = y - (int) Math.round(scroll) + index * rowHeight;
		int inset = scale.dp(2);
		int rowX = x + inset;
		int rowW = width - inset * 2;
		int rowH = rowHeight - scale.dp(3);
		boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= rowY && mouseY < rowY + rowH;
		boolean playing = playingIndex >= 0 ? playingIndex == index
				: track.equals(PlaybackService.INSTANCE.current());

		int background = playing ? Theme.primaryContainer
				: hovered ? Theme.surfaceContainerHigh : Theme.surfaceContainerLow;
		Md3.roundRect(graphics, rowX, rowY, rowW, rowH, scale.dp(Theme.SHAPE_M), background);
		if (playing) {
			Md3.roundRect(graphics, rowX, rowY + scale.dp(10), scale.dp(3), rowH - scale.dp(20), 2, Theme.primary);
		}

		int contentX = rowX + scale.dp(10);
		int coverSize = rowH - scale.dp(10);
		int coverY = rowY + scale.dp(5);
		if (showIndex) {
			String number = String.format("%02d", index + 1);
			Md3.text(graphics, font, number, contentX, Md3.centeredTextY(font, rowY, rowH, textScale),
					playing ? Theme.onPrimaryContainer : Theme.onSurfaceVariant, textScale);
			contentX += scale.dp(28);
		}

		CoverCache.Cover cover = CoverCache.get(track.coverUrl());
		if (cover != null) {
			Md3.image(graphics, cover.id(), cover.width(), cover.height(), contentX, coverY, coverSize,
					scale.dp(Theme.SHAPE_S), background);
		} else {
			Md3.placeholderCover(graphics, contentX, coverY, coverSize, scale.dp(Theme.SHAPE_S),
					playing ? Theme.primaryContainer : Theme.surfaceContainerHighest, Theme.onSurfaceVariant);
		}
		if (hovered) {
			graphics.fill(contentX, coverY, contentX + coverSize, coverY + coverSize, 0x66000000);
			Md3.play(graphics, contentX + coverSize / 2, coverY + coverSize / 2, scale.dp(18), 0xFFFFFFFF);
		}
		if (playing) {
			Md3.equalizer(graphics, contentX + coverSize - scale.dp(14), coverY + coverSize - scale.dp(13),
					scale.dp(10), scale.dp(9), Theme.primary, System.currentTimeMillis());
		}

		int textX = contentX + coverSize + scale.dp(12);
		int durationWidth = scale.dp(48);
		int reservedRight = scale.dp(12) + durationWidth + (track.paid() ? scale.dp(42) : 0);
		int textRight = rowX + rowW - reservedRight;
		int lineHeight = Math.max(8, Math.round(font.lineHeight * textScale));
		int titleY = rowY + Math.max(scale.dp(6), (rowH - lineHeight * 2 - scale.dp(3)) / 2);
		int artistY = titleY + lineHeight + scale.dp(3);
		Md3.text(graphics, font, Md3.ellipsize(font, track.title(), textRight - textX - scale.dp(8), textScale),
				textX, titleY, playing ? Theme.onPrimaryContainer : Theme.onSurface, textScale);
		Md3.text(graphics, font,
				Md3.ellipsize(font, track.artists(), textRight - textX - scale.dp(8), textScale * 0.92F),
				textX, artistY, playing ? Theme.onPrimaryContainer : Theme.onSurfaceVariant, textScale * 0.92F);

		if (track.paid()) {
			String vip = "VIP";
			Md3.badge(graphics, font, vip, rowX + rowW - scale.dp(12) - durationWidth - scale.dp(40),
					rowY + scale.dp(9), scale.dp(15), Theme.alpha(Theme.primary, 40), Theme.primary,
					textScale * 0.78F);
		}
		String duration = Md3.duration(track.durationMillis());
		Md3.textRight(graphics, font, duration, rowX + rowW - scale.dp(12),
				Md3.centeredTextY(font, rowY, rowH, textScale), Theme.onSurfaceVariant, textScale * 0.92F);
	}

	private void drawScrollbar(GuiGraphics graphics) {
		double max = maxScroll();
		if (max <= 0D) {
			return;
		}
		int barWidth = scale.dp(3);
		int barX = x + width - barWidth - scale.dp(1);
		int trackHeight = height - scale.dp(8);
		int barHeight = Math.max(scale.dp(24), Math.round(trackHeight * height / (float) (height + max)));
		int barY = y + scale.dp(4) + (int) Math.round((trackHeight - barHeight) * (scroll / max));
		Md3.roundRect(graphics, barX, barY, barWidth, barHeight, barWidth / 2, Theme.outline);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !inside(mouseX, mouseY)) {
			return false;
		}
		int index = (int) Math.floor((mouseY - y + scroll) / rowHeight);
		if (index >= 0 && index < tracks.size()) {
			if (onPick != null) {
				onPick.accept(index);
			}
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!inside(mouseX, mouseY) || maxScroll() <= 0D) {
			return false;
		}
		scroll -= scrollY * rowHeight * 0.6D;
		clampScroll();
		return true;
	}

	public boolean inside(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private double maxScroll() {
		return Math.max(0D, tracks.size() * (double) rowHeight - height);
	}

	private void clampScroll() {
		scroll = Math.max(0D, Math.min(scroll, maxScroll()));
	}
}
