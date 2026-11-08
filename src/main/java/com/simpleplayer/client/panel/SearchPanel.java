package com.simpleplayer.client.panel;

import java.util.List;
import java.util.concurrent.CompletionException;

import com.simpleplayer.client.PlayerScreen;
import com.simpleplayer.config.ClientConfig;
import com.simpleplayer.music.MusicSource;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.Track;
import com.simpleplayer.ui.Md3;
import com.simpleplayer.ui.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Search page: source switch, query field and the resulting track list. */
public final class SearchPanel extends PlayerPanel {

	private final TrackListView list;
	private final EditBox input;

	private MusicSource source = MusicSource.NETEASE;
	private List<Track> results = List.of();
	private String status = "";
	private boolean error;
	private boolean loading;
	private int generation;

	private boolean compact;
	private int barX;
	private int barY;
	private int barWidth;
	private int barHeight;
	private int contentWidth;
	private int sourceX;
	private int sourceWidth;
	private int chipsY;
	private int statusY;
	private int listY;
	private int listHeight;

	public SearchPanel(PlayerScreen screen) {
		super(screen);
		this.list = new TrackListView(screen.scale());
		this.list.setOnPick(index -> PlaybackService.INSTANCE.playQueue(results, index));
		this.input = new EditBox(screen.font(), 0, 0, 100, 18, Component.empty());
		this.input.setBordered(false);
		this.input.setMaxLength(96);
		this.input.setTextColor(Theme.onSurface);
		this.input.setTextColorUneditable(Theme.onSurfaceVariant);
		updateHint();
	}

	@Override
	public String title() {
		return I18n.get("gui.simpleplayer.search.title");
	}

	@Override
	public String subtitle() {
		return I18n.get("gui.simpleplayer.search.subtitle");
	}

	@Override
	protected void onLayout(int x, int y, int width, int height) {
		compact = height < 560;
		int pad = compact ? scale().dp(10) : scale().dp(20);
		barX = x + pad;
		barY = y + pad;
		contentWidth = width - pad * 2;
		if (compact) {
			// Short windows put the source switch next to the query field: one header row instead
			// of the two-row search bar + chip block, which buys the result list two extra rows.
			barHeight = scale().dp(40);
			sourceWidth = Math.min(scale().dp(122), Math.max(scale().dp(74), contentWidth / 4));
			sourceX = barX + contentWidth - sourceWidth;
			barWidth = contentWidth - sourceWidth - scale().dp(8);
			statusY = barY + barHeight + scale().dp(6);
			listY = statusY + scale().dp(28);
		} else {
			// Wide windows get a centred, bounded reading column rather than a bar glued to the
			// left edge with a thousand empty pixels beside it.
			contentWidth = Math.min(contentWidth, scale().dp(920));
			barX = x + pad + (width - pad * 2 - contentWidth) / 2;
			barWidth = contentWidth;
			barHeight = scale().dp(48);
			sourceWidth = 0;
			chipsY = barY + barHeight + scale().dp(12);
			statusY = chipsY + scale().dp(34);
			listY = statusY + scale().dp(28);
		}
		listHeight = Math.max(scale().dp(40), y + height - scale().dp(10) - listY);
		list.setRowHeight(compact ? scale().dp(40) : scale().dp(60));
		list.setBounds(barX, listY, compact ? contentWidth : barWidth, listHeight);
		input.setX(barX + scale().dp(compact ? 36 : 46));
		input.setY(barY + (barHeight - 18) / 2);
		input.setWidth(barWidth - scale().dp(compact ? 36 : 46) - scale().dp(compact ? 58 : 76));
		input.setHeight(18);
	}

	@Override
	protected void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		drawSearchBar(graphics, mouseX, mouseY);
		if (compact) {
			drawSourceToggle(graphics, mouseX, mouseY);
		} else {
			drawSources(graphics, mouseX, mouseY);
		}
		drawStatus(graphics, mouseX, mouseY);

