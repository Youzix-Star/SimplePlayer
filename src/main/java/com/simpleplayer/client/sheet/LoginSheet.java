package com.simpleplayer.client.sheet;

import com.simpleplayer.client.PlayerScreen;
import com.simpleplayer.client.QrTextures;
import com.simpleplayer.config.ClientConfig;
import com.simpleplayer.music.PlaybackService;
import com.simpleplayer.music.bilibili.BilibiliApi;
import com.simpleplayer.music.netease.NeteaseApi;
import com.simpleplayer.ui.Md3;
import com.simpleplayer.ui.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * Login sheet for both sources. QR login is the default path; a collapsible text field accepts a
 * pasted cookie for people who already have one. The polling thread is generation-guarded so
 * switching tabs or closing the sheet cancels the previous login attempt.
 */
public final class LoginSheet extends BottomSheet {

	private static final int QR_PIXELS = 128;

	private volatile boolean netease = true;
	private volatile String status = "";
	private volatile ResourceLocation qrTexture;
	private volatile int generation;

	private boolean manual;
	private EditBox cookieBox;
	private int cookieX;
	private int cookieY;
	private int cookieWidth;
	private int cookieHeight;

	public LoginSheet(PlayerScreen screen) {
		super(screen);
	}

	@Override
	protected String title() {
		return I18n.get("gui.simpleplayer.login.title");
	}

	@Override
	protected int preferredWidth() {
		return scale.dp(460);
	}

	@Override
	protected int preferredHeight() {
		return scale.dp(560);
	}

	@Override
	protected void onOpen() {
		manual = false;
		if (cookieBox != null) {
			cookieBox.setVisible(false);
			cookieBox.setFocused(false);
		}
		if (loggedIn()) {
			status = I18n.get("gui.simpleplayer.login.logged_in", sourceName());
		} else {
			refreshQr();
		}
	}

	@Override
	protected void onClose() {
		generation++;
		if (cookieBox != null) {
			if (cookieBox.isFocused()) {
				screen.setFocused(null);
			}
			cookieBox.setFocused(false);
			cookieBox.setVisible(false);
		}
	}

