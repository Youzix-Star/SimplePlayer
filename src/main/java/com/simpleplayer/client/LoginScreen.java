package com.simpleplayer.client;

import com.simpleplayer.config.ClientConfig;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.bilibili.BilibiliApi;
import com.simpleplayer.music.netease.NeteaseApi;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Login screen: QR scan for both sources, plus manual cookie entry as a fallback.
 */
public class LoginScreen extends Screen {

	private final Screen parent;
	private EditBox neteaseBox;
	private EditBox bilibiliBox;
	private volatile String status = "";
	private volatile String qrStatus = "";
	private volatile boolean busy;
	private volatile ResourceLocation qrTexture;

	private int left;
	private int contentWidth;
	private int qrTop;
	private int qrSize;
	private float scale;

	public LoginScreen(Screen parent) {
		super(Component.literal("登录"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		contentWidth = Math.min(width - 24, 300);
		left = (width - contentWidth) / 2;
		scale = Math.min(1F, (height - 40) / 320F);
		qrSize = Math.max(64, Math.round(128 * scale));
		int buttonWidth = (contentWidth - 6) / 2;
		int buttonsRow = Math.round(28 * scale);

		addRenderableWidget(Button.builder(Component.literal("网易云扫码"), button -> startQr(true))
				.bounds(left, buttonsRow, buttonWidth, 18).build());
		addRenderableWidget(Button.builder(Component.literal("B站扫码"), button -> startQr(false))
				.bounds(left + buttonWidth + 6, buttonsRow, buttonWidth, 18).build());

		qrTop = buttonsRow + 22;

		neteaseBox = new EditBox(font, left, qrTop + qrSize + 26, contentWidth, 18, Component.literal("网易云 Cookie"));
		neteaseBox.setMaxLength(2048);
		neteaseBox.setValue(ClientConfig.get().neteaseCookie);
		neteaseBox.setHint(Component.literal("MUSIC_U=..."));
		addRenderableWidget(neteaseBox);

		bilibiliBox = new EditBox(font, left, neteaseBox.getY() + 44, contentWidth, 18, Component.literal("B站 Cookie"));
		bilibiliBox.setMaxLength(2048);
		bilibiliBox.setValue(ClientConfig.get().bilibiliCookie);
		bilibiliBox.setHint(Component.literal("SESSDATA=..."));
		addRenderableWidget(bilibiliBox);

		int buttonsY = bilibiliBox.getY() + 32;
		addRenderableWidget(Button.builder(Component.literal("保存"), button -> save())
				.bounds(left, buttonsY, 70, 18).build());
		addRenderableWidget(Button.builder(Component.literal("验证"), button -> verify())
				.bounds(left + 78, buttonsY, 70, 18).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), button -> {
			if (minecraft != null) {
				minecraft.setScreen(parent);
			}
		}).bounds(left + 156, buttonsY, 70, 18).build());
	}

	private void startQr(boolean netease) {
		if (busy) {
			return;
		}
		busy = true;
		qrStatus = "获取二维码…";
		qrTexture = null;
		Thread thread = new Thread(() -> {
			try {
				String url;
				String key;
				if (netease) {
					NeteaseApi.QrCode code = PlaybackService.INSTANCE.netease().createQrCode();
					url = code.url();
					key = code.key();
				} else {
					BilibiliApi.QrCode code = PlaybackService.INSTANCE.bilibili().createQrCode();
					url = code.url();
					key = code.key();
				}
				if (minecraft != null) {
					minecraft.execute(() -> qrTexture = QrTextures.get(url));
				}
				qrStatus = "请用手机扫码";
				for (int i = 0; i < 90; i++) {
					Thread.sleep(2000);
					if (netease) {
						NeteaseApi.LoginState state = PlaybackService.INSTANCE.netease().checkQrCode(new NeteaseApi.QrCode(key, url));
						qrStatus = switch (state) {
							case WAITING -> "等待扫码…";
							case SCANNED -> "已扫码，请在手机上确认";
							case CONFIRMED -> "网易云登录成功";
							case EXPIRED -> "二维码已过期";
							default -> "登录失败";
						};
						if (state == NeteaseApi.LoginState.CONFIRMED) {
							ClientConfig.get().neteaseCookie = PlaybackService.INSTANCE.netease().getCookie();
							ClientConfig.save();
							break;
						}
						if (state == NeteaseApi.LoginState.EXPIRED) {
							break;
						}
					} else {
						BilibiliApi.LoginState state = PlaybackService.INSTANCE.bilibili().checkQrCode(new BilibiliApi.QrCode(url, key));
						qrStatus = switch (state) {
							case WAITING -> "等待扫码…";
							case SCANNED -> "已扫码，请在手机上确认";
							case CONFIRMED -> "B站登录成功";
							case EXPIRED -> "二维码已过期";
							default -> "登录失败";
						};
						if (state == BilibiliApi.LoginState.CONFIRMED) {
							ClientConfig.get().bilibiliCookie = PlaybackService.INSTANCE.bilibili().getCookie();
							ClientConfig.save();
							break;
						}
						if (state == BilibiliApi.LoginState.EXPIRED) {
							break;
						}
					}
				}
				PlaybackService.INSTANCE.applyCookies();
			} catch (Exception e) {
				qrStatus = "登录出错：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			} finally {
				busy = false;
			}
		}, "SimplePlayer-QR");
		thread.setDaemon(true);
		thread.start();
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
		Thread thread = new Thread(() -> {
			String netease = PlaybackService.INSTANCE.netease().accountName();
			String bilibili = PlaybackService.INSTANCE.bilibili().accountName();
			status = "网易云：" + (netease == null ? "未登录/失效" : netease)
					+ "  B站：" + (bilibili == null ? "未登录/失效" : bilibili);
		}, "SimplePlayer-Login");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(0, 0, width, height, 0x99000000);

		graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
		graphics.drawString(font, "扫码登录（推荐）", left, Math.round(14 * scale), 0xFFE0A458, false);

		ResourceLocation texture = qrTexture;
		if (texture != null) {
			graphics.blit(texture, left, qrTop, qrSize, qrSize, 0F, 0F, 128, 128, 128, 128);
		} else {
			graphics.fill(left, qrTop, left + qrSize, qrTop + qrSize, 0x22FFFFFF);
		}
		graphics.drawString(font, qrStatus, left, qrTop + qrSize + 6, 0xFFE0A458, false);

		graphics.drawString(font, "网易云 Cookie（MUSIC_U）", left, neteaseBox.getY() - 12, 0xFFCCCCCC, false);
		graphics.drawString(font, "B站 Cookie（SESSDATA）", left, bilibiliBox.getY() - 12, 0xFFCCCCCC, false);
		graphics.drawString(font, status, left, bilibiliBox.getY() + 54, 0xFFE0A458, false);

		for (var renderable : renderables) {
			renderable.render(graphics, mouseX, mouseY, partialTick);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
