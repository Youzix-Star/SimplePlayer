package com.simpleplayer.client;

import com.simpleplayer.SimplePlayer;
import com.simpleplayer.config.ClientConfig;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only bootstrap: loads settings and hooks the config screen into the mod list.
 */
@Mod(value = SimplePlayer.MOD_ID, dist = Dist.CLIENT)
public final class SimplePlayerClient {

	public SimplePlayerClient(ModContainer container) {
		ClientConfig.load();
		container.registerExtensionPoint(IConfigScreenFactory.class,
				(IConfigScreenFactory) (modContainer, parent) -> new SimplePlayerConfigScreen(parent));
	}
}
