package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Opinionated onboarding flows that compose organisations, units, facility/site links, and role catalogue entries.
 */
@Service
public class OnboardingWorkflowService {

    private final OrganisationService organisationService;
    private final OrganisationUnitService organisationUnitService;
    private final LinkageService linkageService;
    private final RoleCatalogService roleCatalogService;
    private final RoleDefinitionRepository roleDefinitionRepository;

    public OnboardingWorkflowService(OrganisationService organisationService,
                                     OrganisationUnitService organisationUnitService,
                                     LinkageService linkageService,
                                     RoleCatalogService roleCatalogService,
                                     RoleDefinitionRepository roleDefinitionRepository) {
        this.organisationService = organisationService;
        this.organisationUnitService = organisationUnitService;
        this.linkageService = linkageService;
        this.roleCatalogService = roleCatalogService;
        this.roleDefinitionRepository = roleDefinitionRepository;
    }

    @Transactional
    public OnboardingResult onboardSingleFacilityOrganisation(UUID tenantId,
                                                              String organisationCode,
                                                              String organisationName,
                                                              String legalName,
                                                              String organisationType,
                                                              Long facilityId,
                                                              boolean createDefaultUnit) {
        TrustContextHolder.require();
        ensureBaselineRoles(tenantId);
        OrganisationEntity org = organisationService.createOrganisation(
                tenantId, organisationCode, organisationName, legalName, organisationType, null, null);
        org = organisationService.updateStatus(org.getId(), GovernanceEnums.OrganisationStatus.ACTIVE);

        UUID unitId = null;
        if (createDefaultUnit) {
            OrganisationUnitEntity unit = organisationUnitService.createUnit(
                    tenantId, org.getId(), null, "HQ", "Head office", "HEAD_OFFICE");
            unitId = unit.getId();
        }
        linkageService.linkFacility(tenantId, facilityId, org.getId(), unitId, "OPERATES", true, null, null);
        return new OnboardingResult(org.getId(), unitId, List.of(facilityId));
    }

    @Transactional
    public OnboardingResult onboardMultiFacilityOrganisation(UUID tenantId,
                                                              String organisationCode,
                                                              String organisationName,
                                                              String legalName,
                                                              String organisationType,
                                                              List<Long> facilityIds,
                                                              boolean createDefaultUnit) {
        TrustContextHolder.require();
        ensureBaselineRoles(tenantId);
        OrganisationEntity org = organisationService.createOrganisation(
                tenantId, organisationCode, organisationName, legalName, organisationType, null, null);
        org = organisationService.updateStatus(org.getId(), GovernanceEnums.OrganisationStatus.ACTIVE);
        UUID unitId = null;
        if (createDefaultUnit) {
            OrganisationUnitEntity unit = organisationUnitService.createUnit(
                    tenantId, org.getId(), null, "HQ", "Head office", "HEAD_OFFICE");
            unitId = unit.getId();
        }
        boolean first = true;
        for (Long fid : facilityIds) {
            linkageService.linkFacility(tenantId, fid, org.getId(), unitId, "OPERATES", first, null, null);
            first = false;
        }
        return new OnboardingResult(org.getId(), unitId, facilityIds);
    }

    private void ensureBaselineRoles(UUID tenantId) {
        if (roleDefinitionRepository.findByTenantIdAndRoleCode(tenantId, "FACILITY_CLINICIAN").isEmpty()) {
            roleCatalogService.createRoleDefinition(
                    tenantId,
                    "FACILITY_CLINICIAN",
                    "Facility clinician",
                    "Clinical work anchored to a facility",
                    GovernanceEnums.RoleCategory.CLINICAL,
                    GovernanceEnums.RoleLevel.FACILITY_LEVEL,
                    List.of(GovernanceEnums.AssignmentTargetType.FACILITY,
                            GovernanceEnums.AssignmentTargetType.ORGANISATION,
                            GovernanceEnums.AssignmentTargetType.ORGANISATION_UNIT),
                    true,
                    true,
                    false);
        }
        if (roleDefinitionRepository.findByTenantIdAndRoleCode(tenantId, "DISTRICT_SUPERVISOR").isEmpty()) {
            roleCatalogService.createRoleDefinition(
                    tenantId,
                    "DISTRICT_SUPERVISOR",
                    "District supervisor",
                    "Above-facility supervisory scope",
                    GovernanceEnums.RoleCategory.SUPERVISORY,
                    GovernanceEnums.RoleLevel.JURISDICTIONAL,
                    List.of(GovernanceEnums.AssignmentTargetType.JURISDICTION,
                            GovernanceEnums.AssignmentTargetType.ORGANISATION,
                            GovernanceEnums.AssignmentTargetType.ORGANISATION_UNIT,
                            GovernanceEnums.AssignmentTargetType.MULTI_SITE_GROUP),
                    false,
                    false,
                    false);
        }
        if (roleDefinitionRepository.findByTenantIdAndRoleCode(tenantId, "PRACTITIONER_IN_CHARGE").isEmpty()) {
            roleCatalogService.createRoleDefinition(
                    tenantId,
                    "PRACTITIONER_IN_CHARGE",
                    "Practitioner in charge",
                    "Governed clinical accountability for a facility (paired with VARAPI PIC workflow)",
                    GovernanceEnums.RoleCategory.CLINICAL,
                    GovernanceEnums.RoleLevel.FACILITY_LEVEL,
                    List.of(GovernanceEnums.AssignmentTargetType.FACILITY),
                    true,
                    true,
                    true);
        }
    }

    public record OnboardingResult(UUID organisationId, UUID defaultUnitId, List<Long> linkedFacilityIds) {}
}