	@Override
	protected void drawContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int areaX, int areaY,
			int areaWidth, int areaHeight) {
		if (loggedIn()) {
			drawTabs(graphics, mouseX, mouseY, areaX, areaY, areaWidth);
			drawLoggedIn(graphics, mouseX, mouseY, areaX, areaY + scale.dp(52), areaWidth,
					areaHeight - scale.dp(52));
			return;
		}
		if (areaHeight < 280) {
			drawTinyLogin(graphics, mouseX, mouseY, areaX, areaY, areaWidth, areaHeight);
			return;
		}
		drawTabs(graphics, mouseX, mouseY, areaX, areaY, areaWidth);

		int noticeY = areaY + areaHeight - scale.dp(22);
		if (manual) {
			drawManual(graphics, mouseX, mouseY, areaX, areaY + scale.dp(52), areaWidth);
			drawManualToggle(graphics, mouseX, mouseY, areaX, noticeY, areaWidth);
			return;
		}

		boolean tight = areaHeight < 430;
		int refreshHeight = scale.dp(32);
		int refreshY = tight ? noticeY - scale.dp(30) : noticeY - refreshHeight - scale.dp(26);
		int qrY = areaY + scale.dp(52);
		int qrLimit = tight ? refreshY : refreshY;
		int maxCard = qrLimit - qrY - scale.dp(20);
		int qrSize = Math.min(scale.dp(190), Math.min(areaWidth - scale.dp(80),
				maxCard - scale.dp(12)));
		qrSize = Math.max(scale.dp(56), qrSize);
		int qrX = areaX + (areaWidth - qrSize) / 2;
		int card = qrSize + scale.dp(12);
		Md3.roundRect(graphics, qrX - scale.dp(6), qrY - scale.dp(6), card, card, scale.dp(Theme.SHAPE_L),
				0xFFF7F7F7);
		ResourceLocation texture = qrTexture;
		if (texture != null) {
			graphics.blit(texture, qrX, qrY, qrSize, qrSize, 0F, 0F, QR_PIXELS, QR_PIXELS, QR_PIXELS, QR_PIXELS);
		} else {
			Md3.textCentered(graphics, screen.font(), I18n.get("gui.simpleplayer.common.loading"),
					qrX + qrSize / 2, qrY + qrSize / 2 - 4, Theme.onSurfaceVariant, screen.bodyText() * 0.9F);
		}
		hit(qrX, qrY, qrSize, qrSize, this::refreshQr);

		int statusY = qrY + card + scale.dp(6);
		Md3.textCentered(graphics, screen.font(),
				Md3.ellipsize(screen.font(), status, areaWidth, screen.bodyText() * 0.92F),
				areaX + areaWidth / 2, statusY, Theme.onSurfaceVariant, screen.bodyText() * 0.92F);

		String refresh = I18n.get("gui.simpleplayer.login.refresh");
		if (tight) {
			int textWidth = Md3.textWidth(screen.font(), refresh, screen.bodyText() * 0.92F);
			int textX = areaX + (areaWidth - textWidth) / 2;
			boolean hovered = inside(mouseX, mouseY, textX, refreshY - scale.dp(4), textWidth,
					scale.dp(20));
			Md3.text(graphics, screen.font(), refresh, textX, refreshY,
					hovered ? Theme.primary : Theme.onSurfaceVariant, screen.bodyText() * 0.92F);
			hit(textX, refreshY - scale.dp(4), textWidth, scale.dp(20), this::refreshQr);
		} else {
			int refreshWidth = Md3.textWidth(screen.font(), refresh, screen.bodyText()) + scale.dp(28);
			int refreshX = areaX + (areaWidth - refreshWidth) / 2;
			boolean refreshHovered = inside(mouseX, mouseY, refreshX, refreshY, refreshWidth, refreshHeight);
			Md3.roundRect(graphics, refreshX, refreshY, refreshWidth, refreshHeight, refreshHeight / 2,
					refreshHovered ? Theme.primaryContainer : Theme.surfaceContainerHigh);
			Md3.textCentered(graphics, screen.font(), refresh, refreshX + refreshWidth / 2,
					Md3.centeredTextY(screen.font(), refreshY, refreshHeight, screen.bodyText()),
					refreshHovered ? Theme.onPrimaryContainer : Theme.onSurface, screen.bodyText());
			hit(refreshX, refreshY, refreshWidth, refreshHeight, this::refreshQr);
			if (areaHeight > 430) {
				Md3.textCentered(graphics, screen.font(), I18n.get("gui.simpleplayer.login.notice"),
						areaX + areaWidth / 2, refreshY + refreshHeight + scale.dp(6),
						Theme.onSurfaceVariant, screen.bodyText() * 0.82F);
			}
		}

		drawManualToggle(graphics, mouseX, mouseY, areaX, noticeY, areaWidth);
	}

	private void drawManualToggle(GuiGraphics graphics, int mouseX, int mouseY, int areaX, int noticeY,
			int areaWidth) {
		String toggle = I18n.get("gui.simpleplayer.login.manual");
		int toggleWidth = Md3.textWidth(screen.font(), toggle, screen.bodyText() * 0.92F);
		int toggleX = areaX + (areaWidth - toggleWidth) / 2;
		boolean toggleHovered = inside(mouseX, mouseY, toggleX, noticeY - scale.dp(6), toggleWidth,
				scale.dp(20));
		Md3.text(graphics, screen.font(), toggle, toggleX, noticeY - scale.dp(4),
				toggleHovered ? Theme.primary : Theme.onSurfaceVariant, screen.bodyText() * 0.92F);
		hit(toggleX, noticeY - scale.dp(6), toggleWidth, scale.dp(20), () -> {
			manual = !manual;
			if (cookieBox != null) {
				cookieBox.setVisible(manual);
				if (!manual) {
					cookieBox.setFocused(false);
					screen.setFocused(null);
				}
			}
		});
	}

	private void drawTinyLogin(GuiGraphics graphics, int mouseX, int mouseY, int areaX, int areaY, int areaWidth,
			int areaHeight) {
		int tabHeight = scale.dp(24);
		int tabWidth = Math.min(areaWidth, scale.dp(260));
		int tabX = areaX + (areaWidth - tabWidth) / 2;
		Md3.roundRect(graphics, tabX, areaY, tabWidth, tabHeight, tabHeight / 2, Theme.surfaceContainerHigh);
		int half = tabWidth / 2;
		for (int i = 0; i < 2; i++) {
			boolean selected = (i == 0) == netease;
			Md3.roundRect(graphics, tabX + i * half, areaY, half, tabHeight, tabHeight / 2,
					selected ? Theme.secondaryContainer : Theme.surfaceContainerHigh);
			String label = I18n.get(i == 0 ? "gui.simpleplayer.login.netease" : "gui.simpleplayer.login.bilibili");
			Md3.textCentered(graphics, screen.font(), label, tabX + i * half + half / 2,
					Md3.centeredTextY(screen.font(), areaY, tabHeight, screen.bodyText() * 0.78F),
					selected ? Theme.onSecondaryContainer : Theme.onSurfaceVariant, screen.bodyText() * 0.78F);
			int index = i;
			hit(tabX + i * half, areaY, half, tabHeight, () -> switchSource(index == 0));
		}

		int qrY = areaY + tabHeight + scale.dp(8);
		int available = areaY + areaHeight - qrY - scale.dp(26);
		int qrSize = Math.max(scale.dp(56), Math.min(scale.dp(140), Math.min(areaWidth - scale.dp(64), available)));
		int qrX = areaX + (areaWidth - qrSize) / 2;
		int card = qrSize + scale.dp(10);
		Md3.roundRect(graphics, qrX - scale.dp(5), qrY - scale.dp(5), card, card, scale.dp(Theme.SHAPE_M), 0xFFF7F7F7);
		ResourceLocation texture = qrTexture;
		if (texture != null) {
			graphics.blit(texture, qrX, qrY, qrSize, qrSize, 0F, 0F, QR_PIXELS, QR_PIXELS, QR_PIXELS, QR_PIXELS);
		} else {
			Md3.textCentered(graphics, screen.font(), I18n.get("gui.simpleplayer.common.loading"),
					qrX + qrSize / 2, qrY + qrSize / 2 - 4, Theme.onSurfaceVariant, screen.bodyText() * 0.78F);
		}
		hit(qrX, qrY, qrSize, qrSize, this::refreshQr);
		Md3.textCentered(graphics, screen.font(),
				Md3.ellipsize(screen.font(), status, areaWidth, screen.bodyText() * 0.78F),
				areaX + areaWidth / 2, qrY + card + scale.dp(4), Theme.onSurfaceVariant, screen.bodyText() * 0.78F);
	}

	private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY, int areaX, int areaY, int areaWidth) {
		int height = scale.dp(36);
		int tabWidth = Math.min(areaWidth, scale.dp(280));
		int startX = areaX + (areaWidth - tabWidth) / 2;
		Md3.roundRect(graphics, startX, areaY, tabWidth, height, height / 2, Theme.surfaceContainerHigh);
		int half = tabWidth / 2;
		for (int i = 0; i < 2; i++) {
			boolean selected = (i == 0) == netease;
			boolean hovered = inside(mouseX, mouseY, startX + i * half, areaY, half, height);
			int color = selected ? Theme.secondaryContainer
					: hovered ? Theme.surfaceContainerHighest : Theme.surfaceContainerHigh;
			Md3.roundRect(graphics, startX + i * half, areaY, half, height, height / 2, color);
			String label = I18n.get(i == 0 ? "gui.simpleplayer.login.netease" : "gui.simpleplayer.login.bilibili");
			Md3.textCentered(graphics, screen.font(), label, startX + i * half + half / 2,
					Md3.centeredTextY(screen.font(), areaY, height, screen.bodyText()),
					selected ? Theme.onSecondaryContainer : Theme.onSurfaceVariant, screen.bodyText() * 0.92F);
			int index = i;
			hit(startX + i * half, areaY, half, height, () -> switchSource(index == 0));
		}
	}

	private void drawLoggedIn(GuiGraphics graphics, int mouseX, int mouseY, int areaX, int areaY, int areaWidth,
			int areaHeight) {
		int centerX = areaX + areaWidth / 2;
		int circle = scale.dp(72);
		int circleY = areaY + Math.max(scale.dp(20), areaHeight / 2 - scale.dp(90));
		Md3.roundRect(graphics, centerX - circle / 2, circleY, circle, circle, circle / 2, Theme.primaryContainer);
		Md3.check(graphics, centerX, circleY + circle / 2, scale.dp(28), Theme.onPrimaryContainer);

		Md3.textCentered(graphics, screen.font(),
				Md3.ellipsize(screen.font(), status, areaWidth, screen.titleText() * 0.72F), centerX,
				circleY + circle + scale.dp(18), Theme.onSurface, screen.titleText() * 0.72F);
		Md3.textCentered(graphics, screen.font(), I18n.get("gui.simpleplayer.login.connected", sourceName()), centerX,
				circleY + circle + scale.dp(40), Theme.onSurfaceVariant, screen.bodyText() * 0.92F);

		String logout = I18n.get("gui.simpleplayer.login.logout");
		int width = Md3.textWidth(screen.font(), logout, screen.bodyText()) + scale.dp(36);
		int height = scale.dp(36);
		int x = centerX - width / 2;
		int y = circleY + circle + scale.dp(64);
		boolean hovered = inside(mouseX, mouseY, x, y, width, height);
		Md3.roundRect(graphics, x, y, width, height, height / 2,
				hovered ? Theme.surfaceContainerHighest : Theme.surfaceContainerHigh);
		Md3.textCentered(graphics, screen.font(), logout, centerX,
				Md3.centeredTextY(screen.font(), y, height, screen.bodyText()),
				hovered ? Theme.error : Theme.onSurfaceVariant, screen.bodyText());
		hit(x, y, width, height, this::logout);
	}

	private void drawManual(GuiGraphics graphics, int mouseX, int mouseY, int areaX, int areaY, int areaWidth) {
		if (cookieBox == null) {
			cookieBox = new EditBox(screen.font(), 0, 0, 100, 18, Component.empty());
			cookieBox.setMaxLength(4096);
			cookieBox.setBordered(false);
			cookieBox.setTextColor(Theme.onSurface);
			cookieBox.setTextColorUneditable(Theme.onSurfaceVariant);
			cookieBox.setHint(Component.literal(I18n.get("gui.simpleplayer.login.cookie_hint")));
			cookieBox.setVisible(manual);
		}
		int saveWidth = scale.dp(56);
		cookieX = areaX;
		cookieY = areaY;
		cookieWidth = areaWidth - saveWidth - scale.dp(10);
		cookieHeight = scale.dp(30);
		Md3.roundRect(graphics, cookieX, cookieY, cookieWidth, cookieHeight, scale.dp(Theme.SHAPE_S),
				Theme.surfaceContainerHigh);
		cookieBox.setTextColor(Theme.onSurface);
		cookieBox.setTextColorUneditable(Theme.onSurfaceVariant);
		cookieBox.setX(cookieX + scale.dp(8));
		cookieBox.setY(cookieY + (cookieHeight - 18) / 2);
		cookieBox.setWidth(cookieWidth - scale.dp(16));
		cookieBox.setHeight(18);
		cookieBox.setVisible(true);
		cookieBox.render(graphics, mouseX, mouseY, 0F);

		int saveX = cookieX + cookieWidth + scale.dp(10);
		boolean hovered = inside(mouseX, mouseY, saveX, cookieY, saveWidth, cookieHeight);
		Md3.roundRect(graphics, saveX, cookieY, saveWidth, cookieHeight, cookieHeight / 2,
				hovered ? Theme.primary : Theme.primaryContainer);
		Md3.textCentered(graphics, screen.font(), I18n.get("gui.simpleplayer.login.save"),
				saveX + saveWidth / 2, Md3.centeredTextY(screen.font(), cookieY, cookieHeight, screen.bodyText()),
				hovered ? Theme.onPrimary : Theme.onPrimaryContainer, screen.bodyText() * 0.92F);
		hit(saveX, cookieY, saveWidth, cookieHeight, this::saveCookie);
	}

	private boolean loggedIn() {
		return netease ? PlaybackService.INSTANCE.netease().loggedIn()
				: PlaybackService.INSTANCE.bilibili().loggedIn();
	}

	private String sourceName() {
		return I18n.get(netease ? "gui.simpleplayer.login.netease" : "gui.simpleplayer.login.bilibili");
	}

	private void switchSource(boolean neteaseSource) {
		if (netease == neteaseSource) {
			return;
		}
		netease = neteaseSource;
		generation++;
		manual = false;
		if (cookieBox != null) {
			cookieBox.setVisible(false);
			cookieBox.setFocused(false);
			screen.setFocused(null);
		}
		status = "";
		qrTexture = null;
		if (loggedIn()) {
			status = I18n.get("gui.simpleplayer.login.logged_in", sourceName());
		} else {
			refreshQr();
		}
	}

	/** Starts (or restarts) the QR login flow for the selected source. */
	public void refreshQr() {
		if (loggedIn()) {
			status = I18n.get("gui.simpleplayer.login.logged_in", sourceName());
			return;
		}
		final int token = ++generation;
		status = I18n.get("gui.simpleplayer.login.loading_qr");
		qrTexture = null;
		boolean source = netease;
		Thread thread = new Thread(() -> runQr(token, source), "SimplePlayer-Login");
		thread.setDaemon(true);
		thread.start();
	}

	private void runQr(int token, boolean source) {
		String url;
		String key;
		try {
			if (source) {
				NeteaseApi.QrCode code = PlaybackService.INSTANCE.netease().createQrCode();
				url = code.url();
				key = code.key();
			} else {
				BilibiliApi.QrCode code = PlaybackService.INSTANCE.bilibili().createQrCode();
				url = code.url();
				key = code.key();
			}
		} catch (Exception e) {
			post(token, I18n.get("gui.simpleplayer.login.failed"));
			return;
		}
		Minecraft.getInstance().execute(() -> {
			if (!isCurrent(token)) {
				return;
			}
			qrTexture = QrTextures.get(url);
			status = I18n.get("gui.simpleplayer.login.scan", sourceName());
		});

		int errors = 0;
		for (int attempt = 0; attempt < 90; attempt++) {
			try {
				Thread.sleep(2000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			if (!isCurrent(token)) {
				return;
			}
			try {
				if (source) {
					NeteaseApi.LoginState state = PlaybackService.INSTANCE.netease()
							.checkQrCode(new NeteaseApi.QrCode(key, url));
					switch (state) {
						case WAITING -> post(token, I18n.get("gui.simpleplayer.login.waiting"));
						case SCANNED -> post(token, I18n.get("gui.simpleplayer.login.scanned"));
						case EXPIRED -> {
							post(token, I18n.get("gui.simpleplayer.login.expired"));
							return;
						}
						case ERROR -> {
							if (++errors > 4) {
								post(token, I18n.get("gui.simpleplayer.login.failed"));
								return;
							}
						}
						case CONFIRMED -> {
							finishLogin(token, true);
							return;
						}
					}
				} else {
					BilibiliApi.LoginState state = PlaybackService.INSTANCE.bilibili()
							.checkQrCode(new BilibiliApi.QrCode(url, key));
					switch (state) {
						case WAITING -> post(token, I18n.get("gui.simpleplayer.login.waiting"));
						case SCANNED -> post(token, I18n.get("gui.simpleplayer.login.scanned"));
						case EXPIRED -> {
							post(token, I18n.get("gui.simpleplayer.login.expired"));
							return;
						}
						case ERROR -> {
							if (++errors > 4) {
								post(token, I18n.get("gui.simpleplayer.login.failed"));
								return;
							}
						}
						case CONFIRMED -> {
							finishLogin(token, false);
							return;
						}
					}
				}
			} catch (Exception e) {
				if (++errors > 4) {
					post(token, I18n.get("gui.simpleplayer.login.failed"));
					return;
				}
			}
		}
		post(token, I18n.get("gui.simpleplayer.login.expired"));
	}

	private void finishLogin(int token, boolean source) {
		ClientConfig config = ClientConfig.get();
		String nickname;
		if (source) {
			config.neteaseCookie = PlaybackService.INSTANCE.netease().getCookie();
			nickname = PlaybackService.INSTANCE.netease().accountName();
		} else {
			config.bilibiliCookie = PlaybackService.INSTANCE.bilibili().getCookie();
			nickname = PlaybackService.INSTANCE.bilibili().accountName();
		}
		ClientConfig.save();
		PlaybackService.INSTANCE.applyCookies();
		String name = nickname == null || nickname.isBlank() ? sourceName() : nickname;
		Minecraft.getInstance().execute(() -> {
			if (!isCurrent(token)) {
				return;
			}
			status = I18n.get("gui.simpleplayer.login.success", name);
			screen.onLoginChanged();
		});
	}

	private void logout() {
		if (netease) {
			ClientConfig.get().neteaseCookie = "";
		} else {
			ClientConfig.get().bilibiliCookie = "";
		}
		ClientConfig.save();
		PlaybackService.INSTANCE.applyCookies();
		generation++;
		status = I18n.get("gui.simpleplayer.login.logout_done");
		screen.onLoginChanged();
		refreshQr();
	}

	private void saveCookie() {
		if (cookieBox == null) {
			return;
		}
		String value = cookieBox.getValue().trim();
		if (value.isEmpty()) {
			status = I18n.get("gui.simpleplayer.login.need_cookie");
			return;
		}
		if (netease) {
			ClientConfig.get().neteaseCookie = value;
		} else {
			ClientConfig.get().bilibiliCookie = value;
		}
		ClientConfig.save();
		PlaybackService.INSTANCE.applyCookies();
		generation++;
		manual = false;
		cookieBox.setVisible(false);
		cookieBox.setFocused(false);
		screen.setFocused(null);
		status = I18n.get("gui.simpleplayer.login.saved");
		screen.onLoginChanged();
	}

	private void post(int token, String text) {
		Minecraft.getInstance().execute(() -> {
			if (isCurrent(token)) {
				status = text;
			}
		});
	}

	private boolean isCurrent(int token) {
		return token == generation && Minecraft.getInstance().screen == screen;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (ready() && manual && cookieBox != null && cookieBox.isVisible()
				&& inside(mouseX, mouseY, cookieX, cookieY, cookieWidth, cookieHeight)) {
			screen.setFocused(cookieBox);
			return cookieBox.mouseClicked(mouseX, mouseY, button);
		}
		if (ready() && !(manual && cookieBox != null && cookieBox.isVisible()
				&& inside(mouseX, mouseY, cookieX, cookieY, cookieWidth, cookieHeight))) {
			screen.setFocused(null);
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (cookieBox != null && cookieBox.isFocused() && manual) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				saveCookie();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				cookieBox.setFocused(false);
				screen.setFocused(null);
				return true;
			}
			return cookieBox.keyPressed(keyCode, scanCode, modifiers);
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (cookieBox != null && cookieBox.isFocused() && manual) {
			return cookieBox.charTyped(codePoint, modifiers);
		}
		return super.charTyped(codePoint, modifiers);
	}
}
