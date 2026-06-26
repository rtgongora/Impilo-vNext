package zw.gov.mohcc.impilo.learning.fundo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Records a learning-notification <em>intent</em> in {@code lrn_learning_notification}
 * with status {@code PENDING}. This is deliberately the "record intent without
 * faking delivery" primitive: it never claims a message was sent. Actual delivery
 * is performed separately by {@link FundoNotificationDispatcher}, which routes
 * PENDING intents through a provider-agnostic adapter (default: a no-op stub that
 * marks the intent RECORDED, not SENT).
 */
@Component
public class FundoNotificationIntentWriter {

    private static final Logger log = LoggerFactory.getLogger(FundoNotificationIntentWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FundoNotificationIntentWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Insert a PENDING notification intent. Returns the new intent id.
     */
    public UUID record(
            UUID tenantId,
            String subjectType,
            String subjectId,
            String eventCode,
            String title,
            String message,
            String channelPreference,
            OffsetDateTime dueAt,
            Map<String, Object> metadata) {
        UUID id = UUID.randomUUID();
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            metadataJson = "{}";
        }
        jdbcTemplate.update(
                """
                insert into lrn_learning_notification (
                  id, tenant_id, subject_type, subject_id, event_code, title, message,
                  channel_preference, due_at, status, metadata_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, now())
                """,
                id,
                tenantId,
                subjectType,
                subjectId,
                eventCode,
                title,
                message,
                channelPreference == null ? "IN_APP" : channelPreference,
                dueAt,
                metadataJson);
        log.debug("Recorded learning notification intent {} ({}) for {}/{}", id, eventCode, subjectType, subjectId);
        return id;
    }
}
