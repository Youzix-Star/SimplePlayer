package com.simpleplayer.music.bilibili;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simpleplayer.config.ClientConfig.BilibiliQuality;
import com.simpleplayer.music.Http;
import com.simpleplayer.music.MusicException;
import com.simpleplayer.music.MusicSource;
import com.simpleplayer.music.Track;

/**
 * Bilibili video-to-audio client.
 *
 * <p>Search requires a signed (WBI) request and the {@code buvid3} cookie; stream URLs require the
 * {@code Referer} header when downloading.</p>
 */
public final class BilibiliApi {

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
	private static final String REFERER = "https://www.bilibili.com";
	private static final String API = "https://api.bilibili.com";
	private static final String PASSPORT = "https://passport.bilibili.com";
	private static final int[] MIXIN_TABLE = {
			46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
			27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
			37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
			22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
	};

	private final Map<String, String> cookies = new LinkedHashMap<>();
	private String mixinKey;
	private long mixinKeyFetchedAt;

	public boolean loggedIn() {
		return cookies.containsKey("SESSDATA");
	}

	public String getCookie() {
		return cookieHeader();
	}

	/** Accepts a raw {@code name=value; name=value} cookie string. */
	public void setCookie(String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		for (String part : raw.split(";")) {
			String pair = part.trim();
			int eq = pair.indexOf('=');
			if (eq > 0) {
				cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
			}
		}
	}

	/** Returns the logged-in nickname, or {@code null} when not logged in. */
	public String accountName() {
		try {
			JsonObject data = optObject(get(API, "/x/web-interface/nav", Map.of()), "data");
			if (data == null) {
				return null;
			}
			JsonElement login = data.get("isLogin");
			if (login == null || !login.getAsBoolean()) {
				return null;
			}
			return text(data, "uname");
		} catch (Exception e) {
			return null;
		}
	}

	public String cookieHeader() {
		StringBuilder builder = new StringBuilder();
		cookies.forEach((name, value) -> {
			if (!builder.isEmpty()) {
				builder.append("; ");
			}
			builder.append(name).append('=').append(value);
		});
		return builder.toString();
	}

	// ── Search ──────────────────────────────────────────────────────────────

	public List<Track> search(String keyword, int page) {
		ensureCookies();
		Map<String, String> params = new LinkedHashMap<>();
		params.put("search_type", "video");
		params.put("keyword", keyword);
		params.put("page", String.valueOf(Math.max(1, page)));
		JsonObject data = optObject(get(API, "/x/web-interface/wbi/search/type", signed(params)), "data");
		JsonArray result = data == null ? null : arr(data, "result");
		List<Track> tracks = new ArrayList<>();
		if (result == null) {
			return tracks;
		}
		for (JsonElement element : result) {
			Track track = parseTrack(element.getAsJsonObject());
			if (track != null) {
				tracks.add(track);
			}
		}
		return tracks;
	}

	// ── Stream ──────────────────────────────────────────────────────────────

	public String resolveUrl(Track track, BilibiliQuality quality) {
		ensureCookies();
		String cid = cidOf(track.id());
		Map<String, String> params = new LinkedHashMap<>();
		params.put("bvid", track.id());
		params.put("cid", cid);
		params.put("fnval", "80");
		params.put("fourk", "1");
		JsonObject data = optObject(get(API, "/x/player/playurl", params), "data");
		JsonObject dash = data == null ? null : optObject(data, "dash");
		JsonArray audios = dash == null ? null : arr(dash, "audio");
		if (audios == null || audios.isEmpty()) {
			throw new MusicException("Bilibili returned no audio stream (login may be required)");
		}
		int wanted = switch (quality) {
			case LOW -> 30216;
			case MEDIUM -> 30232;
			default -> 30280;
		};
		JsonObject chosen = null;
		for (JsonElement element : audios) {
			JsonObject audio = element.getAsJsonObject();
			if (intValue(audio, "id", 0) == wanted) {
				chosen = audio;
				break;
			}
		}
		if (chosen == null) {
			chosen = audios.get(0).getAsJsonObject();
		}
		JsonElement url = chosen.get("baseUrl");
		if (url == null || url.isJsonNull()) {
			url = chosen.get("base_url");
		}
		if (url == null || url.isJsonNull()) {
			throw new MusicException("Bilibili audio entry has no URL");
		}
		return url.getAsString();
	}

	private String cidOf(String bvid) {
		JsonObject data = optObject(get(API, "/x/web-interface/view", Map.of("bvid", bvid)), "data");
		JsonElement cid = data == null ? null : data.get("cid");
		if (cid == null || cid.isJsonNull()) {
			throw new MusicException("Bilibili could not resolve cid for " + bvid);
		}
		return cid.getAsString();
	}

	// ── QR login ────────────────────────────────────────────────────────────

	public record QrCode(String url, String key) {
	}

	public enum LoginState {
		WAITING,
		SCANNED,
		CONFIRMED,
		EXPIRED,
		ERROR
	}

	public QrCode createQrCode() {
		JsonObject data = optObject(get(PASSPORT, "/x/passport-login/web/qrcode/generate", Map.of()), "data");
		if (data == null) {
			throw new MusicException("Bilibili did not return a QR code");
		}
		return new QrCode(text(data, "url"), text(data, "qrcode_key"));
	}

