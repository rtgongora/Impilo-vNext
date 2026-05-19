package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PacsServiceClient;
import zw.gov.mohcc.impilo.experience.imaging.ImagingAccessPolicyService;
import zw.gov.mohcc.impilo.experience.imaging.ImagingGovernanceService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImagingExperienceControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private PacsServiceClient pacs;
    private ImagingAccessPolicyService policy;
    private ImagingGovernanceService governance;
    private ImagingExperienceController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        pacs = Mockito.mock(PacsServiceClient.class);
        policy = Mockito.mock(ImagingAccessPolicyService.class);
        governance = Mockito.mock(ImagingGovernanceService.class);
        controller = new ImagingExperienceController(pacs, policy, governance, mapper);
        request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader(CompanionHeaders.TENANT_ID)).thenReturn("00000000-0000-0000-0000-000000000001");
        Mockito.when(request.getHeader(CompanionHeaders.CORRELATION_ID)).thenReturn("00000000-0000-0000-0000-000000000002");
        Mockito.when(request.getHeader(CompanionHeaders.PURPOSE_OF_USE)).thenReturn("TREATMENT");
        Mockito.when(request.getHeader(CompanionHeaders.FACILITY_ID)).thenReturn(null);
        Mockito.when(request.getHeader(CompanionHeaders.SUBJECT_ID)).thenReturn("patient:test");

        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("sub-1", "n/a", java.util.List.of(new SimpleGrantedAuthority("ROLE_CLINICIAN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getStudy_invokesGovernancePolicyAndAudit() {
        ObjectNode study = mapper.createObjectNode().put("id", "st-1");
        Mockito.when(pacs.getStudy("st-1")).thenReturn(study);
        Mockito.doNothing().when(policy).assertStudyMatchesPatient(Mockito.eq(study), Mockito.eq("cpid-1"));
        Mockito.doNothing().when(governance).assertGovernedRead();

        ResponseEntity<Map<String, Object>> response = controller.getStudy(request, "st-1", "cpid-1");

        Mockito.verify(governance).assertGovernedRead();
        Mockito.verify(policy).assertStudyMatchesPatient(study, "cpid-1");
        Mockito.verify(governance)
                .recordImagingAudit(
                        Mockito.eq("00000000-0000-0000-0000-000000000001"),
                        Mockito.eq("00000000-0000-0000-0000-000000000002"),
                        Mockito.eq("TREATMENT"),
                        Mockito.isNull(),
                        Mockito.eq("IMAGING_STUDY_GET"),
                        Mockito.eq("GET:imaging/study"),
                        Mockito.eq("SUCCESS"),
                        Mockito.eq("sub-1"),
                        Mockito.eq("ROLE_CLINICIAN"),
                        Mockito.eq("patient:test"),
                        Mockito.eq("imaging_study"),
                        Mockito.eq("st-1"),
                        Mockito.anyMap());
        assertEquals(200, response.getStatusCode().value());
        assertTrue(Boolean.TRUE.equals(response.getBody().get("success")));
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void correlateStudy_invokesMutateGovernance() {
        ObjectNode body = mapper.createObjectNode().put("patientCpid", "p1");
        Mockito.when(pacs.correlateStudy(Mockito.eq("st-2"), Mockito.anyMap())).thenReturn(body);
        Mockito.doNothing().when(governance).assertGovernedMutate();

        ResponseEntity<Map<String, Object>> response =
                controller.correlateStudy(request, "st-2", Map.of("patientCpid", "p1"));

        Mockito.verify(governance).assertGovernedMutate();
        Mockito.verify(governance).recordImagingAudit(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.eq("IMAGING_STUDY_CORRELATE"),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void launchViewer_invokesViewerGovernance() {
        ObjectNode session = mapper.createObjectNode().put("sessionId", "sess-1");
        ObjectNode study = mapper.createObjectNode().put("id", "st-3");
        Mockito.when(pacs.getStudy("st-3")).thenReturn(study);
        Mockito.when(pacs.launchViewerSession(Mockito.eq("st-3"), Mockito.anyMap())).thenReturn(session);
        Mockito.doNothing().when(policy).assertStudyMatchesPatient(Mockito.any(), Mockito.eq("cpid-x"));
        Mockito.doNothing().when(governance).assertViewerLaunch();

        ResponseEntity<Map<String, Object>> response =
                controller.launchViewer(request, "st-3", Map.of("viewerType", "DICOMWEB_STACK"), "cpid-x");

        Mockito.verify(governance).assertViewerLaunch();
        Mockito.verify(governance)
                .recordImagingAudit(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq("IMAGING_VIEWER_LAUNCHED"),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void launchViewer_denied_emitsDeniedAudit() {
        Mockito.doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "deny"))
                .when(governance).assertViewerLaunch();

        assertThrows(ResponseStatusException.class, () ->
                controller.launchViewer(request, "st-denied", Map.of("viewerType", "DICOMWEB_STACK"), "cpid-x"));

        Mockito.verify(governance)
                .recordImagingAudit(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq("IMAGING_VIEWER_ACCESS_DENIED"),
                        Mockito.eq("POST:imaging/viewer-sessions"),
                        Mockito.eq("DENIED"),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq("imaging_study"),
                        Mockito.eq("st-denied"),
                        Mockito.any());
    }

    @Test
    void imagingOpsEndpoints_areGovernedAndAudited() {
        ObjectNode status = mapper.createObjectNode().put("reachable", true);
        ObjectNode retry = mapper.createObjectNode().put("status", "RETRY_REQUESTED");
        ObjectNode study = mapper.createObjectNode().put("id", "st-ctx");
        ObjectNode launchContext = mapper.createObjectNode().put("viewerEngine", "DICOMWEB_STACK");
        Mockito.when(pacs.getStudy("st-ctx")).thenReturn(study);
        Mockito.when(pacs.viewerLaunchContext("st-ctx", "OHIF")).thenReturn(launchContext);
        Mockito.doNothing().when(policy).assertStudyMatchesPatient(study, "cpid-ctx");
        Mockito.when(pacs.opsStatus()).thenReturn(status);
        Mockito.when(pacs.opsUnmatchedStudies()).thenReturn(mapper.createArrayNode());
        Mockito.when(pacs.opsFailedCorrelations()).thenReturn(mapper.createArrayNode());
        Mockito.when(pacs.opsFailedWritebacks()).thenReturn(mapper.createArrayNode());
        Mockito.when(pacs.retryFailedWriteback(11L)).thenReturn(retry);
        Mockito.when(pacs.retryAllFailedWritebacks()).thenReturn(retry);

        ResponseEntity<Map<String, Object>> ctx = controller.viewerLaunchContext(
                request, "st-ctx", "OHIF", "cpid-ctx");
        ResponseEntity<Map<String, Object>> s = controller.opsStatus(request);
        ResponseEntity<Map<String, Object>> u = controller.opsUnmatchedStudies(request);
        ResponseEntity<Map<String, Object>> c = controller.opsFailedCorrelations(request);
        ResponseEntity<Map<String, Object>> w = controller.opsFailedWritebacks(request);
        ResponseEntity<Map<String, Object>> r1 = controller.retryFailedWriteback(request, 11L);
        ResponseEntity<Map<String, Object>> r2 = controller.retryAllFailedWritebacks(request);

        Mockito.verify(governance, Mockito.times(4)).assertGovernedRead();
        Mockito.verify(governance, Mockito.times(1)).assertViewerLaunch();
        Mockito.verify(governance, Mockito.times(2)).assertGovernedMutate();
        assertEquals(200, ctx.getStatusCode().value());
        assertEquals(200, s.getStatusCode().value());
        assertEquals(200, u.getStatusCode().value());
        assertEquals(200, c.getStatusCode().value());
        assertEquals(200, w.getStatusCode().value());
        assertEquals(200, r1.getStatusCode().value());
        assertEquals(200, r2.getStatusCode().value());
    }
}
