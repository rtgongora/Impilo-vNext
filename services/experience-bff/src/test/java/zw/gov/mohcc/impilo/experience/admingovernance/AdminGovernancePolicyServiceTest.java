package zw.gov.mohcc.impilo.experience.admingovernance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminGovernancePolicyServiceTest {

    private final AdminGovernancePolicyService policyService = new AdminGovernancePolicyService();

    @Test
    void deniesFacilityManagerCreatingProviderId() {
        var decision = policyService.evaluate(baseContract(List.of("public_facility_staff_management")), new AdminGovernanceDtos.PrecheckRequest(
                "FACILITY_STAFF_ASSIGN",
                "/work/facility/F1/staff-access/add",
                null,
                "P-1",
                null,
                null,
                null,
                null,
                null,
                null,
                "MEDIUM",
                Map.of("intent", "CREATE_PROVIDER_ID")
        ));
        assertFalse(decision.allowed());
    }

    @Test
    void deniesCouncilFacilityWorkAssignment() {
        var decision = policyService.evaluate(baseContract(List.of("council_user_management")), new AdminGovernanceDtos.PrecheckRequest(
                "REGULATOR_USER_CREATE",
                "/work/regulators/mdpc/users",
                "org-mdpc",
                "P-1",
                null,
                null,
                null,
                null,
                null,
                null,
                "MEDIUM",
                Map.of("intent", "FACILITY_WORK_ASSIGN")
        ));
        assertFalse(decision.allowed());
    }

    @Test
    void allowsHscUserManagementWithinScope() {
        var decision = policyService.evaluate(baseContract(List.of("hsc_user_management")), new AdminGovernanceDtos.PrecheckRequest(
                "ONBOARD_HSC_USER",
                "/work/administration-governance/onboard/hsc-user",
                "org-hsc-zw",
                "P-HSC",
                "hsc_workforce_governance_officer",
                "national",
                null,
                null,
                null,
                null,
                "LOW",
                Map.of()
        ));
        assertTrue(decision.allowed());
    }

    @Test
    void deniesBulkImportWithoutUploaderWorkspace() {
        var decision = policyService.evaluate(baseContract(List.of("national_organisation_registry")), new AdminGovernanceDtos.PrecheckRequest(
                "BULK_IMPORT_ORGANISATION_USERS",
                "/work/administration-governance/organisations/org-1/data-uploads",
                "org-1",
                "P-1",
                null,
                null,
                null,
                null,
                null,
                null,
                "MEDIUM",
                Map.of("importType", "organisation_users")
        ));
        assertFalse(decision.allowed());
    }

    @Test
    void allowsBulkImportForOrganisationDataUploader() {
        var decision = policyService.evaluate(baseContract(List.of("organisation_data_uploader")), new AdminGovernanceDtos.PrecheckRequest(
                "BULK_IMPORT_ORGANISATION_USERS",
                "/work/administration-governance/organisations/org-1/data-uploads",
                "org-1",
                "P-1",
                null,
                null,
                null,
                null,
                null,
                null,
                "MEDIUM",
                Map.of("importType", "organisation_users")
        ));
        assertTrue(decision.allowed());
    }

    @Test
    void deniesAuthorisedRepresentativeSelfEscalation() {
        var decision = policyService.evaluate(baseContract(List.of("organisation_administrator")), new AdminGovernanceDtos.PrecheckRequest(
                "AUTHORISED_REPRESENTATIVE_INVITE",
                "/work/administration-governance/organisations/org-1/users",
                "org-1",
                "P-SELF",
                "national_platform_administrator",
                null,
                null,
                null,
                null,
                null,
                "HIGH",
                Map.of("targetUserId", "P-SELF", "intent", "SELF_ESCALATE")
        ));
        assertFalse(decision.allowed());
    }

    private static Map<String, Object> baseContract(List<String> workspaces) {
        return Map.of(
                "visibleManagementWorkspaces", workspaces,
                "visibleWorkspaces", List.of(),
                "tabs", Map.of("work", Map.of("visible", true))
        );
    }
}
