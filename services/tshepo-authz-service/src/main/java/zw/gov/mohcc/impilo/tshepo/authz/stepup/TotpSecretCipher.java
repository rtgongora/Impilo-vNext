package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts TOTP shared secrets at rest with AES-256-GCM.
 *
 * <p>The 256-bit key is derived (SHA-256) from a strong deployment secret
 * ({@code tshepo.authz.totp.encryption-key}). The component refuses to start when TOTP enrolment is
 * enabled without a sufficiently strong key — the same fail-closed posture as
 * {@code mushe-wallet CardHealthDataService}. The key is a deployment secret (config), not an
 * external service.</p>
 */
@Component
@ConditionalOnProperty(name = "tshepo.authz.totp.enrolment-enabled", havingValue = "true")
public class TotpSecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public TotpSecretCipher(AuthzProperties properties) {
        String configured = properties.getTotp().getEncryptionKey();
        if (configured == null || configured.strip().length() < 32) {
            throw new IllegalStateException(
                    "tshepo.authz.totp.encryption-key must be a strong secret of at least 32 characters "
                            + "when TOTP enrolment is enabled. Refusing to start: enrolled TOTP secrets must "
                            + "be encrypted at rest.");
        }
        try {
            byte[] aesKey = MessageDigest.getInstance("SHA-256")
                    .digest(configured.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(aesKey, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive TOTP encryption key", e);
        }
    }

    /** Encrypted payload: base64 ciphertext + base64 IV. */
    public record Encrypted(String cipherBase64, String ivBase64) {}

    public Encrypted encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);
            return new Encrypted(Base64.getEncoder().encodeToString(ct),
                    Base64.getEncoder().encodeToString(iv));
        } catch (Exception e) {
            throw new IllegalStateException("TOTP secret encryption failed", e);
        }
    }

    public byte[] decrypt(String cipherBase64, String ivBase64) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] ct = Base64.getDecoder().decode(cipherBase64);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP secret decryption failed (tampered or wrong key)", e);
        }
    }
}
