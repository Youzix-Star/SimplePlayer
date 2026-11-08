package com.simpleplayer.client.sheet;

import java.util.List;

import com.simpleplayer.client.PlayerScreen;
import com.simpleplayer.client.panel.TrackListView;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.Track;
import com.simpleplayer.ui.Md3;
import com.simpleplayer.ui.Theme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/** Modal queue: jump to any track, see what is playing and clear the whole list. */
public final class QueueSheet extends BottomSheet {

	private final TrackListView list;
	private List<Track> tracks = List.of();

	public QueueSheet(PlayerScreen screen) {
		super(screen);
		this.list = new TrackListView(screen.scale());
		this.list.setShowIndex(true);
		this.list.setOnPick(index -> PlaybackService.INSTANCE.playAt(index));
	}

	@Override
	protected String title() {
		return I18n.get("gui.simpleplayer.queue.title");
	}

	@Override
	protected int preferredWidth() {
		return scale.dp(560);
	}

	@Override
	protected int preferredHeight() {
		return scale.dp(560);
	}

	@Override
	protected void drawContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int areaX, int areaY,
			int areaWidth, int areaHeight) {
		tracks = PlaybackService.INSTANCE.queue();
		int rowHeight = screen.height < 420 ? scale.dp(48) : scale.dp(60);
		list.setRowHeight(rowHeight);

		String count = I18n.get("gui.simpleplayer.queue.count", tracks.size());
		Track current = PlaybackService.INSTANCE.current();
		int playingIndex = PlaybackService.INSTANCE.index();
		String detail;
		if (current == null || playingIndex < 0) {
			detail = I18n.get("gui.simpleplayer.player.nothing");
		} else {
			detail = I18n.get("gui.simpleplayer.queue.now", playingIndex + 1, tracks.size());
		}
		Md3.text(graphics, screen.font(), count, areaX, areaY, Theme.onSurface, screen.bodyText());
		Md3.text(graphics, screen.font(), detail, areaX,
				areaY + scale.dp(18), Theme.onSurfaceVariant, screen.bodyText() * 0.92F);

		int clearWidth = scale.dp(48);
		int clearHeight = scale.dp(28);
		int clearX = areaX + areaWidth - clearWidth;
		int clearY = areaY - scale.dp(2);
		boolean clearEnabled = !tracks.isEmpty();
		boolean clearHovered = inside(mouseX, mouseY, clearX, clearY, clearWidth, clearHeight);
		if (clearEnabled) {
			Md3.roundRect(graphics, clearX, clearY, clearWidth, clearHeight, clearHeight / 2,
					clearHovered ? Theme.surfaceContainerHighest : Theme.surfaceContainerHigh);
			Md3.textCentered(graphics, screen.font(), I18n.get("gui.simpleplayer.queue.clear"), clearX + clearWidth / 2,
					clearY + scale.dp(8), clearHovered ? Theme.error : Theme.onSurfaceVariant,
					screen.bodyText() * 0.92F);
			hit(clearX, clearY, clearWidth, clearHeight, () -> {
				PlaybackService.INSTANCE.clear();
				list.resetScroll();
				screen.toast(I18n.get("gui.simpleplayer.queue.cleared"));
			});
		}

		int listY = areaY + scale.dp(40);
		int listHeight = Math.max(scale.dp(60), areaY + areaHeight - listY - scale.dp(22));
		list.setBounds(areaX, listY, areaWidth, listHeight);
		list.setTracks(tracks);
		list.setPlayingIndex(playbackIndex());
		list.render(graphics, screen.font(), screen.bodyText(), mouseX, mouseY);

		if (tracks.isEmpty()) {
			drawEmpty(graphics, areaX, listY, areaWidth, listHeight);
		} else {
			long total = 0L;
			for (Track track : tracks) {
				total += Math.max(0L, track.durationMillis());
			}
			String footer = I18n.get("gui.simpleplayer.queue.total", Md3.duration(total));
			Md3.textRight(graphics, screen.font(), footer, areaX + areaWidth, areaY + areaHeight - scale.dp(14),
					Theme.onSurfaceVariant, screen.bodyText() * 0.9F);
		}
	}

	private int playbackIndex() {
		if (PlaybackService.INSTANCE.current() == null) {
			return -1;
		}
		int index = PlaybackService.INSTANCE.index();
		return index >= 0 && index < tracks.size() ? index : -1;
	}

	private void drawEmpty(GuiGraphics graphics, int x, int y, int width, int height) {
		int centerX = x + width / 2;
		int centerY = y + height / 2 - scale.dp(16);
		int circle = scale.dp(64);
		Md3.roundRect(graphics, centerX - circle / 2, centerY - circle / 2, circle, circle, circle / 2,
				Theme.surfaceContainerHigh);
		Md3.queue(graphics, centerX, centerY, scale.dp(24), Theme.onSurfaceVariant);
		Md3.textCentered(graphics, screen.font(), I18n.get("gui.simpleplayer.queue.empty.title"), centerX,
				centerY + circle / 2 + scale.dp(16), Theme.onSurface, screen.bodyText());
		Md3.textCentered(graphics, screen.font(), I18n.get("gui.simpleplayer.queue.empty.subtitle"), centerX,
				centerY + circle / 2 + scale.dp(34), Theme.onSurfaceVariant, screen.bodyText() * 0.9F);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (ready() && list.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
}
