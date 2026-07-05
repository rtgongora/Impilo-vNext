package zw.gov.mohcc.impilo.khuluma.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * HMAC-signed meeting invite tokens for AUTHENTICATED users. A token is
 * {@code base64url(payload) + "." + base64url(hmacSha256(payload, secret))} with claims
 * {@code {v, t: tenantId, c: conversationId, r: role, x: expiryEpochSeconds, m: mintedBy}}.
 * Resolving a valid token joins the caller (their own authenticated identity — the token
 * carries no identity) to the meeting conversation with the embedded role.
 *
 * <p>The secret is khuluma-side ({@code impilo.khuluma.meeting-invite-secret}). When not
 * configured, a random per-boot secret is generated and a warning logged: links then only
 * survive this instance's lifetime and do not verify across replicas — fine for dev/preview
 * single-replica, WRONG for production (set the property/env there).
 *
 * <p>Unauthenticated GUEST identities are intentionally NOT supported: the tshepo guest
 * assurance tier is undefined (see the Wave-5 seam note in the meeting docs).
 */
@Service
public class MeetingInviteService {

    private static final Logger log = LoggerFactory.getLogger(MeetingInviteService.class);
    private static final long DEFAULT_TTL_SECONDS = 24 * 3600;
    private static final long MAX_TTL_SECONDS = 7 * 24 * 3600;

    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public MeetingInviteService(ObjectMapper objectMapper,
                                @Value("${impilo.khuluma.meeting-invite-secret:}") String configuredSecret) {
        this.objectMapper = objectMapper;
        if (configuredSecret == null || configuredSecret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("impilo.khuluma.meeting-invite-secret is not configured — using a random per-boot "
                    + "secret. Meeting invite links will not survive restarts or verify across replicas.");
        } else {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** A verified invite's claims. */
    public record InviteClaims(UUID tenantId, UUID conversationId, String role, Instant expiresAt, String mintedBy) {}

    /** A freshly minted invite. */
    public record MintedInvite(String token, Instant expiresAt, String role) {}

    /** Mint an invite for a meeting conversation. {@code role} is the participant role granted on resolve. */
    public MintedInvite mint(UUID tenantId, UUID conversationId, String role, Long ttlSeconds, String mintedBy) {
        long ttl = ttlSeconds == null || ttlSeconds <= 0 ? DEFAULT_TTL_SECONDS : Math.min(ttlSeconds, MAX_TTL_SECONDS);
        Instant expiresAt = Instant.now().plusSeconds(ttl);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("v", 1);
        payload.put("t", tenantId.toString());
        payload.put("c", conversationId.toString());
        payload.put("r", normalizeRole(role));
        payload.put("x", expiresAt.getEpochSecond());
        payload.put("m", mintedBy);
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        String token = b64(body) + "." + b64(hmac(body));
        return new MintedInvite(token, expiresAt, normalizeRole(role));
    }

    /**
     * Verify a token: signature, expiry, and tenant binding.
     *
     * @throws IllegalArgumentException on malformed/forged/expired/cross-tenant tokens
     */
    public InviteClaims verify(UUID tenantId, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invite token is required");
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed invite token");
        }
        byte[] body;
        byte[] givenSig;
        try {
            body = Base64.getUrlDecoder().decode(parts[0]);
            givenSig = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed invite token");
        }
        if (!MessageDigest.isEqual(hmac(body), givenSig)) {
            throw new IllegalArgumentException("Invite token signature is invalid");
        }
        JsonNode claims;
        try {
            claims = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed invite token payload");
        }
        Instant expiresAt = Instant.ofEpochSecond(claims.path("x").asLong(0));
        if (Instant.now().isAfter(expiresAt)) {
            throw new IllegalArgumentException("Invite token has expired");
        }
        UUID tokenTenant = UUID.fromString(claims.path("t").asText());
        if (!tokenTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Invite token does not belong to this tenant");
        }
        return new InviteClaims(tokenTenant,
                UUID.fromString(claims.path("c").asText()),
                normalizeRole(claims.path("r").asText(null)),
                expiresAt,
                claims.path("m").asText(null));
    }

    /** Invite links grant MEMBER (→ PARTICIPANT media role) unless a host minted a COHOST/OBSERVER link. */
    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "MEMBER";
        String upper = role.trim().toUpperCase();
        return switch (upper) {
            case "COHOST", "OBSERVER", "MEMBER" -> upper;
            default -> "MEMBER";
        };
    }

    private byte[] hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
