package com.simpleplayer.audio;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads 16-bit PCM from a RIFF/WAVE file, used for decoded AAC output.
 */
public final class WavSource implements PcmSource {

	private static final int CHUNK_FRAMES = 4096;

	private final DataInputStream in;
	private final int sampleRate;
	private final int channels;
	private long remainingBytes;
	private final int chunkBytes;

	private WavSource(DataInputStream in, int sampleRate, int channels, long dataBytes) {
		this.in = in;
		this.sampleRate = sampleRate;
		this.channels = channels;
		this.remainingBytes = dataBytes;
		this.chunkBytes = CHUNK_FRAMES * channels * Short.BYTES;
	}

	public static WavSource open(Path file) throws IOException {
		InputStream raw = Files.newInputStream(file);
		DataInputStream in = new DataInputStream(raw);
		try {
			byte[] riff = new byte[12];
			in.readFully(riff);
			if (riff[0] != 'R' || riff[1] != 'I' || riff[2] != 'F' || riff[3] != 'F'
					|| riff[8] != 'W' || riff[9] != 'A' || riff[10] != 'V' || riff[11] != 'E') {
				throw new IOException("Not a WAV file: " + file);
			}
			int sampleRate = 44100;
			int channels = 2;
			long dataBytes = 0;
			while (true) {
				int id = in.readInt();
				int size = Integer.reverseBytes(in.readInt());
				String chunkId = new String(new byte[] {
						(byte) (id >>> 24), (byte) (id >>> 16), (byte) (id >>> 8), (byte) id
				}, java.nio.charset.StandardCharsets.US_ASCII);
				if ("fmt ".equals(chunkId)) {
					in.readShort(); // audio format
					channels = Short.reverseBytes(in.readShort());
					sampleRate = Integer.reverseBytes(in.readInt());
					in.skipBytes(6); // byte rate + block align
					in.readShort(); // bits per sample
					if (size > 16) {
						in.skipBytes(size - 16);
					}
				} else if ("data".equals(chunkId)) {
					dataBytes = size < 0 ? Long.MAX_VALUE : size;
					break;
				} else {
					in.skipBytes(size);
				}
			}
			return new WavSource(in, sampleRate, channels, dataBytes);
		} catch (IOException | RuntimeException e) {
			in.close();
			throw e;
		}
	}

	@Override
	public PcmChunk read() throws IOException {
		if (remainingBytes <= 0) {
			return null;
		}
		int bytes = (int) Math.min(chunkBytes, remainingBytes);
		byte[] buffer = new byte[bytes];
		int read = 0;
		try {
			while (read < bytes) {
				int n = in.read(buffer, read, bytes - read);
				if (n < 0) {
					break;
				}
				read += n;
			}
		} catch (EOFException e) {
			read = 0;
		}
		if (read <= 0) {
			return null;
		}
		remainingBytes -= read;
		short[] samples = new short[read / Short.BYTES];
		for (int i = 0; i < samples.length; i++) {
			samples[i] = (short) ((buffer[i * 2] & 0xFF) | (buffer[i * 2 + 1] << 8));
		}
		return new PcmChunk(samples, sampleRate, channels);
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}
