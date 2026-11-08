package com.simpleplayer.ui;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Pulls a small, stable colour palette out of cover art.
 *
 * <p>Pixels are downsampled into a coarse RGB histogram, washed-out and near-black buckets are
 * penalised, and the remaining buckets are picked by population times chroma. The result feeds
 * {@link Theme#applyCover(int[])}, which turns the most colourful entry into a Material 3 scheme.</p>
 */
public final class CoverPalette {

	private static final int BUCKETS = 4096;
	private static final int[] FALLBACK = {
			0xFFFFB86B, 0xFF8AB4F8, 0xFFF2B8B5, 0xFF81C995, 0xFFC58AF9, 0xFFE8C071
	};

	private CoverPalette() {
	}

	public static int[] fallback() {
		return FALLBACK.clone();
	}

	public static int[] extract(NativeImage image) {
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
			return fallback();
		}
		int width = image.getWidth();
		int height = image.getHeight();
		int step = Math.max(1, (int) Math.ceil(Math.sqrt(width * (double) height / 4096D)));

		int[] counts = new int[BUCKETS];
		int[] reds = new int[BUCKETS];
		int[] greens = new int[BUCKETS];
		int[] blues = new int[BUCKETS];
		int samples = 0;

		for (int y = 0; y < height; y += step) {
			for (int x = 0; x < width; x += step) {
				if ((image.getLuminanceOrAlpha(x, y) & 0xFF) < 96) {
					continue;
				}
				int red = image.getRedOrLuminance(x, y) & 0xFF;
				int green = image.getGreenOrLuminance(x, y) & 0xFF;
				int blue = image.getBlueOrLuminance(x, y) & 0xFF;
				int bucket = ((red >> 4) << 8) | ((green >> 4) << 4) | (blue >> 4);
				counts[bucket]++;
				reds[bucket] += red;
				greens[bucket] += green;
				blues[bucket] += blue;
				samples++;
			}
		}
		if (samples == 0) {
			return fallback();
		}

		double[] scores = new double[BUCKETS];
		int candidates = 0;
		for (int i = 0; i < BUCKETS; i++) {
			if (counts[i] == 0) {
				continue;
			}
			double red = reds[i] / (double) counts[i] / 255D;
			double green = greens[i] / (double) counts[i] / 255D;
			double blue = blues[i] / (double) counts[i] / 255D;
			double max = Math.max(red, Math.max(green, blue));
			double min = Math.min(red, Math.min(green, blue));
			double saturation = max <= 0.0001D ? 0D : (max - min) / max;
			double luminance = 0.2126D * red + 0.7152D * green + 0.0722D * blue;
			if (luminance < 0.035D || luminance > 0.965D) {
				continue;
			}
			double population = counts[i] / (double) samples;
			scores[i] = population * (0.22D + saturation) * (0.30D + luminance);
			candidates++;
		}
		if (candidates == 0) {
			return fallback();
		}

		int[] picked = new int[6];
		int pickedCount = 0;
		for (int n = 0; n < 6; n++) {
			int best = -1;
			double bestScore = -1D;
			for (int i = 0; i < BUCKETS; i++) {
				if (scores[i] <= 0D) {
					continue;
				}
				int color = average(counts[i], reds[i], greens[i], blues[i]);
				boolean close = false;
				for (int j = 0; j < pickedCount; j++) {
					if (distance(color, picked[j]) < 42) {
						close = true;
						break;
					}
				}
				if (!close && scores[i] > bestScore) {
					bestScore = scores[i];
					best = i;
				}
			}
			if (best < 0) {
				break;
			}
			picked[pickedCount] = average(counts[best], reds[best], greens[best], blues[best]);
			pickedCount++;
			scores[best] = 0D;
		}
		if (pickedCount == 0) {
			return fallback();
		}
		int[] result = new int[pickedCount];
		System.arraycopy(picked, 0, result, 0, pickedCount);
		return result;
	}

	private static int average(int count, int red, int green, int blue) {
		return 0xFF000000
				| ((red / count) << 16)
				| ((green / count) << 8)
				| (blue / count);
	}

	private static double distance(int a, int b) {
		int dr = ((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF);
		int dg = ((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF);
		int db = (a & 0xFF) - (b & 0xFF);
		return Math.sqrt(dr * dr + dg * dg + db * db);
	}
}
