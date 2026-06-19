package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkforceAssignmentService {

    private final WorkforceAssignmentRepository assignmentRepository;
    private final WorkforceEligibilityService eligibilityService;
    private final VashandiOutboxWriter outboxWriter;

    public WorkforceAssignmentService(WorkforceAssignmentRepository assignmentRepository,
                                      WorkforceEligibilityService eligibilityService,
                                      VashandiOutboxWriter outboxWriter) {
        this.assignmentRepository = assignmentRepository;
        this.eligibilityService = eligibilityService;
        this.outboxWriter = outboxWriter;
    }

    public List<WorkforceAssignmentEntity> list(UUID tenantId) {
        return assignmentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public Optional<WorkforceAssignmentEntity> get(UUID tenantId, UUID id) {
        return assignmentRepository.findByTenantIdAndId(tenantId, id);
    }

    @Transactional
    public WorkforceAssignmentEntity create(UUID tenantId, VashandiDtos.CreateAssignmentRequest request)
            throws Exception {
        WorkforceAssignmentEntity assignment = new WorkforceAssignmentEntity();
        assignment.setTenantId(tenantId);
        assignment.setWorkforceProfileId(request.workforceProfileId());
        assignment.setAssignmentType(request.assignmentType());
        assignment.setOrganisationId(request.organisationId());
        assignment.setFacilityId(request.facilityId());
        assignment.setDepartmentId(request.departmentId());
        assignment.setUnitId(request.unitId());
        assignment.setProgrammeId(request.programmeId());
        assignment.setWorkspaceId(request.workspaceId());
        assignment.setRoleTemplateId(request.roleTemplateId());
        assignment.setSupervisorProfileId(request.supervisorProfileId());
        assignment.setStartDate(request.startDate());
        assignment.setEndDate(request.endDate());
        assignment.setSourceAuthority(request.sourceAuthority());
        assignment.setStatus("requested");
        assignment.setCreatedBy(actorId());
        WorkforceAssignmentEntity saved = assignmentRepository.save(assignment);
        emit(tenantId, saved, "requested", "vashandi:assignment:requested:" + saved.getId());
        return saved;
    }

    @Transactional
    public VashandiDtos.AssignmentActionResponse precheck(UUID tenantId, UUID assignmentId, String opaDecisionId)
            throws Exception {
        WorkforceAssignmentEntity assignment = require(tenantId, assignmentId);
        VashandiDtos.WorkforceEligibilityResult eligibility = eligibilityService.evaluate(assignment, opaDecisionId);
        assignment.setEligibilityStatus(eligibility.overallStatus());
        assignment.setOpaDecisionId(opaDecisionId);
        assignment.setStatus("prechecked");
        assignmentRepository.save(assignment);
        emit(tenantId, assignment, "prechecked", "vashandi:assignment:prechecked:" + assignmentId);
        return actionResponse(assignment, eligibility.overallStatus(), eligibility);
    }

    @Transactional
    public VashandiDtos.AssignmentActionResponse approve(UUID tenantId, UUID assignmentId) throws Exception {
        WorkforceAssignmentEntity assignment = require(tenantId, assignmentId);
        assignment.setStatus("approved");
        assignment.setApprovedBy(actorId());
        assignmentRepository.save(assignment);
        emit(tenantId, assignment, "approved", "vashandi:assignment:approved:" + assignmentId);
        return actionResponse(assignment, "submitted", null);
    }

    @Transactional
    public VashandiDtos.AssignmentActionResponse activate(UUID tenantId, UUID assignmentId, String opaDecisionId)
            throws Exception {
        WorkforceAssignmentEntity assignment = require(tenantId, assignmentId);
        VashandiDtos.WorkforceEligibilityResult eligibility = eligibilityService.evaluate(assignment, opaDecisionId);
        if (!"allowed".equals(eligibility.overallStatus())) {
            return actionResponse(assignment, eligibility.overallStatus(), eligibility);
        }
        assignment.setStatus("active");
        assignment.setEligibilityStatus("allowed");
        assignment.setOpaDecisionId(opaDecisionId);
        assignmentRepository.save(assignment);
        emit(tenantId, assignment, "activated", "vashandi:assignment:activated:" + assignmentId);
        return actionResponse(assignment, "completed", eligibility);
    }

    @Transactional
    public VashandiDtos.AssignmentActionResponse suspend(UUID tenantId, UUID assignmentId) throws Exception {
        WorkforceAssignmentEntity assignment = require(tenantId, assignmentId);
        assignment.setStatus("suspended");
        assignmentRepository.save(assignment);
        emit(tenantId, assignment, "suspended", "vashandi:assignment:suspended:" + assignmentId);
        return actionResponse(assignment, "completed", null);
    }

    @Transactional
    public VashandiDtos.AssignmentActionResponse end(UUID tenantId, UUID assignmentId) throws Exception {
        WorkforceAssignmentEntity assignment = require(tenantId, assignmentId);
        assignment.setStatus("ended");
        assignmentRepository.save(assignment);
        emit(tenantId, assignment, "ended", "vashandi:assignment:ended:" + assignmentId);
        return actionResponse(assignment, "completed", null);
    }

    @Transactional
    public WorkforceAssignmentEntity update(UUID tenantId, UUID id, VashandiDtos.UpdateAssignmentRequest request) {
        WorkforceAssignmentEntity assignment = require(tenantId, id);
        if (request.departmentId() != null) {
            assignment.setDepartmentId(request.departmentId());
        }
        if (request.unitId() != null) {
            assignment.setUnitId(request.unitId());
        }
        if (request.endDate() != null) {
            assignment.setEndDate(request.endDate());
        }
        if (request.roleTemplateId() != null) {
            assignment.setRoleTemplateId(request.roleTemplateId());
        }
        return assignmentRepository.save(assignment);
    }

    private WorkforceAssignmentEntity require(UUID tenantId, UUID id) {
        return assignmentRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("assignment not found"));
    }

    private void emit(UUID tenantId, WorkforceAssignmentEntity assignment, String action, String idempotencyKey)
            throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("assignmentId", assignment.getId().toString());
        payload.put("status", assignment.getStatus());
        outboxWriter.publish(tenantId, "WORKFORCE_ASSIGNMENT", assignment.getId().toString(),
                "assignment", action, idempotencyKey, payload);
    }

    private VashandiDtos.AssignmentActionResponse actionResponse(WorkforceAssignmentEntity assignment,
                                                                 String status,
                                                                 VashandiDtos.WorkforceEligibilityResult eligibility) {
        return new VashandiDtos.AssignmentActionResponse(assignment.getId(), assignment.getStatus(), status, eligibility);
    }

    private String actorId() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (IllegalStateException ex) {
            return "system";
        }
    }
}
