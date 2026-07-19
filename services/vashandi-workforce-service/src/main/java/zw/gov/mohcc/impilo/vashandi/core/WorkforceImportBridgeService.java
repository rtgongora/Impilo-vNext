package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceMembershipEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceMembershipRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WorkforceImportBridgeService {

    private final WorkforceProfileService profileService;
    private final WorkforceAssignmentService assignmentService;
    private final WorkforceProfileRepository profileRepository;
    private final WorkforceMembershipRepository membershipRepository;

    public WorkforceImportBridgeService(WorkforceProfileService profileService,
                                        WorkforceAssignmentService assignmentService,
                                        WorkforceProfileRepository profileRepository,
                                        WorkforceMembershipRepository membershipRepository) {
        this.profileService = profileService;
        this.assignmentService = assignmentService;
        this.profileRepository = profileRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public VashandiDtos.ImportBridgeResponse importRows(UUID tenantId, VashandiDtos.ImportBridgeRequest request)
            throws Exception {
        List<UUID> profileIds = new ArrayList<>();
        List<UUID> assignmentIds = new ArrayList<>();

        for (VashandiDtos.ImportWorkforceRow row : request.rows()) {
            WorkforceProfileEntity profile = profileRepository
                    .findByTenantIdAndHealthId(tenantId, row.healthId())
                    .orElseGet(() -> {
                        try {
                            return profileService.create(tenantId, new VashandiDtos.CreateWorkforceProfileRequest(
                                    row.healthId(),
                                    row.providerWorkerId(),
                                    row.keycloakUserId(),
                                    row.organisationId(),
                                    row.organisationId(),
                                    row.workerType(),
                                    row.profession(),
                                    row.cadre(),
                                    row.currentStatus()
                            ));
                        } catch (Exception e) {
                            throw new IllegalStateException("profile import failed", e);
                        }
                    });
            profileIds.add(profile.getId());

            if (row.organisationId() != null) {
                WorkforceMembershipEntity membership = new WorkforceMembershipEntity();
                membership.setTenantId(tenantId);
                membership.setWorkforceProfileId(profile.getId());
                membership.setOrganisationId(row.organisationId());
                membership.setOrganisationType(row.organisationType() != null ? row.organisationType() : "FACILITY");
                membership.setMembershipType(row.membershipType() != null ? row.membershipType() : "EMPLOYEE");
                membership.setStatus("active");
                membership.setEffectiveDate(LocalDate.now());
                membership.setSource(request.source());
                membershipRepository.save(membership);
            }

            if (row.assignmentType() != null) {
                WorkforceAssignmentEntity assignment = assignmentService.create(tenantId,
                        new VashandiDtos.CreateAssignmentRequest(
                                profile.getId(),
                                row.assignmentType(),
                                row.organisationId(),
                                row.facilityId(),
                                row.departmentId(),
                                null,
                                null,
                                null,
                                row.roleTemplateId(),
                                null,
                                LocalDate.now(),
                                null,
                                request.source(),
                                null // engagementType — unknown at bulk import; set on later postings
                        ));
                assignmentIds.add(assignment.getId());
            }
        }

        return new VashandiDtos.ImportBridgeResponse(
                profileIds.size(),
                assignmentIds.size(),
                profileIds,
                assignmentIds,
                "submitted"
        );
    }
}
