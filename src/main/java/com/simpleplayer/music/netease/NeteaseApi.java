package com.simpleplayer.music.netease;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simpleplayer.config.ClientConfig.NeteaseQuality;
import com.simpleplayer.music.Http;
import com.simpleplayer.music.MusicException;
import com.simpleplayer.music.MusicSource;
import com.simpleplayer.music.Playlist;
import com.simpleplayer.music.Track;

/**
 * NetEase Cloud Music client (weapi endpoints).
 */
public final class NeteaseApi {

	private static final Gson GSON = new Gson();
	private static final String DEFAULT_BASE = "https://music.163.com";

	private final String base;
	private volatile String cookie = "";

	public NeteaseApi(String apiBase) {
		this.base = apiBase == null || apiBase.isBlank() ? DEFAULT_BASE : apiBase.replaceAll("/+$", "");
	}

	public void setCookie(String cookie) {
		this.cookie = cookie == null ? "" : cookie;
	}

	public String getCookie() {
		return cookie;
	}

	public boolean loggedIn() {
		return cookie.contains("MUSIC_U=");
	}

	/** Returns the logged-in nickname, or {@code null} when not logged in. */
	public String accountName() {
		try {
			JsonObject data = weapi("/weapi/w/nuser/account/get", new LinkedHashMap<>());
			JsonObject profile = optObject(data, "profile");
			return profile == null ? null : text(profile, "nickname");
		} catch (Exception e) {
			return null;
		}
	}

	/** Account id and nickname, or {@code null} when the stored cookie is missing or expired. */
	public Profile profile() {
		try {
			JsonObject data = weapi("/weapi/w/nuser/account/get", new LinkedHashMap<>());
			JsonObject profile = optObject(data, "profile");
			if (profile == null) {
				return null;
			}
			JsonElement id = profile.get("userId");
			long userId = id == null || id.isJsonNull() ? 0L : id.getAsLong();
			if (userId <= 0L) {
				return null;
			}
			return new Profile(userId, text(profile, "nickname"));
		} catch (Exception e) {
			return null;
		}
	}

	public record Profile(long userId, String nickname) {
	}

	// ── Search ──────────────────────────────────────────────────────────────

