package zw.gov.mohcc.impilo.coverage.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.security.secrets.RequiredSecret;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signed eligibility token (spec §12.3). A compact HS256 JWS (JWT) minted with a shared
 * secret, so a facility can carry an eligibility decision offline and it can be validated
 * later at claim time. Implemented with the JDK's HMAC (no external crypto dependency);
 * the wire format is a standard {@code header.payload.signature} JWT.
 *
 * <p>{@code ruvimbo.token.secret} is required. It previously carried an inline default with no
 * warning at all, so a token minted anywhere in the estate could be forged by anyone reading this
 * file — and since a token is what lets a facility claim against a payer offline, that is a
 * financial control, not a nicety.</p>
 *
 * <p>A symmetric HMAC is defensible only while issue and verify both happen inside this service.
 * If a payer is ever to verify a token independently, this must become an asymmetric signature via
 * tshepo-keys, since handing the verifier the secret hands it the ability to mint. Recorded in
 * {@code docs/runbooks/key-ceremony.md}.</p>
 */
@Service
public class EligibilityTokenService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] secret;

    public EligibilityTokenService(@Value("${ruvimbo.token.secret:}") String secret) {
        this.secret = RequiredSecret.require(
                "ruvimbo.token.secret", secret,
                "Offline eligibility tokens are signed with it, so a known secret lets anyone mint a "
                        + "coverage decision that a payer will honour.")
                .getBytes(StandardCharsets.UTF_8);
    }

    public record IssuedToken(String token, Instant issuedAt, Instant expiresAt) {}

    public record VerifiedToken(boolean valid, String reason, Map<String, Object> claims) {}

    /**
     * Issue a token for an eligible decision.
     *
     * @param ttlSeconds token lifetime (spec §12.2 response validity period)
     */
    public IssuedToken issue(String decisionId, String coverageId, String memberCpid, String facilityId,
                             String scope, String payer, long ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "coverage-service");
        claims.put("sub", memberCpid);
        claims.put("jti", decisionId);
        claims.put("coverage_id", coverageId);
        claims.put("facility_id", facilityId);
        claims.put("scope", scope);
        claims.put("payer", payer);
        claims.put("iat", now.getEpochSecond());
        claims.put("nbf", now.getEpochSecond());
        claims.put("exp", exp.getEpochSecond());

        try {
            String header = B64.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String payload = B64.encodeToString(MAPPER.writeValueAsBytes(claims));
            String signingInput = header + "." + payload;
            String sig = B64.encodeToString(hmac(signingInput));
            return new IssuedToken(signingInput + "." + sig, now, exp);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue eligibility token", e);
        }
    }

    /** Verify a token's signature and expiry. Returns the decoded claims when valid. */
    @SuppressWarnings("unchecked")
    public VerifiedToken verify(String token) {
        if (token == null || token.isBlank()) {
            return new VerifiedToken(false, "MISSING_TOKEN", Map.of());
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            return new VerifiedToken(false, "MALFORMED_TOKEN", Map.of());
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            String expected = B64.encodeToString(hmac(signingInput));
            if (!constantTimeEquals(expected, parts[2])) {
                return new VerifiedToken(false, "BAD_SIGNATURE", Map.of());
            }
            Map<String, Object> claims = MAPPER.readValue(B64D.decode(parts[1]), Map.class);
            Object exp = claims.get("exp");
            if (exp instanceof Number n && Instant.now().getEpochSecond() > n.longValue()) {
                return new VerifiedToken(false, "EXPIRED", claims);
            }
            return new VerifiedToken(true, "VALID", claims);
        } catch (Exception e) {
            return new VerifiedToken(false, "VERIFY_ERROR", Map.of());
        }
    }

    private byte[] hmac(String signingInput) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int r = 0;
        for (int i = 0; i < x.length; i++) r |= x[i] ^ y[i];
        return r == 0;
    }
}
