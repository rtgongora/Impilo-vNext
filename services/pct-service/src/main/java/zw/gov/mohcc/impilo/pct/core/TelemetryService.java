package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.TelemetryEventEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.TelemetryEventRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Records operational telemetry events for the PCT service.
 *
 * <p>Telemetry events capture fine-grained operational data such as shift
 * start/end, queue transitions, routing decisions, encounter lifecycle
 * milestones, and control-tower heartbeats. These events feed real-time
 * dashboards and historical analytics pipelines.</p>
 *
 * <p>Every event is persisted in the {@code pct.telemetry_event} table with
 * the full trust context (tenant, facility, workspace, actor) so that
 * downstream consumers can slice metrics by any dimension.</p>
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetryEventRepository telemetryRepository;
    private final ObjectMapper objectMapper;

    public TelemetryService(TelemetryEventRepository telemetryRepository,
                            ObjectMapper objectMapper) {
        this.telemetryRepository = telemetryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Record a telemetry event with an explicit facility and workspace.
     *
     * @param facilityId  the facility where the event occurred
     * @param workspaceId the workspace (service point) where the event occurred; may be {@code null}
     * @param eventType   a short, dot-separated event type identifier (e.g. {@code "shift.started"})
     * @param journeyId   the patient journey this event relates to; may be {@code null}
     * @param payloadJson a raw JSON string carrying event-specific data
     */
    @Transactional
    public void record(UUID facilityId, UUID workspaceId, String eventType,
                       String journeyId, String payloadJson) {
        TrustContext ctx = TrustContextHolder.require();

        TelemetryEventEntity event = new TelemetryEventEntity();
        event.setTenantId(ctx.tenantId());
        event.setFacilityId(facilityId);
        event.setWorkspaceId(workspaceId);
        event.setEventType(eventType);
        event.setJourneyId(journeyId);
        event.setPayload(payloadJson);

        telemetryRepository.save(event);

        log.debug("Telemetry recorded: type={}, journey={}, facility={}",
                eventType, journeyId, facilityId);
    }

    /**
     * Convenience method that serialises a {@link Map} payload to JSON and
     * derives facility/workspace from the current trust context.
     *
     * <p>Prefer this overload for most in-service telemetry calls where
     * the trust context is available and the payload is a simple map of
     * key-value pairs.</p>
     *
     * @param eventType a short, dot-separated event type identifier
     * @param journeyId the patient journey this event relates to; may be {@code null}
     * @param data      a map of event-specific key-value pairs to serialise as JSON
     */
    @Transactional
    public void record(String eventType, String journeyId, Map<String, Object> data) {
        TrustContext ctx = TrustContextHolder.require();
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise telemetry payload for event {}: {}",
                    eventType, e.getMessage());
            payloadJson = "{}";
        }
        record(ctx.facilityId(), ctx.workspaceId(), eventType, journeyId, payloadJson);
    }
}
