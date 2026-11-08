package com.simpleplayer.ui;

/** Adaptive dp scale so one layout fits phones and desktops. */
public final class UiScale {

	private final float factor;

	public UiScale(int screenWidth, int screenHeight) {
		float base = Math.min(screenWidth / 360F, screenHeight / 240F);
		factor = Math.max(0.75F, Math.min(1.6F, base));
	}

	public int dp(int value) {
		return Math.round(value * factor);
	}

	public float f() {
		return factor;
	}
}
