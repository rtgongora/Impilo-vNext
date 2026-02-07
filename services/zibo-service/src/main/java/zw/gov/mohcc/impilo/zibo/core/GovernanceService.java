package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.domain.ScopeType;
import zw.gov.mohcc.impilo.zibo.domain.ValidationSeverity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.AssignmentEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.PackEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationLogEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.AssignmentRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.PackRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationLogRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages terminology pack assignments and provides validation analytics.
 *
 * <p>Assignments bind terminology packs to scopes (tenant, facility, or
 * workspace), optionally with a policy mode override. This enables
 * hierarchical governance where different facilities or workspaces can
 * have different terminology requirements.</p>
 *
 * <p>Also provides analytics over validation logs, including top failure
 * rankings and aggregate statistics by severity.</p>
 */
@Service
public class GovernanceService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceService.class);

    private final AssignmentRepository assignmentRepository;
    private final PackRepository packRepository;
    private final ValidationLogRepository validationLogRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public GovernanceService(AssignmentRepository assignmentRepository,
                             PackRepository packRepository,
                             ValidationLogRepository validationLogRepository,
                             EventOutboxRepository outboxRepository,
                             ObjectMapper objectMapper) {
        this.assignmentRepository = assignmentRepository;
        this.packRepository = packRepository;
        this.validationLogRepository = validationLogRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Assign a terminology pack to a scope (tenant, facility, or workspace).
     *
     * <p>If an assignment for the same scope + pack combination already exists,
     * it is updated with the new policy mode and reactivated. Otherwise a new
     * assignment is created.</p>
     *
     * @param scopeType   the type of scope (TENANT, FACILITY, WORKSPACE)
     * @param scopeId     the identifier of the scope entity
     * @param packId      the pack to assign
     * @param packVersion the version of the pack being assigned
     * @param policyMode  the policy mode for this assignment (STRICT or LENIENT)
     * @return the created or updated assignment entity
     * @throws IllegalArgumentException if the pack is not found or its version does not match
     */
    @Transactional
    public AssignmentEntity assignPack(ScopeType scopeType, UUID scopeId, UUID packId,
                                       String packVersion, PolicyMode policyMode) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        // Validate pack exists and version matches
        PackEntity pack = packRepository.findByTenantIdAndPackId(tenantId, packId)
                .orElseThrow(() -> new IllegalArgumentException("Pack not found: " + packId));

        if (!pack.getVersion().equals(packVersion)) {
            throw new IllegalArgumentException(
                    "Pack version mismatch: expected '" + packVersion + "' but found '" + pack.getVersion() + "'");
        }

        // Check for existing assignment (same scope + pack)
        Optional<AssignmentEntity> existingOpt =
                assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndPackId(
                        tenantId, scopeType, scopeId, packId);

        AssignmentEntity assignment;
        if (existingOpt.isPresent()) {
            // Update existing assignment
            assignment = existingOpt.get();
            assignment.setPackVersion(packVersion);
            assignment.setPolicyMode(policyMode);
            assignment.setActive(true);
            log.info("Updating existing assignment: id={}, scope={}:{}, pack={}",
                    assignment.getId(), scopeType, scopeId, packId);
        } else {
            // Create new assignment
            assignment = new AssignmentEntity();
            assignment.setId(UUID.randomUUID());
            assignment.setTenantId(tenantId);
            assignment.setScopeType(scopeType);
            assignment.setScopeId(scopeId);
            assignment.setPackId(packId);
            assignment.setPackVersion(packVersion);
            assignment.setPolicyMode(policyMode);
            assignment.setActive(true);
            assignment.setAssignedBy(ctx.actorId());
            assignment.setCreatedAt(OffsetDateTime.now());
        }

        assignment = assignmentRepository.save(assignment);

        // Write outbox event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "PACK_ASSIGNED");
        payload.put("assignmentId", assignment.getId().toString());
        payload.put("tenantId", tenantId.toString());
        payload.put("scopeType", scopeType.name());
        payload.put("scopeId", scopeId.toString());
        payload.put("packId", packId.toString());
        payload.put("packVersion", packVersion);
        payload.put("policyMode", policyMode.name());
        payload.put("assignedBy", ctx.actorId());
        payload.put("timestamp", OffsetDateTime.now().toString());
        writeOutbox("ASSIGNMENT", assignment.getId().toString(), "PACK_ASSIGNED", toJson(payload));

        log.info("Pack assigned: assignmentId={}, scope={}:{}, pack={}, version={}, mode={}",
                assignment.getId(), scopeType, scopeId, packId, packVersion, policyMode);

        return assignment;
    }

    /**
     * Deactivate a pack assignment.
     *
     * <p>Sets the assignment's active flag to {@code false}. The assignment
     * record is retained for audit purposes.</p>
     *
     * @param assignmentId the assignment to deactivate
     * @throws IllegalArgumentException if the assignment is not found for this tenant
     */
    @Transactional
    public void unassignPack(UUID assignmentId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .filter(a -> tenantId.equals(a.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Assignment not found: " + assignmentId + " for tenant " + tenantId));

        assignment.setActive(false);
        assignmentRepository.save(assignment);

        log.info("Pack unassigned: assignmentId={}, scope={}:{}, pack={}",
                assignmentId, assignment.getScopeType(), assignment.getScopeId(), assignment.getPackId());
    }

    /**
     * Retrieve all assignments for a given scope.
     *
     * @param scopeType the type of scope
     * @param scopeId   the identifier of the scope entity
     * @return a list of assignments for the scope (may include inactive)
     */
    @Transactional(readOnly = true)
    public List<AssignmentEntity> getAssignments(ScopeType scopeType, UUID scopeId) {
        TrustContext ctx = TrustContextHolder.require();
        return assignmentRepository.findByTenantIdAndScopeTypeAndScopeId(
                ctx.tenantId(), scopeType, scopeId);
    }

    /**
     * Retrieve paginated validation logs with optional severity and facility filters.
     *
     * @param severity   optional severity filter; if {@code null}, all severities returned
     * @param facilityId optional facility filter; if {@code null}, all facilities returned
     * @param pageable   pagination parameters
     * @return a page of validation log entities
     */
    @Transactional(readOnly = true)
    public Page<ValidationLogEntity> getValidationLogs(ValidationSeverity severity,
                                                        UUID facilityId, Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        if (severity != null) {
            return validationLogRepository.findByTenantIdAndSeverity(tenantId, severity, pageable);
        } else if (facilityId != null) {
            return validationLogRepository.findByTenantIdAndFacilityId(tenantId, facilityId, pageable);
        }
        return validationLogRepository.findByTenantId(tenantId, pageable);
    }

    /**
     * Retrieve the most common validation failures ranked by frequency.
     *
     * <p>Uses the aggregated {@code findTopFailures} repository query to
     * group by issue code and return counts ordered descending.</p>
     *
     * @param topN  the maximum number of results to return
     * @param since only include log entries created after this timestamp
     * @return a ranked list of failure summaries, ordered by frequency descending
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopValidationFailures(int topN, OffsetDateTime since) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        OffsetDateTime effectiveSince = since != null ? since : OffsetDateTime.now().minusDays(30);

        List<Object[]> rows = validationLogRepository.findTopFailures(tenantId, effectiveSince);

        return rows.stream()
                .limit(topN)
                .map(row -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("issueCode", row[0]);
                    result.put("count", row[1]);
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * Retrieve validation statistics for a facility, grouped by severity.
     *
     * @param facilityId the facility to compute stats for
     * @param since      only include log entries created after this timestamp; may be {@code null}
     * @return a map of severity to count (e.g. {"ERROR": 42, "WARNING": 108, "INFORMATION": 5})
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getValidationStats(UUID facilityId, OffsetDateTime since) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        Page<ValidationLogEntity> logsPage;
        if (facilityId != null) {
            logsPage = validationLogRepository.findByTenantIdAndFacilityId(
                    tenantId, facilityId, Pageable.unpaged());
        } else {
            logsPage = validationLogRepository.findByTenantId(tenantId, Pageable.unpaged());
        }

        List<ValidationLogEntity> logs = logsPage.getContent();

        // Filter by time window
        List<ValidationLogEntity> filtered = logs.stream()
                .filter(l -> since == null || (l.getCreatedAt() != null && l.getCreatedAt().isAfter(since)))
                .toList();

        // Group by severity
        Map<String, Long> stats = new LinkedHashMap<>();
        for (ValidationSeverity sev : ValidationSeverity.values()) {
            long count = filtered.stream()
                    .filter(l -> sev.equals(l.getSeverity()))
                    .count();
            stats.put(sev.name(), count);
        }

        return stats;
    }

    // ------------------------------------------------------------------
    // Internal helpers
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise outbox payload: {}", e.getMessage());
            return "{}";
        }
    }
}
