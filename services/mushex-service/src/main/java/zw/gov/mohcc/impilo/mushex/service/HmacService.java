package zw.gov.mohcc.impilo.mushex.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 hashing service used for remittance token/OTP hashing
 * and webhook signature verification.
 *
 * <p>Registered as a Spring bean from {@link zw.gov.mohcc.impilo.mushex.config.MushexCryptoConfig}.</p>
 */
public class HmacService {

    private static final String ALGORITHM = "HmacSHA256";
    private final String pepper;

    public HmacService(String pepper) {
        this.pepper = pepper;
    }

    /**
     * Compute HMAC-SHA256 hex digest of the given data using the configured pepper.
     */
    public String hash(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM
            );
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }

    /**
     * Constant-time comparison of two HMAC digests to prevent timing attacks.
     */
    public boolean verify(String data, String expectedHash) {
        String computedHash = hash(data);
        return MessageDigest.isEqual(
            computedHash.getBytes(StandardCharsets.UTF_8),
            expectedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
