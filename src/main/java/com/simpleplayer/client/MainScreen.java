package com.simpleplayer.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Placeholder while the UI is rebuilt from scratch. */
public class MainScreen extends Screen {

	public MainScreen() {
		super(Component.literal("SimplePlayer"));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(0, 0, width, height, 0xCC000000);
		graphics.drawCenteredString(font, "UI 重建中", width / 2, height / 2 - 10, 0xFFFFFFFF);
		graphics.drawCenteredString(font, "按 Esc 关闭", width / 2, height / 2 + 6, 0xFF808080);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
