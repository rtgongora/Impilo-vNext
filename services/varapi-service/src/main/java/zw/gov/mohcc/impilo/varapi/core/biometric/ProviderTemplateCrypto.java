package zw.gov.mohcc.impilo.varapi.core.biometric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM encryption at rest for provider biometric templates
 * ({@code varapi.provider_biometric_template.template_data}).
 *
 * <p>Templates are irreversible, non-resettable biometric derivatives — a database compromise that
 * yielded them would be permanent. They must never sit in plaintext (PII Enforcement Wave, 0.1).
 * The at-rest layout is {@code MAGIC || IV(12) || GCM ciphertext+tag}.</p>
 *
 * <p>Key custody follows the varapi house pattern ({@code BadgeSigningService}): production supplies
 * {@code VARAPI_BIOMETRIC_TEMPLATE_KEY_SEED} via the tshepo-keys custody flow; a loud dev fallback
 * keeps preview working but must never reach production. The 32-byte AES key is SHA-256 of the seed.</p>
 *
 * <p>{@link #decrypt(byte[])} is <b>plaintext-tolerant</b>: a value not carrying {@link #MAGIC} is
 * returned unchanged, so any pre-encryption (legacy plaintext) row still reads correctly and is
 * upgraded to ciphertext on its next write.</p>
 */
@Component
public class ProviderTemplateCrypto {

    private static final Logger log = LoggerFactory.getLogger(ProviderTemplateCrypto.class);
    private static final String DEV_FALLBACK_SEED = "varapi-biometric-template-dev-seed-do-not-use-in-production";
    /** Version marker distinguishing our ciphertext envelope from legacy plaintext. */
    private static final byte[] MAGIC = "VBT1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec kek;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProviderTemplateCrypto(@Value("${varapi.biometric.template-key-seed:}") String configuredSeed) {
        String seed = configuredSeed == null || configuredSeed.isBlank() ? DEV_FALLBACK_SEED : configuredSeed;
        if (DEV_FALLBACK_SEED.equals(seed)) {
            log.warn("PROVIDER BIOMETRIC TEMPLATE ENCRYPTION IS USING THE DEV FALLBACK SEED — set "
                    + "VARAPI_BIOMETRIC_TEMPLATE_KEY_SEED (varapi.biometric.template-key-seed) via tshepo-keys "
                    + "custody before capturing real provider biometrics.");
        }
        try {
            byte[] key32 = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            this.kek = new SecretKeySpec(key32, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive provider-template KEK", e);
        }
    }

    public byte[] encrypt(byte[] plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(MAGIC.length + iv.length + ciphertext.length)
                    .put(MAGIC).put(iv).put(ciphertext).array();
        } catch (Exception e) {
            throw new IllegalStateException("Provider template encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        if (stored == null) {
            return null;
        }
        // Plaintext-tolerant: anything not carrying our envelope marker is a legacy plaintext row.
        if (stored.length < MAGIC.length + IV_LENGTH || !hasMagic(stored)) {
            return stored;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, kek,
                    new GCMParameterSpec(TAG_BITS, stored, MAGIC.length, IV_LENGTH));
            int offset = MAGIC.length + IV_LENGTH;
            return cipher.doFinal(stored, offset, stored.length - offset);
        } catch (Exception e) {
            throw new IllegalStateException("Provider template decryption failed", e);
        }
    }

    private static boolean hasMagic(byte[] value) {
        return Arrays.equals(value, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }
}
