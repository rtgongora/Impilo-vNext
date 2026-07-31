package zw.gov.mohcc.impilo.cardprint.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.security.secrets.RequiredSecret;

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
 * dual-verify window was needed. The seed is a required deployment secret
 * (tshepo-keys custody target) whenever signing is local: the earlier dev-seed fallback was
 * published in this repository, so any reader could sign a card assertion that VITO would accept.
 * In {@code tshepo-keys} mode the private key never enters the agent, so no local seed is
 * needed and none is demanded.</p>
 */
@Service
public class QrAssertionService {

    private static final Logger log = LoggerFactory.getLogger(QrAssertionService.class);
    private static final String TSHEPO_KEYS_SOURCE = "tshepo-keys";
    private static final String ALGORITHM = "Ed25519";

    private final ObjectMapper objectMapper;
    /** Null only when signing is delegated to tshepo-keys, where no local key exists. */
    private final String seedMaterial;

    /**
     * W4b signing source. {@code seed} (default) = local seed-derived Ed25519 key (unchanged
     * legacy path, fallback for one release); {@code tshepo-keys} = delegate signing to the
     * sovereign tshepo-keys-service.
     */
    private final String signingSource;

    private Ed25519PrivateKeyParameters privateKey;
    private String keyId;
    private String publicKeyBase64;

    /** Present only when {@code card-print.qr.signing-source=tshepo-keys} (conditional bean). */
    @Autowired(required = false)
    private TshepoKeysSigningClient keysClient;

    public QrAssertionService(ObjectMapper objectMapper,
                              @Value("${card-print.qr.signing-source:seed}") String signingSource,
                              @Value("${card-print.qr.signing-key-seed:}") String configuredSeed) {
        this.objectMapper = objectMapper;
        this.signingSource = signingSource;
        boolean delegated = TSHEPO_KEYS_SOURCE.equalsIgnoreCase(signingSource);
        if (delegated && !RequiredSecret.isUsable(configuredSeed)) {
            this.seedMaterial = null;
        } else {
            this.seedMaterial = RequiredSecret.require(
                    "card-print.qr.signing-key-seed", configuredSeed,
                    "Smart-card credentials are signed with it, so a known seed lets anyone mint a card "
                            + "assertion that verifies as genuine.");
        }
    }

    @PostConstruct
    public void init() throws Exception {
        if (seedMaterial == null) {
            if (keysClient == null) {
                throw new IllegalStateException(
                        "Refusing to start: card-print.qr.signing-source=" + TSHEPO_KEYS_SOURCE
                                + " but the tshepo-keys signing client is not wired, and no local "
                                + "card-print.qr.signing-key-seed is set. Card signing would have no key at all.");
            }
            log.info("Card QR signing delegated to tshepo-keys — no local Ed25519 key derived");
            return;
        }
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

            // W4b: when repointed to tshepo-keys, the private key never enters the agent — the
            // canonical payload is signed by the sovereign key service (purpose CARD_ASSERTION)
            // and the returned kid identifies the JWKS-published public key for verifiers.
            String signature;
            String kidToUse;
            if (useTshepoKeys()) {
                TshepoKeysSigningClient.SignResult result = keysClient.sign(null, payloadJson);
                signature = result.signature();
                kidToUse = result.keyId();
            } else {
                signature = sign(payloadJson);
                kidToUse = keyId;
            }

            Map<String, Object> signedAssertion = new LinkedHashMap<>();
            signedAssertion.put("payload", assertionPayload);
            signedAssertion.put("signature", signature);
            signedAssertion.put("algorithm", ALGORITHM);
            signedAssertion.put("kid", kidToUse);

            return objectMapper.writeValueAsString(signedAssertion);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize QR assertion for subject {}/{}", subjectType, subjectId, e);
            throw new RuntimeException("Failed to generate QR assertion", e);
        }
    }

    /** True when signing is delegated to tshepo-keys and its client is wired. */
    private boolean useTshepoKeys() {
        return TSHEPO_KEYS_SOURCE.equalsIgnoreCase(signingSource) && keysClient != null;
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
