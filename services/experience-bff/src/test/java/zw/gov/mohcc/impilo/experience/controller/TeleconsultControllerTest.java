package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.AnalyticsPipelineServiceClient;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.FhirGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.RtcGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineGovernanceService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TeleconsultControllerTest {

    private PctServiceClient pctClient;
    private MvumoServiceClient mvumoClient;
    private CostaServiceClient costaClient;
    private AnalyticsPipelineServiceClient analyticsClient;
    private RtcGatewayServiceClient rtcClient;
    private NotificationServiceClient notificationClient;

    private TelemedicineGovernanceService governanceService;
    private TeleconsultController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        pctClient = Mockito.mock(PctServiceClient.class);
        mvumoClient = Mockito.mock(MvumoServiceClient.class);
        DocumentServiceClient documentClient = Mockito.mock(DocumentServiceClient.class);
        VarapiServiceClient varapiClient = Mockito.mock(VarapiServiceClient.class);
        TusoServiceClient tusoClient = Mockito.mock(TusoServiceClient.class);
        notificationClient = Mockito.mock(NotificationServiceClient.class);
        FhirGatewayServiceClient fhirGatewayClient = Mockito.mock(FhirGatewayServiceClient.class);
        costaClient = Mockito.mock(CostaServiceClient.class);
        analyticsClient = Mockito.mock(AnalyticsPipelineServiceClient.class);
        rtcClient = Mockito.mock(RtcGatewayServiceClient.class);
        governanceService = Mockito.mock(TelemedicineGovernanceService.class);
        controller = new TeleconsultController(
                pctClient, mvumoClient, documentClient, varapiClient, tusoClient,
                notificationClient, fhirGatewayClient, costaClient, analyticsClient, rtcClient,
                governanceService, objectMapper
        );
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
}
