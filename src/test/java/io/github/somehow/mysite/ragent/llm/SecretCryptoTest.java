package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.commons.framework.exception.ClientException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecretCrypto — AES-256-GCM")
class SecretCryptoTest {

    @Test
    @DisplayName("加解密往返，密文带 enc:v1: 前缀且不含明文")
    void roundTrip() {
        SecretCrypto crypto = new SecretCrypto(masterKey());
        String cipher = crypto.encrypt("sk-secret");
        assertTrue(crypto.isCiphertext(cipher));
        assertTrue(cipher.startsWith(SecretCrypto.PREFIX));
        assertFalse(cipher.contains("sk-secret"));
        assertEquals("sk-secret", crypto.decrypt(cipher));
    }

    @Test
    @DisplayName("每次加密 IV 不同，密文不相等")
    void ivIsRandom() {
        SecretCrypto crypto = new SecretCrypto(masterKey());
        assertNotEquals(crypto.encrypt("same"), crypto.encrypt("same"));
    }

    @Test
    @DisplayName("明文原样返回，已是密文不再套一层")
    void plaintextAndIdempotentEncrypt() {
        SecretCrypto crypto = new SecretCrypto(masterKey());
        assertEquals("plain", crypto.decrypt("plain"));
        assertNull(crypto.decrypt(null));
        String cipher = crypto.encrypt("sk");
        assertEquals(cipher, crypto.encrypt(cipher));
    }

    @Test
    @DisplayName("空串不加密、不要求主密钥")
    void emptySkipsCrypto() {
        SecretCrypto crypto = new SecretCrypto("");
        assertFalse(crypto.isConfigured());
        assertEquals("", crypto.encrypt(""));
        assertNull(crypto.encrypt(null));
    }

    @Test
    @DisplayName("错误主密钥无法解密")
    void wrongKeyFails() {
        String keyA = masterKey();
        String keyB = masterKey();
        String cipher = new SecretCrypto(keyA).encrypt("sk-secret");
        ClientException ex = assertThrows(ClientException.class, () -> new SecretCrypto(keyB).decrypt(cipher));
        assertTrue(ex.getMessage().contains("解密"));
    }

    @Test
    @DisplayName("不支持的密文版本直接失败")
    void unknownVersionFails() {
        SecretCrypto crypto = new SecretCrypto(masterKey());
        ClientException ex = assertThrows(ClientException.class, () -> crypto.decrypt("enc:v2:AAAA"));
        assertTrue(ex.getMessage().contains("密文版本"));
        assertTrue(crypto.isCiphertext("enc:v2:AAAA"));
    }

    @Nested
    @DisplayName("主密钥校验")
    class MasterKey {

        @Test
        @DisplayName("长度不是 32 字节则启动失败")
        void wrongLengthFails() {
            String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
            ClientException ex = assertThrows(ClientException.class, () -> new SecretCrypto(shortKey));
            assertTrue(ex.getMessage().contains("32 字节"));
        }

        @Test
        @DisplayName("非法 Base64 失败")
        void invalidBase64Fails() {
            ClientException ex = assertThrows(ClientException.class, () -> new SecretCrypto("%%%not-base64%%%"));
            assertTrue(ex.getMessage().contains("Base64"));
        }

        @Test
        @DisplayName("缺主密钥时加密保存失败")
        void encryptWithoutKeyFails() {
            SecretCrypto crypto = new SecretCrypto("");
            ClientException ex = assertThrows(ClientException.class, () -> crypto.encrypt("sk-secret"));
            assertTrue(ex.getMessage().contains(SecretCrypto.ENV_NAME));
        }

        @Test
        @DisplayName("缺主密钥时解密密文失败")
        void decryptWithoutKeyFails() {
            SecretCrypto crypto = new SecretCrypto(null);
            ClientException ex = assertThrows(ClientException.class,
                () -> crypto.decrypt(SecretCrypto.PREFIX + "AAAA"));
            assertTrue(ex.getMessage().contains("拒绝启动"));
        }
    }

    static String masterKey() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }
}
