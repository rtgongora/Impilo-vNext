package zw.gov.mohcc.impilo.guidance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.guidance.events.GuidanceOutboxAppender;
import zw.gov.mohcc.impilo.guidance.persistence.entity.NompiloHandoffEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.NompiloHandoffRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Nompilo AI→human handoff lifecycle (G-KH-05/06). Owns the state machine
 * QUEUED → ACCEPTED → (ESCALATED) → CLOSED (CANCELLED is also terminal) and emits a
 * lifecycle event on every transition via the guidance outbox. Guidance-service is the SoR
 * for Nompilo, so the lifecycle lives here, not in the (stateless) BFF.
 */
@Service
public class NompiloHandoffService {

    public static final String EVENT_REQUESTED = "core.nompilo.handoff.requested";
    public static final String EVENT_ACCEPTED = "guidance.nompilo.handoff.accepted";
    public static final String EVENT_ESCALATED = "guidance.nompilo.handoff.escalated";
    public static final String EVENT_CLOSED = "guidance.nompilo.handoff.closed";
    public static final String EVENT_CANCELLED = "guidance.nompilo.handoff.cancelled";

    private static final Set<String> OPEN_STATUSES = Set.of("QUEUED", "ACCEPTED", "ESCALATED");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> DESTINATIONS =
            Set.of("CARE_TEAM", "FACILITY_HELP_DESK", "CALL_CENTRE", "OPERATIONS_DESK");

    private final NompiloHandoffRepository repository;
    private final GuidanceOutboxAppender outbox;

    public NompiloHandoffService(NompiloHandoffRepository repository, GuidanceOutboxAppender outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    /** New-handoff command. Unknown priority/destination fall back to safe defaults (no silent block). */
    public record NewHandoff(String transactionId, String subjectId, String actorId, String reason,
                             String priority, String destination, String flowId, String contextJson) {
    }

    @Transactional
    public NompiloHandoffEntity request(String tenantId, NewHandoff cmd) {
        NompiloHandoffEntity h = new NompiloHandoffEntity();
        h.setTenantId(tenantId);
        h.setTransactionId(cmd.transactionId());
        h.setSubjectId(cmd.subjectId());
        h.setActorId(cmd.actorId());
        h.setReason(cmd.reason());
        h.setPriority(normalize(cmd.priority(), PRIORITIES, "MEDIUM"));
        h.setDestination(normalize(cmd.destination(), DESTINATIONS, "CARE_TEAM"));
        h.setFlowId(cmd.flowId());
        h.setContextJson(cmd.contextJson());
        h.setStatus("QUEUED");
        NompiloHandoffEntity saved = repository.save(h);
        outbox.recordHandoffEvent(saved, EVENT_REQUESTED);
        return saved;
    }

    @Transactional
    public NompiloHandoffEntity accept(String tenantId, java.util.UUID id, String assignedTo) {
        NompiloHandoffEntity h = require(tenantId, id);
        if (!h.getStatus().equals("QUEUED") && !h.getStatus().equals("ESCALATED")) {
            throw new IllegalStateException("Only a QUEUED or ESCALATED handoff can be accepted (was " + h.getStatus() + ")");
        }
        h.setStatus("ACCEPTED");
        h.setAssignedTo(assignedTo);
        h.setAcceptedAt(OffsetDateTime.now());
        NompiloHandoffEntity saved = repository.save(h);
        outbox.recordHandoffEvent(saved, EVENT_ACCEPTED);
        return saved;
    }

    @Transactional
    public NompiloHandoffEntity escalate(String tenantId, java.util.UUID id, String reason) {
        NompiloHandoffEntity h = require(tenantId, id);
        if (!OPEN_STATUSES.contains(h.getStatus())) {
            throw new IllegalStateException("Only an open handoff can be escalated (was " + h.getStatus() + ")");
        }
        h.setStatus("ESCALATED");
        if (reason != null && !reason.isBlank()) {
            h.setReason(reason);
        }
        NompiloHandoffEntity saved = repository.save(h);
        outbox.recordHandoffEvent(saved, EVENT_ESCALATED);
        return saved;
    }

    @Transactional
    public NompiloHandoffEntity close(String tenantId, java.util.UUID id, String note) {
        NompiloHandoffEntity h = require(tenantId, id);
        if (!OPEN_STATUSES.contains(h.getStatus())) {
            throw new IllegalStateException("Handoff is already terminal (" + h.getStatus() + ")");
        }
        h.setStatus("CLOSED");
        h.setCloseNote(note);
        h.setClosedAt(OffsetDateTime.now());
        NompiloHandoffEntity saved = repository.save(h);
        outbox.recordHandoffEvent(saved, EVENT_CLOSED);
        return saved;
    }

    @Transactional
    public NompiloHandoffEntity cancel(String tenantId, java.util.UUID id, String note) {
        NompiloHandoffEntity h = require(tenantId, id);
        if (!OPEN_STATUSES.contains(h.getStatus())) {
            throw new IllegalStateException("Handoff is already terminal (" + h.getStatus() + ")");
        }
        h.setStatus("CANCELLED");
        h.setCloseNote(note);
        h.setClosedAt(OffsetDateTime.now());
        NompiloHandoffEntity saved = repository.save(h);
        outbox.recordHandoffEvent(saved, EVENT_CANCELLED);
        return saved;
    }

    @Transactional(readOnly = true)
    public NompiloHandoffEntity get(String tenantId, java.util.UUID id) {
        return require(tenantId, id);
    }

    /** The operations-desk queue: open handoffs, oldest first. */
    @Transactional(readOnly = true)
    public List<NompiloHandoffEntity> queue(String tenantId) {
        return repository.findByTenantIdAndStatusInOrderByCreatedAtAsc(tenantId, List.copyOf(OPEN_STATUSES));
    }

    @Transactional(readOnly = true)
    public List<NompiloHandoffEntity> list(String tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    private NompiloHandoffEntity require(String tenantId, java.util.UUID id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Handoff not found: " + id));
    }

    private static String normalize(String value, Set<String> allowed, String fallback) {
        if (value == null) {
            return fallback;
        }
        String upper = value.trim().toUpperCase();
        return allowed.contains(upper) ? upper : fallback;
    }
}
