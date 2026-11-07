package com.simpleplayer.audio;

import java.io.Closeable;

/**
 * Produces decoded PCM chunks, one per call, until the stream ends.
 */
public interface PcmSource extends Closeable {

	PcmChunk read() throws Exception;
}
