package com.simpleplayer.audio;

import java.io.Closeable;
import java.io.InputStream;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 * Minimal streaming MP3 decoder built on JLayer.
 */
public final class Mp3Decoder implements Closeable {

	private final Bitstream bitstream;
	private final Decoder decoder = new Decoder();

	public Mp3Decoder(InputStream input) {
		this.bitstream = new Bitstream(input);
	}

	/**
	 * Decodes the next frame, or returns {@code null} at end of stream.
	 */
	public PcmChunk read() throws Exception {
		Header header = bitstream.readFrame();
		if (header == null) {
			return null;
		}
		try {
			SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
			short[] samples = new short[buffer.getBufferLength()];
			System.arraycopy(buffer.getBuffer(), 0, samples, 0, buffer.getBufferLength());
			return new PcmChunk(samples, buffer.getSampleFrequency(), buffer.getChannelCount());
		} finally {
			bitstream.closeFrame();
		}
	}

	@Override
	public void close() throws Exception {
		bitstream.close();
	}
}
