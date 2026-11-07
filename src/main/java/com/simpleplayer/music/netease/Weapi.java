package com.simpleplayer.music.netease;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * NetEase weapi request encryption (double AES-CBC plus RSA).
 *
 * <p>Reimplemented from MuoniumPlayer's MuWeapi (Apache-2.0).</p>
 */
public final class Weapi {

	private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
	private static final String IV = "0102030405060708";
	private static final String BASE62 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final BigInteger RSA_MODULUS = new BigInteger(
			"00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7",
			16);
	private static final BigInteger RSA_EXPONENT = new BigInteger("010001", 16);
	private static final SecureRandom RANDOM = new SecureRandom();

	private Weapi() {
	}

	public record Encrypted(String params, String encSecKey) {
	}

	public static Encrypted encrypt(String plain) {
		String secKey = randomKey(16);
		String params = aesCbcBase64(aesCbcBase64(plain, PRESET_KEY), secKey);
		return new Encrypted(params, rsa(secKey));
	}

	private static String aesCbcBase64(String text, String key) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE,
					new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
					new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
			return Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("weapi AES failed", e);
		}
	}

	private static String rsa(String text) {
		byte[] reversed = new StringBuilder(text).reverse().toString().getBytes(StandardCharsets.UTF_8);
		BigInteger cipher = new BigInteger(1, reversed).modPow(RSA_EXPONENT, RSA_MODULUS);
		String hex = cipher.toString(16);
		return "0".repeat(Math.max(0, 256 - hex.length())) + hex;
	}

	private static String randomKey(int length) {
		StringBuilder builder = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			builder.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
		}
		return builder.toString();
	}
}
