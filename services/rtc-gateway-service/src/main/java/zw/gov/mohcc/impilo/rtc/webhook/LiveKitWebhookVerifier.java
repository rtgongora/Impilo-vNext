package zw.gov.mohcc.impilo.rtc.webhook;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rtc.RtcGatewayProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;

/**
 * Verifies LiveKit webhook authenticity. LiveKit posts events with
 * {@code Authorization: <JWT>} where the JWT is HS256-signed with the same
 * api key/secret the token service uses, {@code iss} is the api key, and the
 * {@code sha256} claim carries the base64-encoded SHA-256 digest of the raw
 * request body. Reject on any mismatch — fail closed.
 */
@Component
public class LiveKitWebhookVerifier {

    private final RtcGatewayProperties properties;

    public LiveKitWebhookVerifier(RtcGatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * @throws WebhookVerificationException when the signature, issuer, expiry, or body hash is invalid.
     */
    public void verify(String authorizationHeader, String rawBody) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new WebhookVerificationException("Missing Authorization header");
        }
        String apiKey = properties.getLivekit().getApiKey();
        String apiSecret = properties.getLivekit().getApiSecret();
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new WebhookVerificationException("LiveKit api key/secret not configured");
        }

        // Header is the bare JWT; tolerate a Bearer prefix defensively.
        String token = authorizationHeader.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }

        JWTClaimsSet claims;
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(apiSecret.getBytes(StandardCharsets.UTF_8)))) {
                throw new WebhookVerificationException("Webhook token signature invalid");
            }
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException | JOSEException e) {
            throw new WebhookVerificationException("Webhook token unparseable or unverifiable");
        }

        if (!apiKey.equals(claims.getIssuer())) {
            throw new WebhookVerificationException("Webhook token issuer mismatch");
        }
        Date expiration = claims.getExpirationTime();
        if (expiration != null && expiration.before(new Date())) {
            throw new WebhookVerificationException("Webhook token expired");
        }

        Object sha256Claim = claims.getClaim("sha256");
        if (sha256Claim == null || sha256Claim.toString().isBlank()) {
            throw new WebhookVerificationException("Webhook token missing sha256 body-hash claim");
        }
        String expected = base64Sha256(rawBody == null ? "" : rawBody);
        if (!constantTimeEquals(expected, sha256Claim.toString())) {
            throw new WebhookVerificationException("Webhook body hash mismatch");
        }
    }

    private static String base64Sha256(String body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Thrown when a webhook fails authenticity checks; mapped to 401 by the controller. */
    public static class WebhookVerificationException extends RuntimeException {
        public WebhookVerificationException(String message) {
            super(message);
        }
    }
}
