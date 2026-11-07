package com.simpleplayer.music;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin wrapper over {@link HttpClient} used by every music source.
 */
public final class Http {

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private Http() {
	}

	public static HttpResponse<String> postForm(String url, Map<String, String> form, Map<String, String> headers) throws Exception {
		String body = form.entrySet().stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.collect(Collectors.joining("&"));
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		headers.forEach(request::header);
		return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	public static HttpResponse<String> get(String url, Map<String, String> headers) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", "Mozilla/5.0")
				.GET();
		headers.forEach(request::header);
		return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	/** Opens a binary stream, used for audio downloads. */
	public static InputStream openStream(String url, Map<String, String> headers) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setConnectTimeout(15_000);
		connection.setReadTimeout(60_000);
		connection.setRequestProperty("User-Agent", "Mozilla/5.0");
		headers.forEach(connection::setRequestProperty);
		return connection.getInputStream();
	}

	/** Fetches a URL into memory, used for cover images. */
	public static byte[] getBytes(String url, Map<String, String> headers) throws IOException {
		try (InputStream in = openStream(url, headers)) {
			return in.readAllBytes();
		}
	}

	/** Downloads a URL into {@code destination}. */
	public static void download(String url, java.nio.file.Path destination, Map<String, String> headers) throws IOException {
		try (InputStream in = openStream(url, headers)) {
			java.nio.file.Files.copy(in, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
