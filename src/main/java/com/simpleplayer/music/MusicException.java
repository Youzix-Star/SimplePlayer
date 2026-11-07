package com.simpleplayer.music;

public class MusicException extends RuntimeException {

	public MusicException(String message) {
		super(message);
	}

	public MusicException(String message, Throwable cause) {
		super(message, cause);
	}
}
