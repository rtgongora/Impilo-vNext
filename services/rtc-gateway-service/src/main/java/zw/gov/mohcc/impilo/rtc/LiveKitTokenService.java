package zw.gov.mohcc.impilo.rtc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipant;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveKitTokenService {
    private final RtcGatewayProperties properties;

    public LiveKitTokenService(RtcGatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Legacy hard-coded grant path (OBSERVER cannot publish, everyone else can).
     * Kept for callers without a session template; template-resolved callers use
     * {@link #issueParticipantToken(String, RtcParticipant, SessionTemplate.TokenGrantProfile, long)}.
     */
    public TokenResult issueParticipantToken(String roomName, RtcParticipant participant) {
        SessionTemplate.TokenGrantProfile legacy = new SessionTemplate.TokenGrantProfile(
                canPublish(participant.role()), true, true, null, null, null);
        return issueParticipantToken(roomName, participant, legacy, properties.getGateway().getTokenTtlSeconds());
    }

    /** Template-driven token: grants come from the session mode's TokenGrantProfile. */
    public TokenResult issueParticipantToken(String roomName, RtcParticipant participant,
                                             SessionTemplate.TokenGrantProfile grant, long ttlSeconds) {
        return issueParticipantToken(roomName, participant, grant, ttlSeconds, null);
    }

    /**
     * Template-driven token with a media profile. AUDIO_ONLY narrows the publish
     * grant to the microphone source via the LiveKit {@code canPublishSources}
     * video-grant claim; FULL (or null) leaves all sources allowed.
     */
    public TokenResult issueParticipantToken(String roomName, RtcParticipant participant,
                                             SessionTemplate.TokenGrantProfile grant, long ttlSeconds,
                                             String mediaProfile) {
        if (grant == null) {
            throw new IllegalArgumentException(
                    "Role " + participant.role() + " has no token grant profile for this session");
        }
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : properties.getGateway().getTokenTtlSeconds();
        Instant expiresAt = Instant.now().plusSeconds(Math.max(60, effectiveTtl));
        if (properties.getGateway().isDevModeEnabled() && !properties.getLivekit().isEnabled()) {
            String token = "dev-rtc-token-" + UUID.randomUUID().toString().replace("-", "");
            return new TokenResult(token, expiresAt);
        }
        assertLiveKitConfigured();
        Map<String, Object> videoGrant = new LinkedHashMap<>();
        videoGrant.put("room", roomName);
        videoGrant.put("roomJoin", true);
        videoGrant.put("canPublish", grant.canPublish());
        videoGrant.put("canSubscribe", grant.canSubscribe());
        videoGrant.put("canPublishData", grant.canPublishData());
        if (grant.canPublish() && "AUDIO_ONLY".equals(mediaProfile)) {
            videoGrant.put("canPublishSources", List.of("microphone"));
        }
        if (grant.isRoomAdmin()) {
            videoGrant.put("roomAdmin", true);
        }
        if (grant.isHidden()) {
            videoGrant.put("hidden", true);
        }

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

    /**
     * Admin token for the Egress twirp API: starting/stopping a room egress
     * requires the {@code roomRecord} grant (roomAdmin alone is not enough).
     */
    public String issueRoomRecordToken(String roomName) {
        if (properties.getGateway().isDevModeEnabled() && !properties.getLivekit().isEnabled()) {
            return "dev-rtc-record-token";
        }
        assertLiveKitConfigured();
        Map<String, Object> videoGrant = new LinkedHashMap<>();
        if (roomName != null && !roomName.isBlank()) {
            videoGrant.put("room", roomName);
        }
        videoGrant.put("roomAdmin", true);
        videoGrant.put("roomRecord", true);
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
