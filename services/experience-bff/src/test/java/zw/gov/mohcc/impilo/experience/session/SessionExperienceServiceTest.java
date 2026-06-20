package zw.gov.mohcc.impilo.experience.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.vashandi.VashandiSessionContextResolver;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionExperienceServiceTest {

    @Mock
    private VarapiServiceClient varapiClient;

    @Mock
    private WorkforceGovernanceClient workforceGovernanceClient;

    @Mock
    private VashandiSessionContextResolver vashandiSessionContextResolver;

    private SessionExperienceService service;

    @BeforeEach
    void setUp() {
        service = new SessionExperienceService(
                varapiClient,
                workforceGovernanceClient,
                vashandiSessionContextResolver,
                new ObjectMapper()
        );
        when(vashandiSessionContextResolver.applyToContract(any(), anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
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
    void buildExperienceContract_resolvesCitizenWhenNoProviderOrAssignment() {
        when(varapiClient.getProviderByHealthId("citizen-only")).thenReturn(null);

        Map<String, Object> contract = service.buildExperienceContract(
                "citizen-only", "email", null, false, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> identity = (Map<String, Object>) contract.get("identity");
        assertEquals("citizen", identity.get("identityType"));

        @SuppressWarnings("unchecked")
        Map<String, Object> tabs = (Map<String, Object>) contract.get("tabs");
        @SuppressWarnings("unchecked")
        Map<String, Object> work = (Map<String, Object>) tabs.get("work");
        assertEquals(false, work.get("visible"));

        @SuppressWarnings("unchecked")
        List<String> visible = (List<String>) contract.get("visibleManagementWorkspaces");
        assertTrue(visible.isEmpty());

        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) contract.get("policyMetadata");
        assertNull(policy.get("previewProductOwnerAccess"));
        assertEquals("1.3.0", policy.get("contractVersion"));
    }

    @Test
    void buildExperienceContract_readsRoleTemplateIdFromAssignmentExtensionJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode provider = mapper.createObjectNode();
        provider.put("providerId", "PROV-ZW-VASH-002");
        provider.put("status", "ACTIVE");
        when(varapiClient.getProviderByHealthId("c0000000-0000-4000-8000-000000000102")).thenReturn(provider);

        ArrayNode assignments = mapper.createArrayNode();
        ObjectNode assignment = mapper.createObjectNode();
        assignment.put("organisationId", "f2000000-0000-4000-8000-000000000001");
        assignment.put("status", "ACTIVE");
        assignment.put("extensionJson", "{\"roleTemplateId\":\"vashandi_facility_workforce_manager\"}");
        assignments.add(assignment);
        when(workforceGovernanceClient.searchAssignments("PROVIDER", "PROV-ZW-VASH-002", "ACTIVE"))
                .thenReturn(assignments);

        ObjectNode organisation = mapper.createObjectNode();
        organisation.put("organisationType", "PUBLIC_FACILITY");
        organisation.put("name", "Harare Central Hospital");
        organisation.put("status", "ACTIVE");
        when(workforceGovernanceClient.getJson("/v1/internal/governance/organisations/f2000000-0000-4000-8000-000000000001"))
                .thenReturn(organisation);

        Map<String, Object> contract = service.buildExperienceContract(
                "c0000000-0000-4000-8000-000000000102", "email", null, false);

        @SuppressWarnings("unchecked")
        List<String> roleTemplates = (List<String>) contract.get("roleTemplates");
        assertEquals(List.of("vashandi_facility_workforce_manager"), roleTemplates);
    }
}
