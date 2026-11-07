package com.simpleplayer;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SimplePlayer.MOD_ID)
public final class SimplePlayer {
	public static final String MOD_ID = "simpleplayer";
	public static final Logger LOGGER = LogUtils.getLogger();

	public SimplePlayer(IEventBus modEventBus, ModContainer modContainer) {
		LOGGER.info("SimplePlayer {} initialised", modContainer.getModInfo().getVersion());
	}
}
