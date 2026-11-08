package com.simpleplayer.ui;

/**
 * Material 3 colour roles for SimplePlayer.
 *
 * <p>The palette is seeded on orange, then retinted whenever a new cover finishes loading:
 * {@link #applyCover(int[])} picks the most chromatic colour in the extracted cover palette and
 * derives a full dark-scheme set from it. Role changes are interpolated over roughly half a second so
 * skipping through a queue does not strobe the interface.</p>
 */
public final class Theme {

	public static final int DEFAULT_SEED = 0xFFFFB86B;

	// Material 3 roles actually used by the player UI.
	public static int primary;
	public static int onPrimary;
	public static int primaryContainer;
	public static int onPrimaryContainer;
	public static int secondaryContainer;
	public static int onSecondaryContainer;
	public static int surface;
	public static int surfaceContainerLow;
	public static int surfaceContainer;
	public static int surfaceContainerHigh;
	public static int surfaceContainerHighest;
	public static int onSurface;
	public static int onSurfaceVariant;
	public static int outline;
	public static int outlineVariant;
	public static int error;
	public static int onError;

	public static final int SHAPE_XS = 4;
	public static final int SHAPE_S = 8;
	public static final int SHAPE_M = 12;
	public static final int SHAPE_L = 16;
	public static final int SHAPE_XL = 28;

	private static final int ROLE_COUNT = 17;
	private static final int[] CURRENT = new int[ROLE_COUNT];
	private static final int[] FROM = new int[ROLE_COUNT];
	private static final int[] TARGET = new int[ROLE_COUNT];
	private static final long TRANSITION_MS = 520L;

	private static long transitionStartedAt = -1L;
	private static int activeSeed = DEFAULT_SEED;

	static {
		int[] palette = paletteFor(DEFAULT_SEED);
		System.arraycopy(palette, 0, CURRENT, 0, ROLE_COUNT);
		System.arraycopy(palette, 0, TARGET, 0, ROLE_COUNT);
		publish();
	}

	private Theme() {
	}

	/** Retints the whole UI from a cover palette, e.g. the one stored in {@code CoverCache}. */
	public static void applyCover(int[] palette) {
		if (palette == null || palette.length == 0) {
			applySeed(DEFAULT_SEED);
			return;
		}
		applySeed(pickAccent(palette));
	}

	public static void applySeed(int seed) {
		if ((seed & 0xFFFFFF) == (activeSeed & 0xFFFFFF)) {
			return;
		}
		activeSeed = seed | 0xFF000000;
		System.arraycopy(CURRENT, 0, FROM, 0, ROLE_COUNT);
		System.arraycopy(paletteFor(activeSeed), 0, TARGET, 0, ROLE_COUNT);
		transitionStartedAt = System.currentTimeMillis();
	}

	public static void reset() {
		applySeed(DEFAULT_SEED);
	}

	/** Advances the colour transition. Called once per player-screen frame. */
	public static void tick() {
		if (transitionStartedAt < 0L) {
			return;
		}
		float raw = (System.currentTimeMillis() - transitionStartedAt) / (float) TRANSITION_MS;
		if (raw >= 1F) {
			System.arraycopy(TARGET, 0, CURRENT, 0, ROLE_COUNT);
			transitionStartedAt = -1L;
			publish();
			return;
		}
		float t = easeOutCubic(raw);
		for (int i = 0; i < ROLE_COUNT; i++) {
			CURRENT[i] = mix(FROM[i], TARGET[i], t);
		}
		publish();
	}

