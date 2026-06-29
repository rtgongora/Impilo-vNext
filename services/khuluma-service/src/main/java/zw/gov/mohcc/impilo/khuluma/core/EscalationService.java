package zw.gov.mohcc.impilo.khuluma.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.domain.EscalationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.SlaPolicyEntity;
import zw.gov.mohcc.impilo.khuluma.repository.EscalationRepository;
import zw.gov.mohcc.impilo.khuluma.repository.SlaPolicyRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Khuluma escalation, routing & SLA (Phase 5 / W4, G-KH-01). Owns the escalation lifecycle
 * OPEN → ASSIGNED → ACCEPTED → RESOLVED (BREACHED / CANCELLED also terminal), computes first-response
 * + resolution SLA targets from a per-tenant policy (priority defaults otherwise), and runs a scheduled
 * sweep that marks breaches and emits events — the same shape as OROS SlaService.
 */
@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);
    private static final String AGGREGATE = "KHULUMA_ESCALATION";
    private static final Set<String> OPEN = Set.of("OPEN", "ASSIGNED", "ACCEPTED");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

    private final EscalationRepository escalations;
    private final SlaPolicyRepository policies;
    private final OutboxAppender outbox;

    public EscalationService(EscalationRepository escalations, SlaPolicyRepository policies, OutboxAppender outbox) {
        this.escalations = escalations;
        this.policies = policies;
        this.outbox = outbox;
    }

    public record NewEscalation(UUID conversationId, String reason, String priority) {}

    /** Default SLA targets by priority when no tenant policy exists: {firstResponse, resolution} minutes. */
    private static int[] defaultSla(String priority) {
        return switch (priority) {
            case "URGENT" -> new int[]{5, 30};
            case "HIGH" -> new int[]{15, 120};
            case "LOW" -> new int[]{60, 1440};
            default -> new int[]{30, 240}; // MEDIUM
        };
    }

    @Transactional
    public EscalationEntity open(UUID tenantId, NewEscalation cmd) {
        String priority = normalize(cmd.priority());
        EscalationEntity e = new EscalationEntity();
        e.setTenantId(tenantId);
        e.setConversationId(cmd.conversationId());
        e.setReason(cmd.reason());
        e.setPriority(priority);
        e.setStatus("OPEN");

        int[] sla = policies.findByTenantIdAndPriorityAndActiveTrue(tenantId, priority)
                .map(p -> new int[]{p.getFirstResponseMinutes(), p.getResolutionMinutes()})
                .orElseGet(() -> defaultSla(priority));
        OffsetDateTime now = OffsetDateTime.now();
        e.setFirstResponseDueAt(now.plusMinutes(sla[0]));
        e.setResolutionDueAt(now.plusMinutes(sla[1]));

        EscalationEntity saved = escalations.save(e);
        emit("impilo.khuluma.escalation.opened.v1", saved);
        return saved;
    }

    @Transactional
    public EscalationEntity assign(UUID tenantId, UUID id, String assignee) {
        EscalationEntity e = require(tenantId, id);
        requireOpen(e);
        e.setAssignedTo(assignee);
        if (e.getStatus().equals("OPEN")) e.setStatus("ASSIGNED");
        EscalationEntity saved = escalations.save(e);
        emit("impilo.khuluma.escalation.assigned.v1", saved);
        return saved;
    }

    /** Accept = first human response; stops the first-response SLA clock. */
    @Transactional
    public EscalationEntity accept(UUID tenantId, UUID id, String actor) {
        EscalationEntity e = require(tenantId, id);
        requireOpen(e);
        e.setStatus("ACCEPTED");
        if (e.getAssignedTo() == null) e.setAssignedTo(actor);
        if (e.getFirstRespondedAt() == null) e.setFirstRespondedAt(OffsetDateTime.now());
        EscalationEntity saved = escalations.save(e);
        emit("impilo.khuluma.escalation.accepted.v1", saved);
        return saved;
    }

    @Transactional
    public EscalationEntity resolve(UUID tenantId, UUID id, String note) {
        EscalationEntity e = require(tenantId, id);
        requireOpen(e);
        e.setStatus("RESOLVED");
        e.setResolutionNote(note);
        e.setResolvedAt(OffsetDateTime.now());
        EscalationEntity saved = escalations.save(e);
        emit("impilo.khuluma.escalation.resolved.v1", saved);
        return saved;
    }

    /** Re-escalate: bump the tier (and priority one step) — keeps the SLA clock running. */
    @Transactional
    public EscalationEntity escalate(UUID tenantId, UUID id, String reason) {
        EscalationEntity e = require(tenantId, id);
        requireOpen(e);
        e.setTier(e.getTier() + 1);
        e.setPriority(bump(e.getPriority()));
        if (reason != null && !reason.isBlank()) e.setReason(reason);
        EscalationEntity saved = escalations.save(e);
        emit("impilo.khuluma.escalation.escalated.v1", saved);
        return saved;
    }

    @Transactional
    public EscalationEntity cancel(UUID tenantId, UUID id, String note) {
        EscalationEntity e = require(tenantId, id);
        requireOpen(e);
        e.setStatus("CANCELLED");
        e.setResolutionNote(note);
        EscalationEntity saved = escalations.save(e);
        emit("impilo.khuluma.escalation.cancelled.v1", saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public EscalationEntity get(UUID tenantId, UUID id) { return require(tenantId, id); }

    @Transactional(readOnly = true)
    public List<EscalationEntity> queue(UUID tenantId) {
        return escalations.findByTenantIdAndStatusInOrderByCreatedAtAsc(tenantId, List.copyOf(OPEN));
    }

    @Transactional(readOnly = true)
    public List<EscalationEntity> list(UUID tenantId) {
        return escalations.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    /**
     * Scheduled SLA sweep. Marks first-response and resolution breaches and emits an event per breach.
     * Idempotent: the {@code *_breached} flags ensure a breach fires once.
     */
    @Scheduled(fixedDelayString = "${khuluma.sla.breach-check-interval-ms:60000}")
    @Transactional
    public void checkSlaBreaches() {
        OffsetDateTime now = OffsetDateTime.now();
        for (EscalationEntity e : escalations.findFirstResponseBreaches(now)) {
            e.setFirstResponseBreached(true);
            escalations.save(e);
            emit("impilo.khuluma.escalation.first-response-breached.v1", e);
        }
        for (EscalationEntity e : escalations.findResolutionBreaches(now)) {
            e.setResolutionBreached(true);
            e.setStatus("BREACHED");
            escalations.save(e);
            emit("impilo.khuluma.escalation.breached.v1", e);
        }
    }

    private void emit(String eventType, EscalationEntity e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("escalationId", e.getId().toString());
        payload.put("conversationId", e.getConversationId() != null ? e.getConversationId().toString() : null);
        payload.put("status", e.getStatus());
        payload.put("priority", e.getPriority());
        payload.put("tier", e.getTier());
        payload.put("assignedTo", e.getAssignedTo());
        String subject = e.getConversationId() != null ? e.getConversationId().toString() : e.getId().toString();
        outbox.append(eventType, AGGREGATE, e.getId().toString(), e.getTenantId(), "national-spine",
                UUID.randomUUID().toString(), eventType + "-" + e.getId() + "-" + UUID.randomUUID(),
                payload, e.getTenantId() + ":" + e.getId(), subject, "CONVERSATION");
    }

    private EscalationEntity require(UUID tenantId, UUID id) {
        return escalations.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Escalation not found: " + id));
    }

    private static void requireOpen(EscalationEntity e) {
        if (!OPEN.contains(e.getStatus())) {
            throw new IllegalStateException("Escalation is already terminal (" + e.getStatus() + ")");
        }
    }

    private static String normalize(String priority) {
        if (priority == null) return "MEDIUM";
        String p = priority.trim().toUpperCase();
        return PRIORITIES.contains(p) ? p : "MEDIUM";
    }

    private static String bump(String priority) {
        return switch (priority) {
            case "LOW" -> "MEDIUM";
            case "MEDIUM" -> "HIGH";
            default -> "URGENT"; // HIGH or URGENT -> URGENT
        };
    }
}
