package com.simpleplayer.music;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
	private final List<Track> queue = new ArrayList<>();
	private volatile Track current;
	private volatile int index = -1;
	private volatile boolean starting;
	private volatile int playToken;

	private PlaybackService() {
		this.netease = new NeteaseApi(ClientConfig.get().neteaseApiBase);
		applyCookies();
	}

	/** Re-applies cookies stored in the config, e.g. after the login screen saved new ones. */
	public void applyCookies() {
		ClientConfig config = ClientConfig.get();
		netease.setCookie(config.neteaseCookie);
		bilibili.setCookie(config.bilibiliCookie);
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
					case NETEASE -> netease.search(query, 30, 0);
					case BILIBILI -> bilibili.search(query, 1);
				};
			} catch (Exception e) {
				SimplePlayer.LOGGER.warn("Search failed on {}", source, e);
				throw new MusicException("Search failed: " + e.getMessage(), e);
			}
		}, EXECUTOR);
	}

	/** Daily recommendations for the logged-in NetEase account. */
	public CompletableFuture<List<Track>> dailyRecommendations() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return netease.dailyRecommendations();
			} catch (Exception e) {
				SimplePlayer.LOGGER.warn("Daily recommendations failed", e);
				throw new MusicException("Daily recommendations failed: " + e.getMessage(), e);
			}
		}, EXECUTOR);
	}

	/** Playlists of the logged-in NetEase account. */
	public CompletableFuture<List<Playlist>> playlists() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				NeteaseApi.Profile profile = netease.profile();
				if (profile == null) {
					throw new MusicException("NetEase login required");
				}
				return netease.userPlaylists(profile.userId());
			} catch (MusicException e) {
				throw e;
			} catch (Exception e) {
				SimplePlayer.LOGGER.warn("Playlist loading failed", e);
				throw new MusicException("Playlist loading failed: " + e.getMessage(), e);
			}
		}, EXECUTOR);
	}

	public CompletableFuture<List<Track>> playlistTracks(String playlistId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return netease.playlistTracks(playlistId, 500);
			} catch (Exception e) {
				SimplePlayer.LOGGER.warn("Playlist detail failed for {}", playlistId, e);
				throw new MusicException("Playlist loading failed: " + e.getMessage(), e);
			}
		}, EXECUTOR);
	}

	public List<Track> queue() {
		synchronized (queue) {
			return List.copyOf(queue);
		}
	}

	public int index() {
		return index;
	}

	/** Replaces the queue and starts at {@code startIndex}. */
	public void playQueue(List<Track> tracks, int startIndex) {
		if (tracks.isEmpty()) {
			return;
		}
		synchronized (queue) {
			queue.clear();
			queue.addAll(tracks);
		}
		index = Math.max(0, Math.min(startIndex, tracks.size() - 1));
		play(queue.get(index));
	}

	/** Jumps to an existing queue slot without rebuilding the queue. */
	public void playAt(int target) {
		List<Track> snapshot = queue();
		if (snapshot.isEmpty()) {
			return;
		}
		index = Math.max(0, Math.min(target, snapshot.size() - 1));
		play(snapshot.get(index));
	}

	public void next() {
		List<Track> snapshot = queue();
		if (snapshot.isEmpty()) {
			return;
		}
		index = (index + 1) % snapshot.size();
		play(snapshot.get(index));
	}

	public void previous() {
		List<Track> snapshot = queue();
		if (snapshot.isEmpty()) {
			return;
		}
		index = (index - 1 + snapshot.size()) % snapshot.size();
		play(snapshot.get(index));
	}

	public void togglePause() {
		if (AudioEngine.INSTANCE.isPaused()) {
			AudioEngine.INSTANCE.resume();
		} else {
			AudioEngine.INSTANCE.pause();
		}
	}

	/** Called every client tick; advances the queue when a track ends. */
	public void tick() {
		if (starting || current == null) {
			return;
		}
		if (!AudioEngine.INSTANCE.isPlaying() && !AudioEngine.INSTANCE.isPaused() && !queue().isEmpty()) {
			next();
		}
	}

	public CompletableFuture<Void> play(Track track) {
		stop();
		final int token = ++playToken;
		current = track;
		starting = true;
		return CompletableFuture.runAsync(() -> {
			PcmSource source = null;
			try {
				source = open(track);
				if (token != playToken) {
					source.close();
					return;
				}
				ClientConfig config = ClientConfig.get();
				AudioEngine.INSTANCE.play(source, config.volume, config.fadeInMillis);
			} catch (Exception e) {
				if (source != null) {
					try {
						source.close();
					} catch (Exception ignored) {
					}
				}
				if (token == playToken) {
					SimplePlayer.LOGGER.error("Playback failed for {}", track.title(), e);
					current = null;
				}
			} finally {
				if (token == playToken) {
					starting = false;
				}
			}
		}, EXECUTOR);
	}

	public void stop() {
		playToken++;
		starting = false;
		AudioEngine.INSTANCE.stop();
		current = null;
	}

	/** Drops the queue and stops playback. */
	public void clear() {
		synchronized (queue) {
			queue.clear();
		}
		index = -1;
		stop();
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
