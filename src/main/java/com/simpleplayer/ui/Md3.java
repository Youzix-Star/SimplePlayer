package com.simpleplayer.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Small Material 3 drawing kit for the player screen: rounded surfaces, elevation, text and the
 * handful of vector icons the UI needs. Everything is drawn with quads so no texture atlas or
 * custom font is required.
 */
public final class Md3 {

	private Md3() {
	}

	// ── Surfaces ────────────────────────────────────────────────────────────

	public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		if (r == 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			int inset = inset(r, i);
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
		}
	}

	/** Draws a one-pixel (or {@code thickness} pixel) outline; {@code inner} is the fill underneath. */
	public static void stroke(GuiGraphics g, int x, int y, int w, int h, int radius, int thickness, int color,
			int inner) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int t = Math.max(1, Math.min(thickness, Math.min(w, h) / 2));
		roundRect(g, x, y, w, h, radius, color);
		roundRect(g, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, radius - t), inner);
	}

	public static void shadow(GuiGraphics g, int x, int y, int w, int h, int radius) {
		roundRect(g, x - 2, y + 3, w + 4, h + 3, radius + 2, 0x26000000);
		roundRect(g, x - 1, y + 4, w + 2, h + 2, radius + 1, 0x33000000);
	}

	public static void verticalGradient(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int strips = Math.min(h, 48);
		for (int i = 0; i < strips; i++) {
			int y0 = y + h * i / strips;
			int y1 = y + h * (i + 1) / strips;
			float t = (i + 0.5F) / strips;
			g.fill(x, y0, x + w, Math.max(y0 + 1, y1), Theme.mix(top, bottom, t));
		}
	}

	/** Draws a cover with rounded corners by masking the four outside corners with the card colour. */
	public static void image(GuiGraphics g, ResourceLocation texture, int textureWidth, int textureHeight, int x,
			int y, int size, int radius, int background) {
		if (texture == null || size <= 0) {
			return;
		}
		g.blit(texture, x, y, size, size, 0F, 0F, textureWidth, textureHeight, textureWidth, textureHeight);
		int r = Math.max(0, Math.min(radius, size / 2));
		for (int i = 0; i < r; i++) {
			int inset = inset(r, i);
			g.fill(x, y + i, x + inset, y + i + 1, background);
			g.fill(x + size - inset, y + i, x + size, y + i + 1, background);
			g.fill(x, y + size - 1 - i, x + inset, y + size - i, background);
			g.fill(x + size - inset, y + size - 1 - i, x + size, y + size - i, background);
		}
	}

	public static void placeholderCover(GuiGraphics g, int x, int y, int size, int radius, int color, int glyph) {
		roundRect(g, x, y, size, size, radius, color);
		int noteX = x + size / 2;
		int noteY = y + size / 2;
		int note = Math.max(3, size / 5);
		g.fill(noteX - note, noteY - note, noteX + 1, noteY + 1, glyph);
		g.fill(noteX - note, noteY - note, noteX - note + 2, noteY + note / 2, glyph);
		g.fill(noteX + note - 4, noteY - note - 2, noteX + note - 2, noteY + note / 2, glyph);
	}

	private static int inset(int radius, int row) {
		double dy = radius - row - 0.5D;
		double dx = Math.sqrt(Math.max(0D, radius * (double) radius - dy * dy));
		return (int) Math.round(radius - dx);
	}

	// ── Text ────────────────────────────────────────────────────────────────

	public static void text(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
		if (text == null || text.isEmpty()) {
			return;
		}
		if (Math.abs(scale - 1F) < 0.01F) {
			g.drawString(font, text, x, y, color, false);
			return;
		}
		g.pose().pushPose();
		g.pose().translate(x, y, 0F);
		g.pose().scale(scale, scale, 1F);
		g.drawString(font, text, 0, 0, color, false);
		g.pose().popPose();
	}

	public static void textCentered(GuiGraphics g, Font font, String text, int centerX, int y, int color,
			float scale) {
		text(g, font, text, centerX - Math.round(textWidth(font, text, scale) / 2F), y, color, scale);
	}

	public static void textRight(GuiGraphics g, Font font, String text, int right, int y, int color, float scale) {
		text(g, font, text, right - Math.round(textWidth(font, text, scale)), y, color, scale);
	}

	public static int textWidth(Font font, String text, float scale) {
		return Math.round(font.width(text) * scale);
	}

	public static String ellipsize(Font font, String text, int maxWidth, float scale) {
		if (text == null) {
			return "";
		}
		int limit = Math.max(4, Math.round(maxWidth / Math.max(0.01F, scale)));
		if (font.width(text) <= limit) {
			return text;
		}
		int ellipsis = font.width("…");
		String cut = font.plainSubstrByWidth(text, Math.max(1, limit - ellipsis));
		return cut + "…";
	}

	/** Text baseline for vertically centring an unscaled line inside a box. */
	public static int centeredTextY(Font font, int y, int height, float scale) {
		return y + Math.max(0, Math.round((height - font.lineHeight * scale) / 2F));
	}

	public static String duration(long millis) {
		if (millis <= 0L) {
			return "--:--";
		}
		long seconds = millis / 1000L;
		long hours = seconds / 3600L;
		long minutes = (seconds % 3600L) / 60L;
		long remaining = seconds % 60L;
		if (hours > 0L) {
			return String.format("%d:%02d:%02d", hours, minutes, remaining);
		}
		return String.format("%d:%02d", minutes, remaining);
	}

	// ── Icons ───────────────────────────────────────────────────────────────

	public static void play(GuiGraphics g, int centerX, int centerY, int size, int color) {
		int left = centerX - size / 2;
		for (int i = 0; i < size; i++) {
			int height = Math.max(1, size - i);
			int top = centerY - height / 2;
			g.fill(left + i, top, left + i + 1, top + height, color);
		}
	}

	public static void pause(GuiGraphics g, int centerX, int centerY, int size, int color) {
		int bar = Math.max(2, size / 3);
		int gap = Math.max(2, size / 5);
		g.fill(centerX - gap / 2 - bar, centerY - size / 2, centerX - gap / 2, centerY + size / 2, color);
		g.fill(centerX + gap / 2, centerY - size / 2, centerX + gap / 2 + bar, centerY + size / 2, color);
	}

	public static void previous(GuiGraphics g, int centerX, int centerY, int size, int color) {
		int bar = Math.max(2, size / 6);
		int left = centerX - size / 2;
		g.fill(left, centerY - size / 2, left + bar, centerY + size / 2, color);
		triangleLeft(g, left + bar + 1, centerY - size / 2, size - bar - 1, size, color);
	}

	public static void next(GuiGraphics g, int centerX, int centerY, int size, int color) {
		int bar = Math.max(2, size / 6);
		int right = centerX + size / 2;
		triangleRight(g, right - bar - size + bar, centerY - size / 2, size - bar - 1, size, color);
		g.fill(right - bar, centerY - size / 2, right, centerY + size / 2, color);
	}

	private static void triangleRight(GuiGraphics g, int x, int y, int w, int h, int color) {
		for (int i = 0; i < w; i++) {
			int bar = Math.max(1, Math.round((w - i) * (float) h / w));
			int top = y + (h - bar) / 2;
			g.fill(x + i, top, x + i + 1, top + bar, color);
		}
	}

	private static void triangleLeft(GuiGraphics g, int x, int y, int w, int h, int color) {
		for (int i = 0; i < w; i++) {
			int bar = Math.max(1, Math.round((i + 1) * (float) h / w));
			int top = y + (h - bar) / 2;
			g.fill(x + i, top, x + i + 1, top + bar, color);
		}
	}

	public static void search(GuiGraphics g, int centerX, int centerY, int size, int color) {
		ring(g, centerX - size / 6, centerY - size / 6, Math.max(3, size / 3), Math.max(1, size / 8), color);
		line(g, centerX + size / 6, centerY + size / 6, centerX + size / 2, centerY + size / 2,
				Math.max(1, size / 8), color);
	}

	public static void close(GuiGraphics g, int centerX, int centerY, int size, int color) {
		int half = size / 2;
		line(g, centerX - half, centerY - half, centerX + half, centerY + half, 2, color);
		line(g, centerX + half, centerY - half, centerX - half, centerY + half, 2, color);
	}

	public static void back(GuiGraphics g, int centerX, int centerY, int size, int color) {
		int half = size / 2;
		line(g, centerX + half, centerY - half, centerX - half, centerY, 2, color);
		line(g, centerX - half, centerY, centerX + half, centerY + half, 2, color);
	}

	public static void queue(GuiGraphics g, int centerX, int centerY, int size, int color) {
		int half = size / 2;
		int gap = Math.max(3, size / 3);
		for (int i = -1; i <= 1; i++) {
			int y = centerY + i * gap;
			g.fill(centerX - half + 3, y, centerX + half, y + 1, color);
			g.fill(centerX - half, y, centerX - half + 2, y + 2, color);
		}
	}

	public static void settings(GuiGraphics g, int centerX, int centerY, int size, int color) {
		int half = size / 2;
		int gap = Math.max(3, size / 3);
		for (int i = -1; i <= 1; i++) {
			int y = centerY + i * gap;
			g.fill(centerX - half, y, centerX + half, y + 1, color);
			int knob = centerX + (i == -1 ? -half / 3 : i == 0 ? half / 3 : 0);
			g.fill(knob, y - 2, knob + 3, y + 3, color);
		}
	}

	public static void refresh(GuiGraphics g, int centerX, int centerY, int size, int color) {
		int radius = Math.max(3, size / 2);
		for (int angle = 30; angle <= 330; angle += 8) {
			double radians = Math.toRadians(angle);
			int x = centerX + (int) Math.round(Math.cos(radians) * radius);
			int y = centerY + (int) Math.round(Math.sin(radians) * radius);
			g.fill(x, y, x + 2, y + 2, color);
		}
		int ax = centerX + (int) Math.round(Math.cos(Math.toRadians(30)) * radius);
		int ay = centerY + (int) Math.round(Math.sin(Math.toRadians(30)) * radius);
		triangleRight(g, ax - 2, ay - 4, 5, 6, color);
	}

	public static void check(GuiGraphics g, int centerX, int centerY, int size, int color) {
		int half = size / 2;
		line(g, centerX - half, centerY, centerX - 1, centerY + half - 1, 2, color);
		line(g, centerX - 1, centerY + half - 1, centerX + half, centerY - half, 2, color);
	}

	public static void equalizer(GuiGraphics g, int x, int y, int width, int height, int color, long time) {
		int bars = 3;
		int gap = Math.max(1, width / 6);
		int barWidth = Math.max(2, (width - gap * (bars - 1)) / bars);
		for (int i = 0; i < bars; i++) {
			double phase = time * 0.006D + i * 1.9D;
			float level = 0.35F + 0.65F * (float) Math.abs(Math.sin(phase));
			int barHeight = Math.max(2, Math.round(height * level));
			int bx = x + i * (barWidth + gap);
			roundRect(g, bx, y + height - barHeight, barWidth, barHeight, barWidth / 2, color);
		}
	}

	private static void ring(GuiGraphics g, int centerX, int centerY, int radius, int thickness, int color) {
		for (int angle = 0; angle < 360; angle += 5) {
			double radians = Math.toRadians(angle);
			int x = centerX + (int) Math.round(Math.cos(radians) * radius);
			int y = centerY + (int) Math.round(Math.sin(radians) * radius);
			g.fill(x, y, x + Math.max(1, thickness), y + Math.max(1, thickness), color);
		}
	}

	public static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int thickness, int color) {
		int dx = Math.abs(x2 - x1);
		int dy = Math.abs(y2 - y1);
		int sx = x1 < x2 ? 1 : -1;
		int sy = y1 < y2 ? 1 : -1;
		int error = dx - dy;
		int x = x1;
		int y = y1;
		while (true) {
			g.fill(x, y, x + thickness, y + thickness, color);
			if (x == x2 && y == y2) {
				break;
			}
			int doubled = error * 2;
			if (doubled > -dy) {
				error -= dy;
				x += sx;
			}
			if (doubled < dx) {
				error += dx;
				y += sy;
			}
		}
	}

	// ── Controls ────────────────────────────────────────────────────────────

	public static void linearProgress(GuiGraphics g, int x, int y, int width, int height, float fraction, int track,
			int fill) {
		roundRect(g, x, y, width, height, height / 2, track);
		int filled = Math.round(width * Math.max(0F, Math.min(1F, fraction)));
		if (filled > 0) {
			roundRect(g, x, y, filled, height, height / 2, fill);
		}
	}

	public static void indeterminate(GuiGraphics g, int x, int y, int width, int height, int track, int fill,
			long time) {
		roundRect(g, x, y, width, height, height / 2, track);
		int segment = Math.max(height * 4, width / 3);
		double phase = (time % 1600L) / 1600D;
		int left = x + (int) Math.round((width + segment) * phase) - segment;
		int right = left + segment;
		int clippedLeft = Math.max(x, left);
		int clippedRight = Math.min(x + width, right);
		if (clippedRight > clippedLeft) {
			roundRect(g, clippedLeft, y, clippedRight - clippedLeft, height, height / 2, fill);
		}
	}

	public static void badge(GuiGraphics g, Font font, String text, int x, int y, int height, int background,
			int foreground, float scale) {
		int width = textWidth(font, text, scale) + 10;
		roundRect(g, x, y, width, height, height / 2, background);
		text(g, font, text, x + 5, y + Math.max(1, (height - Math.round(font.lineHeight * scale)) / 2),
				foreground, scale);
	}
}
