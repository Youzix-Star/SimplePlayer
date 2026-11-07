package com.simpleplayer.client;

import com.simpleplayer.config.ClientConfig;
import com.simpleplayer.gui.BaseOptionsScreen;
import com.simpleplayer.gui.config.OptionsList;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings screen, styled after Jade's configuration UI.
 */
public class SimplePlayerConfigScreen extends BaseOptionsScreen {

	private final ClientConfig draft;

	public SimplePlayerConfigScreen(Screen parent) {
		super(parent, Component.translatable("gui.simpleplayer.config.title"));
		this.draft = ClientConfig.get().copy();
		this.saver = () -> {
			ClientConfig.apply(draft);
			ClientConfig.save();
		};
	}

	@Override
	public OptionsList createOptions() {
		OptionsList options = new OptionsList(this, minecraft, width, height, 43, 24, ClientConfig::save);

		options.title("general");
		options.slider("volume", () -> draft.volume, value -> draft.volume = value, 0F, 1F, FloatUnaryOperator.identity());
		options.slider("fade_in", () -> (float) draft.fadeInMillis, value -> draft.fadeInMillis = Math.round(value), 0F, 5000F,
				msAligner());
		options.slider("fade_out", () -> (float) draft.fadeOutMillis, value -> draft.fadeOutMillis = Math.round(value), 0F, 5000F,
				msAligner());

		options.title("netease");
		options.choices("netease_enabled", () -> draft.neteaseEnabled, value -> draft.neteaseEnabled = value);
		options.input("netease_api_base", () -> draft.neteaseApiBase, value -> draft.neteaseApiBase = value);
		options.choices("netease_quality", () -> draft.neteaseQuality, value -> draft.neteaseQuality = value);

		options.title("bilibili");
		options.choices("bilibili_enabled", () -> draft.bilibiliEnabled, value -> draft.bilibiliEnabled = value);
		options.choices("bilibili_quality", () -> draft.bilibiliQuality, value -> draft.bilibiliQuality = value);

		return options;
	}

	/** Rounds slider values to whole 50 ms steps. */
	private static FloatUnaryOperator msAligner() {
		return value -> Math.round(value / 50F) * 50F;
	}
}
