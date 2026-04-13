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

    @KafkaListener(topics = {"impilo.vito.identity", "vito.identity"}, groupId = "bff-cache")
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

    private String extractField(String event, String... fieldNames) {
        try {
            JsonNode root = objectMapper.readTree(event);
            for (String field : fieldNames) {
                // Check top level
                if (root.has(field) && !root.get(field).isNull()) {
                    return root.get(field).asText();
                }
                // Check inside payload
                JsonNode payload = root.path("payload");
                if (payload.has(field) && !payload.get(field).isNull()) {
                    return payload.get(field).asText();
                }
            }
        } catch (Exception e) {
            log.trace("Failed to extract field from event: {}", e.getMessage());
        }
        return null;
    }
}
