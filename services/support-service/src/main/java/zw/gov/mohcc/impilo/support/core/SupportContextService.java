package zw.gov.mohcc.impilo.support.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.support.domain.*;
import zw.gov.mohcc.impilo.support.repository.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administers the scoped support-context model (A3): teams, assignments,
 * permitted actions, and case-linked elevated access. Ordinary Support Mode
 * never reads clinical data — see {@link #requestElevation} and
 * {@link #approveElevation}, the only path to anything beyond that boundary.
 */
@Service
public class SupportContextService {

    private static final Logger log = LoggerFactory.getLogger(SupportContextService.class);

    private final SupportTeamRepository teamRepository;
    private final SupportContextAssignmentRepository assignmentRepository;
    private final SupportPermittedActionRepository permittedActionRepository;
    private final SupportElevatedAccessGrantRepository elevationRepository;
    private final TicketRepository ticketRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SupportContextService(SupportTeamRepository teamRepository,
                                  SupportContextAssignmentRepository assignmentRepository,
                                  SupportPermittedActionRepository permittedActionRepository,
                                  SupportElevatedAccessGrantRepository elevationRepository,
                                  TicketRepository ticketRepository,
                                  OutboxEventRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.teamRepository = teamRepository;
        this.assignmentRepository = assignmentRepository;
        this.permittedActionRepository = permittedActionRepository;
        this.elevationRepository = elevationRepository;
        this.ticketRepository = ticketRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }

    public static class InvalidElevationStateException extends RuntimeException {
        public InvalidElevationStateException(String msg) { super(msg); }
    }

    // ---- Teams ------------------------------------------------------------

    @Transactional
    public SupportTeamEntity createTeam(UUID tenantId, String podId, String correlationId,
                                         SupportTeamEntity draft) {
        if (teamRepository.findByTenantIdAndTeamCode(tenantId, draft.getTeamCode()).isPresent()) {
            throw new IllegalArgumentException("Support team code already exists: " + draft.getTeamCode());
        }
        SupportTeamEntity saved = teamRepository.save(draft);
        appendOutbox("impilo.support.team.created.v1", "SupportTeam", saved.getTeamId().toString(),
                tenantId, podId, correlationId, Map.of("teamId", saved.getTeamId().toString(), "teamCode", saved.getTeamCode()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SupportTeamEntity> listTeams(UUID tenantId) {
        return teamRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public SupportTeamEntity getTeam(UUID teamId) {
        return teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("Support team not found: " + teamId));
    }

    // ---- Assignments --------------------------------------------------------

    @Transactional
    public SupportContextAssignmentEntity createAssignment(UUID tenantId, String podId, String correlationId,
                                                             UUID teamId, String personHealthId,
                                                             String supportRole, boolean onCall, String actor) {
        SupportTeamEntity team = getTeam(teamId);
        if (!team.isActive()) {
            throw new IllegalArgumentException("Support team is not active: " + teamId);
        }
        SupportContextAssignmentEntity a = new SupportContextAssignmentEntity(UUID.randomUUID(), tenantId, teamId, personHealthId);
        if (supportRole != null && !supportRole.isBlank()) {
            a.setSupportRole(supportRole);
        }
        a.setOnCall(onCall);
        a.setCreatedBy(actor);
        a.setUpdatedBy(actor);
        SupportContextAssignmentEntity saved = assignmentRepository.save(a);
        appendOutbox("impilo.support.assignment.created.v1", "SupportAssignment", saved.getAssignmentId().toString(),
                tenantId, podId, correlationId,
                Map.of("assignmentId", saved.getAssignmentId().toString(), "teamId", teamId.toString(),
                        "personHealthId", personHealthId));
        return saved;
    }

    @Transactional
    public SupportContextAssignmentEntity endAssignment(UUID assignmentId, UUID tenantId, String podId,
                                                          String correlationId, String actor) {
        SupportContextAssignmentEntity a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Support assignment not found: " + assignmentId));
        a.setStatus("ENDED");
        a.setEndDate(java.time.LocalDate.now());
        a.setUpdatedBy(actor);
        SupportContextAssignmentEntity saved = assignmentRepository.save(a);
        appendOutbox("impilo.support.assignment.ended.v1", "SupportAssignment", assignmentId.toString(),
                tenantId, podId, correlationId, Map.of("assignmentId", assignmentId.toString()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SupportContextAssignmentEntity> assignmentsForPerson(String personHealthId) {
        return assignmentRepository.findByPersonHealthIdAndStatus(personHealthId, "ACTIVE");
    }

    // ---- Permitted actions --------------------------------------------------

    @Transactional
    public SupportPermittedActionEntity addPermittedAction(UUID tenantId, UUID teamId, String actionCode) {
        getTeam(teamId);
        return permittedActionRepository.save(new SupportPermittedActionEntity(UUID.randomUUID(), tenantId, teamId, actionCode));
    }

    @Transactional(readOnly = true)
    public List<SupportPermittedActionEntity> permittedActions(UUID teamId) {
        return permittedActionRepository.findByTeamId(teamId);
    }

    // ---- Elevated access ------------------------------------------------------
    // The only path beyond platform/config/device/integration signals. Every
    // grant is case-linked (ticketId), requires explicit approval before it
    // becomes ACTIVE, and carries a mandatory expiry once approved.

    @Transactional
    public SupportElevatedAccessGrantEntity requestElevation(UUID tenantId, String podId, String correlationId,
                                                               UUID supportAssignmentId, UUID ticketId,
                                                               String statedPurpose, String minimumScope,
                                                               String requestedBy) {
        SupportContextAssignmentEntity assignment = assignmentRepository.findById(supportAssignmentId)
                .orElseThrow(() -> new NotFoundException("Support assignment not found: " + supportAssignmentId));
        if (!"ACTIVE".equals(assignment.getStatus())) {
            throw new InvalidElevationStateException("Support assignment is not active: " + supportAssignmentId);
        }
        if (ticketRepository.findById(ticketId).isEmpty()) {
            throw new NotFoundException("Support case (ticket) not found: " + ticketId);
        }
        SupportElevatedAccessGrantEntity grant = new SupportElevatedAccessGrantEntity(
                UUID.randomUUID(), tenantId, supportAssignmentId, ticketId, statedPurpose, minimumScope, requestedBy);
        SupportElevatedAccessGrantEntity saved = elevationRepository.save(grant);
        appendOutbox("impilo.support.elevation.requested.v1", "SupportElevatedAccessGrant", saved.getGrantId().toString(),
                tenantId, podId, correlationId,
                Map.of("grantId", saved.getGrantId().toString(), "ticketId", ticketId.toString(),
                        "supportAssignmentId", supportAssignmentId.toString()));
        return saved;
    }

    @Transactional
    public SupportElevatedAccessGrantEntity approveElevation(UUID grantId, UUID tenantId, String podId,
                                                               String correlationId, String approvedBy,
                                                               int durationMinutes) {
        SupportElevatedAccessGrantEntity grant = elevationRepository.findById(grantId)
                .orElseThrow(() -> new NotFoundException("Elevation grant not found: " + grantId));
        if (!"PENDING_APPROVAL".equals(grant.getStatus())) {
            throw new InvalidElevationStateException("Elevation grant is not pending approval: " + grantId);
        }
        if (durationMinutes <= 0 || durationMinutes > MAX_ELEVATION_MINUTES) {
            throw new IllegalArgumentException(
                    "Elevation duration must be between 1 and " + MAX_ELEVATION_MINUTES + " minutes");
        }
        OffsetDateTime now = OffsetDateTime.now();
        grant.setApprovedBy(approvedBy);
        grant.setGrantedAt(now);
        grant.setExpiresAt(now.plusMinutes(durationMinutes));
        grant.setStatus("ACTIVE");
        SupportElevatedAccessGrantEntity saved = elevationRepository.save(grant);
        appendOutbox("impilo.support.elevation.approved.v1", "SupportElevatedAccessGrant", grantId.toString(),
                tenantId, podId, correlationId,
                Map.of("grantId", grantId.toString(), "approvedBy", approvedBy,
                        "expiresAt", saved.getExpiresAt().toString()));
        log.info("Elevated support access APPROVED [grantId={}, ticketId={}, expiresAt={}]",
                grantId, saved.getTicketId(), saved.getExpiresAt());
        return saved;
    }

    @Transactional
    public SupportElevatedAccessGrantEntity revokeElevation(UUID grantId, UUID tenantId, String podId,
                                                              String correlationId, String revokedBy, String reason) {
        SupportElevatedAccessGrantEntity grant = elevationRepository.findById(grantId)
                .orElseThrow(() -> new NotFoundException("Elevation grant not found: " + grantId));
        if (!List.of("ACTIVE", "PENDING_APPROVAL").contains(grant.getStatus())) {
            throw new InvalidElevationStateException("Elevation grant cannot be revoked from status " + grant.getStatus());
        }
        grant.setStatus("REVOKED");
        grant.setRevokedAt(OffsetDateTime.now());
        grant.setRevokedBy(revokedBy);
        grant.setRevocationReason(reason);
        SupportElevatedAccessGrantEntity saved = elevationRepository.save(grant);
        appendOutbox("impilo.support.elevation.revoked.v1", "SupportElevatedAccessGrant", grantId.toString(),
                tenantId, podId, correlationId, Map.of("grantId", grantId.toString(), "revokedBy", revokedBy));
        log.warn("Elevated support access REVOKED [grantId={}, ticketId={}, revokedBy={}]",
                grantId, saved.getTicketId(), revokedBy);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SupportElevatedAccessGrantEntity> activeElevations(UUID supportAssignmentId) {
        return elevationRepository.findBySupportAssignmentIdAndStatus(supportAssignmentId, "ACTIVE");
    }

    /** Maximum single-grant elevation window (§ A3: "maximum elevation duration"). */
    public static final int MAX_ELEVATION_MINUTES = 240;

    private void appendOutbox(String eventType, String aggregateType, String aggregateId,
                               UUID tenantId, String podId, String correlationId, Map<String, Object> payload) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setCorrelationId(correlationId != null ? safeUuid(correlationId) : null);
        outbox.setCausationId(correlationId != null ? safeUuid(correlationId) : null);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType(aggregateType);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(aggregateId);
        outboxRepository.save(outbox);
    }

    private static UUID safeUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
