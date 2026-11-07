package com.simpleplayer.audio;

/**
 * A block of decoded PCM audio, kept as interleaved signed 16-bit samples.
 */
public record PcmChunk(short[] samples, int sampleRate, int channels) {

	public int frames() {
		return channels == 0 ? 0 : samples.length / channels;
	}

	public double durationSeconds() {
		return sampleRate == 0 ? 0 : (double) frames() / sampleRate;
	}
}
