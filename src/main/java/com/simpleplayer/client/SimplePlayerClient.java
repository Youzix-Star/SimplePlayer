package com.simpleplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.simpleplayer.SimplePlayer;
import com.simpleplayer.audio.AudioEngine;
import com.simpleplayer.config.ClientConfig;
import com.simpleplayer.music.PlaybackService;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only bootstrap: settings, keybinds and the audio tick.
 */
@Mod(value = SimplePlayer.MOD_ID, dist = Dist.CLIENT)
public final class SimplePlayerClient {

	private static final KeyMapping OPEN_PLAYER = new KeyMapping(
			"key.simpleplayer.open_player",
			KeyConflictContext.IN_GAME,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			"key.categories.simpleplayer");

	public SimplePlayerClient(ModContainer container, IEventBus modBus) {
		ClientConfig.load();
		container.registerExtensionPoint(IConfigScreenFactory.class,
				(IConfigScreenFactory) (modContainer, parent) -> new SimplePlayerConfigScreen(parent));
		modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(OPEN_PLAYER));
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
	}

	private static void tick() {
		AudioEngine.INSTANCE.tick();
		PlaybackService.INSTANCE.tick();
		Minecraft minecraft = Minecraft.getInstance();
		while (OPEN_PLAYER.consumeClick()) {
			if (minecraft.screen == null) {
				minecraft.setScreen(new PlayerScreen());
			}
		}
	}
}
