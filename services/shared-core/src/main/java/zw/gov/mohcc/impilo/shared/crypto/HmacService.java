package zw.gov.mohcc.impilo.shared.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * HMAC-SHA256 service for computing identity lookup hashes.
 *
 * Used by VITO to create indexed, non-reversible lookup keys for the
 * Identity Alias Pattern. The lookup_hash allows O(1) record retrieval
 * without storing plaintext identifiers.
 *
 * Security properties:
 *   - Pepper is a server-side secret (never exposed to clients)
 *   - HMAC output is deterministic for the same (pepper, input) pair
 *   - Cannot reverse to recover the original input without the pepper
 *   - Indistinguishable error responses prevent enumeration
 *
 * Thread-safe: each call creates its own Mac instance.
 */
public class HmacService {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] pepperBytes;

    /**
     * @param pepper the server-side secret pepper (from K8s Secret or Vault)
     */
    public HmacService(String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalArgumentException("Pepper must not be null or blank");
        }
        this.pepperBytes = pepper.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Compute the HMAC-SHA256 lookup hash for a normalized identity value.
     *
     * @param normalizedInput the normalized identity value (lowercase, trimmed, etc.)
     * @return hex-encoded HMAC-SHA256 hash (64 characters)
     */
    public String computeLookupHash(String normalizedInput) {
        if (normalizedInput == null || normalizedInput.isBlank()) {
            throw new IllegalArgumentException("Input must not be null or blank");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepperBytes, ALGORITHM));
            byte[] hash = mac.doFinal(normalizedInput.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /**
     * Verify that an input produces the expected lookup hash.
     * Uses constant-time comparison to prevent timing attacks.
     */
    public boolean verifyLookupHash(String normalizedInput, String expectedHash) {
        String computed = computeLookupHash(normalizedInput);
        return constantTimeEquals(computed, expectedHash);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
