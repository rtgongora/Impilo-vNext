package zw.gov.mohcc.impilo.rtc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipant;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveKitTokenService {
    private final RtcGatewayProperties properties;

    public LiveKitTokenService(RtcGatewayProperties properties) {
        this.properties = properties;
    }

    public TokenResult issueParticipantToken(String roomName, RtcParticipant participant) {
        Instant expiresAt = Instant.now().plusSeconds(Math.max(60, properties.getGateway().getTokenTtlSeconds()));
        if (properties.getGateway().isDevModeEnabled() && !properties.getLivekit().isEnabled()) {
            String token = "dev-rtc-token-" + UUID.randomUUID().toString().replace("-", "");
            return new TokenResult(token, expiresAt);
        }
        assertLiveKitConfigured();
        Map<String, Object> videoGrant = new LinkedHashMap<>();
        videoGrant.put("room", roomName);
        videoGrant.put("roomJoin", true);
        videoGrant.put("canPublish", canPublish(participant.role()));
        videoGrant.put("canSubscribe", true);
        videoGrant.put("canPublishData", true);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getLivekit().getApiKey())
                .subject(participant.identity())
                .expirationTime(Date.from(expiresAt))
                .notBeforeTime(Date.from(Instant.now().minusSeconds(5)))
                .jwtID(UUID.randomUUID().toString())
                .claim("name", participant.displayName() == null ? participant.identity() : participant.displayName())
                .claim("video", videoGrant)
                .build();
        return new TokenResult(sign(claims), expiresAt);
    }

    public String issueRoomAdminToken(String roomName) {
        if (properties.getGateway().isDevModeEnabled() && !properties.getLivekit().isEnabled()) {
            return "dev-rtc-admin-token";
        }
        assertLiveKitConfigured();
        Map<String, Object> videoGrant = new LinkedHashMap<>();
        videoGrant.put("room", roomName);
        videoGrant.put("roomCreate", true);
        videoGrant.put("roomAdmin", true);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getLivekit().getApiKey())
                .subject("impilo-rtc-gateway")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .notBeforeTime(Date.from(Instant.now().minusSeconds(5)))
                .jwtID(UUID.randomUUID().toString())
                .claim("video", videoGrant)
                .build();
        return sign(claims);
    }

    public void assertLiveKitConfigured() {
        if (!properties.getLivekit().isEnabled()) {
            throw new IllegalStateException("LiveKit provider is disabled");
        }
        if (blank(properties.getLivekit().getUrl())
                || blank(properties.getLivekit().getApiKey())
                || blank(properties.getLivekit().getApiSecret())) {
            throw new IllegalStateException("LiveKit URL, API key, and API secret are required");
        }
    }

    private String sign(JWTClaimsSet claims) {
        try {
            byte[] secret = properties.getLivekit().getApiSecret().getBytes(StandardCharsets.UTF_8);
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to issue LiveKit access token", e);
        }
    }

    private boolean canPublish(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        return !"OBSERVER".equals(normalized);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record TokenResult(String accessToken, Instant expiresAt) {
    }
}
