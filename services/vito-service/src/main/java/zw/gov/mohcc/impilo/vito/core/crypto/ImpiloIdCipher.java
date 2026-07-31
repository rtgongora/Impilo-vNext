package zw.gov.mohcc.impilo.vito.core.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.security.secrets.RequiredSecret;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the patient-facing Impilo ID at rest with AES-256-GCM
 * (Identity Contract §6, Pattern B).
 *
 * <p>The Impilo ID must never be stored in plaintext outside the trust layer.
 * VITO is the issuer, so within VITO it is held as an encrypted credential; the
 * HMAC/Argon2id alias vault ({@code ImpiloIdAliasService}) remains the only
 * lookup index. This cipher provides the recoverable half — reissue, same-number
 * card replacement, and erroneous-issuance investigation all need to re-read the
 * value, which the one-way alias vault cannot supply.</p>
 *
 * <p>Envelope format: {@code v<keyVersion>:<ivBase64>:<ctBase64>}. The version
 * prefix lets a future key rotation decrypt old ciphertexts while writing new
 * ones under a new key.</p>
 *
 * <p>Key custody: the key is a deployment secret
 * ({@code vito.identity.impilo-id-encryption-key}); the tshepo-keys custody path
 * is the production target. It is required, not defaulted. This used to fall back to a
 * source-visible dev key with a warning so the preview estate would start, which meant every
 * environment ran on a key published in this repository — the property was provisioned nowhere,
 * so the fallback was the only path anything ever took. Encryption under a public key is not
 * encryption.</p>
 */
@Component
public class ImpiloIdCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_VERSION = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public ImpiloIdCipher(
            @Value("${vito.identity.impilo-id-encryption-key:}") String configured) {
        String material = RequiredSecret.require(
                "vito.identity.impilo-id-encryption-key", configured,
                "Impilo IDs are encrypted at rest with it, so a guessable key exposes every patient identifier.");
        try {
            byte[] aesKey = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(aesKey, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive Impilo ID encryption key", e);
        }
    }

    /** Encrypt a plaintext Impilo ID into the versioned envelope. Null passes through. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v" + KEY_VERSION + ":"
                    + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(ct);
        } catch (Exception e) {
            throw new IllegalStateException("Impilo ID encryption failed", e);
        }
    }

    /** Decrypt a versioned envelope back to the plaintext Impilo ID. Null passes through. */
    public String decrypt(String envelope) {
        if (envelope == null) {
            return null;
        }
        String[] parts = envelope.split(":", 3);
        if (parts.length != 3 || !parts[0].startsWith("v")) {
            // Tolerate legacy plaintext values (pre-migration / preview seeds) — they
            // are exactly the 10-char format, never containing ':'.
            return envelope;
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] ct = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Impilo ID decryption failed (tampered or wrong key)", e);
        }
    }
}
