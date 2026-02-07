package zw.gov.mohcc.impilo.vito.core.recovery;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Secure Health Summary (SHS) — JWS/Verifiable Credential Compressor.
 *
 * Packs a FHIR Patient summary into a signed, offline-readable format
 * that can be stored on a SMART Card.
 *
 * Format: JWS Compact Serialization (header.payload.signature)
 *   - Header: {"alg":"HS256","typ":"VC+JWT"}
 *   - Payload: compressed FHIR Bundle JSON
 *   - Signature: HMAC-SHA256 using shared secret
 *
 * For production: upgrade to RS256/ES256 with card's private key
 * for true asymmetric verification.
 */
@Service
public class HealthSummaryService {

    private final byte[] signingKey;

    public HealthSummaryService(VitoProperties properties) {
        // Use HMAC pepper as signing key for now.
        // Production: use dedicated SHS signing key from Vault.
        this.signingKey = properties.getHmac().getPepper().getBytes(StandardCharsets.UTF_8);
        // Ensure key is at least 256 bits for HS256
        if (this.signingKey.length < 32) {
            throw new IllegalArgumentException("SHS signing key must be at least 32 bytes");
        }
    }

    /**
     * Compress and sign a FHIR Bundle JSON into JWS compact format.
     *
     * @param fhirBundleJson the FHIR Patient summary Bundle as JSON string
     * @param healthId       the client's health_id for claim binding
     * @return JWS compact serialization string
     */
    public String compressAndSign(String fhirBundleJson, String healthId) {
        try {
            // Build payload with metadata
            String payload = String.format(
                    "{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d,\"fhir\":%s}",
                    healthId,
                    Instant.now().getEpochSecond(),
                    Instant.now().plus(365, ChronoUnit.DAYS).getEpochSecond(),
                    fhirBundleJson);

            JWSObject jws = new JWSObject(
                    new JWSHeader.Builder(JWSAlgorithm.HS256)
                            .type(new JOSEObjectType("VC+JWT"))
                            .build(),
                    new Payload(payload));

            jws.sign(new MACSigner(signingKey));

            return jws.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign health summary", e);
        }
    }

    /**
     * Verify and extract the FHIR Bundle from a JWS compact string.
     *
     * @param jwsCompact the JWS compact serialization
     * @return the payload JSON string, or null if verification fails
     */
    public String verifyAndExtract(String jwsCompact) {
        try {
            JWSObject jws = JWSObject.parse(jwsCompact);
            JWSVerifier verifier = new MACVerifier(signingKey);

            if (!jws.verify(verifier)) {
                return null;
            }

            return jws.getPayload().toString();
        } catch (Exception e) {
            return null;
        }
    }
}
