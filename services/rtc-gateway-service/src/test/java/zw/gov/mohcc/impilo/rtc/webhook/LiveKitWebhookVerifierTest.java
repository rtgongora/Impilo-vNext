package zw.gov.mohcc.impilo.rtc.webhook;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.rtc.RtcGatewayProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LiveKitWebhookVerifierTest {

    private static final String API_KEY = "test-api-key";
    private static final String API_SECRET = "test-livekit-secret-0123456789-0123456789";
    private static final String OTHER_SECRET = "wrong-livekit-secret-9876543210-987654321";
    private static final String BODY = "{\"event\":\"room_started\",\"id\":\"EV_1\",\"room\":{\"name\":\"impilo-telemedicine-s1\"}}";

    private final LiveKitWebhookVerifier verifier = new LiveKitWebhookVerifier(properties());

    @Test
    void acceptsValidSignatureAndBodyHash() throws Exception {
        String token = webhookToken(API_KEY, API_SECRET, sha256(BODY), 60);

        assertDoesNotThrow(() -> verifier.verify(token, BODY));
    }

    @Test
    void rejectsTokenSignedWithWrongSecret() throws Exception {
        String token = webhookToken(API_KEY, OTHER_SECRET, sha256(BODY), 60);

        assertThrows(LiveKitWebhookVerifier.WebhookVerificationException.class,
                () -> verifier.verify(token, BODY));
    }

    @Test
    void rejectsBodyHashMismatch() throws Exception {
        String token = webhookToken(API_KEY, API_SECRET, sha256("{\"event\":\"tampered\"}"), 60);

        assertThrows(LiveKitWebhookVerifier.WebhookVerificationException.class,
                () -> verifier.verify(token, BODY));
    }

    @Test
    void rejectsIssuerMismatch() throws Exception {
        String token = webhookToken("some-other-key", API_SECRET, sha256(BODY), 60);

        assertThrows(LiveKitWebhookVerifier.WebhookVerificationException.class,
                () -> verifier.verify(token, BODY));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = webhookToken(API_KEY, API_SECRET, sha256(BODY), -60);

        assertThrows(LiveKitWebhookVerifier.WebhookVerificationException.class,
                () -> verifier.verify(token, BODY));
    }

    @Test
    void rejectsMissingAuthorizationHeader() {
        assertThrows(LiveKitWebhookVerifier.WebhookVerificationException.class,
                () -> verifier.verify(null, BODY));
        assertThrows(LiveKitWebhookVerifier.WebhookVerificationException.class,
                () -> verifier.verify("  ", BODY));
    }

    @Test
    void rejectsGarbageToken() {
        assertThrows(LiveKitWebhookVerifier.WebhookVerificationException.class,
                () -> verifier.verify("not-a-jwt", BODY));
    }

    private static RtcGatewayProperties properties() {
        RtcGatewayProperties props = new RtcGatewayProperties();
        props.getLivekit().setApiKey(API_KEY);
        props.getLivekit().setApiSecret(API_SECRET);
        return props;
    }

    /** Mirrors LiveKit's webhook token: HS256, iss = api key, sha256 = base64 body digest. */
    private static String webhookToken(String issuer, String secret, String sha256, long ttlSeconds) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .expirationTime(new Date(System.currentTimeMillis() + ttlSeconds * 1000))
                .claim("sha256", sha256)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private static String sha256(String body) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