		list.setTracks(results);
		list.setPlayingIndex(-1);
		list.render(graphics, font(), bodyText(), mouseX, mouseY);
		if (results.isEmpty()) {
			drawEmpty(graphics, barX, listY, compact ? contentWidth : barWidth, listHeight);
		}
	}

	private void drawSearchBar(GuiGraphics graphics, int mouseX, int mouseY) {
		Md3.stroke(graphics, barX, barY, barWidth, barHeight, scale().dp(Theme.SHAPE_XL), 1,
				input.isFocused() ? Theme.primary : Theme.outlineVariant, Theme.surfaceContainerHigh);
		Md3.search(graphics, barX + scale().dp(compact ? 18 : 22), barY + barHeight / 2,
				scale().dp(compact ? 15 : 17), Theme.onSurfaceVariant);

		input.setTextColor(Theme.onSurface);
		input.setTextColorUneditable(Theme.onSurfaceVariant);
		input.setVisible(!screen.hasSheet());
		if (input.isVisible()) {
			input.render(graphics, mouseX, mouseY, 0F);
		}

		int submitWidth = scale().dp(compact ? 48 : 56);
		int submitHeight = scale().dp(compact ? 28 : 32);
		int submitX = barX + barWidth - submitWidth - scale().dp(8);
		int submitY = barY + (barHeight - submitHeight) / 2;
		boolean enabled = !input.getValue().trim().isEmpty() && !loading;
		boolean hovered = enabled && inside(mouseX, mouseY, submitX, submitY, submitWidth, submitHeight);
		Md3.roundRect(graphics, submitX, submitY, submitWidth, submitHeight, submitHeight / 2,
				enabled ? (hovered ? Theme.primary : Theme.primaryContainer) : Theme.surfaceContainerHighest);
		Md3.textCentered(graphics, font(), loading ? "…" : I18n.get("gui.simpleplayer.search.button"),
				submitX + submitWidth / 2,
				Md3.centeredTextY(font(), submitY, submitHeight, bodyText() * 0.92F),
				enabled ? (hovered ? Theme.onPrimary : Theme.onPrimaryContainer) : Theme.onSurfaceVariant,
				bodyText() * 0.92F);
		hit(submitX, submitY, submitWidth, submitHeight, this::search);
	}

	private void drawSourceToggle(GuiGraphics graphics, int mouseX, int mouseY) {
		String label = I18n.get(source == MusicSource.NETEASE ? "gui.simpleplayer.login.netease"
				: "gui.simpleplayer.login.bilibili");
		boolean hovered = inside(mouseX, mouseY, sourceX, barY, sourceWidth, barHeight);
		Md3.roundRect(graphics, sourceX, barY, sourceWidth, barHeight, scale().dp(Theme.SHAPE_M),
				hovered ? Theme.secondaryContainer : Theme.surfaceContainerHigh);
		Md3.textCentered(graphics, font(), Md3.ellipsize(font(), label, sourceWidth - scale().dp(12), bodyText() * 0.9F),
				sourceX + sourceWidth / 2,
				Md3.centeredTextY(font(), barY, barHeight, bodyText() * 0.9F),
				hovered ? Theme.onSecondaryContainer : Theme.onSurfaceVariant, bodyText() * 0.9F);
		hit(sourceX, barY, sourceWidth, barHeight, this::cycleSource);
	}

	private void cycleSource() {
		MusicSource next = source == MusicSource.NETEASE ? MusicSource.BILIBILI : MusicSource.NETEASE;
		boolean available = next == MusicSource.NETEASE ? ClientConfig.get().neteaseEnabled
				: ClientConfig.get().bilibiliEnabled;
		if (!available) {
			status = I18n.get("gui.simpleplayer.search.source_disabled");
			return;
		}
		switchSource(next);
	}

	private void drawSources(GuiGraphics graphics, int mouseX, int mouseY) {
		int chipHeight = scale().dp(32);
		int gap = scale().dp(8);
		int chipX = barX;
		for (MusicSource option : MusicSource.values()) {
			String label = I18n.get(option == MusicSource.NETEASE ? "gui.simpleplayer.login.netease"
					: "gui.simpleplayer.login.bilibili");
			int chipWidth = Md3.textWidth(font(), label, bodyText() * 0.92F) + scale().dp(26);
			boolean selected = option == source;
			boolean available = option == MusicSource.NETEASE ? ClientConfig.get().neteaseEnabled
					: ClientConfig.get().bilibiliEnabled;
			boolean hovered = available && inside(mouseX, mouseY, chipX, chipsY, chipWidth, chipHeight);
			int background = selected ? Theme.secondaryContainer
					: hovered ? Theme.surfaceContainerHigh : Theme.surfaceContainerLow;
			Md3.roundRect(graphics, chipX, chipsY, chipWidth, chipHeight, chipHeight / 2, background);
			if (!selected && !available) {
				Md3.stroke(graphics, chipX, chipsY, chipWidth, chipHeight, chipHeight / 2, 1, Theme.outlineVariant,
						Theme.surfaceContainerLow);
			}
			Md3.textCentered(graphics, font(), label, chipX + chipWidth / 2,
					Md3.centeredTextY(font(), chipsY, chipHeight, bodyText() * 0.92F),
					selected ? Theme.onSecondaryContainer : available ? Theme.onSurfaceVariant : Theme.outline,
					bodyText() * 0.92F);
			if (available) {
				hit(chipX, chipsY, chipWidth, chipHeight, () -> switchSource(option));
			} else {
				hit(chipX, chipsY, chipWidth, chipHeight,
						() -> status = I18n.get("gui.simpleplayer.search.source_disabled"));
			}
			chipX += chipWidth + gap;
		}
	}

	private void drawStatus(GuiGraphics graphics, int mouseX, int mouseY) {
		String left;
		int color = Theme.onSurfaceVariant;
		if (loading) {
			left = I18n.get("gui.simpleplayer.search.searching");
		} else if (!status.isEmpty()) {
			left = status;
			color = error ? Theme.error : Theme.onSurfaceVariant;
		} else if (!results.isEmpty()) {
			left = I18n.get("gui.simpleplayer.search.results", results.size());
		} else {
			left = "";
		}
		int statusWidth = compact ? contentWidth : barWidth;
		Md3.text(graphics, font(), Md3.ellipsize(font(), left, statusWidth - scale().dp(90), bodyText() * 0.92F),
				barX, statusY, color, bodyText() * 0.92F);

		if (!results.isEmpty()) {
			String playAll = I18n.get("gui.simpleplayer.search.play_all");
			int textWidth = Md3.textWidth(font(), playAll, bodyText() * 0.92F);
			int pillWidth = textWidth + scale().dp(24);
			int pillHeight = scale().dp(28);
			int pillX = barX + statusWidth - pillWidth;
			int pillY = statusY - scale().dp(4);
			boolean hovered = inside(mouseX, mouseY, pillX, pillY, pillWidth, pillHeight);
			Md3.roundRect(graphics, pillX, pillY, pillWidth, pillHeight, pillHeight / 2,
					hovered ? Theme.primary : Theme.primaryContainer);
			Md3.textCentered(graphics, font(), playAll, pillX + pillWidth / 2,
					Md3.centeredTextY(font(), pillY, pillHeight, bodyText() * 0.92F),
					hovered ? Theme.onPrimary : Theme.onPrimaryContainer, bodyText() * 0.92F);
			hit(pillX, pillY, pillWidth, pillHeight, this::playAll);
		}

		if (loading) {
			Md3.indeterminate(graphics, barX, statusY + scale().dp(18), statusWidth, 2,
					Theme.surfaceContainerHighest, Theme.primary, System.currentTimeMillis());
		}
	}

	private void drawEmpty(GuiGraphics graphics, int x, int y, int width, int height) {
		int centerX = x + width / 2;
		int centerY = y + height / 2 - scale().dp(24);
		int circle = scale().dp(72);
		Md3.roundRect(graphics, centerX - circle / 2, centerY - circle / 2, circle, circle, circle / 2,
				Theme.surfaceContainerHigh);
		if (loading) {
			Md3.refresh(graphics, centerX, centerY, scale().dp(24), Theme.primary);
		} else {
			Md3.search(graphics, centerX, centerY, scale().dp(26), Theme.onSurfaceVariant);
		}
		String title;
		String subtitle;
		if (loading) {
			title = I18n.get("gui.simpleplayer.search.searching");
			subtitle = I18n.get("gui.simpleplayer.search.idle.subtitle");
		} else if (!status.isEmpty()) {
			title = status;
			subtitle = I18n.get("gui.simpleplayer.search.empty.subtitle");
		} else {
			title = I18n.get("gui.simpleplayer.search.idle.title");
			subtitle = I18n.get("gui.simpleplayer.search.idle.subtitle");
		}
		Md3.textCentered(graphics, font(), Md3.ellipsize(font(), title, width - scale().dp(40), bodyText()),
				centerX, centerY + circle / 2 + scale().dp(16), Theme.onSurface, bodyText());
		Md3.textCentered(graphics, font(), Md3.ellipsize(font(), subtitle, width - scale().dp(40),
				bodyText() * 0.9F), centerX, centerY + circle / 2 + scale().dp(34), Theme.onSurfaceVariant,
				bodyText() * 0.9F);
	}

	private void switchSource(MusicSource option) {
		if (source == option) {
			return;
		}
		source = option;
		generation++;
		loading = false;
		results = List.of();
		status = "";
		error = false;
		list.resetScroll();
		updateHint();
	}

	private void updateHint() {
		input.setHint(Component.literal(I18n.get(source == MusicSource.NETEASE
				? "gui.simpleplayer.search.hint.netease" : "gui.simpleplayer.search.hint.bilibili")));
	}

	private void playAll() {
		if (!results.isEmpty()) {
			PlaybackService.INSTANCE.playQueue(results, 0);
		}
	}

	public void search() {
		String query = input.getValue().trim();
		if (query.isEmpty() || loading) {
			return;
		}
		boolean available = source == MusicSource.NETEASE ? ClientConfig.get().neteaseEnabled
				: ClientConfig.get().bilibiliEnabled;
		if (!available) {
			status = I18n.get("gui.simpleplayer.search.source_disabled");
			return;
		}
		final int token = ++generation;
		loading = true;
		error = false;
		status = "";
		results = List.of();
		list.resetScroll();
		PlaybackService.INSTANCE.search(source, query).whenComplete((tracks, error) -> {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.execute(() -> {
				if (token != generation) {
					return;
				}
				loading = false;
				if (error != null) {
					status = I18n.get("gui.simpleplayer.search.failed", message(error));
					SearchPanel.this.error = true;
					results = List.of();
				} else {
					results = tracks;
					SearchPanel.this.error = false;
					status = tracks.isEmpty() ? I18n.get("gui.simpleplayer.search.no_results") : "";
				}
			});
		});
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
	public void onShown() {
		input.setVisible(!screen.hasSheet());
	}

	@Override
	public void onHidden() {
		input.setVisible(false);
		input.setFocused(false);
	}

	@Override
	public void setInputVisible(boolean visible) {
		input.setVisible(visible);
		if (!visible) {
			input.setFocused(false);
		}
	}

	public boolean inputFocused() {
		return input.isFocused();
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (input.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				search();
				return true;
			}
			return input.keyPressed(keyCode, scanCode, modifiers);
		}
		return false;
	}

	public boolean charTyped(char codePoint, int modifiers) {
		return input.isFocused() && input.charTyped(codePoint, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (list.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (input.isVisible() && insideInput(mouseX, mouseY)) {
			screen.setFocused(input);
			return input.mouseClicked(mouseX, mouseY, button);
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean insideInput(double mouseX, double mouseY) {
		return mouseX >= input.getX() && mouseX < input.getX() + input.getWidth() && mouseY >= input.getY()
				&& mouseY < input.getY() + input.getHeight();
	}
}
