package zw.gov.mohcc.impilo.rito.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rito.core.MpdsrReviewService;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes PCT {@code DEATH_REVIEW_REQUIRED} outbox events and idempotently opens MPDSR reviews.
 *
 * <p>Events arrive on the pct.events catch-all until a dedicated topic is leased. HTTP intake at
 * {@code POST /internal/v1/mpdsr/reviews/from-death-event} mirrors this path for BFF/PCT callers.
 */
@Component
@Profile("!test")
public class MpdsrDeathReviewConsumer {

    private static final Logger log = LoggerFactory.getLogger(MpdsrDeathReviewConsumer.class);

    private final MpdsrReviewService mpdsrReviewService;
    private final ObjectMapper objectMapper;

    public MpdsrDeathReviewConsumer(MpdsrReviewService mpdsrReviewService, ObjectMapper objectMapper) {
        this.mpdsrReviewService = mpdsrReviewService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"${rito.signals.topics.pct-death:pct.events}"},
            groupId = "rito-quality-safety-service-mpdsr")
    public void onPctDeathEvent(String message) {
        try {
            Map<String, Object> root = objectMapper.readValue(message, new TypeReference<>() {});
            String eventType = String.valueOf(root.getOrDefault("eventType", ""));
            if (!"DEATH_REVIEW_REQUIRED".equals(eventType)) {
                return;
            }
            UUID tenantId = parseUuid(root, "tenantId", "tenant_id");
            if (tenantId == null) {
                log.warn("Skipping DEATH_REVIEW_REQUIRED — no tenantId");
                return;
            }
            Map<String, Object> payload = extractPayload(root);
            UUID deathCaseId = parseUuid(payload, "caseId", "deathCaseId", "death_case_id");
            if (deathCaseId == null) {
                deathCaseId = parseUuid(root, "aggregateId");
            }
            if (deathCaseId == null) {
                log.warn("Skipping DEATH_REVIEW_REQUIRED — no death case id");
                return;
            }
            MpdsrReviewService.DeathEventIntake intake = new MpdsrReviewService.DeathEventIntake(
                    deathCaseId,
                    parseUuid(payload, "facilityId", "facility_id"),
                    parseUuid(payload, "districtId", "district_id"),
                    str(payload, "flags"),
                    str(payload, "nearMissRef", "near_miss_ref"),
                    str(payload, "whoClassification", "who_classification"),
                    str(payload, "reviewLevel", "review_level"));
            mpdsrReviewService.openFromDeathEvent(tenantId, intake, "pct-kafka-consumer");
            log.info("MPDSR review opened/confirmed for death case {}", deathCaseId);
        } catch (Exception e) {
            log.error("Failed to process DEATH_REVIEW_REQUIRED: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractPayload(Map<String, Object> root) {
        Object raw = root.get("payload");
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return root;
    }

    private static UUID parseUuid(Map<String, Object> root, String... keys) {
        for (String key : keys) {
            Object v = root.get(key);
            if (v != null && !v.toString().isBlank()) {
                try {
                    return UUID.fromString(v.toString());
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String str(Map<String, Object> root, String... keys) {
        for (String key : keys) {
            Object v = root.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }
}
