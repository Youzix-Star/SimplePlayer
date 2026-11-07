package com.simpleplayer.music;

public enum MusicSource {
	NETEASE("netease"),
	BILIBILI("bilibili");

	private final String id;

	MusicSource(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}
}
