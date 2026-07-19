package zw.gov.mohcc.impilo.vito.core.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the printed-card assertion is genuinely signature-checked: a real
 * Ed25519 signature over the payload (as card-print produces it) verifies; a
 * tampered payload does not.
 */
class CardAssertionVerifierTest {

    private static final String DEV_SEED = "card-qr-signing-dev-seed-change-me-32b";
    private final ObjectMapper mapper = new ObjectMapper();
    // Default (empty) configs → verifier derives the public key from the dev seed.
    private final CardAssertionVerifier verifier = new CardAssertionVerifier(mapper, "", "");

    private String signedAssertion(String subjectType, String subjectId, String credentialId) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjectType", subjectType);
        payload.put("subjectId", subjectId);
        payload.put("credentialId", credentialId);
        payload.put("issuedAt", "2026-07-19T00:00:00Z");
        payload.put("issuer", "impilo-card-print-agent");
        String payloadJson = mapper.writeValueAsString(payload);

        byte[] priv = MessageDigest.getInstance("SHA-256").digest(DEV_SEED.getBytes(StandardCharsets.UTF_8));
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, new Ed25519PrivateKeyParameters(priv, 0));
        byte[] data = payloadJson.getBytes(StandardCharsets.UTF_8);
        signer.update(data, 0, data.length);
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature());

        Map<String, Object> signed = new LinkedHashMap<>();
        signed.put("payload", payload);
        signed.put("signature", sig);
        signed.put("algorithm", "Ed25519");
        signed.put("kid", "card-qr-devtest");
        return mapper.writeValueAsString(signed);
    }

    @Test
    @DisplayName("valid signature → subject extracted")
    void validSignatureVerified() throws Exception {
        String hid = "b0000000-0000-4000-8000-000000000001";
        String json = signedAssertion("CLIENT", hid, "job-123");

        Optional<CardAssertionVerifier.VerifiedAssertion> out = verifier.verify(json);

        assertTrue(out.isPresent());
        assertEquals("CLIENT", out.get().subjectType());
        assertEquals(hid, out.get().subjectId());
        assertEquals("job-123", out.get().credentialId());
    }

    @Test
    @DisplayName("tampered payload → rejected")
    void tamperedRejected() throws Exception {
        String json = signedAssertion("CLIENT", "b0000000-0000-4000-8000-000000000001", "job-123");
        // Flip the subjectId after signing.
        String tampered = json.replace("000000000001", "000000000099");

        assertTrue(verifier.verify(tampered).isEmpty());
    }

    @Test
    @DisplayName("garbage / blank → rejected, never throws")
    void garbageRejected() {
        assertTrue(verifier.verify(null).isEmpty());
        assertTrue(verifier.verify("").isEmpty());
        assertTrue(verifier.verify("not-json").isEmpty());
        assertTrue(verifier.verify("{\"payload\":{}}").isEmpty());
    }
}
