package zw.gov.mohcc.impilo.abis.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * AES-256-GCM template encryption at rest. The KEK ({@code abis.template-kek}) must come from
 * tshepo-keys custody / a KMS in production. Ciphertext layout: 12-byte IV || GCM ciphertext+tag.
 *
 * <p>Templates are irreversible biometric derivatives; they are still encrypted at rest so a
 * database compromise alone does not yield usable templates (blast-radius isolation, D6).</p>
 */
@Component
public class TemplateCrypto {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec kek;
    private final SecureRandom secureRandom = new SecureRandom();

    public TemplateCrypto(@Value("${abis.template-kek}") String kekHex) {
        byte[] keyBytes = HexFormat.of().parseHex(kekHex);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("abis.template-kek must be 32 bytes (64 hex chars)");
        }
        this.kek = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (Exception e) {
            throw new IllegalStateException("Template encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] encrypted) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, kek,
                    new GCMParameterSpec(TAG_BITS, encrypted, 0, IV_LENGTH));
            return cipher.doFinal(encrypted, IV_LENGTH, encrypted.length - IV_LENGTH);
        } catch (Exception e) {
            throw new IllegalStateException("Template decryption failed", e);
        }
    }
}
