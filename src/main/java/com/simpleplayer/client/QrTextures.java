package com.simpleplayer.client;

import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.simpleplayer.SimplePlayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders QR payloads into registered textures.
 */
public final class QrTextures {

	private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
	private static final int SIZE = 128;

	private QrTextures() {
	}

	public static ResourceLocation get(String data) {
		ResourceLocation cached = CACHE.get(data);
		if (cached != null) {
			return cached;
		}
		try {
			BitMatrix matrix = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, SIZE, SIZE);
			com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(SIZE, SIZE, false);
			for (int x = 0; x < SIZE; x++) {
				for (int y = 0; y < SIZE; y++) {
					image.setPixelRGBA(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
				}
			}
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SimplePlayer.MOD_ID,
					"qr_" + Integer.toHexString(data.hashCode()).replace('-', 'n'));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
			CACHE.put(data, id);
			return id;
		} catch (Exception e) {
			SimplePlayer.LOGGER.error("Failed to build QR texture", e);
			return null;
		}
	}
}
