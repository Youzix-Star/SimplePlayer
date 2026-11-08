package com.simpleplayer.ui;

import java.util.List;

/** A rectangle with a click action, rebuilt by a screen or panel every frame. */
public final class Hit {

	private final int x;
	private final int y;
	private final int width;
	private final int height;
	private final Runnable action;

	public Hit(int x, int y, int width, int height, Runnable action) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.action = action;
	}

	public boolean inside(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public void run() {
		if (action != null) {
			action.run();
		}
	}

	/** Runs the topmost hit under the pointer. */
	public static boolean dispatch(List<Hit> hits, double mouseX, double mouseY) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.inside(mouseX, mouseY)) {
				hit.run();
				return true;
			}
		}
		return false;
	}
}
