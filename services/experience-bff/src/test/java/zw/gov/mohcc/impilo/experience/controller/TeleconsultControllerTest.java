package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.AnalyticsPipelineServiceClient;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.KhulumaServiceClient;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.FhirGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.client.RtcGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineBillingContextService;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineGovernanceService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;

class TeleconsultControllerTest {

    private PctServiceClient pctClient;
    private VarapiServiceClient varapiClient;
    private MvumoServiceClient mvumoClient;
    private CostaServiceClient costaClient;
    private CoverageServiceClient coverageClient;
    private AnalyticsPipelineServiceClient analyticsClient;
    private RtcGatewayServiceClient rtcClient;
    private NotificationServiceClient notificationClient;
    private BookingServiceClient bookingClient;
    private KhulumaServiceClient khulumaClient;
    private OrosServiceClient orosClient;
    private FhirGatewayServiceClient fhirGatewayClient;

    private TelemedicineGovernanceService governanceService;
    private VitoServiceClient vitoClient;
    private TeleconsultController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        pctClient = Mockito.mock(PctServiceClient.class);
        mvumoClient = Mockito.mock(MvumoServiceClient.class);
        DocumentServiceClient documentClient = Mockito.mock(DocumentServiceClient.class);
        varapiClient = Mockito.mock(VarapiServiceClient.class);
        TusoServiceClient tusoClient = Mockito.mock(TusoServiceClient.class);
        coverageClient = Mockito.mock(CoverageServiceClient.class);
        // Real billing-context service over the mocked coverage/tuso clients — the extraction
        // must keep the exact enrichment behaviour the controller had before.
        TelemedicineBillingContextService billingContextService =
                new TelemedicineBillingContextService(coverageClient, tusoClient);
        notificationClient = Mockito.mock(NotificationServiceClient.class);
        fhirGatewayClient = Mockito.mock(FhirGatewayServiceClient.class);
        costaClient = Mockito.mock(CostaServiceClient.class);
        analyticsClient = Mockito.mock(AnalyticsPipelineServiceClient.class);
        rtcClient = Mockito.mock(RtcGatewayServiceClient.class);
        bookingClient = Mockito.mock(BookingServiceClient.class);
        khulumaClient = Mockito.mock(KhulumaServiceClient.class);
        orosClient = Mockito.mock(OrosServiceClient.class);
        governanceService = Mockito.mock(TelemedicineGovernanceService.class);
        vitoClient = Mockito.mock(VitoServiceClient.class);
        // Default: patient exists in VITO so existing createSession tests pass the intake guard.
        Mockito.when(vitoClient.getPatient(Mockito.anyString()))
                .thenReturn(objectMapper.createObjectNode().put("id", "known"));
        controller = new TeleconsultController(
                pctClient, vitoClient, mvumoClient, documentClient, varapiClient, tusoClient, billingContextService,
                notificationClient, fhirGatewayClient, costaClient, analyticsClient, rtcClient,
                bookingClient, khulumaClient, orosClient, governanceService, objectMapper
        );
    }

    @Test
    void acceptPassesThroughUpstreamDomainRejectionInsteadOfMaskingAsOutage() {
        // A0: a governed 4xx from PCT (e.g. TM-B1 ILLEGAL_TRANSITION / PROVIDER_NOT_AUTHORIZED)
        // must reach the client with its real status + code, not a 502 PCT_UNAVAILABLE.
        String body = "{\"success\":false,\"error\":{\"code\":\"PROVIDER_NOT_AUTHORIZED\","
                + "\"message\":\"licence suspended\",\"status\":403}}";
        HttpClientErrorException upstream = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", org.springframework.http.HttpHeaders.EMPTY,
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
        Mockito.when(pctClient.acceptReferral(eq("ref-401"), any())).thenThrow(upstream);

        var response = controller.accept(
                "ref-401", "req-1", "corr-1", "tenant-a", "TREATMENT", "fac-1", "provider-1", null, Map.of());

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("PROVIDER_NOT_AUTHORIZED", error.get("code"));
    }

    @Test
    void placeSessionOrderStampsTeleconsultProvenance() {
        // TM-B7: an order placed in-session delegates to OROS with the referral id as source_ref.
        Mockito.when(orosClient.placeTeleconsultOrder(
                        eq("ref-ord"), eq("LAB"), any(), eq("CPID-1"), eq("ZIBO-CBC"), any(), any()))
                .thenReturn(objectMapper.createObjectNode().put("orderId", "ORD-1"));

        var response = controller.placeSessionOrder(
                "ref-ord", "req", "corr", "tenant", "TREATMENT", "fac", "actor-1",
                Map.of("orderType", "LAB", "patientCpid", "CPID-1", "ziboOrderCode", "ZIBO-CBC"));

        assertEquals(201, response.getStatusCode().value());
        Mockito.verify(orosClient).placeTeleconsultOrder(
                eq("ref-ord"), eq("LAB"), any(), eq("CPID-1"), eq("ZIBO-CBC"), any(), any());
    }

    @Test
    void placeSessionOrderPassesThroughDuplicateConflict() {
        // TM-B7: OROS 409 CONFLICT (duplicate active order) surfaces as 409, not a fake outage.
        String body = "{\"success\":false,\"error\":{\"code\":\"CONFLICT\","
                + "\"message\":\"already exists\",\"status\":409}}";
        HttpClientErrorException upstream = HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", org.springframework.http.HttpHeaders.EMPTY,
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
        Mockito.when(orosClient.placeTeleconsultOrder(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(upstream);

        var response = controller.placeSessionOrder(
                "ref-dup", "req", "corr", "tenant", "TREATMENT", "fac", "actor-1",
                Map.of("orderType", "LAB", "patientCpid", "CPID-1", "ziboOrderCode", "ZIBO-CBC"));

        assertEquals(409, response.getStatusCode().value());
    }

    @Test
    void addSessionTaskDelegatesToPct() {
        // TM-B7: a follow-up task created in-session delegates to PCT referral-scoped tasks.
        Mockito.when(pctClient.addReferralTask(eq("ref-t"), any()))
                .thenReturn(objectMapper.createObjectNode().put("taskId", "T-1"));

        var response = controller.addSessionTask(
                "ref-t", "req", "corr", "tenant", "TREATMENT", "fac", "actor-1",
                Map.of("taskType", "FOLLOW_UP", "blocksClosure", true));

        assertEquals(201, response.getStatusCode().value());
        Mockito.verify(pctClient).addReferralTask(eq("ref-t"), any());
    }

    @Test
    void acceptAttachesResolvedProviderAuthorityFromVarapi() {
        // TM-B8: the accept payload sent to PCT carries the resolved VARAPI standing.
        Mockito.when(varapiClient.getProvider(eq("PROV-1")))
                .thenReturn(objectMapper.createObjectNode().put("status", "ACTIVE"));
        Mockito.when(pctClient.acceptReferral(eq("ref-a"), any()))
                .thenReturn(objectMapper.createObjectNode().put("id", "ref-a"));
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);

        controller.accept("ref-a", "req", "corr", "tenant", "TREATMENT", "fac", "actor-1", "PROV-1", Map.of());

        Mockito.verify(pctClient).acceptReferral(eq("ref-a"), cap.capture());
        Map<?, ?> authority = (Map<?, ?>) cap.getValue().get("authority");
        assertEquals(true, authority.get("resolved"));
        assertEquals("ACTIVE", authority.get("status"));
    }

    @Test
    void acceptMarksAuthorityUnresolvedWhenVarapiFails() {
        Mockito.when(varapiClient.getProvider(any())).thenThrow(new RuntimeException("varapi down"));
        Mockito.when(pctClient.acceptReferral(eq("ref-b"), any()))
                .thenReturn(objectMapper.createObjectNode().put("id", "ref-b"));
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);

        controller.accept("ref-b", "req", "corr", "tenant", "TREATMENT", "fac", "actor-1", "PROV-1", Map.of());

        Mockito.verify(pctClient).acceptReferral(eq("ref-b"), cap.capture());
        Map<?, ?> authority = (Map<?, ?>) cap.getValue().get("authority");
        assertEquals(false, authority.get("resolved")); // BFF never blocks; PCT decides
    }

    @Test
    void acceptStillReportsTransportFailureAsUpstreamOutage() {
        // A genuine 5xx / transport failure keeps the upstream-failure (502) shape.
        Mockito.when(pctClient.acceptReferral(eq("ref-500"), any()))
                .thenThrow(new RuntimeException("connection refused"));

        var response = controller.accept(
                "ref-500", "req-1", "corr-1", "tenant-a", "TREATMENT", "fac-1", "provider-1", null, Map.of());

        assertEquals(502, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("PCT_UNAVAILABLE", error.get("code"));
    }

    @Test
    void completeRoutesFhirSummaryThroughGovernedForwardNotBrokenFhirPath() {
        // A4/TM-B6: the teleconsult clinical summary is written via the gateway forward endpoint
        // (POST /internal/v1/gateway/forward) — NOT createResource (POST /fhir/{type}, a route
        // fhir-gateway never served, which silently 404'd the summary).
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        var completedNode = objectMapper.createObjectNode();
        completedNode.put("patientCpid", "CPID-9").put("id", "ref-fhir");
        completedNode.putObject("completion").put("closureNarrative", "done");
        Mockito.when(pctClient.completeReferral(eq("ref-fhir"), any())).thenReturn(completedNode);

        var response = controller.complete(
                "ref-fhir", "req", "corr", "tenant-x", "TREATMENT", "fac", "actor-1",
                Map.of("closureNarrative", "done"));

        assertEquals(200, response.getStatusCode().value());
        ArgumentCaptor<String> resourceType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        Mockito.verify(fhirGatewayClient).forward(
                eq("tenant-x"), eq("corr"), eq("actor-1"),
                resourceType.capture(), eq("CREATE"), payload.capture(),
                eq("CPID-9"), eq("TREATMENT"));
        assertEquals("DiagnosticReport", resourceType.getValue());
        // The forwarded payload is the FHIR resource JSON with the real conclusion.
        org.junit.jupiter.api.Assertions.assertTrue(payload.getValue().contains("DiagnosticReport"));
        org.junit.jupiter.api.Assertions.assertTrue(payload.getValue().contains("done"));
        Mockito.verify(fhirGatewayClient, Mockito.never()).createResource(any(), any());
    }

    @Test
    void completeRejectsBreakGlassWithoutReasonAndApprover() {
        var response = controller.complete(
                "ref-001",
                "req-1",
                "corr-1",
                "tenant-a",
                "TREATMENT",
                "fac-1",
                "provider-1",
                Map.of("breakGlassOverride", true)
        );

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("BREAK_GLASS_REQUIREMENTS_MISSING", error.get("code"));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    void updateReferralAcceptsSpecialtyPoolRouting() {
        ObjectNode updated = objectMapper.createObjectNode();
        updated.put("id", "ref-123");
        updated.put("status", "IN_REVIEW");
        updated.put("specialty", "CARDIOLOGY");
        Mockito.when(pctClient.updateReferralStage(eq("ref-123"), any())).thenReturn(updated);

        var response = controller.updateReferral(
                "ref-123",
                "req-2",
                "corr-2",
                "tenant-a",
                "TREATMENT",
                "fac-1",
                "provider-1",
                Map.of(
                        "routingType", "SPECIALTY_POOL",
                        "routingTarget", "cardiology_pool",
                        "clinicalQuestion", "Review ECG abnormalities"
                )
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Mockito.verify(pctClient).updateReferralStage(eq("ref-123"), any());
    }

    @Test
    void createSessionRejectsUnsupportedPurposeOfUse() {
        Mockito.when(governanceService.normalizePurposeOfUse("UNSUPPORTED"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported purpose-of-use"));

        var response = controller.createSession(
                "req-3",
                "corr-3",
                "tenant-a",
                "UNSUPPORTED",
                "fac-1",
                "provider-1",
                Map.of("patientId", "patient-1", "reason", "Need consult")
        );

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("TELEMEDICINE_GOVERNANCE_INVALID", error.get("code"));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    void createSessionRejectsUnknownPatient() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        Mockito.when(vitoClient.getPatient("ghost-patient")).thenReturn(null);

        var response = controller.createSession(
                "req-v1", "corr-v1", "tenant-a", "TREATMENT", "fac-1", "provider-1",
                Map.of("patientId", "ghost-patient", "reason", "Need consult"));

        assertEquals(422, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("PATIENT_NOT_FOUND", error.get("code"));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    void createSessionFailsClosedWhenVitoUnavailable() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        Mockito.when(vitoClient.getPatient("patient-1"))
                .thenThrow(new RuntimeException("connection refused"));

        var response = controller.createSession(
                "req-v2", "corr-v2", "tenant-a", "TREATMENT", "fac-1", "provider-1",
                Map.of("patientId", "patient-1", "reason", "Need consult"));

        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("VITO_UNAVAILABLE", error.get("code"));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createSessionEnrichesPatientCategoryFromCoverage() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        ObjectNode category = objectMapper.createObjectNode();
        category.put("category", "PRIVATE");
        category.put("source", "COVERAGE_PLAN");
        Mockito.when(coverageClient.resolvePatientCategory("CPID-1")).thenReturn(category);
        ObjectNode created = objectMapper.createObjectNode();
        created.put("id", "ref-1");
        Mockito.when(pctClient.createReferral(any())).thenReturn(created);

        var response = controller.createSession(
                "req-x", "corr-x", "tenant-a", "TREATMENT", "fac-uuid", "provider-1",
                new java.util.HashMap<>(Map.of("patientId", "CPID-1", "specialty", "CARDIOLOGY")));

        assertEquals(201, response.getStatusCode().value());
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(pctClient).createReferral(payloadCaptor.capture());
        // coverage-resolved category is injected for COSTA's charging rules; non-numeric facility
        // ref ("fac-uuid") is skipped gracefully, leaving facility_category unset.
        assertEquals("PRIVATE", payloadCaptor.getValue().get("patient_category"));
    }

    @Test
    void completeEmitsTelemedicineAnalyticsEvent() throws Exception {
        ObjectNode completed = objectMapper.createObjectNode();
        completed.put("id", "ref-001");
        completed.put("patientCpid", "CPID-1");
        completed.put("status", "COMPLETED");
        Mockito.when(pctClient.completeReferral(eq("ref-001"), any())).thenReturn(completed);
        Mockito.when(analyticsClient.ingestTelemedicineEvent(any())).thenReturn(objectMapper.createObjectNode());

        var response = controller.complete(
                "ref-001",
                "req-4",
                "corr-4",
                "tenant-a",
                "TREATMENT",
                "fac-1",
                "provider-1",
                Map.of("outcome", "COMPLETED"));

        assertEquals(200, response.getStatusCode().value());
        Mockito.verify(analyticsClient).ingestTelemedicineEvent(Mockito.argThat(event ->
                "TELECONSULT_COMPLETED".equals(event.get("eventType"))
                        && "ref-001".equals(event.get("sessionId"))
                        && "CPID-1".equals(event.get("patientId"))));
    }

    @Test
    void createSession_createsReferralWhenGovernanceAllows() {
        ObjectNode created = objectMapper.createObjectNode();
        created.put("id", "ref-new-1");
        created.put("status", "DRAFT");
        Mockito.doNothing().when(governanceService).assertGovernedMutate();
        Mockito.when(governanceService.normalizePurposeOfUse("TREATMENT")).thenReturn("TREATMENT");
        Mockito.when(pctClient.createReferral(any())).thenReturn(created);

        var response = controller.createSession(
                "req-create",
                "corr-create",
                "tenant-a",
                "TREATMENT",
                "fac-1",
                "provider-1",
                Map.of("patientId", "pat-99", "clinicalQuestion", "Chest pain review"));

        assertEquals(201, response.getStatusCode().value());
        Mockito.verify(pctClient).createReferral(any());
    }

    @Test
    void recordConsent_createsMvumoRequestAndUpdatesReferral() throws Exception {
        ObjectNode mvumo = objectMapper.createObjectNode();
        mvumo.put("id", "consent-77");
        mvumo.put("tshepoConsentId", "tshepo-88");
        Mockito.when(mvumoClient.createConsentRequest(any())).thenReturn(mvumo);
        Mockito.when(pctClient.updateReferralConsent(eq("ref-consent"), any())).thenReturn(objectMapper.createObjectNode());

        var response = controller.recordConsent(
                "ref-consent",
                "req-consent",
                "corr-consent",
                Map.of("patientId", "pat-001", "consentType", "TELEHEALTH"));

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("consent-77", data.get("consentReference"));
        Mockito.verify(mvumoClient).createConsentRequest(Mockito.argThat(body ->
                "pat-001".equals(body.get("subjectPatientRef"))
                        && "referral:ref-consent".equals(body.get("workflowRef"))));
        Mockito.verify(pctClient).updateReferralConsent(eq("ref-consent"), any());
    }

    @Test
    void rtcOpsHealth_proxiesRtcGatewayHealth() {
        ObjectNode health = objectMapper.createObjectNode();
        health.put("provider", "LIVEKIT");
        health.put("devModeEnabled", true);
        health.put("livekitEnabled", false);
        health.put("livekitConfigured", false);
        health.put("productionReady", false);
        health.put("serverUrl", "dev://livekit");

        Mockito.doNothing().when(governanceService).assertGovernedRead();
        Mockito.when(rtcClient.getOpsHealth()).thenReturn(health);

        var response = controller.rtcOpsHealth("req-rtc-health", "corr-rtc-health");

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("LIVEKIT", data.get("provider").asText());
        assertEquals(false, data.get("productionReady").asBoolean());
        Mockito.verify(rtcClient).getOpsHealth();
    }

    @Test
    void issueMediaToken_provisionsRtcAndReturnsRoomCredentials() {
        ObjectNode referral = objectMapper.createObjectNode();
        referral.put("id", "ref-rtc-1");
        referral.put("patientCpid", "CPID-9");
        referral.put("consentStatus", "GRANTED");

        ObjectNode provisioned = objectMapper.createObjectNode();
        provisioned.put("roomUrl", "wss://livekit.preview/room");
        provisioned.put("channel", "LIVEKIT");

        ObjectNode token = objectMapper.createObjectNode();
        token.put("accessToken", "rtc-token-abc");

        Mockito.doNothing().when(governanceService).assertGovernedMutate();
        Mockito.when(governanceService.normalizePurposeOfUse("TREATMENT")).thenReturn("TREATMENT");
        Mockito.when(pctClient.getReferral("ref-rtc-1")).thenReturn(referral);
        Mockito.when(rtcClient.getSession("ref-rtc-1")).thenReturn(null);
        Mockito.when(rtcClient.provisionSession(any())).thenReturn(provisioned);
        Mockito.when(rtcClient.issueParticipantToken(eq("ref-rtc-1"), any())).thenReturn(token);

        var response = controller.issueMediaToken(
                "ref-rtc-1",
                "req-rtc",
                "corr-rtc",
                "tenant-a",
                "TREATMENT",
                "fac-1",
                "provider-9",
                Map.of());

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("wss://livekit.preview/room", data.get("room_url"));
        assertEquals("rtc-token-abc", data.get("token"));
        Mockito.verify(rtcClient).provisionSession(any());
        Mockito.verify(rtcClient).issueParticipantToken(eq("ref-rtc-1"), any());
    }

    // ── W2: participant roles, waiting-room, console, scheduling, ON_CALL ──

    private ObjectNode consentedReferral(String id, String patientCpid, String providerId) {
        ObjectNode referral = objectMapper.createObjectNode();
        referral.put("id", id);
        if (patientCpid != null) {
            referral.put("patientCpid", patientCpid);
        }
        if (providerId != null) {
            referral.put("providerId", providerId);
        }
        referral.put("consentStatus", "GRANTED");
        return referral;
    }

    @Test
    void issueMediaToken_rejectsUnknownParticipantRole() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        Mockito.when(pctClient.getReferral("ref-role-1")).thenReturn(consentedReferral("ref-role-1", "CPID-9", null));

        var response = controller.issueMediaToken(
                "ref-role-1", "req-r", "corr-r", "tenant-a", "TREATMENT", "fac-1", "provider-9",
                Map.of("role", "HACKER"));

        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("INVALID_PARTICIPANT_ROLE", error.get("code"));
        Mockito.verifyNoInteractions(rtcClient);
    }

    @Test
    void issueMediaToken_rejectsUnknownMediaProfile() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        Mockito.when(pctClient.getReferral("ref-mp-1")).thenReturn(consentedReferral("ref-mp-1", "CPID-9", null));

        var response = controller.issueMediaToken(
                "ref-mp-1", "req-mp", "corr-mp", "tenant-a", "TREATMENT", "fac-1", "provider-9",
                Map.of("mediaProfile", "VIDEO_4K"));

        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("INVALID_MEDIA_PROFILE", error.get("code"));
        Mockito.verifyNoInteractions(rtcClient);
    }

    @Test
    void issueMediaToken_passesWaitingThroughAndNotifiesProviderWithDedupe() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        Mockito.when(pctClient.getReferral("ref-wait-1"))
                .thenReturn(consentedReferral("ref-wait-1", "CPID-9", "prov-1"));
        Mockito.when(rtcClient.getSession("ref-wait-1")).thenReturn(objectMapper.createObjectNode());
        ObjectNode waiting = objectMapper.createObjectNode();
        waiting.put("status", "WAITING");
        waiting.put("sessionId", "ref-wait-1");
        waiting.put("identity", "CPID-9");
        Mockito.when(rtcClient.issueParticipantToken(eq("ref-wait-1"), any())).thenReturn(waiting);

        var response = controller.issueMediaToken(
                "ref-wait-1", "req-w", "corr-w", "tenant-a", "TREATMENT", "fac-1", "CPID-9",
                Map.of("role", "PATIENT", "mediaProfile", "AUDIO_ONLY"));

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("WAITING", data.get("status"));
        assertEquals("ref-wait-1", data.get("sessionId"));
        assertEquals("CPID-9", data.get("identity"));
        // patient-waiting notification to the referral provider
        Mockito.verify(notificationClient).sendNotification(Mockito.argThat(body ->
                "rtc.telemedicine.patient-waiting".equals(body.get("templateKey"))
                        && "prov-1".equals(body.get("to"))));

        // second poll while still WAITING → deduped, no second notification
        controller.issueMediaToken(
                "ref-wait-1", "req-w2", "corr-w2", "tenant-a", "TREATMENT", "fac-1", "CPID-9",
                Map.of("role", "PATIENT"));
        Mockito.verify(notificationClient, Mockito.times(1)).sendNotification(any());
    }

    @Test
    void issueMediaToken_patientRoleVerifiedMismatchIsForbidden() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        Mockito.when(pctClient.getReferral("ref-gov-1"))
                .thenReturn(consentedReferral("ref-gov-1", "CPID-9", "prov-1"));
        // The caller's health anchor resolves to a DIFFERENT patient — verified mismatch.
        Mockito.when(vitoClient.resolveIdentity("intruder-1"))
                .thenReturn(objectMapper.createObjectNode().put("cpid", "CPID-OTHER"));

        var response = controller.issueMediaToken(
                "ref-gov-1", "req-g", "corr-g", "tenant-a", "TREATMENT", "fac-1", "intruder-1",
                Map.of("role", "PATIENT"));

        assertEquals(403, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("PATIENT_IDENTITY_MISMATCH", error.get("code"));
        Mockito.verifyNoInteractions(rtcClient);
    }

    @Test
    void issueMediaToken_patientRoleVerifiedMatchProceeds() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        Mockito.when(pctClient.getReferral("ref-gov-2"))
                .thenReturn(consentedReferral("ref-gov-2", "CPID-9", "prov-1"));
        Mockito.when(vitoClient.resolveIdentity("patient-anchor-1"))
                .thenReturn(objectMapper.createObjectNode().put("cpid", "CPID-9"));
        Mockito.when(rtcClient.issueParticipantToken(any(), any()))
                .thenReturn(objectMapper.createObjectNode()
                        .put("room_url", "ws://livekit:7880").put("token", "jwt"));

        var response = controller.issueMediaToken(
                "ref-gov-2", "req-g2", "corr-g2", "tenant-a", "TREATMENT", "fac-1", "patient-anchor-1",
                Map.of("role", "PATIENT"));

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void issueMediaToken_patientRoleUnverifiableLinkageReachesTheLobbyOnly() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        Mockito.when(pctClient.getReferral("ref-gov-3"))
                .thenReturn(consentedReferral("ref-gov-3", "CPID-9", "prov-1"));
        // No caller↔patient linkage derivable (VITO has no record for the anchor).
        Mockito.when(vitoClient.resolveIdentity("unlinked-1")).thenReturn(null);
        // The rtc lobby holds the un-admitted patient at WAITING (frozen contract).
        Mockito.when(rtcClient.issueParticipantToken(any(), any()))
                .thenReturn(objectMapper.createObjectNode().put("status", "WAITING"));

        var response = controller.issueMediaToken(
                "ref-gov-3", "req-g3", "corr-g3", "tenant-a", "TREATMENT", "fac-1", "unlinked-1",
                Map.of("role", "PATIENT"));

        assertEquals(200, response.getStatusCode().value());
        Mockito.verify(governanceService).audit(
                any(), any(), any(), any(),
                Mockito.eq("TELEMEDICINE_PATIENT_TOKEN_LINKAGE_UNVERIFIED"),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void issueMediaToken_patientRoleWithoutPatientAnchorRequiresTreatment() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("OPERATIONS");
        Mockito.when(pctClient.getReferral("ref-gov-2"))
                .thenReturn(consentedReferral("ref-gov-2", null, "prov-1"));

        var response = controller.issueMediaToken(
                "ref-gov-2", "req-g2", "corr-g2", "tenant-a", "OPERATIONS", "fac-1", "caller-1",
                Map.of("role", "PATIENT"));

        assertEquals(403, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("PATIENT_LINKAGE_UNVERIFIED", error.get("code"));
        Mockito.verifyNoInteractions(rtcClient);
    }

    @Test
    void refreshMediaToken_passesRefreshedTokenThrough() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        ObjectNode refreshed = objectMapper.createObjectNode();
        refreshed.put("accessToken", "rtc-token-refreshed");
        Mockito.when(rtcClient.refreshParticipantToken(eq("ref-rf-1"), any())).thenReturn(refreshed);

        var response = controller.refreshMediaToken(
                "ref-rf-1", "req-rf", "corr-rf", "tenant-a", "TREATMENT", "fac-1", "provider-9",
                Map.of());

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("rtc-token-refreshed", data.get("token"));
        assertEquals("READY", data.get("status"));
        Mockito.verify(rtcClient).refreshParticipantToken(eq("ref-rf-1"), any());
    }

    @Test
    void waitingRoom_listsOnlyWaitingParticipants() {
        var participants = objectMapper.createArrayNode();
        participants.add(objectMapper.createObjectNode().put("identity", "CPID-9").put("state", "WAITING"));
        participants.add(objectMapper.createObjectNode().put("identity", "prov-1").put("state", "ADMITTED"));
        Mockito.when(rtcClient.listParticipants("ref-wr-1")).thenReturn(participants);

        var response = controller.waitingRoom("ref-wr-1", "req-wr", "corr-wr");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        JsonNode waiting = (JsonNode) data.get("waiting");
        assertEquals(1, waiting.size());
        assertEquals("CPID-9", waiting.get(0).get("identity").asText());
    }

    @Test
    void admitWaitingParticipant_admitsAndNotifiesPatient() {
        Mockito.when(rtcClient.admitParticipant(eq("ref-adm-1"), eq("CPID-9"), any()))
                .thenReturn(objectMapper.createObjectNode().put("state", "ADMITTED"));

        var response = controller.admitWaitingParticipant(
                "ref-adm-1", "req-a", "corr-a", "tenant-a", "TREATMENT", "fac-1", "prov-1",
                Map.of("identity", "CPID-9"));

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("ADMITTED", data.get("state"));
        Mockito.verify(rtcClient).admitParticipant(eq("ref-adm-1"), eq("CPID-9"), any());
        Mockito.verify(notificationClient).sendNotification(Mockito.argThat(body ->
                "rtc.telemedicine.session-ready".equals(body.get("templateKey"))
                        && "CPID-9".equals(body.get("to"))));
    }

    @Test
    void denyWaitingParticipant_deniesWithReason() {
        Mockito.when(rtcClient.denyParticipant(eq("ref-den-1"), eq("CPID-9"), any()))
                .thenReturn(objectMapper.createObjectNode().put("state", "DENIED"));

        var response = controller.denyWaitingParticipant(
                "ref-den-1", "req-d", "corr-d", "tenant-a", "TREATMENT", "fac-1", "prov-1",
                Map.of("identity", "CPID-9", "reason", "Wrong session"));

        assertEquals(200, response.getStatusCode().value());
        Mockito.verify(rtcClient).denyParticipant(eq("ref-den-1"), eq("CPID-9"),
                Mockito.argThat(body -> "Wrong session".equals(body.get("reason"))));
        Mockito.verifyNoInteractions(notificationClient);
    }

    @Test
    void providerConsole_composesReferralWaitingRoomRecordingsAndEncounter() {
        ObjectNode referral = consentedReferral("ref-con-1", "CPID-9", "prov-1");
        referral.put("encounterId", "enc-77");
        Mockito.when(pctClient.getReferral("ref-con-1")).thenReturn(referral);
        var participants = objectMapper.createArrayNode();
        participants.add(objectMapper.createObjectNode().put("identity", "CPID-9").put("state", "WAITING"));
        Mockito.when(rtcClient.listParticipants("ref-con-1")).thenReturn(participants);
        var recordings = objectMapper.createArrayNode();
        recordings.add(objectMapper.createObjectNode().put("recordingId", "rec-1"));
        Mockito.when(rtcClient.listRecordings("ref-con-1")).thenReturn(recordings);

        var response = controller.providerConsole("ref-con-1", "req-c", "corr-c");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertNotNull(data.get("referral"));
        assertEquals(1, ((JsonNode) data.get("waitingRoom")).size());
        assertEquals("rec-1", ((JsonNode) data.get("recordings")).get(0).get("recordingId").asText());
        assertEquals("enc-77", data.get("encounterId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createSession_withScheduledAtCreatesBookingAndTagsReferral() {
        Mockito.when(governanceService.normalizePurposeOfUse(any())).thenReturn("TREATMENT");
        ObjectNode created = objectMapper.createObjectNode();
        created.put("id", "ref-sch-1");
        created.put("patientCpid", "CPID-1");
        Mockito.when(pctClient.createReferral(any())).thenReturn(created);
        Mockito.when(bookingClient.createAppointment(any()))
                .thenReturn(objectMapper.createObjectNode().put("id", "appt-1"));

        var response = controller.createSession(
                "req-s", "corr-s", "tenant-a", "TREATMENT", "55", "provider-1",
                Map.of("patientId", "CPID-1", "clinicalQuestion", "Follow-up review",
                        "scheduledAt", "2026-07-10T09:00:00Z"));

        assertEquals(201, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("appt-1", data.get("appointmentId").asText());

        ArgumentCaptor<Map<String, Object>> apptCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(bookingClient).createAppointment(apptCaptor.capture());
        assertEquals("TELECONSULT", apptCaptor.getValue().get("appointment_type"));
        assertEquals("2026-07-10T09:00:00Z", apptCaptor.getValue().get("scheduled_at"));
        assertEquals("teleconsult:referral:ref-sch-1", apptCaptor.getValue().get("notes"));

        Mockito.verify(pctClient).updateReferralStage(eq("ref-sch-1"), Mockito.argThat(update ->
                "appt-1".equals(update.get("appointment_id"))));
        Mockito.verify(notificationClient).sendNotification(Mockito.argThat(body ->
                "rtc.telemedicine.appointment-reminder".equals(body.get("templateKey"))
                        && "CPID-1".equals(body.get("to"))));
    }

    @Test
    void submit_onCallRoutingResolvesProviderFromKhulumaRoster() {
        ObjectNode referral = consentedReferral("ref-oc-1", "CPID-1", null);
        ObjectNode routing = objectMapper.createObjectNode();
        routing.put("type", "ON_CALL");
        routing.put("target_ref", "CARDIOLOGY");
        referral.set("routingTarget", routing);
        Mockito.when(pctClient.getReferral("ref-oc-1")).thenReturn(referral);
        var roster = objectMapper.createArrayNode();
        roster.add(objectMapper.createObjectNode().put("actorId", "prov-9").put("dutyStatus", "ON_CALL"));
        Mockito.when(khulumaClient.onCallRoster("tenant-a", "CARDIOLOGY")).thenReturn(roster);
        ObjectNode submitted = objectMapper.createObjectNode();
        submitted.put("id", "ref-oc-1");
        submitted.put("status", "SUBMITTED");
        submitted.put("patientCpid", "CPID-1");
        Mockito.when(pctClient.submitReferral("ref-oc-1")).thenReturn(submitted);

        var response = controller.submitReferral(
                "ref-oc-1", "req-oc", "corr-oc", "tenant-a", "TREATMENT", "fac-1", "actor-1");

        assertEquals(200, response.getStatusCode().value());
        Mockito.verify(pctClient).updateReferralStage(eq("ref-oc-1"), Mockito.argThat(update -> {
            Object target = update.get("routing_target");
            return target instanceof Map<?, ?> t
                    && "PRACTITIONER".equals(t.get("type"))
                    && "prov-9".equals(t.get("target_ref"));
        }));
        Mockito.verify(pctClient).submitReferral("ref-oc-1");
        Mockito.verify(notificationClient).sendNotification(Mockito.argThat(body ->
                "prov-9".equals(body.get("to"))));
    }

    @Test
    void submit_onCallWithoutAvailableProviderIsHonest422() {
        ObjectNode referral = consentedReferral("ref-oc-2", "CPID-1", null);
        ObjectNode routing = objectMapper.createObjectNode();
        routing.put("type", "ON_CALL");
        routing.put("target_ref", "ONCOLOGY");
        referral.set("routingTarget", routing);
        Mockito.when(pctClient.getReferral("ref-oc-2")).thenReturn(referral);
        Mockito.when(khulumaClient.onCallRoster("tenant-a", "ONCOLOGY"))
                .thenReturn(objectMapper.createArrayNode());

        var response = controller.submitReferral(
                "ref-oc-2", "req-oc2", "corr-oc2", "tenant-a", "TREATMENT", "fac-1", "actor-1");

        assertEquals(422, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("NO_ON_CALL_PROVIDER", error.get("code"));
        Mockito.verify(pctClient, Mockito.never()).submitReferral(any());
    }

    @Test
    void accept_passesReceivingFacilityContextThroughToPct() {
        ObjectNode accepted = objectMapper.createObjectNode();
        accepted.put("id", "ref-77");
        accepted.put("status", "ACCEPTED");
        Mockito.when(pctClient.acceptReferral(eq("ref-77"), any())).thenReturn(accepted);

        var response = controller.accept(
                "ref-77", "req-a", "corr-a", "tenant-a", "TREATMENT", "fac-1", "dr-ncube", null,
                Map.of("receiving_facility_id", "fac-99", "scheduled_at", "2026-08-01T10:00:00Z", "notes", "review am"));

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(pctClient).acceptReferral(eq("ref-77"), captor.capture());
        Map<String, Object> sent = captor.getValue();
        assertEquals("dr-ncube", sent.get("accepted_by"));
        assertEquals("fac-99", sent.get("receiving_facility_id"));
        assertEquals("2026-08-01T10:00:00Z", sent.get("scheduled_at"));
        assertEquals("review am", sent.get("notes"));
    }

    @Test
    void decline_requiresAReason() {
        var response = controller.decline(
                "ref-88", "req-d", "corr-d", "tenant-a", "TREATMENT", "fac-1", "dr-ncube",
                Map.of());

        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("DECLINE_REASON_REQUIRED", error.get("code"));
        Mockito.verify(pctClient, Mockito.never()).respondReferral(any(), any());
    }

    @Test
    void poolQueue_proxiesPctPoolListingWithGovernedReadAndAudit() {
        var rows = objectMapper.createArrayNode();
        ObjectNode row = objectMapper.createObjectNode();
        row.put("id", "ref-p1");
        row.put("status", "SUBMITTED");
        row.put("routingPoolId", "GENERAL_TELEMEDICINE");
        rows.add(row);
        Mockito.when(pctClient.listPoolReferrals(eq("GENERAL_TELEMEDICINE"), any(), eq(0), eq(50)))
                .thenReturn(rows);

        var response = controller.poolQueue(
                "GENERAL_TELEMEDICINE", "req-pq", "corr-pq", "tenant-a", "TREATMENT",
                "fac-1", "dr-moyo", null, 0, 50);

        assertEquals(200, response.getStatusCode().value());
        Mockito.verify(governanceService).assertGovernedRead();
        Mockito.verify(governanceService).audit(
                eq("tenant-a"), eq("corr-pq"), eq("TREATMENT"), eq("fac-1"),
                eq("TELEMEDICINE_POOL_QUEUE_VIEWED"), eq("GET:teleconsult/pool/queue"), eq("SUCCESS"),
                eq("dr-moyo"), eq("PROVIDER"), any(), eq("TeleconsultPool"), eq("GENERAL_TELEMEDICINE"), any());
    }

    @Test
    void routeSession_requiresAPoolId() {
        var response = controller.routeSession(
                "ref-r1", "req-r", "corr-r", "tenant-a", "TREATMENT", "fac-1", "dr-moyo",
                Map.of());

        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("MISSING_ROUTING_POOL", error.get("code"));
        Mockito.verify(pctClient, Mockito.never()).routeReferral(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeSession_proxiesPctRouteWithPoolPayload() {
        ObjectNode routed = objectMapper.createObjectNode();
        routed.put("id", "ref-r2");
        routed.put("status", "ROUTED");
        routed.put("patientCpid", "CPID-7");
        Mockito.when(pctClient.routeReferral(eq("ref-r2"), any())).thenReturn(routed);

        var response = controller.routeSession(
                "ref-r2", "req-r2", "corr-r2", "tenant-a", "TREATMENT", "fac-1", "dr-moyo",
                Map.of("routingPoolId", "MATERNAL_NEWBORN"));

        assertEquals(200, response.getStatusCode().value());
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(pctClient).routeReferral(eq("ref-r2"), payloadCaptor.capture());
        assertEquals("POOL", payloadCaptor.getValue().get("routing_kind"));
        assertEquals("MATERNAL_NEWBORN", payloadCaptor.getValue().get("routing_pool_id"));
        Mockito.verify(governanceService).assertGovernedMutate();
        Mockito.verify(governanceService).audit(
                eq("tenant-a"), eq("corr-r2"), eq("TREATMENT"), eq("fac-1"),
                eq("TELEMEDICINE_REFERRAL_ROUTED"), eq("POST:teleconsult/route"), eq("SUCCESS"),
                eq("dr-moyo"), eq("PROVIDER"), eq("CPID-7"), eq("TeleconsultReferral"), eq("ref-r2"), any());
    }
}
