package com.simpleplayer.music;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.simpleplayer.SimplePlayer;
import com.simpleplayer.audio.aac.AacAudioDecoder;
import com.simpleplayer.audio.AudioEngine;
import com.simpleplayer.audio.Mp3Source;
import com.simpleplayer.audio.PcmSource;
import com.simpleplayer.audio.WavSource;
import com.simpleplayer.config.ClientConfig;
import com.simpleplayer.music.bilibili.BilibiliApi;
import com.simpleplayer.music.netease.NeteaseApi;

/**
 * Glue between the music APIs and the audio engine: resolves a stream, prepares a decodable source
 * and hands it to {@link AudioEngine}.
 */
public final class PlaybackService {

	public static final PlaybackService INSTANCE = new PlaybackService();

	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "SimplePlayer-IO");
		thread.setDaemon(true);
		return thread;
	});

	private final NeteaseApi netease;
	private final BilibiliApi bilibili = new BilibiliApi();
	private volatile Track current;

	private PlaybackService() {
		this.netease = new NeteaseApi(ClientConfig.get().neteaseApiBase);
	}

	public Track current() {
		return current;
	}

	public NeteaseApi netease() {
		return netease;
	}

	public BilibiliApi bilibili() {
		return bilibili;
	}

	public CompletableFuture<List<Track>> search(MusicSource source, String query) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return switch (source) {
					case NETEASE -> netease.search(query, 10, 0);
					case BILIBILI -> bilibili.search(query, 1);
				};
			} catch (Exception e) {
				SimplePlayer.LOGGER.warn("Search failed on {}", source, e);
				throw new MusicException("Search failed: " + e.getMessage(), e);
			}
		}, EXECUTOR);
	}

	public CompletableFuture<Void> play(Track track) {
		stop();
		current = track;
		return CompletableFuture.runAsync(() -> {
			try {
				PcmSource source = open(track);
				ClientConfig config = ClientConfig.get();
				AudioEngine.INSTANCE.play(source, config.volume, config.fadeInMillis);
			} catch (Exception e) {
				SimplePlayer.LOGGER.error("Playback failed for {}", track.title(), e);
				current = null;
			}
		}, EXECUTOR);
	}

	public void stop() {
		AudioEngine.INSTANCE.stop();
		current = null;
	}

	private PcmSource open(Track track) throws Exception {
		ClientConfig config = ClientConfig.get();
		return switch (track.source()) {
			case NETEASE -> {
				String url = netease.resolveUrl(track, config.neteaseQuality);
				yield new Mp3Source(Http.openStream(url, Map.of()));
			}
			case BILIBILI -> {
				String url = bilibili.resolveUrl(track, config.bilibiliQuality);
				Path container = Files.createTempFile("simpleplayer-", ".m4s");
				Http.download(url, container, Map.of("Referer", "https://www.bilibili.com"));
				Path wav = Files.createTempFile("simpleplayer-", ".wav");
				AacAudioDecoder.decodeToWav(container.toFile(), "m4a", wav.toFile());
				Files.deleteIfExists(container);
				yield WavSource.open(wav);
			}
		};
	}
}
