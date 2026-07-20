package zw.gov.mohcc.impilo.cardprint.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W4b: proves the {@code card-print.qr.signing-source=tshepo-keys} path end-to-end against a
 * stub tshepo-keys exposing {@code POST /v1/sign} and {@code GET /v1/keys/jwks}.
 *
 * <p>The stub performs <b>real Ed25519 signing</b> over the exact payload the agent submits, and
 * the test verifies the resulting assertion signature against the public key published in the
 * stub's JWKS (resolved by the returned kid) — a genuine cryptographic round-trip through the
 * client, not a canned signature. (A JDK {@link HttpServer} is used as the HTTP stub rather than
 * WireMock so the dynamic-signing behaviour needs no custom response transformer.)</p>
 */
class QrAssertionServiceTshepoKeysTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    private Ed25519PrivateKeyParameters stubPrivate;
    private Ed25519PublicKeyParameters stubPublic;
    private String stubKid;
    private final AtomicReference<String> signedPayload = new AtomicReference<>();

    @BeforeEach
    void startStub() throws Exception {
        Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair kp = gen.generateKeyPair();
        stubPrivate = (Ed25519PrivateKeyParameters) kp.getPrivate();
        stubPublic = (Ed25519PublicKeyParameters) kp.getPublic();
        stubKid = "kid-stub-abc123";

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/v1/sign", exchange -> {
            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> req = MAPPER.readValue(body, Map.class);
            String payload = (String) req.get("payload");
            signedPayload.set(payload);
            // Real Ed25519 signature over the submitted payload bytes.
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(true, stubPrivate);
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            signer.update(data, 0, data.length);
            String sig = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signer.generateSignature());
            String resp = MAPPER.writeValueAsString(Map.of(
                    "keyId", stubKid, "algorithm", "Ed25519", "signature", sig));
            respond(exchange, 200, resp);
        });

        server.createContext("/v1/keys/jwks", exchange -> {
            String x = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(stubPublic.getEncoded());
            String jwks = MAPPER.writeValueAsString(Map.of("keys", java.util.List.of(
                    Map.of("kty", "OKP", "crv", "Ed25519", "use", "sig", "kid", stubKid, "x", x))));
            respond(exchange, 200, jwks);
        });

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void assertionSignedViaTshepoKeys_verifiesAgainstJwksPublishedKey() throws Exception {
        QrAssertionService service = new QrAssertionService(MAPPER, "");
        service.init(); // seed fields (unused on this path) — mirrors bean lifecycle
        ReflectionTestUtils.setField(service, "signingSource", "tshepo-keys");
        ReflectionTestUtils.setField(service, "keysClient",
                new TshepoKeysSigningClient(baseUrl, "11111111-1111-1111-1111-111111111111"));

        String envelopeJson = service.generateSignedAssertion(
                "PRACTITIONER", "subject-99", "card-777");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = MAPPER.readValue(envelopeJson, Map.class);

        // The envelope carries the tshepo-keys kid, not the local seed kid.
        assertThat(envelope.get("kid")).isEqualTo(stubKid);
        assertThat(envelope.get("algorithm")).isEqualTo("Ed25519");
        assertThat((String) envelope.get("signature")).isNotBlank();

        // Resolve the public key from the stub JWKS (by kid) and verify the assertion signature
        // over the exact payload the stub signed — a real cryptographic round-trip.
        byte[] sigBytes = java.util.Base64.getUrlDecoder().decode((String) envelope.get("signature"));
        byte[] payloadBytes = signedPayload.get().getBytes(StandardCharsets.UTF_8);
        Ed25519PublicKeyParameters resolved = resolveFromJwks(stubKid);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, resolved);
        verifier.update(payloadBytes, 0, payloadBytes.length);
        assertThat(verifier.verifySignature(sigBytes))
                .as("assertion signed via tshepo-keys must verify against the JWKS public key")
                .isTrue();

        // And the payload the agent submitted is exactly the canonical assertion payload.
        assertThat(signedPayload.get()).contains("\"subjectId\":\"subject-99\"");
        assertThat(signedPayload.get()).contains("\"credentialId\":\"card-777\"");
    }

    /** Fetch the JWKS from the stub and rebuild the Ed25519 public key for the given kid. */
    private Ed25519PublicKeyParameters resolveFromJwks(String kid) throws Exception {
        var uri = java.net.URI.create(baseUrl + "/v1/keys/jwks");
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var resp = client.send(java.net.http.HttpRequest.newBuilder(uri).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            Map<String, Object> jwks = MAPPER.readValue(resp.body(), Map.class);
            @SuppressWarnings("unchecked")
            var keys = (java.util.List<Map<String, Object>>) jwks.get("keys");
            Map<String, Object> match = keys.stream()
                    .filter(k -> kid.equals(k.get("kid"))).findFirst().orElseThrow();
            byte[] pub = java.util.Base64.getUrlDecoder().decode((String) match.get("x"));
            return new Ed25519PublicKeyParameters(pub, 0);
        }
    }
}
