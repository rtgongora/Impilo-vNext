package zw.gov.mohcc.impilo.experience.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that evicts BFF Redis cache entries when sovereign
 * services publish change events.
 *
 * <p>This ensures the cache never serves stale data beyond the TTL.
 * Without this consumer, cache staleness is bounded by TTL only.
 * With it, cache is evicted immediately on change.</p>
 */
@Component
public class CacheEvictionConsumer {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictionConsumer.class);

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public CacheEvictionConsumer(CacheService cacheService, ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    // Clinical-plane subject stream (Identity Contract §7.3) — the BFF's patient
    // caches are CPID-keyed, so eviction listens to CPID events, not vito.* topics.
    @KafkaListener(topics = "impilo.identity.subject", groupId = "bff-cache")
    public void onPatientEvent(String event) {
        String cpid = extractField(event, "cpid", "subject_id");
        if (cpid != null) {
            cacheService.evictPattern("patient:" + cpid + ":*");
            log.debug("Evicted patient cache for CPID={}", cpid);
        }
    }

    @KafkaListener(topics = {"impilo.pct.journey", "pct.journey"}, groupId = "bff-cache")
    public void onJourneyEvent(String event) {
        String encounterId = extractField(event, "encounter_id");
        String facilityId = extractField(event, "facility_id");
        if (encounterId != null) cacheService.evict("encounter:" + encounterId + ":active");
        if (facilityId != null) cacheService.evictPattern("queue:" + facilityId + ":*");
    }

    @KafkaListener(topics = {"mushe.transactions", "mushe.wallet"}, groupId = "bff-cache")
    public void onWalletEvent(String event) {
        String walletId = extractField(event, "wallet_id", "subject_id");
        if (walletId != null) cacheService.evict("wallet:" + walletId + ":balance");
    }

    @KafkaListener(topics = {"impilo.tuso.facility", "tuso.facility"}, groupId = "bff-cache")
    public void onFacilityEvent(String event) {
        String facilityId = extractField(event, "facility_id", "subject_id");
        if (facilityId != null) cacheService.evictPattern("facility:" + facilityId + ":*");
    }

    @KafkaListener(topics = {"impilo.varapi.provider", "varapi.provider"}, groupId = "bff-cache")
    public void onProviderEvent(String event) {
        String providerId = extractField(event, "provider_id", "subject_id");
        if (providerId != null) cacheService.evictPattern("provider:" + providerId + ":*");
    }

    @KafkaListener(topics = {"platform.consent.events"}, groupId = "bff-cache")
    public void onConsentEvent(String event) {
        String cpid = extractField(event, "subject_cpid", "subject_id");
        if (cpid != null) cacheService.evictPattern("patient:" + cpid + ":*");
    }

    /**
     * Reads a field from either wire shape and either naming convention.
     *
     * <p>Envelope-vs-root was already handled. Casing was not, and it matters: the producing
     * services write camelCase domain payloads ({@code facilityId}) while these listeners ask
     * for snake_case ({@code facility_id}). The mismatch stayed invisible because the topics
     * concerned had no producer — {@code pct.journey} never existed at all (PCT routes journey
     * events to {@code pct.journey.state_changed}), so {@link #onJourneyEvent(String)} had
     * never once fired. Converting PCT gives {@code impilo.pct.journey} a producer, at which
     * point a casing-only miss would evict nothing and report nothing.</p>
     */
    private String extractField(String event, String... fieldNames) {
        try {
            JsonNode root = objectMapper.readTree(event);
            JsonNode payload = root.path("payload");
            for (String field : fieldNames) {
                for (String candidate : namingVariants(field)) {
                    if (root.has(candidate) && !root.get(candidate).isNull()) {
                        return root.get(candidate).asText();
                    }
                    if (payload.has(candidate) && !payload.get(candidate).isNull()) {
                        return payload.get(candidate).asText();
                    }
                }
            }
        } catch (Exception e) {
            log.trace("Failed to extract field from event: {}", e.getMessage());
        }
        return null;
    }

    /** {@code facility_id} → [facility_id, facilityId], and the reverse. */
    private static String[] namingVariants(String field) {
        String other = field.indexOf('_') >= 0 ? toCamelCase(field) : toSnakeCase(field);
        return other.equals(field) ? new String[]{field} : new String[]{field, other};
    }

    private static String toCamelCase(String snake) {
        StringBuilder out = new StringBuilder(snake.length());
        boolean upperNext = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else {
                out.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        return out.toString();
    }

    private static String toSnakeCase(String camel) {
        StringBuilder out = new StringBuilder(camel.length() + 4);
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                out.append('_').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
