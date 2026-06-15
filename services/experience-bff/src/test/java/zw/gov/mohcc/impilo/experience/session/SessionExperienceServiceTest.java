package zw.gov.mohcc.impilo.experience.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.bootstrap.BootstrapProperties;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.config.ProductOwnerAccessProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionExperienceServiceTest {

    @Mock
    private VarapiServiceClient varapiClient;

    @Mock
    private WorkforceGovernanceClient workforceGovernanceClient;

    private SessionExperienceService service;
    private ProductOwnerAccessProperties productOwnerAccessProperties;
    private BootstrapProperties bootstrapProperties;

    @BeforeEach
    void setUp() {
        productOwnerAccessProperties = new ProductOwnerAccessProperties();
        bootstrapProperties = new BootstrapProperties();
        bootstrapProperties.setEnvironment("preview");
        service = new SessionExperienceService(
                varapiClient,
                workforceGovernanceClient,
                new ObjectMapper(),
                productOwnerAccessProperties,
                bootstrapProperties
        );
    }

    @Test
    void buildExperienceContract_enrichesOrganisationTypeFromWgvWhenAssignmentLacksIt() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode provider = mapper.createObjectNode();
        provider.put("providerId", "PROV-ZW-ADMIN-001");
        provider.put("status", "ACTIVE");
        when(varapiClient.getProviderByHealthId("b0000000-0000-4000-8000-000000000010")).thenReturn(provider);

        ArrayNode assignments = mapper.createArrayNode();
        ObjectNode assignment = mapper.createObjectNode();
        assignment.put("organisationId", "f2000000-0000-4000-8000-000000000002");
        assignment.put("status", "ACTIVE");
        assignments.add(assignment);
        when(workforceGovernanceClient.searchAssignments("PROVIDER", "PROV-ZW-ADMIN-001", "ACTIVE"))
                .thenReturn(assignments);

        ObjectNode organisation = mapper.createObjectNode();
        organisation.put("organisationType", "SOVEREIGN_PUBLIC_OWNER");
        organisation.put("name", "Ministry of Health and Child Care");
        organisation.put("status", "ACTIVE");
        when(workforceGovernanceClient.getJson("/v1/internal/governance/organisations/f2000000-0000-4000-8000-000000000002"))
                .thenReturn(organisation);

        Map<String, Object> contract = service.buildExperienceContract(
                "b0000000-0000-4000-8000-000000000010", "email", null, false);

        @SuppressWarnings("unchecked")
        List<String> visible = (List<String>) contract.get("visibleManagementWorkspaces");
        assertTrue(visible.contains("national_platform_user_administration"));
        @SuppressWarnings("unchecked")
        Map<String, Object> org = (Map<String, Object>) contract.get("organisation");
        assertEquals("sovereign_public_owner", org.get("organisationType"));
    }

    @Test
    void buildExperienceContract_appliesProductOwnerOverrideForAllowlistedActor() throws Exception {
        productOwnerAccessProperties.setEnabled(true);
        when(varapiClient.getProviderByHealthId(anyString())).thenReturn(null);

        Map<String, Object> contract = service.buildExperienceContract(
                "b0000000-0000-4000-8000-000000000010", "email", null, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> tabs = (Map<String, Object>) contract.get("tabs");
        @SuppressWarnings("unchecked")
        Map<String, Object> work = (Map<String, Object>) tabs.get("work");
        assertEquals(true, work.get("visible"));

        @SuppressWarnings("unchecked")
        List<String> visible = (List<String>) contract.get("visibleManagementWorkspaces");
        assertTrue(visible.contains("national_platform_user_administration"));
        assertTrue(visible.contains("municipal_user_management"));

        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) contract.get("policyMetadata");
        assertEquals(true, policy.get("previewProductOwnerAccess"));
    }

    @Test
    void buildExperienceContract_appliesProductOwnerOverrideForAllowlistedEmail() throws Exception {
        productOwnerAccessProperties.setEnabled(true);
        when(varapiClient.getProviderByHealthId(anyString())).thenReturn(null);

        Map<String, Object> contract = service.buildExperienceContract(
                "f9697886-ad9c-4b24-87e8-9040944b2d65", "email", null, false,
                "superadmin@impilo.gov.zw");

        @SuppressWarnings("unchecked")
        Map<String, Object> tabs = (Map<String, Object>) contract.get("tabs");
        @SuppressWarnings("unchecked")
        Map<String, Object> work = (Map<String, Object>) tabs.get("work");
        assertEquals(true, work.get("visible"));

        @SuppressWarnings("unchecked")
        List<String> visible = (List<String>) contract.get("visibleManagementWorkspaces");
        assertTrue(visible.contains("national_platform_user_administration"));
    }

    @Test
    void buildExperienceContract_doesNotApplyProductOwnerOverrideInProduction() throws Exception {
        productOwnerAccessProperties.setEnabled(true);
        bootstrapProperties.setEnvironment("production");
        when(varapiClient.getProviderByHealthId(anyString())).thenReturn(null);

        Map<String, Object> contract = service.buildExperienceContract(
                "b0000000-0000-4000-8000-000000000010", "email", null, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) contract.get("policyMetadata");
        assertNull(policy.get("previewProductOwnerAccess"));
    }
}
