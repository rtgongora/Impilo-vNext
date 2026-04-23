package zw.gov.mohcc.impilo.experience.shell;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityHeaderParser;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ShellWorkspaceStateService {

    private static final Logger log = LoggerFactory.getLogger(ShellWorkspaceStateService.class);
    private static final String KEY_PREFIX = "impilo:experience:shell-workspace:v1:";
    private static final int MAX_PINS = 32;
    private static final int MAX_RECENTS = 40;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public ShellWorkspaceStateService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${impilo.shell-workspace.redis-ttl-days:120}") int ttlDays) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofDays(Math.max(1, ttlDays));
    }

    public Map<String, Object> load(String tenantId, String actorId, HttpServletRequest request) {
        VisibilityProfile profile = VisibilityHeaderParser.resolve(request, objectMapper);
        String raw = redis.opsForValue().get(key(tenantId, actorId));
        if (raw == null || raw.isBlank()) {
            return emptyEnvelope();
        }
        try {
            Map<String, Object> doc = objectMapper.readValue(raw, new TypeReference<>() {
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recents = (List<Map<String, Object>>) doc.getOrDefault("recentItems", List.of());
            List<String> pins = readPins(doc.get("pinnedAppCodes"));
            List<Map<String, Object>> filteredRecents = ShellRecentItemVisibilityFilter.filter(recents, profile);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("pinnedAppCodes", pins);
            out.put("recentItems", filteredRecents);
            return out;
        } catch (Exception e) {
            log.warn("shell-workspace: corrupt payload tenant={} actor={}: {}", tenantId, actorId, e.getMessage());
            return emptyEnvelope();
        }
    }

    public void save(String tenantId, String actorId, Map<String, Object> body, HttpServletRequest request) {
        VisibilityProfile profile = VisibilityHeaderParser.resolve(request, objectMapper);
        List<String> pins = sanitizePins(body.get("pinnedAppCodes"));
        List<Map<String, Object>> recentsRaw = new ArrayList<>();
        Object rawList = body.get("recentItems");
        if (rawList instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> {
                        if (k != null) {
                            row.put(String.valueOf(k), v);
                        }
                    });
                    recentsRaw.add(row);
                }
            }
        }
        List<Map<String, Object>> recents = sanitizeRecents(recentsRaw);
        recents = ShellRecentItemVisibilityFilter.filter(recents, profile);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("pinnedAppCodes", pins);
        doc.put("recentItems", recents);
        try {
            redis.opsForValue().set(key(tenantId, actorId), objectMapper.writeValueAsString(doc), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist shell workspace", e);
        }
    }

    public void delete(String tenantId, String actorId) {
        redis.delete(key(tenantId, actorId));
    }

    private static Map<String, Object> emptyEnvelope() {
        return Map.of(
                "pinnedAppCodes", List.of(),
                "recentItems", List.of());
    }

    private static String key(String tenantId, String actorId) {
        return KEY_PREFIX + tenantId + ":" + actorId.toLowerCase(Locale.ROOT);
    }

    private static List<String> readPins(Object raw) {
        if (!(raw instanceof List<?> l)) {
            return List.of();
        }
        List<String> pins = new ArrayList<>();
        for (Object o : l) {
            if (o == null) {
                continue;
            }
            String code = String.valueOf(o).trim();
            if (!code.isEmpty() && code.length() <= 64 && SAFE_TOKEN.matcher(code).matches()) {
                pins.add(code);
            }
            if (pins.size() >= MAX_PINS) {
                break;
            }
        }
        return pins;
    }

    private static final java.util.regex.Pattern SAFE_TOKEN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private static List<String> sanitizePins(Object raw) {
        return readPins(raw);
    }

    private static List<Map<String, Object>> sanitizeRecents(List<Map<String, Object>> in) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (in == null) {
            return out;
        }
        for (Map<String, Object> row : in) {
            if (row == null) {
                continue;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            putString(r, "id", row.get("id"), 80);
            putString(r, "kind", row.get("kind"), 32);
            putString(r, "title", row.get("title"), 240);
            putString(r, "subtitle", row.get("subtitle"), 240);
            putString(r, "href", row.get("href"), 512);
            putString(r, "refKey", row.get("refKey"), 200);
            putString(r, "recordedAt", row.get("recordedAt"), 40);
            putString(r, "sensitivity", row.get("sensitivity"), 32);
            if (r.containsKey("href") && r.containsKey("title") && r.containsKey("refKey")) {
                out.add(r);
            }
            if (out.size() >= MAX_RECENTS) {
                break;
            }
        }
        return out;
    }

    private static void putString(Map<String, Object> target, String key, Object val, int maxLen) {
        if (val == null) {
            return;
        }
        String s = String.valueOf(val).trim();
        if (s.isEmpty()) {
            return;
        }
        if (s.length() > maxLen) {
            s = s.substring(0, maxLen);
        }
        target.put(key, s);
    }
}
