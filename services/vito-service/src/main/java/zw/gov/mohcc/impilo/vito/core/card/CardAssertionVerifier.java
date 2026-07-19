package zw.gov.mohcc.impilo.vito.core.card;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * B0: server-side verifier for the <b>printed-card QR assertion</b> minted by
 * {@code card-print-agent}'s {@code QrAssertionService} — the Ed25519-signed
 * JSON physically encoded on a smart card. Until now nothing on the server
 * consumed a scanned printed card; the {@link CardResolveService} seam does,
 * and this class is how it trusts the presentation.
 *
 * <p>The assertion is {@code {"payload":{subjectType,subjectId,credentialId,...},
 * "signature":<b64url raw Ed25519>,"algorithm":"Ed25519","kid":...}}. We verify
 * the signature over the canonical payload JSON with card-print's public key.</p>
 *
 * <p>Key distribution: production injects card-print's <b>published public key</b>
 * ({@code card-print.qr.public-key}, base64url raw Ed25519). When unset we derive
 * it from the shared card-print seed ({@code card-print.qr.signing-key-seed},
 * default dev seed) — the same derivation card-print uses — so preview verifies
 * without a key-exchange step. The seed is NEVER used to sign here, only to
 * reconstruct the public key.</p>
 */
@Service
public class CardAssertionVerifier {

    private static final Logger log = LoggerFactory.getLogger(CardAssertionVerifier.class);
    private static final String DEV_SEED = "card-qr-signing-dev-seed-change-me-32b";

    private final ObjectMapper objectMapper;
    private final Ed25519PublicKeyParameters publicKey;

    public CardAssertionVerifier(ObjectMapper objectMapper,
                                 @Value("${card-print.qr.public-key:}") String configuredPublicKey,
                                 @Value("${card-print.qr.signing-key-seed:}") String configuredSeed) {
        this.objectMapper = objectMapper;
        Ed25519PublicKeyParameters pub;
        if (configuredPublicKey != null && !configuredPublicKey.isBlank()) {
            byte[] raw = Base64.getUrlDecoder().decode(configuredPublicKey.strip());
            pub = new Ed25519PublicKeyParameters(raw, 0);
            log.info("Card assertion verifier using configured card-print public key");
        } else {
            String seed = (configuredSeed != null && configuredSeed.strip().length() >= 32)
                    ? configuredSeed : DEV_SEED;
            if (seed.equals(DEV_SEED)) {
                log.warn("card-print.qr.public-key unset — deriving verify key from the DEV seed. "
                        + "Production MUST inject card-print's published public key.");
            }
            pub = deriveFromSeed(seed);
        }
        this.publicKey = pub;
    }

    private static Ed25519PublicKeyParameters deriveFromSeed(String seed) {
        try {
            byte[] priv = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return new Ed25519PrivateKeyParameters(priv, 0).generatePublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive card-print public key", e);
        }
    }

    /** Verified subject of a printed-card assertion. */
    public record VerifiedAssertion(String subjectType, String subjectId, String credentialId) {}

    /**
     * Verify the signature of a printed-card assertion JSON and extract its subject.
     * @return the verified subject, or empty if malformed or the signature is invalid.
     */
    public Optional<VerifiedAssertion> verify(String assertionJson) {
        if (assertionJson == null || assertionJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(assertionJson);
            JsonNode payload = root.get("payload");
            String signatureB64 = root.path("signature").asText(null);
            if (payload == null || signatureB64 == null) {
                return Optional.empty();
            }
            // Re-serialize the payload canonically (field-insertion order preserved,
            // matching card-print's ObjectMapper) and verify the raw Ed25519 signature.
            String payloadJson = objectMapper.writeValueAsString(payload);
            byte[] data = payloadJson.getBytes(StandardCharsets.UTF_8);
            byte[] sig = Base64.getUrlDecoder().decode(signatureB64);

            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, publicKey);
            verifier.update(data, 0, data.length);
            if (!verifier.verifySignature(sig)) {
                log.warn("Card assertion signature failed verification");
                return Optional.empty();
            }
            return Optional.of(new VerifiedAssertion(
                    payload.path("subjectType").asText(null),
                    payload.path("subjectId").asText(null),
                    payload.path("credentialId").asText(null)));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Card assertion verification error: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
