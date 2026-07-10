package com.eldercare.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

/**
 * 密码加密工具。
 * 完整移植自原 JavaFX 项目 DataManager 的 PBKDF2 方案,保证与旧数据兼容:
 * PBKDF2WithHmacSHA256 + 随机盐 + 12 万次迭代,存储格式 pbkdf2$迭代次数$盐$哈希。
 * 同时支持"旧明文密码"的校验(便于种子数据用明文,首次登录后升级)。
 */
@Component
public class PasswordEncoder {

    private static final int HASH_ITERATIONS = 120000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final String HASH_PREFIX = "pbkdf2";

    /** 生成哈希字符串。 */
    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, HASH_ITERATIONS, HASH_BYTES);
        return HASH_PREFIX + "$" + HASH_ITERATIONS + "$" +
                Base64.getEncoder().encodeToString(salt) + "$" +
                Base64.getEncoder().encodeToString(hash);
    }

    /** 校验候选密码是否匹配已存储的密码(存储的可能是哈希,也可能是旧明文)。 */
    public boolean matches(String candidate, String stored) {
        if (!isHashed(stored)) {
            // 旧明文密码:直接比较
            return candidate.equals(stored);
        }
        try {
            String[] parts = stored.split("\\$");
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(candidate.toCharArray(), salt, iterations, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 判断存储的密码是否已是哈希格式。 */
    public boolean isHashed(String password) {
        return password != null && password.startsWith(HASH_PREFIX + "$");
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }
}
