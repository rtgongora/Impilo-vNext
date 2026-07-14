package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineBillingContextService;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineGovernanceService;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineOperatingModelRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Citizen virtual-care lane tests: honest availability mapping over the REAL governed
 * virtual-hospital registry (classpath doctrine document), fail-closed identity, the
 * consent gate, non-routable rejection, and strict server-side self-scoping.
 */
class VirtualCareControllerTest {

    private static final String ROUTABLE_VH = "vh-national-telemedicine";
    private static final String ROUTABLE_POOL = "GENERAL_TELEMEDICINE";
    private static final String COMING_SOON_VH = "vh-mental-health";

    private PctServiceClient pctClient;
    private VitoServiceClient vitoClient;
    private MvumoServiceClient mvumoClient;
    private NotificationServiceClient notificationClient;
    private CoverageServiceClient coverageClient;
    private TelemedicineGovernanceService governanceService;
    private VirtualCareController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        pctClient = Mockito.mock(PctServiceClient.class);
        vitoClient = Mockito.mock(VitoServiceClient.class);
        mvumoClient = Mockito.mock(MvumoServiceClient.class);
        notificationClient = Mockito.mock(NotificationServiceClient.class);
        coverageClient = Mockito.mock(CoverageServiceClient.class);
        TusoServiceClient tusoClient = Mockito.mock(TusoServiceClient.class);
        governanceService = Mockito.mock(TelemedicineGovernanceService.class);
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        controller = new VirtualCareController(
                new TelemedicineOperatingModelRegistry(),
                pctClient,
                vitoClient,
                mvumoClient,
                notificationClient,
                new TelemedicineBillingContextService(coverageClient, tusoClient),
                governanceService);
    }

    private void citizenExistsInVito() {
        Mockito.when(vitoClient.getClientRegistryProfile(anyString()))
                .thenReturn(objectMapper.createObjectNode().put("id", "known"));
    }

    private Map<String, Object> dataOf(org.springframework.http.ResponseEntity<Map<String, Object>> response) {
        assertNotNull(response.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        return data;
    }

    private String errorCode(org.springframework.http.ResponseEntity<Map<String, Object>> response) {
        assertNotNull(response.getBody());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertNotNull(error);
        return String.valueOf(error.get("code"));
    }

    // ── directory ───────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void directory_mapsHonestAvailabilityAndCitizenSafeFields() {
        var response = controller.listVirtualHospitals("citizen", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        List<Map<String, Object>> hospitals =
                (List<Map<String, Object>>) dataOf(response).get("virtualHospitals");
        assertEquals(21, hospitals.size());

        Map<String, Object> requestable = hospitals.stream()
                .filter(h -> ROUTABLE_VH.equals(h.get("id"))).findFirst().orElseThrow();
        assertEquals("REQUESTABLE", requestable.get("availability"));
        assertEquals(ROUTABLE_POOL, requestable.get("poolId"));

        Map<String, Object> comingSoon = hospitals.stream()
                .filter(h -> COMING_SOON_VH.equals(h.get("id"))).findFirst().orElseThrow();
        assertEquals("COMING_SOON", comingSoon.get("availability"));
        assertNull(comingSoon.get("poolId"));

        long requestableCount = hospitals.stream()
                .filter(h -> "REQUESTABLE".equals(h.get("availability"))).count();
        assertEquals(5, requestableCount);

        // Citizen-safe subset: internal operating-model plumbing never leaks.
        for (Map<String, Object> hospital : hospitals) {
            assertFalse(hospital.containsKey("substrateStatus"));
            assertFalse(hospital.containsKey("currentRoutingSeams"));
            assertFalse(hospital.containsKey("queues"));
            assertFalse(hospital.containsKey("sessionModeIds"));
        }
    }

    // ── request creation ────────────────────────────────────────────────────

    @Test
    void request_toComingSoonHospital_is422WithNoSideEffects() {
        var response = controller.createRequest(
                "req-2", "corr-2", "tenant-a", "TREATMENT", null, "citizen-1",
                Map.of("virtualHospitalId", COMING_SOON_VH, "reason", "Feeling anxious", "modality", "message"));

        assertEquals(422, response.getStatusCode().value());
        assertEquals("VIRTUAL_HOSPITAL_NOT_YET_ROUTABLE", errorCode(response));
        Mockito.verifyNoInteractions(pctClient);
        Mockito.verifyNoInteractions(vitoClient);
    }

    @Test
    void request_toUnknownHospital_is404() {
        var response = controller.createRequest(
                "req-3", "corr-3", "tenant-a", "TREATMENT", null, "citizen-1",
                Map.of("virtualHospitalId", "vh-does-not-exist", "reason", "Cough"));

        assertEquals(404, response.getStatusCode().value());
        assertEquals("VIRTUAL_HOSPITAL_UNKNOWN", errorCode(response));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    void request_videoWithoutConsent_isRejectedBeforeAnyReferralExists() {
        var response = controller.createRequest(
                "req-4", "corr-4", "tenant-a", "TREATMENT", null, "citizen-1",
                Map.of("virtualHospitalId", ROUTABLE_VH, "reason", "Rash", "modality", "video"));

        assertEquals(422, response.getStatusCode().value());
        assertEquals("CONSENT_REQUIRED", errorCode(response));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    void request_failsClosedWhenVitoUnavailable() {
        Mockito.when(vitoClient.getClientRegistryProfile(anyString()))
                .thenThrow(new RuntimeException("connection refused"));
        Mockito.when(vitoClient.getPatient(anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        var response = controller.createRequest(
                "req-5", "corr-5", "tenant-a", "TREATMENT", null, "citizen-1",
                Map.of("virtualHospitalId", ROUTABLE_VH, "reason", "Cough", "modality", "message"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("VITO_UNAVAILABLE", errorCode(response));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    void request_rejectsUnknownCitizenIdentity() {
        Mockito.when(vitoClient.getClientRegistryProfile(anyString()))
                .thenThrow(org.springframework.web.client.HttpClientErrorException.NotFound.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "nf",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));
        Mockito.when(vitoClient.getPatient(anyString()))
                .thenThrow(org.springframework.web.client.HttpClientErrorException.NotFound.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "nf",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        var response = controller.createRequest(
                "req-6", "corr-6", "tenant-a", "TREATMENT", null, "citizen-ghost",
                Map.of("virtualHospitalId", ROUTABLE_VH, "reason", "Cough", "modality", "message"));

        assertEquals(422, response.getStatusCode().value());
        assertEquals("PATIENT_NOT_FOUND", errorCode(response));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    void request_withoutActorIdentity_is401() {
        var response = controller.createRequest(
                "req-7", "corr-7", "tenant-a", "TREATMENT", null, null,
                Map.of("virtualHospitalId", ROUTABLE_VH, "reason", "Cough"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("MISSING_ACTOR_IDENTITY", errorCode(response));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void request_isStrictlySelfScoped_ignoresBodyPatientOverride() {
        citizenExistsInVito();
        ObjectNode created = objectMapper.createObjectNode().put("id", "ref-vc-1").put("status", "DRAFT");
        Mockito.when(pctClient.createReferral(any())).thenReturn(created);
        Mockito.when(pctClient.submitReferral("ref-vc-1"))
                .thenReturn(objectMapper.createObjectNode().put("id", "ref-vc-1").put("status", "SUBMITTED"));

        var response = controller.createRequest(
                "req-8", "corr-8", "tenant-a", "TREATMENT", null, "citizen-1",
                Map.of(
                        "virtualHospitalId", ROUTABLE_VH,
                        "reason", "Cough for two weeks",
                        "modality", "message",
                        // Hostile override attempt: MUST be ignored — the anchor is X-Actor-ID.
                        "patientId", "someone-else"));

        assertEquals(201, response.getStatusCode().value());
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(pctClient).createReferral(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("citizen-1", payload.get("patient_id"));
        assertEquals("POOL", payload.get("routing_kind"));
        assertEquals(ROUTABLE_POOL, payload.get("routing_pool_id"));
        assertEquals("virtual", payload.get("modality"));
        assertEquals("message", payload.get("virtual_mode"));

        Map<String, Object> data = dataOf(response);
        assertEquals("ref-vc-1", data.get("requestId"));
        assertEquals(ROUTABLE_POOL, data.get("poolId"));
        assertTrue(String.valueOf(data.get("message")).contains("Queued for"));

        Mockito.verify(pctClient).submitReferral("ref-vc-1");
        Mockito.verify(governanceService).audit(
                eq("tenant-a"), eq("corr-8"), eq("TREATMENT"), any(),
                eq("TELECONSULT_CITIZEN_POOL_REQUESTED"), eq("POST:virtual-care/requests"), eq("SUCCESS"),
                eq("citizen-1"), eq("PATIENT"), eq("citizen-1"), eq("TeleconsultReferral"), eq("ref-vc-1"), any());
        Mockito.verify(notificationClient).sendNotification(Mockito.argThat(body ->
                "TELECONSULT_REQUESTED".equals(body.get("templateKey"))
                        && "citizen-1".equals(body.get("to"))));
    }

    @Test
    void request_videoWithConsent_recordsGovernedConsentBeforeSubmit() {
        citizenExistsInVito();
        ObjectNode created = objectMapper.createObjectNode().put("id", "ref-vc-2").put("status", "DRAFT");
        Mockito.when(pctClient.createReferral(any())).thenReturn(created);
        Mockito.when(mvumoClient.createConsentRequest(any()))
                .thenReturn(objectMapper.createObjectNode().put("id", "consent-9"));
        Mockito.when(pctClient.updateReferralConsent(eq("ref-vc-2"), any()))
                .thenReturn(objectMapper.createObjectNode());
        Mockito.when(pctClient.submitReferral("ref-vc-2"))
                .thenReturn(objectMapper.createObjectNode().put("id", "ref-vc-2").put("status", "SUBMITTED"));

        var response = controller.createRequest(
                "req-9", "corr-9", "tenant-a", "TREATMENT", null, "citizen-1",
                Map.of("virtualHospitalId", ROUTABLE_VH, "reason", "Skin rash", "modality", "video",
                        "consentGranted", true));

        assertEquals(201, response.getStatusCode().value());
        Mockito.verify(mvumoClient).createConsentRequest(Mockito.argThat(req ->
                "citizen-1".equals(req.get("subjectPatientRef"))
                        && "referral:ref-vc-2".equals(req.get("workflowRef"))));
        Mockito.verify(pctClient).updateReferralConsent(eq("ref-vc-2"), Mockito.argThat(consent ->
                "consent-9".equals(consent.get("consent_reference"))));
        Mockito.verify(pctClient).submitReferral("ref-vc-2");
    }

    @Test
    void request_videoFailsClosedWhenConsentPlaneUnavailable() {
        citizenExistsInVito();
        ObjectNode created = objectMapper.createObjectNode().put("id", "ref-vc-3").put("status", "DRAFT");
        Mockito.when(pctClient.createReferral(any())).thenReturn(created);
        Mockito.when(mvumoClient.createConsentRequest(any()))
                .thenThrow(new RuntimeException("mvumo down"));

        var response = controller.createRequest(
                "req-10", "corr-10", "tenant-a", "TREATMENT", null, "citizen-1",
                Map.of("virtualHospitalId", ROUTABLE_VH, "reason", "Skin rash", "modality", "video",
                        "consentGranted", true));

        assertEquals(502, response.getStatusCode().value());
        assertEquals("MVUMO_UNAVAILABLE", errorCode(response));
        // The request is NOT queued without its consent record.
        Mockito.verify(pctClient, Mockito.never()).submitReferral(anyString());
    }

    // ── my requests ─────────────────────────────────────────────────────────

    @Test
    void myRequests_requireAnActorIdentity() {
        var response = controller.myRequests("req-11", "corr-11", " ", 0, 50);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("MISSING_ACTOR_IDENTITY", errorCode(response));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void myRequests_areSelfScopedAndFilteredToVirtualReferrals() {
        var referrals = objectMapper.createArrayNode();
        ObjectNode virtual = objectMapper.createObjectNode();
        virtual.put("id", "ref-a");
        virtual.put("modality", "virtual");
        virtual.put("status", "SUBMITTED");
        virtual.put("virtualMode", "message");
        virtual.put("routingPoolId", ROUTABLE_POOL);
        referrals.add(virtual);
        ObjectNode inPerson = objectMapper.createObjectNode();
        inPerson.put("id", "ref-b");
        inPerson.put("modality", "in_person");
        referrals.add(inPerson);
        Mockito.when(pctClient.listPatientReferrals(eq("citizen-1"), eq(0), eq(50))).thenReturn(referrals);

        var response = controller.myRequests("req-12", "corr-12", "citizen-1", 0, 50);

        assertEquals(200, response.getStatusCode().value());
        List<Map<String, Object>> rows = (List<Map<String, Object>>) dataOf(response).get("requests");
        assertEquals(1, rows.size());
        assertEquals("ref-a", rows.get(0).get("id"));
        Map<String, Object> hospital = (Map<String, Object>) rows.get(0).get("virtualHospital");
        assertNotNull(hospital);
        assertEquals(ROUTABLE_VH, hospital.get("id"));
        // Self-scope: the PCT lookup is keyed by the caller's actor id.
        Mockito.verify(pctClient).listPatientReferrals(eq("citizen-1"), eq(0), eq(50));
    }
}
