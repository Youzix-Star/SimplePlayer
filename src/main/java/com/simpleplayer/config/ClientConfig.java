package com.simpleplayer.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simpleplayer.SimplePlayer;

import net.minecraft.util.Mth;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Client-side settings, persisted to {@code config/simpleplayer-client.json}.
 */
public final class ClientConfig {

	public enum NeteaseQuality {
		STANDARD,
		HIGH,
		EXHIGH,
		LOSSLESS
	}

	public enum BilibiliQuality {
		LOW,
		MEDIUM,
		HIGH
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientConfig instance;

	// Playback
	public float volume = 0.8F;
	public int fadeInMillis = 800;
	public int fadeOutMillis = 1200;

	// NetEase Cloud Music
	public boolean neteaseEnabled = true;
	public String neteaseCookie = "";
	public String neteaseApiBase = "";
	public NeteaseQuality neteaseQuality = NeteaseQuality.EXHIGH;

	// Bilibili
	public boolean bilibiliEnabled = true;
	public String bilibiliCookie = "";
	public BilibiliQuality bilibiliQuality = BilibiliQuality.HIGH;

	public static ClientConfig get() {
		if (instance == null) {
			load();
		}
		return instance;
	}

	public static Path file() {
		return FMLPaths.CONFIGDIR.get().resolve("simpleplayer-client.json");
	}

	public static void load() {
		ClientConfig loaded = null;
		Path path = file();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, ClientConfig.class);
			} catch (Exception e) {
				SimplePlayer.LOGGER.warn("Failed to read {}, using defaults", path, e);
			}
		}
		instance = loaded == null ? new ClientConfig() : loaded;
		instance.sanitize();
	}

	public static void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(get(), writer);
			}
		} catch (IOException e) {
			SimplePlayer.LOGGER.error("Failed to save {}", path, e);
		}
	}

	/** Applies another instance on top of the singleton, used when the settings screen is confirmed. */
	public static void apply(ClientConfig other) {
		ClientConfig current = get();
		current.copyFrom(other);
		current.sanitize();
	}

	/** Deep copy through JSON so the settings screen can be cancelled without side effects. */
	public ClientConfig copy() {
		return GSON.fromJson(GSON.toJson(this), ClientConfig.class);
	}

	private void copyFrom(ClientConfig other) {
		volume = other.volume;
		fadeInMillis = other.fadeInMillis;
		fadeOutMillis = other.fadeOutMillis;
		neteaseEnabled = other.neteaseEnabled;
		neteaseCookie = other.neteaseCookie;
		neteaseApiBase = other.neteaseApiBase;
		neteaseQuality = other.neteaseQuality;
		bilibiliEnabled = other.bilibiliEnabled;
		bilibiliCookie = other.bilibiliCookie;
		bilibiliQuality = other.bilibiliQuality;
	}

	private void sanitize() {
		volume = Mth.clamp(volume, 0F, 1F);
		fadeInMillis = Mth.clamp(fadeInMillis, 0, 10000);
		fadeOutMillis = Mth.clamp(fadeOutMillis, 0, 10000);
		if (neteaseApiBase == null) {
			neteaseApiBase = "";
		}
		if (neteaseCookie == null) {
			neteaseCookie = "";
		}
		if (bilibiliCookie == null) {
			bilibiliCookie = "";
		}
		if (neteaseQuality == null) {
			neteaseQuality = NeteaseQuality.EXHIGH;
		}
		if (bilibiliQuality == null) {
			bilibiliQuality = BilibiliQuality.HIGH;
		}
	}
}
