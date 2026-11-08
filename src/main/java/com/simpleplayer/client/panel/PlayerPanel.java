package com.simpleplayer.client.panel;

import java.util.ArrayList;
import java.util.List;

import com.simpleplayer.client.PlayerScreen;
import com.simpleplayer.ui.Hit;
import com.simpleplayer.ui.UiScale;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * One page of the player shell. Panels own their scroll state and network requests; the shell owns
 * the rail, app bar, player bar and modal sheets.
 */
public abstract class PlayerPanel {

	protected final PlayerScreen screen;
	protected final List<Hit> hits = new ArrayList<>();
	protected int x;
	protected int y;
	protected int width;
	protected int height;

	protected PlayerPanel(PlayerScreen screen) {
		this.screen = screen;
	}

	public final void layout(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		onLayout(x, y, width, height);
	}

	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		hits.clear();
		draw(graphics, mouseX, mouseY, partialTick);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return Hit.dispatch(hits, mouseX, mouseY);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return false;
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return false;
	}

	public void onShown() {
	}

	public void onHidden() {
	}

	public void onLoginChanged() {
	}

	/** Hides the page's text field while a modal sheet owns the keyboard. */
	public void setInputVisible(boolean visible) {
	}

	public void tick() {
	}

	public abstract String title();

	public abstract String subtitle();

	protected abstract void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

	protected void onLayout(int x, int y, int width, int height) {
	}

	protected final void hit(int x, int y, int width, int height, Runnable action) {
		hits.add(new Hit(x, y, width, height, action));
	}

	protected final UiScale scale() {
		return screen.scale();
	}

	protected final Font font() {
		return screen.font();
	}

	protected final float bodyText() {
		return screen.bodyText();
	}

	protected final float titleText() {
		return screen.titleText();
	}

	protected final boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
