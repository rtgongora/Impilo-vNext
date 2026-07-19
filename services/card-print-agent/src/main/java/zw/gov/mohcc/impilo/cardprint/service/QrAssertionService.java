package zw.gov.mohcc.impilo.cardprint.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates signed QR assertion payloads for smart cards.
 *
 * <p>X4 (key ceremony readiness): assertions are signed with a deterministic
 * seed-derived <b>Ed25519</b> key and carry a versioned {@code kid}
 * ({@code card-qr-<fingerprint8>}), replacing the earlier symmetric
 * HMAC-SHA256 scheme — a card credential must be verifiable without
 * distributing a shared secret, and the kid is what makes rotation-with-overlap
 * possible. Zero cards had been issued when the scheme switched, so no
 * dual-verify window was needed. The seed is a deployment secret
 * (tshepo-keys custody target); unset falls back to a dev seed with a loud
 * warning so preview boots.</p>
 */
@Service
public class QrAssertionService {

    private static final Logger log = LoggerFactory.getLogger(QrAssertionService.class);
    private static final String DEV_SEED = "card-qr-signing-dev-seed-change-me-32b";
    private static final String ALGORITHM = "Ed25519";

    private final ObjectMapper objectMapper;
    private final String seedMaterial;

    private Ed25519PrivateKeyParameters privateKey;
    private String keyId;
    private String publicKeyBase64;

    public QrAssertionService(ObjectMapper objectMapper,
                              @Value("${card-print.qr.signing-key-seed:}") String configuredSeed) {
        this.objectMapper = objectMapper;
        if (configuredSeed == null || configuredSeed.strip().length() < 32) {
            log.warn("card-print.qr.signing-key-seed is unset/weak — using the DEV seed. Production MUST "
                    + "supply a >=32-char secret (tshepo-keys custody); card QR signatures depend on it.");
            this.seedMaterial = DEV_SEED;
        } else {
            this.seedMaterial = configuredSeed;
        }
    }

    @PostConstruct
    public void init() throws Exception {
        byte[] priv = MessageDigest.getInstance("SHA-256")
                .digest(seedMaterial.getBytes(StandardCharsets.UTF_8));
        privateKey = new Ed25519PrivateKeyParameters(priv, 0);
        Ed25519PublicKeyParameters pub = privateKey.generatePublicKey();
        publicKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getEncoded());

        byte[] fingerprint = MessageDigest.getInstance("SHA-256").digest(pub.getEncoded());
        StringBuilder kid = new StringBuilder("card-qr-");
        for (int i = 0; i < 4; i++) {
            kid.append(String.format("%02x", fingerprint[i]));
        }
        keyId = kid.toString();
        log.info("Ed25519 card QR signing key initialized (kid: {}, stable across restarts)", keyId);
    }

    /**
     * Generates a signed QR assertion JSON string.
     *
     * @param subjectType  the type of subject (PRACTITIONER, CLIENT, etc.)
     * @param subjectId    the unique identifier of the subject
     * @param credentialId the credential/card identifier (e.g., job ID or card number)
     * @return JSON string containing the assertion payload, kid and Ed25519 signature
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
            String signature = sign(payloadJson);

            Map<String, Object> signedAssertion = new LinkedHashMap<>();
            signedAssertion.put("payload", assertionPayload);
            signedAssertion.put("signature", signature);
            signedAssertion.put("algorithm", ALGORITHM);
            signedAssertion.put("kid", keyId);

            return objectMapper.writeValueAsString(signedAssertion);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize QR assertion for subject {}/{}", subjectType, subjectId, e);
            throw new RuntimeException("Failed to generate QR assertion", e);
        }
    }

    /** Base64url raw Ed25519 signature over the canonical payload JSON. */
    private String sign(String payloadJson) {
        byte[] data = payloadJson.getBytes(StandardCharsets.UTF_8);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(data, 0, data.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature());
    }

    /** Current signing kid (versioned by key material). */
    public String getKeyId() {
        return keyId;
    }

    /** Base64url raw Ed25519 public key for external verifiers. */
    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }
}
