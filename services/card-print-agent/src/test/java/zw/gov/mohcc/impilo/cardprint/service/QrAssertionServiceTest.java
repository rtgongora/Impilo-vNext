package zw.gov.mohcc.impilo.cardprint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X4: card QR assertions are Ed25519-signed with a versioned kid — verifiable
 * from the public key alone, no shared secret.
 */
class QrAssertionServiceTest {

    private static final String SEED = "a-stable-card-qr-seed-of-32-bytes-min";

    private final ObjectMapper mapper = new ObjectMapper();
    private QrAssertionService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new QrAssertionService(mapper, SEED);
        service.init();
    }

    @Test
    @DisplayName("assertion carries Ed25519 algorithm + versioned kid and verifies against the public key")
    void assertionVerifiesWithPublicKeyOnly() throws Exception {
        String json = service.generateSignedAssertion("CLIENT", "subject-1", "card-42");
        JsonNode node = mapper.readTree(json);

        assertEquals("Ed25519", node.get("algorithm").asText());
        assertTrue(node.get("kid").asText().startsWith("card-qr-"), "kid must be versioned");
        assertEquals(service.getKeyId(), node.get("kid").asText());

        byte[] payloadBytes = mapper.writeValueAsBytes(node.get("payload"));
        byte[] signature = Base64.getUrlDecoder().decode(node.get("signature").asText());
        byte[] publicKey = Base64.getUrlDecoder().decode(service.getPublicKeyBase64());

        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
        verifier.update(payloadBytes, 0, payloadBytes.length);
        assertTrue(verifier.verifySignature(signature), "signature must verify with the public key alone");
    }

    @Test
    @DisplayName("kid is stable across restarts for the same seed and changes with the seed")
    void kidVersionedBySeed() throws Exception {
        QrAssertionService restarted = new QrAssertionService(mapper, SEED);
        restarted.init();
        assertEquals(service.getKeyId(), restarted.getKeyId());

        QrAssertionService rotated = new QrAssertionService(mapper, "a-DIFFERENT-card-qr-seed-of-32-bytes+");
        rotated.init();
        assertNotEquals(service.getKeyId(), rotated.getKeyId());
    }

    @Test
    @DisplayName("tampered payload fails verification")
    void tamperFails() throws Exception {
        String json = service.generateSignedAssertion("CLIENT", "subject-1", "card-42");
        JsonNode node = mapper.readTree(json);

        var tampered = (com.fasterxml.jackson.databind.node.ObjectNode) node.get("payload");
        tampered.put("subjectId", "someone-else");
        byte[] payloadBytes = mapper.writeValueAsBytes(tampered);
        byte[] signature = Base64.getUrlDecoder().decode(node.get("signature").asText());
        byte[] publicKey = Base64.getUrlDecoder().decode(service.getPublicKeyBase64());

        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
        verifier.update(payloadBytes, 0, payloadBytes.length);
        assertFalse(verifier.verifySignature(signature));
    }
}
