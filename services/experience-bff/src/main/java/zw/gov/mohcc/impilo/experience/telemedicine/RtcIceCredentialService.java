package zw.gov.mohcc.impilo.experience.telemedicine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RtcIceCredentialService {

    private final List<String> stunUrls;
    private final List<String> turnUrls;
    private final String turnSecret;
    private final long credentialTtlSeconds;

    public RtcIceCredentialService(
            @Value("${impilo.telemedicine.stun-urls:stun:stun.l.google.com:19302}") String stunUrls,
            @Value("${impilo.telemedicine.turn-urls:turn:localhost:3478}") String turnUrls,
            @Value("${impilo.telemedicine.turn-secret:impilo-turn-secret}") String turnSecret,
            @Value("${impilo.telemedicine.turn-credential-ttl-seconds:86400}") long credentialTtlSeconds) {
        this.stunUrls = splitUrls(stunUrls);
        this.turnUrls = splitUrls(turnUrls);
        this.turnSecret = turnSecret;
        this.credentialTtlSeconds = credentialTtlSeconds;
    }

    public Map<String, Object> buildIceServers(String actorId) {
        List<Map<String, Object>> iceServers = new ArrayList<>();

        if (!stunUrls.isEmpty()) {
            iceServers.add(Map.of("urls", stunUrls));
        }

        if (!turnUrls.isEmpty() && turnSecret != null && !turnSecret.isBlank()) {
            long expiry = Instant.now().getEpochSecond() + credentialTtlSeconds;
            String username = expiry + ":" + (actorId == null || actorId.isBlank() ? "impilo" : actorId);
            String credential = hmacSha1Base64(turnSecret, username);
            Map<String, Object> turn = new LinkedHashMap<>();
            turn.put("urls", turnUrls);
            turn.put("username", username);
            turn.put("credential", credential);
            iceServers.add(turn);
        }

        return Map.of("iceServers", iceServers);
    }

    private static List<String> splitUrls(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split(","));
    }

    private static String hmacSha1Base64(String secret, String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(username.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate TURN credential", e);
        }
    }
}
