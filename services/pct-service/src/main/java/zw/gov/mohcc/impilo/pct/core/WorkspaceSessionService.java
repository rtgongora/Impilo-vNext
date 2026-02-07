package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.WorkspaceSessionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.WorkspaceSessionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages workspace (service-point) sessions for healthcare workers.
 *
 * <p>A workspace session represents a provider's active duty period at a
 * specific service point within a facility. Starting a shift creates a new
 * session and automatically ends any previously active session for the same
 * actor. Ending a shift closes the current session, timestamps it, and
 * publishes a telemetry event so that dashboards can track real-time
 * staffing levels.</p>
 *
 * <p>The session entity carries the {@code dutyMode} (e.g. NORMAL, ON_CALL,
 * EMERGENCY) which downstream services can use to adjust routing weights
 * and queue prioritisation.</p>
 */
@Service
public class WorkspaceSessionService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSessionService.class);

    private final WorkspaceSessionRepository sessionRepository;
    private final EventOutboxRepository outboxRepository;
    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    public WorkspaceSessionService(WorkspaceSessionRepository sessionRepository,
                                   EventOutboxRepository outboxRepository,
                                   TelemetryService telemetryService,
                                   ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.outboxRepository = outboxRepository;
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Start a new workspace shift for the current actor.
     *
     * <p>If the actor already has an active (un-ended) session, it is
     * automatically closed before the new session is created. This ensures
     * at most one active session per actor at any time.</p>
     *
     * @param facilityId  the facility where the shift is starting
     * @param workspaceId the workspace (service point) being staffed
     * @param dutyMode    the duty mode for this shift (e.g. NORMAL, ON_CALL, EMERGENCY)
     * @return the newly created workspace session
     */
    @Transactional
    public WorkspaceSessionEntity startShift(UUID facilityId, UUID workspaceId, String dutyMode) {
        TrustContext ctx = TrustContextHolder.require();

        // End any existing active session for this actor
        sessionRepository.findByTenantIdAndActorIdAndEndedAtIsNull(ctx.tenantId(), ctx.actorId())
                .ifPresent(existing -> {
                    existing.setEndedAt(OffsetDateTime.now());
                    sessionRepository.save(existing);
                    log.info("Auto-ended previous session {} for actor {}",
                            existing.getId(), ctx.actorId());
                });

        // Create and persist the new session
        WorkspaceSessionEntity session = new WorkspaceSessionEntity();
        session.setTenantId(ctx.tenantId());
        session.setFacilityId(facilityId);
        session.setWorkspaceId(workspaceId);
        session.setActorId(ctx.actorId());
        session.setDutyMode(dutyMode);
        session.setStartedAt(OffsetDateTime.now());

        session = sessionRepository.save(session);

        // Record telemetry
        Map<String, Object> telemetryData = new LinkedHashMap<>();
        telemetryData.put("sessionId", session.getId());
        telemetryData.put("workspaceId", workspaceId.toString());
        telemetryData.put("dutyMode", dutyMode);
        telemetryService.record("shift.started", null, telemetryData);

        log.info("Shift started: actor={}, facility={}, workspace={}, mode={}",
                ctx.actorId(), facilityId, workspaceId, dutyMode);

        return session;
    }

    /**
     * End the current actor's active workspace session.
     *
     * <p>Sets the {@code endedAt} timestamp and publishes a telemetry event
     * for real-time staffing dashboards. If no active session exists, an
     * {@link IllegalStateException} is thrown.</p>
     *
     * @return the closed workspace session
     * @throws IllegalStateException if the current actor has no active session
     */
    @Transactional
    public WorkspaceSessionEntity endShift() {
        TrustContext ctx = TrustContextHolder.require();

        WorkspaceSessionEntity session = sessionRepository
                .findByTenantIdAndActorIdAndEndedAtIsNull(ctx.tenantId(), ctx.actorId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active workspace session found for actor: " + ctx.actorId()));

        session.setEndedAt(OffsetDateTime.now());
        session = sessionRepository.save(session);

        // Publish telemetry
        Map<String, Object> telemetryData = new LinkedHashMap<>();
        telemetryData.put("sessionId", session.getId());
        telemetryData.put("workspaceId", session.getWorkspaceId().toString());
        telemetryData.put("durationMinutes", java.time.Duration.between(
                session.getStartedAt().toInstant(), session.getEndedAt().toInstant()).toMinutes());
        telemetryService.record("shift.ended", null, telemetryData);

        log.info("Shift ended: actor={}, session={}", ctx.actorId(), session.getId());

        return session;
    }

    /**
     * Retrieve the current actor's active workspace session.
     *
     * @return the active session, or {@code null} if the actor is not currently on shift
     */
    @Transactional(readOnly = true)
    public WorkspaceSessionEntity getActiveSession() {
        TrustContext ctx = TrustContextHolder.require();
        return sessionRepository
                .findByTenantIdAndActorIdAndEndedAtIsNull(ctx.tenantId(), ctx.actorId())
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Outbox helper
    // ------------------------------------------------------------------

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(TrustContextHolder.require().tenantId());
        outboxRepository.save(outbox);
    }
}
