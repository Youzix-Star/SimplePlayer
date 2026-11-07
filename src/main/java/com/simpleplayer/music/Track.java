package com.simpleplayer.music;

/**
 * A playable song, independent of the source it came from.
 */
public record Track(
		MusicSource source,
		String id,
		String title,
		String artists,
		String album,
		long durationMillis,
		String coverUrl,
		boolean paid) {

	public String displayName() {
		return artists.isEmpty() ? title : title + " - " + artists;
	}
}
