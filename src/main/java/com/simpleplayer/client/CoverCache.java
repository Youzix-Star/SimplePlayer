package com.simpleplayer.client;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import com.simpleplayer.SimplePlayer;
import com.simpleplayer.music.Http;
import com.simpleplayer.ui.CoverPalette;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Downloads cover art in the background and keeps the resulting textures around.
 */
public final class CoverCache {

	/** A registered cover texture plus a handful of representative colours sampled from it. */
	public record Cover(ResourceLocation id, int width, int height, int[] palette) {
	}

	private static final Map<String, Cover> CACHE = new ConcurrentHashMap<>();
	private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

	private CoverCache() {
	}

	/** Returns the cover if it is ready, otherwise starts loading it and returns {@code null}. */
	public static Cover get(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		Cover cover = CACHE.get(url);
		if (cover != null) {
			return cover;
		}
		if (!LOADING.add(url)) {
			return null;
		}
		Thread thread = new Thread(() -> {
			try {
				byte[] bytes = Http.getBytes(url, Map.of("Referer", "https://www.bilibili.com"));
				NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
				int width = image.getWidth();
				int height = image.getHeight();
				int[] sampled;
				try {
					sampled = CoverPalette.extract(image);
				} catch (Exception e) {
					sampled = CoverPalette.fallback();
					SimplePlayer.LOGGER.warn("Failed to sample cover colours", e);
				}
				final int[] palette = sampled;
				Minecraft.getInstance().execute(() -> {
					try {
						ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SimplePlayer.MOD_ID,
								"cover_" + Integer.toHexString(url.hashCode()).replace('-', 'n'));
						Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
						CACHE.put(url, new Cover(id, width, height, palette));
					} catch (Exception e) {
						image.close();
						LOADING.remove(url);
						SimplePlayer.LOGGER.warn("Failed to register cover", e);
					}
				});
			} catch (Exception e) {
				LOADING.remove(url);
			}
		}, "SimplePlayer-Cover");
		thread.setDaemon(true);
		thread.start();
		return null;
	}
}
