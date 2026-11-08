package com.simpleplayer.music;

/** A user playlist / collection shown by the playlist panel. */
public record Playlist(
		String id,
		String name,
		String creator,
		String coverUrl,
		int trackCount) {
}
