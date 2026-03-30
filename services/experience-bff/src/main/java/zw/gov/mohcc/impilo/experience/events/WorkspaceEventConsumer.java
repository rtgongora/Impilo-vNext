package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static zw.gov.mohcc.impilo.experience.events.EventPayloadExtractor.*;

/**
 * Consumes TUSO workspace events from {@code tuso.events} / {@code kernel.tuso.events}
 * and upserts into the BFF-local {@code workspaces} table.
 *
 * <p>TUSO publishes WORKSPACE aggregate events to the default {@code tuso.events} topic
 * (the OutboxPublisher switch falls to default for "WORKSPACE").</p>
 *
 * <p>Workspace payload from TUSO includes {@code facilityId} as the TUSO internal Long.
 * The consumer resolves the BFF UUID via the {@code tuso_id} column on the
 * {@code facilities} table (populated by FacilityEventConsumer).
 * If the facility hasn't arrived yet the event is skipped (will retry on next redelivery).</p>
 */
@Component
public class WorkspaceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceEventConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkspaceEventConsumer(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"tuso.events", "kernel.tuso.events"},
            groupId = "experience-bff",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onTusoEvent(String message) {
        Map<String, Object> raw = extract(message, objectMapper);
        if (raw == null) return;

        String aggregateType = str(raw, "aggregate_type", "aggregateType");
        if (!"WORKSPACE".equals(aggregateType)) {
            return;
        }

        Map<String, Object> payload = unwrapFull(raw);

        String workspaceIdStr  = str(payload, "workspace_id", "workspaceId");
        String tenantId        = str(payload, "tenant_id", "tenantId");
        String name            = str(payload, "name");
        String workspaceType   = str(payload, "workspace_type", "workspaceType");
        String status          = str(payload, "status");

        if (workspaceIdStr == null || tenantId == null || name == null) {
            log.warn("WorkspaceEventConsumer: missing required fields, skipping. keys={}", payload.keySet());
            return;
        }

        if (workspaceType == null) workspaceType = "GENERAL";
        if (status == null) status = "ACTIVE";

        Long tusoFacilityId = extractLong(payload, "facility_id", "facilityId");
        UUID facilityUuid = null;

        if (tusoFacilityId != null) {
            facilityUuid = resolveFacilityUuid(tenantId, tusoFacilityId);
            if (facilityUuid == null) {
                log.warn("WorkspaceEventConsumer: facility not found for tusoFacilityId={} tenantId={} — skipping workspace {}",
                        tusoFacilityId, tenantId, workspaceIdStr);
                return;
            }
        } else {
            log.warn("WorkspaceEventConsumer: no facilityId in workspace payload, skipping. keys={}", payload.keySet());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        jdbc.update("""
            INSERT INTO workspaces
                (id, tenant_id, facility_id, name, workspace_type, status, tuso_id, created_at, updated_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, tuso_id) DO UPDATE SET
                name           = EXCLUDED.name,
                workspace_type = EXCLUDED.workspace_type,
                status         = EXCLUDED.status,
                updated_at     = EXCLUDED.updated_at
            """,
                tenantId, facilityUuid, name, workspaceType, status, workspaceIdStr, now, now);

        log.info("WorkspaceEventConsumer: upserted workspace tusoId={} name='{}' tenantId={}", workspaceIdStr, name, tenantId);
    }

    private UUID resolveFacilityUuid(String tenantId, Long tusoFacilityId) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM facilities WHERE tenant_id = ? AND tuso_id = ?",
                    UUID.class, tenantId, tusoFacilityId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Long extractLong(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object v = payload.get(key);
            if (v != null) {
                try {
                    return Long.parseLong(v.toString());
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
