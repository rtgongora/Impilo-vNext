package zw.gov.mohcc.impilo.costa.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.costa.config.CostaProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves whether the person behind an encounter is a recognised health
 * worker, by querying BOTH sources directly (design choice: direct VARAPI +
 * Vashandi clients rather than a hop through the experience BFF — billing
 * infrastructure must not depend on the experience plane).
 *
 * <p>FAIL-OPEN TO NO-DISCOUNT: any transport error, timeout, non-2xx or parse
 * failure yields {@code notRecognised()}. Billing must never block — and
 * never grant a discount — on an unavailable registry.</p>
 *
 * <p>Short-TTL in-process cache (default 60s, configurable via
 * {@code costa.recognition.cache-ttl-seconds}) bounds lookup load on the
 * encounter-ingest hot path.</p>
 *
 * <p>Kafka consumer threads carry no servlet request, so v1.1 trust headers
 * are synthesized here (same defect-family fix as the service-to-service
 * header synthesis in Dura/PharmacyConsumer).</p>
 */
@Component
public class ProviderRecognitionClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderRecognitionClient.class);

    /** Composed recognition verdict for billing policy. */
    public record Recognition(boolean recognised, String recognitionClass) {
        public static Recognition notRecognised() {
            return new Recognition(false, null);
        }
    }

    private record CacheEntry(Recognition value, Instant expiresAt) {}

    private final RestClient varapiRestClient;
    private final RestClient vashandiRestClient;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ProviderRecognitionClient(@Qualifier("varapiRestClient") RestClient varapiRestClient,
                                     @Qualifier("vashandiRestClient") RestClient vashandiRestClient,
                                     ObjectMapper objectMapper,
                                     CostaProperties properties) {
        this.varapiRestClient = varapiRestClient;
        this.vashandiRestClient = vashandiRestClient;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(Math.max(1, properties.getRecognition().getCacheTtlSeconds()));
    }

    public Recognition resolve(UUID tenantId, String healthId) {
        if (tenantId == null || healthId == null || healthId.isBlank()) {
            return Recognition.notRecognised();
        }
        String key = tenantId + ":" + healthId.trim();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        Recognition value = compose(tenantId, healthId.trim());
        if (cache.size() > 10_000) {
            cache.clear();
        }
        cache.put(key, new CacheEntry(value, Instant.now().plus(ttl)));
        return value;
    }

    private Recognition compose(UUID tenantId, String healthId) {
        boolean provider = recognised(fetch(varapiRestClient,
                "/v1/internal/providers/by-health-id/" + healthId + "/recognition", tenantId), true);
        boolean workforce = recognised(fetch(vashandiRestClient,
                "/v1/internal/vashandi/workforce/by-health-id/" + healthId + "/recognition", tenantId), false);
        if (!provider && !workforce) {
            return Recognition.notRecognised();
        }
        String cls = provider && workforce ? "BOTH"
                : provider ? "LICENSED_PROVIDER" : "HEALTH_WORKFORCE";
        return new Recognition(true, cls);
    }

    /** null on ANY failure — the caller treats null as not recognised (fail-open). */
    private JsonNode fetch(RestClient client, String path, UUID tenantId) {
        try {
            ResponseEntity<String> response = client.get()
                    .uri(path)
                    .headers(h -> synthesizeTrustHeaders(h, tenantId))
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.debug("Recognition source unavailable ({}): {}", path, e.getMessage());
            return null;
        }
    }

    /** VARAPI wraps in ApiResponse {data:{...}}; vashandi returns the bare body. */
    private static boolean recognised(JsonNode body, boolean unwrapData) {
        if (body == null) {
            return false;
        }
        JsonNode node = unwrapData && body.has("data") ? body.get("data") : body;
        return node.path("recognised").asBoolean(false);
    }

    private static void synthesizeTrustHeaders(HttpHeaders h, UUID tenantId) {
        h.set("X-Tenant-ID", tenantId.toString());
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        h.set("X-Correlation-ID", UUID.randomUUID().toString());
        h.set("X-Actor-ID", "costa-billing");
        h.set("X-Actor-Type", "SYSTEM");
        h.set("X-Purpose-Of-Use", "BILLING");
    }
}
