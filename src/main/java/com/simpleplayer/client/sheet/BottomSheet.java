package com.simpleplayer.client.sheet;

import java.util.ArrayList;
import java.util.List;

import com.simpleplayer.client.PlayerScreen;
import com.simpleplayer.ui.Hit;
import com.simpleplayer.ui.Md3;
import com.simpleplayer.ui.Theme;
import com.simpleplayer.ui.UiScale;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Base class for the modal bottom sheets (queue and login).
 *
 * <p>Geometry is recomputed every frame from the screen size, so a window resize while a sheet is
 * open simply lays it out again. The sheet slides up over ~200 ms; clicks that land on the scrim
 * close it. Rendering happens without a pose translation, which keeps scissor rectangles and the
 * embedded text fields in screen coordinates.</p>
 */
public abstract class BottomSheet {

	protected final PlayerScreen screen;
	protected final UiScale scale;
	protected final List<Hit> hits = new ArrayList<>();

	private long openedAt;
	private long closingAt = -1L;
	private float progress;

	protected int x;
	protected int y;
	protected int width;
	protected int height;

	protected BottomSheet(PlayerScreen screen) {
		this.screen = screen;
		this.scale = screen.scale();
	}

	public final void open() {
		openedAt = System.currentTimeMillis();
		closingAt = -1L;
		onOpen();
	}

	public final void close() {
		if (closingAt < 0L) {
			closingAt = System.currentTimeMillis();
			onClose();
		}
	}

	public final boolean isFinished() {
		return closingAt >= 0L && System.currentTimeMillis() - closingAt >= 220L;
	}

	public final boolean isClosing() {
		return closingAt >= 0L;
	}

	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		progress = updateProgress();
		if (isFinished()) {
			return;
		}
		layout();
		hits.clear();

		graphics.fill(0, 0, screen.width, screen.height,
				Theme.alpha(0xFF000000, Math.round(150F * progress)));
		Md3.shadow(graphics, x, y, width, height, scale.dp(Theme.SHAPE_XL));
		Md3.roundRect(graphics, x, y, width, height, scale.dp(Theme.SHAPE_XL), Theme.surfaceContainerLow);

		int pad = scale.dp(24);
		int handleWidth = scale.dp(32);
		Md3.roundRect(graphics, x + (width - handleWidth) / 2, y + scale.dp(10), handleWidth, scale.dp(4),
				scale.dp(2), Theme.outlineVariant);

		Md3.text(graphics, screen.font(), title(), x + pad, y + scale.dp(24), Theme.onSurface,
				screen.titleText() * 0.88F);

		int closeSize = scale.dp(32);
		int closeX = x + width - pad - closeSize;
		int closeY = y + scale.dp(16);
		boolean closeHovered = inside(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
		Md3.roundRect(graphics, closeX, closeY, closeSize, closeSize, closeSize / 2,
				closeHovered ? Theme.surfaceContainerHighest : Theme.surfaceContainerHigh);
		Md3.close(graphics, closeX + closeSize / 2, closeY + closeSize / 2, scale.dp(10),
				closeHovered ? Theme.onSurface : Theme.onSurfaceVariant);
		hit(closeX, closeY, closeSize, closeSize, this::close);

		int contentX = x + pad;
		int contentY = y + scale.dp(68);
		int contentWidth = width - pad * 2;
		int contentHeight = y + height - scale.dp(20) - contentY;
		drawContent(graphics, mouseX, mouseY, partialTick, contentX, contentY, contentWidth, contentHeight);
	}

	private float updateProgress() {
		if (closingAt >= 0L) {
			float value = 1F - Math.min(1F, (System.currentTimeMillis() - closingAt) / 220F);
			return ease(value);
		}
		return ease(Math.min(1F, (System.currentTimeMillis() - openedAt) / 220F));
	}

	private void layout() {
		int margin = scale.dp(16);
		width = Math.min(preferredWidth(), screen.width - margin * 2);
		height = Math.min(preferredHeight(), screen.height - margin * 2);
		x = (screen.width - width) / 2;
		int bottom = screen.height - scale.dp(12);
		int slide = Math.round((1F - progress) * scale.dp(26));
		y = bottom - height + slide;
	}

	protected abstract String title();

	protected abstract int preferredWidth();

	protected abstract int preferredHeight();

	protected abstract void drawContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int areaX,
			int areaY, int areaWidth, int areaHeight);

	protected void onOpen() {
	}

	protected void onClose() {
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (isFinished()) {
			return false;
		}
		if (isClosing()) {
			return true;
		}
		if (!inside(mouseX, mouseY, x, y, width, height)) {
			close();
			return true;
		}
		if (progress < 0.85F) {
			return true;
		}
		return Hit.dispatch(hits, mouseX, mouseY);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return false;
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return false;
	}

	public boolean charTyped(char codePoint, int modifiers) {
		return false;
	}

	protected final void hit(int x, int y, int width, int height, Runnable action) {
		hits.add(new Hit(x, y, width, height, action));
	}

	protected final boolean ready() {
		return progress >= 0.85F && !isClosing();
	}

	protected final boolean inside(double mouseX, double mouseY, int left, int top, int boxWidth, int boxHeight) {
		return mouseX >= left && mouseX < left + boxWidth && mouseY >= top && mouseY < top + boxHeight;
	}

	private static float ease(float value) {
		float clamped = Math.max(0F, Math.min(1F, value));
		return 1F - (1F - clamped) * (1F - clamped);
	}
}