	/** Multiplies an ARGB colour's alpha (0–255). */
	public static int alpha(int color, int alpha) {
		return (color & 0xFFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
	}

	public static int mix(int from, int to, float amount) {
		float t = Math.max(0F, Math.min(1F, amount));
		int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
		int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static void publish() {
		primary = CURRENT[0];
		onPrimary = CURRENT[1];
		primaryContainer = CURRENT[2];
		onPrimaryContainer = CURRENT[3];
		secondaryContainer = CURRENT[4];
		onSecondaryContainer = CURRENT[5];
		surface = CURRENT[6];
		surfaceContainerLow = CURRENT[7];
		surfaceContainer = CURRENT[8];
		surfaceContainerHigh = CURRENT[9];
		surfaceContainerHighest = CURRENT[10];
		onSurface = CURRENT[11];
		onSurfaceVariant = CURRENT[12];
		outline = CURRENT[13];
		outlineVariant = CURRENT[14];
		error = CURRENT[15];
		onError = CURRENT[16];
	}

	private static int pickAccent(int[] palette) {
		int best = DEFAULT_SEED;
		float bestScore = -1F;
		for (int color : palette) {
			float[] hsb = rgbToHsb(color);
			if (hsb[2] < 0.12F || hsb[1] < 0.10F) {
				continue;
			}
			float score = hsb[1] * (0.35F + hsb[2]);
			if (score > bestScore) {
				bestScore = score;
				best = color | 0xFF000000;
			}
		}
		// Nearly grey covers are deliberately tinted with the brand orange instead of turning
		// the whole player into a black-and-white wireframe.
		return bestScore < 0.10F ? DEFAULT_SEED : best;
	}

	private static int[] paletteFor(int seed) {
		float[] hsb = rgbToHsb(seed);
		float hue = hsb[0];
		float saturation = clamp(hsb[1], 0.24F, 0.86F);
		float brightness = clamp(hsb[2], 0.35F, 0.90F);

		int primary = hsbToRgb(hue, clamp(saturation, 0.46F, 0.78F), clamp(brightness + 0.10F, 0.66F, 0.86F));
		int onPrimary = hsbToRgb(hue, saturation, 0.16F);
		int primaryContainer = hsbToRgb(hue, saturation * 0.88F, 0.30F);
		int onPrimaryContainer = hsbToRgb(hue, saturation * 0.28F, 0.93F);
		int secondaryContainer = hsbToRgb(hue, saturation * 0.52F, 0.24F);
		int onSecondaryContainer = hsbToRgb(hue, saturation * 0.18F, 0.90F);

		int surface = hsbToRgb(hue, saturation * 0.15F, 0.055F);
		int surfaceLow = hsbToRgb(hue, saturation * 0.16F, 0.075F);
		int surfaceContainer = hsbToRgb(hue, saturation * 0.18F, 0.100F);
		int surfaceHigh = hsbToRgb(hue, saturation * 0.20F, 0.145F);
		int surfaceHighest = hsbToRgb(hue, saturation * 0.21F, 0.190F);

		int onSurface = hsbToRgb(hue, saturation * 0.06F, 0.93F);
		int onSurfaceVariant = hsbToRgb(hue, saturation * 0.10F, 0.70F);
		int outline = hsbToRgb(hue, saturation * 0.13F, 0.43F);
		int outlineVariant = hsbToRgb(hue, saturation * 0.11F, 0.27F);

		return new int[] {
				primary, onPrimary, primaryContainer, onPrimaryContainer,
				secondaryContainer, onSecondaryContainer,
				surface, surfaceLow, surfaceContainer, surfaceHigh, surfaceHighest,
				onSurface, onSurfaceVariant, outline, outlineVariant,
				0xFFF2B8B5, 0xFF601410
		};
	}

	private static float[] rgbToHsb(int color) {
		float r = ((color >>> 16) & 0xFF) / 255F;
		float g = ((color >>> 8) & 0xFF) / 255F;
		float b = (color & 0xFF) / 255F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float hue = 0F;
		if (delta > 0.0001F) {
			if (max == r) {
				hue = ((g - b) / delta) % 6F;
			} else if (max == g) {
				hue = (b - r) / delta + 2F;
			} else {
				hue = (r - g) / delta + 4F;
			}
			hue /= 6F;
			if (hue < 0F) {
				hue += 1F;
			}
		}
		return new float[] { hue, max <= 0.0001F ? 0F : delta / max, max };
	}

	private static int hsbToRgb(float hue, float saturation, float brightness) {
		float h = ((hue % 1F) + 1F) % 1F * 6F;
		float s = clamp(saturation, 0F, 1F);
		float v = clamp(brightness, 0F, 1F);
		int sector = (int) Math.floor(h);
		float f = h - sector;
		float p = v * (1F - s);
		float q = v * (1F - s * f);
		float t = v * (1F - s * (1F - f));
		float r;
		float g;
		float b;
		switch (sector % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return 0xFF000000
				| (Math.round(r * 255F) << 16)
				| (Math.round(g * 255F) << 8)
				| Math.round(b * 255F);
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float easeOutCubic(float value) {
		float t = 1F - clamp(value, 0F, 1F);
		return 1F - t * t * t;
	}
}
