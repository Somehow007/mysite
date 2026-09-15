package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.commons.framework.exception.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 用环境变量 {@code MYSITE_CRYPTO_KEY}（32 字节 Base64）做 AES-256-GCM。
 * 密文格式 {@code enc:v1:} + Base64(12 字节 IV || ciphertext || 16 字节 tag)，主密钥不进库。
 */
@Slf4j
@Component
public class SecretCrypto {

    public static final String ENV_NAME = "MYSITE_CRYPTO_KEY";
    public static final String PREFIX = "enc:v1:";

    private static final String PREFIX_FAMILY = "enc:";
    private static final int KEY_LEN = 32;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_LEN = TAG_BITS / 8;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;

    public SecretCrypto(@Value("${MYSITE_CRYPTO_KEY:}") String encodedKey) {
        this.key = parseKey(encodedKey);
        if (this.key == null) {
            log.warn("{} 未配置：历史明文 LLM API Key 仍可用；后台保存 Key 前必须写入 32 字节 Base64 主密钥", ENV_NAME);
        } else {
            log.info("LLM API Key encryption enabled (AES-256-GCM {})", PREFIX);
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    public boolean isCiphertext(String value) {
        return value != null && value.startsWith(PREFIX_FAMILY);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        if (isCiphertext(plaintext)) {
            return plaintext;
        }
        ensureKey("未配置 " + ENV_NAME + "，无法保存 API Key。请先执行 openssl rand -base64 32 并写入 .env");
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException("加密 LLM API Key 失败: " + e.getMessage());
        }
    }

    public String decrypt(String stored) {
        if (!isCiphertext(stored)) {
            return stored;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new ClientException("不支持的 LLM API Key 密文版本");
        }
        ensureKey("库中的 LLM API Key 已加密，但未配置 " + ENV_NAME + "，拒绝启动");
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (packed.length < IV_LEN + TAG_LEN) {
                throw new ClientException("LLM API Key 密文格式无效");
            }
            byte[] iv = Arrays.copyOfRange(packed, 0, IV_LEN);
            byte[] cipherText = Arrays.copyOfRange(packed, IV_LEN, packed.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException("解密 LLM API Key 失败: " + e.getMessage());
        }
    }

    private void ensureKey(String message) {
        if (key == null) {
            throw new ClientException(message);
        }
    }

    static SecretKey parseKey(String encoded) {
        if (!StringUtils.hasText(encoded)) {
            return null;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new ClientException(ENV_NAME + " 不是合法 Base64");
        }
        if (raw.length != KEY_LEN) {
            throw new ClientException(ENV_NAME + " 须为 32 字节（Base64 编码），当前 " + raw.length + " 字节");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
