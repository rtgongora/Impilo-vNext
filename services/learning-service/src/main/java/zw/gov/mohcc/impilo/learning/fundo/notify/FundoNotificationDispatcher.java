package zw.gov.mohcc.impilo.learning.fundo.notify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains PENDING {@code lrn_learning_notification} intents through the configured
 * {@link LearningNotificationProvider}. The default provider is {@code STUB}, which
 * marks intents {@code RECORDED} without delivering — so the system is honest about
 * whether messages actually left the platform. Switching the provider to
 * {@code NOTIFICATION_SERVICE} forwards to the platform comms hub.
 */
@Service
public class FundoNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(FundoNotificationDispatcher.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, LearningNotificationProvider> providers = new LinkedHashMap<>();
    private final boolean enabled;
    private final String providerKey;
    private final int batchSize;

    public FundoNotificationDispatcher(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            List<LearningNotificationProvider> providerBeans,
            @Value("${learning.notifications.dispatch.enabled:true}") boolean enabled,
            @Value("${learning.notifications.dispatch.provider:STUB}") String providerKey,
            @Value("${learning.notifications.dispatch.batch-size:100}") int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        for (LearningNotificationProvider p : providerBeans) {
            providers.put(p.key(), p);
        }
        this.enabled = enabled;
        this.providerKey = providerKey;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${learning.notifications.dispatch.initial-delay-ms:90000}",
            fixedDelayString = "${learning.notifications.dispatch.poll-interval-ms:300000}")
    public void dispatchPending() {
        if (!enabled) {
            return;
        }
        try {
            int processed = drainOnce();
            if (processed > 0) {
                log.info("Notification dispatcher: processed {} intents via provider {}", processed, providerKey);
            }
        } catch (Exception e) {
            log.warn("Notification dispatch sweep failed: {}", e.getMessage());
        }
    }

    /** Process up to one batch of PENDING intents. Returns the number handled. */
    @Transactional
    public int drainOnce() {
        LearningNotificationProvider provider = providers.getOrDefault(
                providerKey, providers.get(StubLearningNotificationProvider.KEY));
        if (provider == null) {
            log.warn("No notification provider available for key {}", providerKey);
            return 0;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                select id, tenant_id, subject_type, subject_id, event_code, title, message,
                       channel_preference, metadata_json
                from lrn_learning_notification
                where status = 'PENDING'
                order by created_at
                limit ?
                """,
                batchSize);
        int handled = 0;
        for (Map<String, Object> row : rows) {
            LearningNotificationProvider.Intent intent = toIntent(row);
            LearningNotificationProvider.DeliveryOutcome outcome;
            try {
                outcome = provider.deliver(intent);
            } catch (Exception ex) {
                outcome = LearningNotificationProvider.DeliveryOutcome.failed(
                        ex.getMessage() == null ? "provider error" : ex.getMessage());
            }
            applyOutcome(intent.id(), row.get("metadata_json"), provider.key(), outcome);
            handled++;
        }
        return handled;
    }

    private LearningNotificationProvider.Intent toIntent(Map<String, Object> row) {
        return new LearningNotificationProvider.Intent(
                (UUID) row.get("id"),
                (UUID) row.get("tenant_id"),
                (String) row.get("subject_type"),
                (String) row.get("subject_id"),
                (String) row.get("event_code"),
                (String) row.get("title"),
                (String) row.get("message"),
                (String) row.get("channel_preference"),
                parseMetadata(row.get("metadata_json")));
    }

    private void applyOutcome(UUID id, Object existingMetadata, String providerKey,
            LearningNotificationProvider.DeliveryOutcome outcome) {
        Map<String, Object> metadata = parseMetadata(existingMetadata);
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("provider", providerKey);
        delivery.put("delivered", outcome.delivered());
        delivery.put("detail", outcome.detail());
        delivery.put("attemptedAt", OffsetDateTime.now().toString());
        metadata.put("_delivery", delivery);
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            metadataJson = null;
        }
        // sent_at is set ONLY when the message actually left the platform.
        jdbcTemplate.update(
                "update lrn_learning_notification set status = ?, sent_at = ?, metadata_json = coalesce(?, metadata_json) where id = ?",
                outcome.status(),
                outcome.delivered() ? OffsetDateTime.now() : null,
                metadataJson,
                id);
    }

    private Map<String, Object> parseMetadata(Object metadataJson) {
        if (metadataJson == null) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    metadataJson.toString(), new TypeReference<Map<String, Object>>() {});
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** Visible for tests / ops: which provider keys are registered. */
    public List<String> registeredProviders() {
        return new ArrayList<>(providers.keySet());
    }
}