	public LoginState checkQrCode(QrCode qr) {
		try {
			HttpResponse<String> response = send(PASSPORT, "/x/passport-login/web/qrcode/poll", Map.of("qrcode_key", qr.key()));
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonObject data = optObject(json, "data");
			int code = data == null ? -1 : intValue(data, "code", -1);
			LoginState state = switch (code) {
				case 0 -> LoginState.CONFIRMED;
				case 86090 -> LoginState.SCANNED;
				case 86101 -> LoginState.WAITING;
				case 86038 -> LoginState.EXPIRED;
				default -> LoginState.ERROR;
			};
			if (state == LoginState.CONFIRMED) {
				response.headers().allValues("set-cookie").forEach(this::captureCookie);
			}
			return state;
		} catch (Exception e) {
			return LoginState.ERROR;
		}
	}

	// ── HTTP ────────────────────────────────────────────────────────────────

	private void ensureCookies() {
		if (cookies.containsKey("buvid3")) {
			return;
		}
		try {
			HttpResponse<String> response = Http.get(REFERER + "/", headers());
			response.headers().allValues("set-cookie").forEach(this::captureCookie);
		} catch (Exception e) {
			throw new MusicException("Bilibili is unreachable", e);
		}
	}

	private void captureCookie(String setCookie) {
		String pair = setCookie.split(";", 2)[0].trim();
		int eq = pair.indexOf('=');
		if (eq > 0) {
			cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
		}
	}

	private Map<String, String> headers() {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("User-Agent", USER_AGENT);
		headers.put("Referer", REFERER);
		String cookie = cookieHeader();
		if (!cookie.isEmpty()) {
			headers.put("Cookie", cookie);
		}
		return headers;
	}

	private JsonObject get(String base, String path, Map<String, String> params) {
		StringBuilder url = new StringBuilder(base).append(path);
		if (!params.isEmpty()) {
			url.append('?');
			boolean first = true;
			for (Map.Entry<String, String> entry : params.entrySet()) {
				if (!first) {
					url.append('&');
				}
				first = false;
				url.append(entry.getKey()).append('=').append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
			}
		}
		try {
			HttpResponse<String> response = Http.get(url.toString(), headers());
			return JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (Exception e) {
			throw new MusicException("Bilibili request failed: " + path, e);
		}
	}

	private HttpResponse<String> send(String base, String path, Map<String, String> params) throws Exception {
		StringBuilder url = new StringBuilder(base).append(path).append('?');
		boolean first = true;
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (!first) {
				url.append('&');
			}
			first = false;
			url.append(entry.getKey()).append('=').append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
		}
		return Http.get(url.toString(), headers());
	}

	// ── WBI signing ─────────────────────────────────────────────────────────

	private Map<String, String> signed(Map<String, String> params) {
		Map<String, String> signed = new LinkedHashMap<>(params);
		signed.put("wts", String.valueOf(System.currentTimeMillis() / 1000));
		StringBuilder query = new StringBuilder();
		signed.keySet().stream().sorted().forEach(key -> {
			if (!query.isEmpty()) {
				query.append('&');
			}
			query.append(key).append('=').append(URLEncoder.encode(clean(signed.get(key)), StandardCharsets.UTF_8));
		});
		String wRid = md5(query + mixinKey());
		signed.put("w_rid", wRid);
		return signed;
	}

	private String mixinKey() {
		if (mixinKey != null && System.currentTimeMillis() - mixinKeyFetchedAt < 3_600_000L) {
			return mixinKey;
		}
		JsonObject data = optObject(get(API, "/x/web-interface/nav", Map.of()), "data");
		JsonObject wbi = data == null ? null : optObject(data, "wbi_img");
		if (wbi == null) {
			throw new MusicException("Bilibili did not return WBI keys");
		}
		String img = filename(text(wbi, "img_url"));
		String sub = filename(text(wbi, "sub_url"));
		String raw = img + sub;
		StringBuilder key = new StringBuilder();
		for (int index : MIXIN_TABLE) {
			if (index < raw.length()) {
				key.append(raw.charAt(index));
			}
		}
		mixinKey = key.substring(0, Math.min(32, key.length()));
		mixinKeyFetchedAt = System.currentTimeMillis();
		return mixinKey;
	}

	private static String filename(String url) {
		String name = url.substring(url.lastIndexOf('/') + 1);
		int dot = name.indexOf('.');
		return dot < 0 ? name : name.substring(0, dot);
	}

	private static String clean(String value) {
		return value.replaceAll("[!'()*]", "");
	}

	private static String md5(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("MD5 unavailable", e);
		}
	}

	// ── Parsing ─────────────────────────────────────────────────────────────

	private static Track parseTrack(JsonObject object) {
		JsonElement bvid = object.get("bvid");
		if (bvid == null || bvid.isJsonNull()) {
			return null;
		}
		String title = text(object, "title")
				.replaceAll("<[^>]+>", "")
				.replace("&quot;", "\"")
				.replace("&amp;", "&")
				.replace("&#39;", "'");
		String cover = text(object, "pic");
		if (cover.startsWith("//")) {
			cover = "https:" + cover;
		}
		return new Track(MusicSource.BILIBILI, bvid.getAsString(), title, text(object, "author"), "",
				parseDuration(text(object, "duration")), cover, false);
	}

	static long parseDuration(String value) {
		if (value == null || value.isBlank()) {
			return 0L;
		}
		long total = 0L;
		for (String part : value.split(":")) {
			total = total * 60 + Long.parseLong(part.trim());
		}
		return total * 1000L;
	}

	private static JsonObject optObject(JsonObject parent, String key) {
		JsonElement element = parent == null ? null : parent.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static JsonArray arr(JsonObject parent, String key) {
		JsonElement element = parent.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
	}

	private static String text(JsonObject parent, String key) {
		JsonElement element = parent.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	private static int intValue(JsonObject parent, String key, int fallback) {
		JsonElement element = parent.get(key);
		return element == null || element.isJsonNull() ? fallback : element.getAsInt();
	}
}
