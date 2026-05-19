package zw.gov.mohcc.impilo.experience.publichealth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Service
public class PublicHealthPreferenceService {

    private static final String KEY_PREFIX = "impilo:experience:public-health:prefs:v1:";
    private static final Duration TTL = Duration.ofDays(180);

    private final StringRedisTemplate redisTemplate;

    public PublicHealthPreferenceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String loadJurisdictionPack(String tenantId, String actorId) {
        return redisTemplate.opsForValue().get(key(tenantId, actorId));
    }

    public Map<String, Object> saveJurisdictionPack(String tenantId, String actorId, String jurisdictionPack) {
        String value = sanitizePack(jurisdictionPack);
        redisTemplate.opsForValue().set(key(tenantId, actorId), value, TTL);
        return Map.of("jurisdiction_pack", value);
    }

    private String sanitizePack(String raw) {
        if (raw == null || raw.isBlank()) {
            return "national";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "city_health", "rdc_health", "provincial", "national", "port_health", "school_health" -> v;
            default -> "national";
        };
    }

    private String key(String tenantId, String actorId) {
        return KEY_PREFIX + tenantId + ":" + actorId.toLowerCase(Locale.ROOT);
    }
}
