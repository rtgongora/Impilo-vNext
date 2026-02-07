package zw.gov.mohcc.impilo.cardprint.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.cardprint.config.CardPrintProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates signed QR assertion payloads for smart cards.
 *
 * The assertion contains subject identification info plus an HMAC-SHA256 signature
 * that can be verified by any service holding the shared HMAC secret. This approach
 * does NOT embed secret tokens in the QR code; the signature is used purely for
 * integrity verification.
 */
@Service
public class QrAssertionService {

    private static final Logger log = LoggerFactory.getLogger(QrAssertionService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final CardPrintProperties properties;
    private final ObjectMapper objectMapper;

    public QrAssertionService(CardPrintProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a signed QR assertion JSON string.
     *
     * @param subjectType  the type of subject (PRACTITIONER, CLIENT, etc.)
     * @param subjectId    the unique identifier of the subject
     * @param credentialId the credential/card identifier (e.g., job ID or card number)
     * @return JSON string containing the assertion payload and HMAC signature
     */
    public String generateSignedAssertion(String subjectType, String subjectId, String credentialId) {
        Map<String, Object> assertionPayload = new LinkedHashMap<>();
        assertionPayload.put("subjectType", subjectType);
        assertionPayload.put("subjectId", subjectId);
        assertionPayload.put("credentialId", credentialId);
        assertionPayload.put("issuedAt", OffsetDateTime.now().toString());
        assertionPayload.put("issuer", "impilo-card-print-agent");

        try {
            String payloadJson = objectMapper.writeValueAsString(assertionPayload);
            String signature = computeHmac(payloadJson);

            Map<String, Object> signedAssertion = new LinkedHashMap<>();
            signedAssertion.put("payload", assertionPayload);
            signedAssertion.put("signature", signature);
            signedAssertion.put("algorithm", HMAC_ALGORITHM);

            return objectMapper.writeValueAsString(signedAssertion);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize QR assertion for subject {}/{}", subjectType, subjectId, e);
            throw new RuntimeException("Failed to generate QR assertion", e);
        }
    }

    /**
     * Computes HMAC-SHA256 over the given data using the configured secret.
     */
    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    properties.getQr().getHmacSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC for QR assertion", e);
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
}
