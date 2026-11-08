package com.simpleplayer.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Minimal Material Design 3 token set and drawing helpers (orange seed, MC font).
 */
public final class Md3 {

	// Color roles (orange seed)
	public static final int PRIMARY = 0xFFFFB86B;
	public static final int ON_PRIMARY = 0xFF3B2300;
	public static final int PRIMARY_CONTAINER = 0xFF5C3F14;
	public static final int ON_PRIMARY_CONTAINER = 0xFFFFDDB8;
	public static final int SURFACE = 0xFF121216;
	public static final int SURFACE_CONTAINER = 0xFF1C1C22;
	public static final int SURFACE_CONTAINER_HIGH = 0xFF26262E;
	public static final int ON_SURFACE = 0xFFEDE7E0;
	public static final int ON_SURFACE_VARIANT = 0xFFA8A29C;
	public static final int OUTLINE = 0xFF4A4844;
	public static final int SECONDARY_CONTAINER = 0xFF33281C;

	private Md3() {
	}

	/** Material 3 corner radii. */
	public static final int SHAPE_XS = 4;
	public static final int SHAPE_S = 8;
	public static final int SHAPE_M = 12;
	public static final int SHAPE_L = 16;

	public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.min(r, Math.min(w / 2, h / 2));
		if (r <= 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x + r, y, x + w - r, y + h, color);
		g.fill(x, y + r, x + r, y + h - r, color);
		g.fill(x + w - r, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			double d = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - d * d)));
			g.fill(x + inset, y + i, x + r, y + i + 1, color);
			g.fill(x + w - r, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - 1 - i, x + r, y + h - i, color);
			g.fill(x + w - r, y + h - 1 - i, x + w - inset, y + h - i, color);
		}
	}

	public static void roundRectOutline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		roundRect(g, x, y, w, h, r, color);
		roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), 0);
	}

	/** Filled rounded pill with centred label. */
	public static void pill(GuiGraphics g, int x, int y, int w, int h, int color, String label, int textColor,
			net.minecraft.client.gui.Font font) {
		roundRect(g, x, y, w, h, h / 2, color);
		g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, textColor, false);
	}

	/** Scaled text, used for headline styles. */
	public static void text(GuiGraphics g, net.minecraft.client.gui.Font font, String value, int x, int y, int color,
			float scale) {
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.pose().scale(scale, scale, 1F);
		g.drawString(font, value, 0, 0, color, false);
		g.pose().popPose();
	}
}
