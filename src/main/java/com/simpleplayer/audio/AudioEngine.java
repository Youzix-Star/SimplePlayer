package com.simpleplayer.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.openal.AL10;

import com.simpleplayer.SimplePlayer;

/**
 * Streaming playback on top of Minecraft's OpenAL context.
 *
 * <p>Decoding happens on a background thread and produces {@link PcmChunk}s; the client tick moves
 * them into OpenAL buffers, so every OpenAL call stays on the render thread.</p>
 */
public final class AudioEngine {

	public static final AudioEngine INSTANCE = new AudioEngine();

	private static final int MAX_QUEUED_BUFFERS = 4;
	private static final int MAX_PENDING_CHUNKS = 16;

	private final ConcurrentLinkedQueue<PcmChunk> pending = new ConcurrentLinkedQueue<>();
	private final Deque<Integer> freeBuffers = new ArrayDeque<>();

	private int source = -1;
	private int queuedBuffers;
	private boolean started;
	private volatile boolean active;
	private volatile boolean paused;
	private double playedSeconds;
	private final Deque<Double> bufferDurations = new ArrayDeque<>();
	private volatile boolean feedDone;

	private PcmSource pcm;
	private Thread decoderThread;
	private long playbackStartNanos;
	private float volume = 1F;
	private int fadeInMillis;

	private AudioEngine() {
	}

	public synchronized void play(PcmSource source, float volume, int fadeInMillis) {
		stop();
		this.pcm = source;
		ensureSource();
		this.volume = clamp(volume);
		this.fadeInMillis = Math.max(0, fadeInMillis);
		this.active = true;
		this.paused = false;
		this.feedDone = false;
		this.playedSeconds = 0;
		this.bufferDurations.clear();
		this.started = false;
		this.playbackStartNanos = System.nanoTime();
		this.decoderThread = new Thread(this::decode, "SimplePlayer-Audio");
		this.decoderThread.setDaemon(true);
		this.decoderThread.start();
	}

	public synchronized void stop() {
		active = false;
		feedDone = false;
		Thread thread = decoderThread;
		decoderThread = null;
		if (thread != null) {
			thread.interrupt();
		}
		pending.clear();
		bufferDurations.clear();
		PcmSource pcmSource = pcm;
		pcm = null;
		if (pcmSource != null) {
			try {
				pcmSource.close();
			} catch (Exception ignored) {
			}
		}
		if (source != -1) {
			AL10.alSourceStop(source);
			int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
			queuedBuffers = Math.max(0, queuedBuffers - Math.max(0, processed));
			AL10.alSourcei(source, AL10.AL_BUFFER, 0);
			while (queuedBuffers > 0) {
				freeBuffers.push(AL10.alSourceUnqueueBuffers(source));
				queuedBuffers--;
			}
		}
	}

	/** Called once per client tick, on the render thread. */
	public synchronized void tick() {
		if (!active || source == -1) {
			return;
		}
		drainProcessed();
		uploadPending();
		applyVolume();
		if (started && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && queuedBuffers > 0) {
			AL10.alSourcePlay(source);
		}
		if (feedDone && pending.isEmpty() && queuedBuffers == 0) {
			active = false;
		}
	}

	public boolean isPlaying() {
		return active && !paused;
	}

	public synchronized void pause() {
		if (source != -1 && active && !paused) {
			AL10.alSourcePause(source);
			paused = true;
		}
	}

	public synchronized void resume() {
		if (source != -1 && active && paused) {
			AL10.alSourcePlay(source);
			paused = false;
		}
	}

	public boolean isPaused() {
		return paused;
	}

	/** Approximate playback position, in seconds. */
	public double positionSeconds() {
		return playedSeconds;
	}

	private void drainProcessed() {
		int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
		while (processed-- > 0 && queuedBuffers > 0) {
			freeBuffers.push(AL10.alSourceUnqueueBuffers(source));
			queuedBuffers--;
			Double duration = bufferDurations.pollFirst();
			if (duration != null) {
				playedSeconds += duration;
			}
		}
	}

	private void uploadPending() {
		while (queuedBuffers < MAX_QUEUED_BUFFERS) {
			PcmChunk chunk = pending.poll();
			if (chunk == null) {
				return;
			}
			int buffer = freeBuffers.isEmpty() ? AL10.alGenBuffers() : freeBuffers.pop();
			upload(buffer, chunk);
			AL10.alSourceQueueBuffers(source, buffer);
			bufferDurations.addLast(chunk.durationSeconds());
			queuedBuffers++;
			if (!started) {
				AL10.alSourcePlay(source);
				started = true;
			}
		}
	}

	private void upload(int buffer, PcmChunk chunk) {
		ByteBuffer data = ByteBuffer.allocateDirect(chunk.samples().length * Short.BYTES).order(ByteOrder.nativeOrder());
		data.asShortBuffer().put(chunk.samples());
		int format = chunk.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
		AL10.alBufferData(buffer, format, data, chunk.sampleRate());
	}

	private void applyVolume() {
		float gain = volume;
		if (fadeInMillis > 0) {
			float elapsed = (System.nanoTime() - playbackStartNanos) / 1_000_000_000F;
			gain *= Math.min(1F, elapsed / (fadeInMillis / 1000F));
		}
		AL10.alSourcef(source, AL10.AL_GAIN, clamp(gain));
	}

	private void decode() {
		PcmSource source = pcm;
		if (source == null) {
			return;
		}
		try (source) {
			while (!Thread.currentThread().isInterrupted()) {
				PcmChunk chunk = source.read();
				if (chunk == null) {
					break;
				}
				pending.add(chunk);
				while (pending.size() > MAX_PENDING_CHUNKS && !Thread.currentThread().isInterrupted()) {
					Thread.sleep(20);
				}
			}
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			SimplePlayer.LOGGER.warn("Audio decoding stopped", e);
		} finally {
			feedDone = true;
		}
	}

	private void ensureSource() {
		if (source == -1) {
			source = AL10.alGenSources();
			AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
			AL10.alSourcef(source, AL10.AL_GAIN, volume);
		}
	}

	private static float clamp(float value) {
		return Math.max(0F, Math.min(1F, value));
	}
}