	public List<Track> search(String keyword, int limit, int offset) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("s", keyword);
		params.put("type", "1");
		params.put("limit", String.valueOf(limit));
		params.put("offset", String.valueOf(offset));
		JsonObject result = optObject(weapi("/weapi/search/get", params), "result");
		JsonArray songs = result == null ? null : arr(result, "songs");
		List<Track> tracks = new ArrayList<>();
		if (songs == null) {
			return tracks;
		}
		for (JsonElement element : songs) {
			Track track = parseTrack(element.getAsJsonObject());
			if (track != null) {
				tracks.add(track);
			}
		}
		return tracks;
	}

	// ── Stream URL ──────────────────────────────────────────────────────────

	public String resolveUrl(Track track, NeteaseQuality quality) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("ids", "[" + track.id() + "]");
		params.put("level", level(quality));
		params.put("encodeType", "flac");
		JsonArray data = arr(weapi("/weapi/song/enhance/player/url/v1", params), "data");
		if (data == null || data.isEmpty()) {
			throw new MusicException("NetEase returned no stream for " + track.title());
		}
		JsonElement url = data.get(0).getAsJsonObject().get("url");
		if (url == null || url.isJsonNull() || url.getAsString().isBlank()) {
			throw new MusicException("NetEase returned an empty stream (login or membership required)");
		}
		return url.getAsString();
	}

	private static String level(NeteaseQuality quality) {
		return switch (quality) {
			case STANDARD -> "standard";
			case HIGH -> "exhigh";
			case LOSSLESS -> "lossless";
			default -> "exhigh";
		};
	}

	/** Daily recommendation list (requires login). */
	public List<Track> dailyRecommendations() {
		JsonObject root = weapi("/weapi/v3/discovery/recommend/songs", new LinkedHashMap<>());
		JsonObject data = optObject(root, "data");
		JsonArray songs = data == null ? null : arr(data, "dailySongs");
		List<Track> out = new ArrayList<>();
		if (songs == null) {
			return out;
		}
		for (JsonElement element : songs) {
			Track track = parseTrack(element.getAsJsonObject());
			if (track != null) {
				out.add(track);
			}
		}
		return out;
	}

	// ── Playlists ───────────────────────────────────────────────────────────

	/** Playlists owned by or subscribed to by {@code userId}. */
	public List<Playlist> userPlaylists(long userId) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("uid", String.valueOf(userId));
		params.put("limit", "100");
		params.put("offset", "0");
		JsonObject root = weapi("/weapi/user/playlist", params);
		JsonArray entries = arr(root, "playlist");
		if (entries == null) {
			JsonObject data = optObject(root, "data");
			entries = data == null ? null : arr(data, "playlist");
		}
		List<Playlist> out = new ArrayList<>();
		if (entries == null) {
			return out;
		}
		for (JsonElement element : entries) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject entry = element.getAsJsonObject();
			JsonElement id = entry.get("id");
			if (id == null || id.isJsonNull()) {
				continue;
			}
			JsonObject creator = optObject(entry, "creator");
			int trackCount = entry.has("trackCount") && !entry.get("trackCount").isJsonNull()
					? entry.get("trackCount").getAsInt()
					: 0;
			out.add(new Playlist(
					id.getAsString(),
					text(entry, "name"),
					creator == null ? "" : text(creator, "nickname"),
					nullableText(entry, "coverImgUrl"),
					trackCount));
		}
		return out;
	}

	public List<Track> playlistTracks(String playlistId, int limit) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("id", playlistId);
		params.put("n", String.valueOf(limit));
		params.put("s", "8");
		JsonObject playlist = optObject(weapi("/weapi/v6/playlist/detail", params), "playlist");
		JsonArray tracks = playlist == null ? null : arr(playlist, "tracks");
		List<Track> out = new ArrayList<>();
		if (tracks == null) {
			return out;
		}
		for (JsonElement element : tracks) {
			Track track = parseTrack(element.getAsJsonObject());
			if (track != null) {
				out.add(track);
			}
		}
		return out;
	}

	// ── QR login ────────────────────────────────────────────────────────────

	public record QrCode(String key, String url) {
	}

	public enum LoginState {
		WAITING,
		SCANNED,
		CONFIRMED,
		EXPIRED,
		ERROR
	}

	public QrCode createQrCode() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("type", "1");
		JsonElement unikey = weapi("/weapi/login/qrcode/unikey", params).get("unikey");
		if (unikey == null || unikey.isJsonNull()) {
			throw new MusicException("NetEase did not return a QR key");
		}
		String key = unikey.getAsString();
		return new QrCode(key, "https://music.163.com/login?codekey=" + key);
	}

	public LoginState checkQrCode(QrCode qr) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("key", qr.key());
		params.put("type", "1");
		try {
			HttpResponse<String> response = post("/weapi/login/qrcode/client/login", params);
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			int code = json.has("code") ? json.get("code").getAsInt() : -1;
			LoginState state = switch (code) {
				case 803 -> LoginState.CONFIRMED;
				case 802 -> LoginState.SCANNED;
				case 801 -> LoginState.WAITING;
				case 800 -> LoginState.EXPIRED;
				default -> LoginState.ERROR;
			};
			if (state == LoginState.CONFIRMED) {
				captureCookies(response);
			}
			return state;
		} catch (Exception e) {
			return LoginState.ERROR;
		}
	}

	private void captureCookies(HttpResponse<String> response) {
		Map<String, String> jar = new LinkedHashMap<>();
		for (String part : cookie.split(";")) {
			String trimmed = part.trim();
			int eq = trimmed.indexOf('=');
			if (eq > 0) {
				jar.put(trimmed.substring(0, eq), trimmed.substring(eq + 1));
			}
		}
		for (String header : response.headers().allValues("set-cookie")) {
			String pair = header.split(";", 2)[0].trim();
			int eq = pair.indexOf('=');
			if (eq > 0) {
				jar.put(pair.substring(0, eq), pair.substring(eq + 1));
			}
		}
		StringBuilder merged = new StringBuilder();
		jar.forEach((name, value) -> {
			if (!merged.isEmpty()) {
				merged.append("; ");
			}
			merged.append(name).append('=').append(value);
		});
		cookie = merged.toString();
	}

	// ── HTTP plumbing ───────────────────────────────────────────────────────

	private HttpRequestBuilder request(String path, Map<String, String> params) {
		Weapi.Encrypted encrypted = Weapi.encrypt(GSON.toJson(params));
		Map<String, String> form = new LinkedHashMap<>();
		form.put("params", encrypted.params());
		form.put("encSecKey", encrypted.encSecKey());
		return new HttpRequestBuilder(base + path, form);
	}

	private JsonObject weapi(String path, Map<String, String> params) {
		try {
			HttpResponse<String> response = request(path, params).send();
			return JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (MusicException e) {
			throw e;
		} catch (Exception e) {
			throw new MusicException("NetEase request failed: " + path, e);
		}
	}

	private HttpResponse<String> post(String path, Map<String, String> params) throws Exception {
		return request(path, params).send();
	}

	private final class HttpRequestBuilder {
		private final String url;
		private final Map<String, String> form;

		private HttpRequestBuilder(String url, Map<String, String> form) {
			this.url = url;
			this.form = form;
		}

		private HttpResponse<String> send() throws Exception {
			Map<String, String> headers = new LinkedHashMap<>();
			headers.put("Referer", "https://music.163.com");
			headers.put("Origin", "https://music.163.com");
			headers.put("User-Agent", "Mozilla/5.0");
			String cookies = "os=pc; appver=8.9.70" + (cookie.isEmpty() ? "" : "; " + cookie);
			headers.put("Cookie", cookies);
			return Http.postForm(url, form, headers);
		}
	}

	// ── Parsing helpers ─────────────────────────────────────────────────────

	private static Track parseTrack(JsonObject object) {
		JsonElement id = object.get("id");
		if (id == null || id.isJsonNull()) {
			return null;
		}
		JsonElement name = object.get("name");
		JsonArray artists = arr(object, "ar");
		if (artists == null) {
			artists = arr(object, "artists");
		}
		StringBuilder names = new StringBuilder();
		if (artists != null) {
			for (JsonElement element : artists) {
				JsonElement artistName = element.getAsJsonObject().get("name");
				if (artistName != null && !artistName.isJsonNull()) {
					if (!names.isEmpty()) {
						names.append(" / ");
					}
					names.append(artistName.getAsString());
				}
			}
		}
		JsonObject album = optObject(object, "al");
		if (album == null) {
			album = optObject(object, "album");
		}
		String albumName = album == null ? "" : text(album, "name");
		String cover = album == null ? null : nullableText(album, "picUrl");
		JsonElement duration = object.get("dt") != null ? object.get("dt") : object.get("duration");
		long millis = duration == null || duration.isJsonNull() ? 0L : duration.getAsLong();
		int fee = object.has("fee") && !object.get("fee").isJsonNull() ? object.get("fee").getAsInt() : 0;
		return new Track(MusicSource.NETEASE, id.getAsString(), name == null ? "" : name.getAsString(),
				names.toString(), albumName, millis, cover, fee != 0);
	}

	private static JsonObject optObject(JsonObject parent, String key) {
		JsonElement element = parent.get(key);
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

	private static String nullableText(JsonObject parent, String key) {
		JsonElement element = parent.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}
}
