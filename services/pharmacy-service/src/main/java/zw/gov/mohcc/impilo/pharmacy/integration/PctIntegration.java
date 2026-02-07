package zw.gov.mohcc.impilo.pharmacy.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Integration service for the PCT Patient Care Tracker.
 *
 * <p>Publishes pharmacy blocker events via the transactional outbox pattern.
 * When dispensing is complete, clears the PHARMACY blocker on the patient's
 * discharge workflow. When a reversal or issue occurs, sets the blocker.</p>
 *
 * <p>Events are written to the {@code rx_event_outbox} table and later
 * published to Kafka by the outbox publisher.</p>
 */
@Service
public class PctIntegration {

    private static final Logger log = LoggerFactory.getLogger(PctIntegration.class);

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PctIntegration(EventOutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a PCT blocker clear event.
     *
     * <p>Called when pharmacy dispensing is complete for a patient,
     * allowing the discharge workflow to proceed past the PHARMACY gate.</p>
     *
     * @param patientCpid the patient CPID
     * @param facilityId  the facility ID
     * @param tenantId    the tenant ID
     */
    public void clearPharmacyBlocker(String patientCpid, UUID facilityId, UUID tenantId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("patientCpid", patientCpid);
        payload.put("facilityId", facilityId.toString());
        payload.put("blockerType", "PHARMACY");
        payload.put("action", "CLEAR");
        payload.put("source", "pharmacy-service");

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("PCT_BLOCKER");
        event.setAggregateId(patientCpid);
        event.setEventType("PCT_BLOCKER_CLEARED");
        event.setPayload(toJson(payload));
        event.setTenantId(tenantId);

        outboxRepository.save(event);

        log.info("PCT pharmacy blocker clear event queued: cpid={}, facilityId={}",
                patientCpid, facilityId);
    }

    /**
     * Publish a PCT blocker set event.
     *
     * <p>Called when pharmacy identifies an issue requiring the PHARMACY
     * blocker to be (re-)set on the patient's discharge workflow.</p>
     *
     * @param patientCpid the patient CPID
     * @param facilityId  the facility ID
     * @param tenantId    the tenant ID
     * @param reason      the reason for setting the blocker
     */
    public void setPharmacyBlocker(String patientCpid, UUID facilityId, UUID tenantId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("patientCpid", patientCpid);
        payload.put("facilityId", facilityId.toString());
        payload.put("blockerType", "PHARMACY");
        payload.put("action", "SET");
        payload.put("reason", reason);
        payload.put("source", "pharmacy-service");

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("PCT_BLOCKER");
        event.setAggregateId(patientCpid);
        event.setEventType("PCT_BLOCKER_SET");
        event.setPayload(toJson(payload));
        event.setTenantId(tenantId);

        outboxRepository.save(event);

        log.info("PCT pharmacy blocker set event queued: cpid={}, facilityId={}, reason={}",
                patientCpid, facilityId, reason);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload", e);
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
