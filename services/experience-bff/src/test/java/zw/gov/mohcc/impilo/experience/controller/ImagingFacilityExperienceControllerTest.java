package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PacsServiceClient;
import zw.gov.mohcc.impilo.experience.imaging.ImagingAccessPolicyService;
import zw.gov.mohcc.impilo.experience.imaging.ImagingGovernanceService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagingFacilityExperienceControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private PacsServiceClient pacs;
    private ImagingAccessPolicyService policy;
    private ImagingGovernanceService governance;
    private ImagingFacilityExperienceController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        pacs = Mockito.mock(PacsServiceClient.class);
        policy = Mockito.mock(ImagingAccessPolicyService.class);
        governance = Mockito.mock(ImagingGovernanceService.class);
        controller = new ImagingFacilityExperienceController(pacs, policy, governance, mapper);
        request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader(CompanionHeaders.TENANT_ID)).thenReturn("00000000-0000-0000-0000-000000000001");
        Mockito.when(request.getHeader(CompanionHeaders.CORRELATION_ID)).thenReturn("00000000-0000-0000-0000-000000000002");
        Mockito.when(request.getHeader(CompanionHeaders.PURPOSE_OF_USE)).thenReturn("TREATMENT");
        Mockito.when(request.getHeader(CompanionHeaders.FACILITY_ID)).thenReturn(null);
        Mockito.when(request.getHeader(CompanionHeaders.SUBJECT_ID)).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("sub-1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_CLINICIAN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ObjectNode modalityWithNetworkFields() {
        ObjectNode m = mapper.createObjectNode();
        m.put("id", 1L);
        m.put("facilityId", "fac-1");
        m.put("modalityType", "CT");
        m.put("name", "Main CT");
        m.put("aeTitle", "CT_MAIN");
        m.put("host", "10.0.0.15");
        m.put("port", 104);
        m.put("calledAeTitle", "PACS_CENTRAL");
        m.put("callingAeTitle", "CT_MAIN");
        m.put("serialNumber", "SN-778899");
        m.put("operationalStatus", "OPERATIONAL");
        m.put("supportsWorklist", true);
        return m;
    }

    @Test
    void listModalities_redactsNetworkFieldsForClinicalActor() {
        Mockito.when(policy.isTechnicalImagingAdmin()).thenReturn(false);
        ArrayNode arr = mapper.createArrayNode();
        arr.add(modalityWithNetworkFields());
        Mockito.when(pacs.listFacilityModalities("fac-1", false)).thenReturn(arr);

        ResponseEntity<Map<String, Object>> response = controller.listModalities(request, "fac-1", false);

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = mapper.valueToTree(response.getBody().get("data"));
        JsonNode first = data.get(0);
        assertFalse(first.has("host"), "host must be redacted for clinical actors");
        assertFalse(first.has("port"), "port must be redacted for clinical actors");
        assertFalse(first.has("aeTitle"), "aeTitle must be redacted for clinical actors");
        assertFalse(first.has("calledAeTitle"));
        assertFalse(first.has("callingAeTitle"));
        assertFalse(first.has("serialNumber"));
        assertEquals("OPERATIONAL", first.path("operationalStatus").asText());
        assertTrue(first.path("supportsWorklist").asBoolean());
        assertEquals("CT", first.path("modalityType").asText());
    }

    @Test
    void listModalities_keepsNetworkFieldsForTechnicalAdmin() {
        Mockito.when(policy.isTechnicalImagingAdmin()).thenReturn(true);
        ArrayNode arr = mapper.createArrayNode();
        arr.add(modalityWithNetworkFields());
        Mockito.when(pacs.listFacilityModalities("fac-1", true)).thenReturn(arr);

        ResponseEntity<Map<String, Object>> response = controller.listModalities(request, "fac-1", true);

        JsonNode first = mapper.valueToTree(response.getBody().get("data")).get(0);
        assertEquals("10.0.0.15", first.path("host").asText());
        assertEquals(104, first.path("port").asInt());
        assertEquals("CT_MAIN", first.path("aeTitle").asText());
    }

    @Test
    void getCapability_redactsArchiveTargetsForClinicalActor() {
        Mockito.when(policy.isTechnicalImagingAdmin()).thenReturn(false);
        ObjectNode cap = mapper.createObjectNode();
        cap.put("facilityId", "fac-1");
        cap.put("configured", true);
        ObjectNode inner = cap.putObject("capability");
        inner.put("deploymentMode", "GATEWAY_STORE_FORWARD");
        inner.put("defaultArchiveTarget", "PACS_CENTRAL@10.0.0.20:104");
        inner.put("defaultWorklistTarget", "MWL@10.0.0.21:105");
        inner.put("goLiveReadiness", "PARTIAL");
        Mockito.when(pacs.getFacilityCapability("fac-1")).thenReturn(cap);

        ResponseEntity<Map<String, Object>> response = controller.getCapability(request, "fac-1");

        JsonNode data = mapper.valueToTree(response.getBody().get("data"));
        assertFalse(data.path("capability").has("defaultArchiveTarget"));
        assertFalse(data.path("capability").has("defaultWorklistTarget"));
        assertEquals("GATEWAY_STORE_FORWARD", data.path("capability").path("deploymentMode").asText());
        assertEquals("PARTIAL", data.path("capability").path("goLiveReadiness").asText());
    }

    @Test
    void upsertCapability_requiresTechnicalAdminAndAudits() {
        ObjectNode saved = mapper.createObjectNode().put("facilityId", "fac-1").put("deploymentMode", "PACS_VNA");
        Mockito.when(pacs.upsertFacilityCapability(Mockito.eq("fac-1"), Mockito.anyMap())).thenReturn(saved);

        ResponseEntity<Map<String, Object>> response =
                controller.upsertCapability(request, "fac-1", Map.of("deploymentMode", "PACS_VNA"));

        Mockito.verify(governance).assertGovernedMutate();
        Mockito.verify(policy).requireTechnicalImagingAdmin();
        Mockito.verify(governance).recordImagingAudit(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.eq("IMAGING_FACILITY_CAPABILITY_UPSERT"),
                Mockito.any(), Mockito.eq("SUCCESS"),
                Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.eq("imaging_facility_capability"), Mockito.eq("fac-1"), Mockito.anyMap());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void registerModality_deniedWithoutTechnicalRole() {
        Mockito.doThrow(new AccessDeniedException("Technical/admin role required"))
                .when(policy).requireTechnicalImagingAdmin();

        assertThrows(AccessDeniedException.class, () ->
                controller.registerModality(request, "fac-1", Map.of("modalityType", "CT")));
        Mockito.verifyNoInteractions(pacs);
    }

    @Test
    void readiness_isGovernedAndAudited() {
        ObjectNode readiness = mapper.createObjectNode()
                .put("facilityId", "fac-1")
                .put("readiness", "PARTIAL");
        Mockito.when(pacs.facilityImagingReadiness("fac-1")).thenReturn(readiness);

        ResponseEntity<Map<String, Object>> response = controller.readiness(request, "fac-1");

        Mockito.verify(governance).assertGovernedRead();
        Mockito.verify(governance).recordImagingAudit(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.eq("IMAGING_FACILITY_READINESS"),
                Mockito.any(), Mockito.eq("SUCCESS"),
                Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.eq("imaging_facility_capability"), Mockito.eq("fac-1"), Mockito.anyMap());
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = mapper.valueToTree(response.getBody().get("data"));
        assertEquals("PARTIAL", data.path("readiness").asText());
    }
}
