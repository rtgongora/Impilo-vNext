package zw.gov.mohcc.impilo.vito.core.did;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Black Box Sovereign ID Generator — W3C Decentralized Identifier (DID).
 *
 * Format: did:impilo:[sha256_hash]
 *
 * Design properties:
 *   - STATELESS: no database, no PII, no side effects
 *   - ISOLATED: input is only a system-generated UUID + entropy
 *   - IRREVERSIBLE: SHA-256 hash cannot be reversed to recover input
 *   - DETERMINISTIC for same input: same (healthId, entropy) → same DID
 *   - COLLISION-RESISTANT: 256-bit hash space
 *
 * The DID is generated from a health_id UUID combined with server-side
 * entropy. The health_id itself is a random UUID with no PII embedded.
 * This makes the DID a pure cryptographic identifier.
 *
 * W3C DID spec: https://www.w3.org/TR/did-core/
 * Method: did:impilo (custom method for the Impilo Health OS)
 */
@Component
public class SovereignIdGenerator {

    private static final String DID_METHOD = "impilo";
    private static final String DID_PREFIX = "did:" + DID_METHOD + ":";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generate a new sovereign DID for a client.
     *
     * @param healthId the client's health_id UUID (system-generated, no PII)
     * @return a W3C DID in format did:impilo:[sha256_hex]
     */
    public String generate(UUID healthId) {
        // Combine health_id with 32 bytes of server entropy
        byte[] entropy = new byte[32];
        SECURE_RANDOM.nextBytes(entropy);

        return generateFromComponents(healthId, entropy);
    }

    /**
     * Generate a DID from explicit components (for deterministic replay in recovery).
     *
     * @param healthId the client's health_id UUID
     * @param entropy  32 bytes of entropy (must be stored/recovered separately)
     * @return did:impilo:[sha256_hex]
     */
    public String generateFromComponents(UUID healthId, byte[] entropy) {
        byte[] input = buildInput(healthId, entropy);
        byte[] hash = sha256(input);
        return DID_PREFIX + HexFormat.of().formatHex(hash);
    }

    /**
     * Validate that a string is a well-formed did:impilo URI.
     */
    public boolean isValid(String did) {
        if (did == null || !did.startsWith(DID_PREFIX)) {
            return false;
        }
        String hash = did.substring(DID_PREFIX.length());
        return hash.length() == 64 && hash.chars().allMatch(c ->
                (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
    }

    /**
     * Extract the method-specific identifier (hash) from a DID URI.
     */
    public String extractHash(String did) {
        if (!isValid(did)) {
            throw new IllegalArgumentException("Invalid DID: " + did);
        }
        return did.substring(DID_PREFIX.length());
    }

    private byte[] buildInput(UUID healthId, byte[] entropy) {
        byte[] healthIdBytes = healthId.toString().getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[healthIdBytes.length + entropy.length];
        System.arraycopy(healthIdBytes, 0, combined, 0, healthIdBytes.length);
        System.arraycopy(entropy, 0, combined, healthIdBytes.length, entropy.length);
        return combined;
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JCA spec
            throw new AssertionError("SHA-256 not available", e);
        }
    }
}
