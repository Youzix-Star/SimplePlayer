package com.simpleplayer.audio;

import java.io.IOException;
import java.io.InputStream;

public final class Mp3Source implements PcmSource {

	private final Mp3Decoder decoder;

	public Mp3Source(InputStream input) {
		this.decoder = new Mp3Decoder(input);
	}

	@Override
	public PcmChunk read() throws Exception {
		return decoder.read();
	}

	@Override
	public void close() throws IOException {
		decoder.close();
	}
}
