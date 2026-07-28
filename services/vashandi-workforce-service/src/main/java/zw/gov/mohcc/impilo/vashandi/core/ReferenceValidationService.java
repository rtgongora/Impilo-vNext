package zw.gov.mohcc.impilo.vashandi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.integration.TusoIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.WorkforceGovernanceIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ReferenceQuarantineEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ReferenceQuarantineRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * A5: soft-validates an assignment's free-text departmentId/programmeId
 * against the canonical registries now that they exist (tuso
 * .facility_department V051, wgv_programme V014). Deliberately NEVER blocks
 * the assignment and NEVER guesses — every outcome is one of NOT_APPLICABLE
 * (nothing to check) / UNVERIFIED (not yet checked) / VALIDATED /
 * UNVALIDATED (quarantined) / UNVERIFIABLE (the registry was unreachable).
 *
 * <p>Called from {@link WorkforceAssignmentService#precheck} as a
 * best-effort side-effect — a failure here must never fail the precheck
 * transaction.</p>
 */
@Service
public class ReferenceValidationService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceValidationService.class);

    private final TusoIntegrationClient tusoClient;
    private final WorkforceGovernanceIntegrationClient governanceClient;
    private final WorkforceAssignmentRepository assignmentRepository;
    private final ReferenceQuarantineRepository quarantineRepository;

    public ReferenceValidationService(TusoIntegrationClient tusoClient,
                                       WorkforceGovernanceIntegrationClient governanceClient,
                                       WorkforceAssignmentRepository assignmentRepository,
                                       ReferenceQuarantineRepository quarantineRepository) {
        this.tusoClient = tusoClient;
        this.governanceClient = governanceClient;
        this.assignmentRepository = assignmentRepository;
        this.quarantineRepository = quarantineRepository;
    }

    @Transactional
    public void validate(WorkforceAssignmentEntity assignment) {
        try {
            validateDepartment(assignment);
        } catch (Exception e) {
            log.warn("Department reference validation failed for assignment {}: {}", assignment.getId(), e.getMessage());
        }
        try {
            validateProgramme(assignment);
        } catch (Exception e) {
            log.warn("Programme reference validation failed for assignment {}: {}", assignment.getId(), e.getMessage());
        }
        assignmentRepository.save(assignment);
    }

    private void validateDepartment(WorkforceAssignmentEntity assignment) {
        String departmentId = assignment.getDepartmentId();
        if (departmentId == null || departmentId.isBlank()) {
            assignment.setDepartmentRefStatus("NOT_APPLICABLE");
            return;
        }
        if (assignment.getFacilityId() == null) {
            // A department code is meaningless without a facility to scope it to.
            assignment.setDepartmentRefStatus("UNVERIFIABLE");
            return;
        }
        Optional<Boolean> known = tusoClient.departmentKnown(assignment.getFacilityId(), departmentId);
        if (known.isEmpty()) {
            assignment.setDepartmentRefStatus("UNVERIFIABLE");
            return;
        }
        if (known.get()) {
            assignment.setDepartmentRefStatus("VALIDATED");
        } else {
            assignment.setDepartmentRefStatus("UNVALIDATED");
            quarantine(assignment, "DEPARTMENT_ID", departmentId);
        }
    }

    private void validateProgramme(WorkforceAssignmentEntity assignment) {
        String programmeId = assignment.getProgrammeId();
        if (programmeId == null || programmeId.isBlank()) {
            assignment.setProgrammeRefStatus("NOT_APPLICABLE");
            return;
        }
        Optional<Boolean> known = governanceClient.programmeKnown(programmeId);
        if (known.isEmpty()) {
            assignment.setProgrammeRefStatus("UNVERIFIABLE");
            return;
        }
        if (known.get()) {
            assignment.setProgrammeRefStatus("VALIDATED");
        } else {
            assignment.setProgrammeRefStatus("UNVALIDATED");
            quarantine(assignment, "PROGRAMME_ID", programmeId);
        }
    }

    private void quarantine(WorkforceAssignmentEntity assignment, String field, String sourceValue) {
        boolean alreadyQuarantined = quarantineRepository.findByWorkforceAssignmentId(assignment.getId()).stream()
                .anyMatch(q -> field.equals(q.getReferenceField()) && sourceValue.equals(q.getSourceValue()) && !q.isResolved());
        if (alreadyQuarantined) {
            return;
        }
        ReferenceQuarantineEntity q = new ReferenceQuarantineEntity();
        q.setId(UUID.randomUUID());
        q.setTenantId(assignment.getTenantId());
        q.setWorkforceAssignmentId(assignment.getId());
        q.setReferenceField(field);
        q.setSourceValue(sourceValue);
        quarantineRepository.save(q);
        log.warn("Quarantined unresolved {} '{}' on assignment {}", field, sourceValue, assignment.getId());
    }
}
