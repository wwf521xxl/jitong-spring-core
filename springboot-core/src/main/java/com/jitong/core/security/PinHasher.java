package com.jitong.core.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

/**
 * PIN 存储格式：
 * pbkdf2_sha256$迭代次数$salt(base64url)$digest(base64url)
 * 与历史数据保持兼容，升级后账号无需重置密码。
 */
@Component
public class PinHasher {

    public static final int DEFAULT_ITERATIONS = 160_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();

    public String hash(String raw) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] digest = pbkdf2(raw, salt, DEFAULT_ITERATIONS);
        return "pbkdf2_sha256$" + DEFAULT_ITERATIONS + "$"
                + Base64.getUrlEncoder().encodeToString(salt) + "$"
                + Base64.getUrlEncoder().encodeToString(digest);
    }

    public boolean matches(String raw, String encoded) {
        if (raw == null || encoded == null) {
            return false;
        }
        try {
            String[] parts = encoded.split("\\$", 4);
            if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(raw, salt, iterations);
            return MessageDigest.isEqual(actual, expected);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private byte[] pbkdf2(String raw, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("PBKDF2 不可用", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
