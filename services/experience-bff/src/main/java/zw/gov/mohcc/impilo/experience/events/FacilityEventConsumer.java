package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

import static zw.gov.mohcc.impilo.experience.events.EventPayloadExtractor.*;

/**
 * Consumes TUSO facility events and upserts into the BFF-local {@code facilities} table.
 *
 * <p>Topics consumed:</p>
 * <ul>
 *   <li>{@code tuso.facility}        — legacy raw payload (facilityId is TUSO internal Long)</li>
 *   <li>{@code kernel.tuso.facility} — v1.1 EventEnvelope</li>
 * </ul>
 *
 * <p>TUSO facilities use a Long primary key internally. The BFF stores a stable UUID generated
 * from the event, tracked via {@code tuso_id} for idempotent upserts. Subsequent consumers
 * (e.g. WorkspaceEventConsumer) look up the BFF UUID by {@code (tenant_id, tuso_id)}.</p>
 */
@Component
public class FacilityEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(FacilityEventConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public FacilityEventConsumer(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"tuso.facility", "kernel.tuso.facility"},
            groupId = "experience-bff",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onFacilityEvent(String message) {
        Map<String, Object> raw = extract(message, objectMapper);
        if (raw == null) return;

        Map<String, Object> payload = unwrapFull(raw);

        String tenantId     = str(payload, "tenant_id", "tenantId");
        String name         = str(payload, "name");
        String code         = str(payload, "facility_code", "facilityCode");
        String facilityType = str(payload, "facility_type", "facilityType");
        String status       = str(payload, "status");
        String province     = str(payload, "province");
        String district     = str(payload, "district");

        if (tenantId == null || name == null) {
            log.warn("FacilityEventConsumer: missing tenantId or name, skipping. keys={}", payload.keySet());
            return;
        }

        Long tusoId = extractLong(payload, "id", "facilityId");
        if (tusoId == null) {
            log.warn("FacilityEventConsumer: missing facility id/facilityId, skipping. keys={}", payload.keySet());
            return;
        }

        if (code == null) code = "FAC-" + tusoId;
        if (facilityType == null) facilityType = "CLINIC";
        if (status == null) status = "ACTIVE";

        Double lat = extractDouble(payload, "latitude");
        Double lon = extractDouble(payload, "longitude");

        OffsetDateTime now = OffsetDateTime.now();

        jdbc.update("""
            INSERT INTO facilities
                (id, tenant_id, name, code, facility_type, status, province, district,
                 latitude, longitude, tuso_id, created_at, updated_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, tuso_id) DO UPDATE SET
                name          = EXCLUDED.name,
                code          = EXCLUDED.code,
                facility_type = EXCLUDED.facility_type,
                status        = EXCLUDED.status,
                province      = COALESCE(EXCLUDED.province, facilities.province),
                district      = COALESCE(EXCLUDED.district, facilities.district),
                latitude      = COALESCE(EXCLUDED.latitude,  facilities.latitude),
                longitude     = COALESCE(EXCLUDED.longitude, facilities.longitude),
                updated_at    = EXCLUDED.updated_at
            """,
                tenantId, name, code, facilityType, status,
                province, district, lat, lon, tusoId, now, now);

        log.info("FacilityEventConsumer: upserted facility tusoId={} name='{}' tenantId={}", tusoId, name, tenantId);
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

    private Double extractDouble(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v == null) return null;
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
