package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentStatusHistoryRepository historyRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final GovernanceEventService governanceEventService;
    private final ObjectMapper objectMapper;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             AssignmentStatusHistoryRepository historyRepository,
                             RoleDefinitionRepository roleDefinitionRepository,
                             GovernanceEventService governanceEventService,
                             ObjectMapper objectMapper) {
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
        this.roleDefinitionRepository = roleDefinitionRepository;
        this.governanceEventService = governanceEventService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssignmentEntity createAssignment(UUID tenantId,
                                             GovernanceEnums.AssignmentSubjectType subjectType,
                                             String subjectId,
                                             UUID roleDefinitionId,
                                             GovernanceEnums.AssignmentTargetType targetType,
                                             String targetId,
                                             UUID organisationId,
                                             UUID organisationUnitId,
                                             UUID jurisdictionId,
                                             GovernanceEnums.AssignmentStatus initialStatus,
                                             boolean primary,
                                             boolean secondary,
                                             String scopeSummary,
                                             String authorityLevel,
                                             String reportingLineRef,
                                             String notes,
                                             String extensionJson) {
        TrustContext ctx = TrustContextHolder.require();
        RoleDefinitionEntity role = roleDefinitionRepository.findById(roleDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("Role definition not found"));
        if (!role.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Role definition tenant mismatch");
        }
        if (!role.isActiveFlag()) {
            throw new IllegalArgumentException("Role definition inactive");
        }
        assertTargetAllowed(role, targetType);

        AssignmentEntity a = new AssignmentEntity(
                UUID.randomUUID(),
                tenantId,
                subjectType.name(),
                subjectId,
                roleDefinitionId,
                targetType.name(),
                targetId,
                initialStatus.name());
        a.setOrganisationId(organisationId);
        a.setOrganisationUnitId(organisationUnitId);
        a.setJurisdictionId(jurisdictionId);
        a.setPrimaryFlag(primary);
        a.setSecondaryFlag(secondary);
        a.setScopeSummary(scopeSummary);
        a.setAuthorityLevel(authorityLevel);
        a.setReportingLineRef(reportingLineRef);
        a.setNotes(notes);
        a.setExtensionJson(extensionJson);
        a = assignmentRepository.save(a);
        historyRepository.save(new AssignmentStatusHistoryEntity(
                UUID.randomUUID(), tenantId, a.getId(), null, initialStatus.name(),
                ctx.actorId(), "created"));
        governanceEventService.enqueue("ASSIGNMENT", a.getId().toString(), "impilo.governance.assignment.created",
                Map.of("assignmentId", a.getId().toString(), "subjectType", subjectType.name(), "subjectId", subjectId,
                        "targetType", targetType.name(), "targetId", targetId, "status", a.getStatus()),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return a;
    }

    private void assertTargetAllowed(RoleDefinitionEntity role, GovernanceEnums.AssignmentTargetType targetType) {
        try {
            JsonNode arr = objectMapper.readTree(role.getAllowedTargetTypes());
            if (!arr.isArray()) {
                throw new IllegalArgumentException("Role definition allowed_target_types must be a JSON array");
            }
            boolean ok = false;
            for (JsonNode n : arr) {
                if (targetType.name().equals(n.asText())) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw new IllegalArgumentException("Target type " + targetType + " not permitted for role " + role.getRoleCode());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid allowed_target_types on role definition", e);
        }
    }

    @Transactional
    public AssignmentEntity transition(UUID assignmentId, GovernanceEnums.AssignmentStatus newStatus, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        AssignmentEntity a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        String prev = a.getStatus();
        a.setStatus(newStatus.name());
        a = assignmentRepository.save(a);
        historyRepository.save(new AssignmentStatusHistoryEntity(
                UUID.randomUUID(), a.getTenantId(), a.getId(), prev, newStatus.name(), ctx.actorId(), reason));
        String event = switch (newStatus) {
            case ACTIVE -> "impilo.governance.assignment.activated";
            case TERMINATED, EXPIRED, INACTIVE, SUSPENDED -> "impilo.governance.assignment.terminated";
            default -> "impilo.governance.assignment.updated";
        };
        governanceEventService.enqueue("ASSIGNMENT", a.getId().toString(), event,
                Map.of("assignmentId", a.getId().toString(), "previousStatus", String.valueOf(prev), "newStatus", newStatus.name()),
                a.getTenantId(), ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return a;
    }

    public List<AssignmentStatusHistoryEntity> assignmentHistory(UUID assignmentId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        AssignmentEntity a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        if (!a.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Assignment tenant mismatch");
        }
        return historyRepository.findByAssignmentIdOrderByChangedAtDesc(assignmentId);
    }

    public List<AssignmentEntity> search(UUID tenantId,
                                         String subjectType,
                                         String subjectId,
                                         String status) {
        if (subjectType != null && subjectId != null) {
            List<AssignmentEntity> rows = assignmentRepository
                    .findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(tenantId, subjectType, subjectId);
            if (status == null) {
                return rows;
            }
            List<AssignmentEntity> out = new ArrayList<>();
            for (AssignmentEntity r : rows) {
                if (status.equals(r.getStatus())) {
                    out.add(r);
                }
            }
            return out;
        }
        if (status != null) {
            return assignmentRepository.findByTenantIdAndStatus(tenantId, status);
        }
        return assignmentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}
