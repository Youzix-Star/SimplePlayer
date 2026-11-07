package com.simpleplayer.client;

import com.simpleplayer.config.ClientConfig;
import com.simpleplayer.music.PlaybackService;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cookie-based login for both sources. QR login will replace this later.
 */
public class LoginScreen extends Screen {

	private final Screen parent;
	private EditBox neteaseBox;
	private EditBox bilibiliBox;
	private String status = "";

	public LoginScreen(Screen parent) {
		super(Component.literal("登录"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = width / 2 - 150;
		int top = 60;

		neteaseBox = new EditBox(font, left, top + 16, 300, 18, Component.literal("网易云 Cookie"));
		neteaseBox.setMaxLength(2048);
		neteaseBox.setValue(ClientConfig.get().neteaseCookie);
		neteaseBox.setHint(Component.literal("MUSIC_U=..."));
		addRenderableWidget(neteaseBox);

		bilibiliBox = new EditBox(font, left, top + 62, 300, 18, Component.literal("哔哩哔哩 Cookie"));
		bilibiliBox.setMaxLength(2048);
		bilibiliBox.setValue(ClientConfig.get().bilibiliCookie);
		bilibiliBox.setHint(Component.literal("SESSDATA=..."));
		addRenderableWidget(bilibiliBox);

		addRenderableWidget(Button.builder(Component.literal("保存"), button -> save())
				.bounds(left, top + 96, 70, 20).build());
		addRenderableWidget(Button.builder(Component.literal("验证"), button -> verify())
				.bounds(left + 78, top + 96, 70, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), button -> {
			if (minecraft != null) {
				minecraft.setScreen(parent);
			}
		}).bounds(left + 156, top + 96, 70, 20).build());
	}

	private void save() {
		ClientConfig config = ClientConfig.get();
		config.neteaseCookie = neteaseBox.getValue().trim();
		config.bilibiliCookie = bilibiliBox.getValue().trim();
		ClientConfig.save();
		PlaybackService.INSTANCE.applyCookies();
		status = "已保存";
	}

	private void verify() {
		save();
		status = "验证中…";
		PlaybackService service = PlaybackService.INSTANCE;
		Thread thread = new Thread(() -> {
			String netease = service.netease().accountName();
			String bilibili = service.bilibili().accountName();
			String message = "网易云：" + (netease == null ? "未登录或 Cookie 失效" : netease)
					+ "　哔哩哔哩：" + (bilibili == null ? "未登录或 Cookie 失效" : bilibili);
			if (minecraft != null) {
				minecraft.execute(() -> status = message);
			}
		}, "SimplePlayer-Login");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int left = width / 2 - 150;
		graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFFFF);
		graphics.drawString(font, "网易云 Cookie（MUSIC_U）", left, 60, 0xFFCCCCCC, false);
		graphics.drawString(font, "哔哩哔哩 Cookie（SESSDATA）", left, 106, 0xFFCCCCCC, false);
		graphics.drawString(font, "登录后可播放高音质与会员曲目", left, 172, 0xFF777777, false);
		graphics.drawString(font, status, left, 190, 0xFFE0A458, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
