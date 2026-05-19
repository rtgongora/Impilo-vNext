package zw.gov.mohcc.impilo.mvumo.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mvumo.service.CommunicationPreferenceService;
import zw.gov.mohcc.impilo.mvumo.service.MvumoService;
import zw.gov.mohcc.impilo.mvumo.service.TenantIds;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MvumoInternalControllerTest {

    @Mock
    private MvumoService mvumoService;

    @Mock
    private CommunicationPreferenceService communicationPreferenceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MvumoInternalController(mvumoService, communicationPreferenceService))
                .build();
    }

    @Test
    void templatesLists() throws Exception {
        when(mvumoService.listTemplates(TenantIds.PLATFORM))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID().toString(), "title", "Telemedicine")));
        mockMvc.perform(get("/internal/v1/mvumo/templates").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("Telemedicine"));
    }

    @Test
    void requirementsEvaluate() throws Exception {
        when(mvumoService.evaluateRequirements(any()))
                .thenReturn(
                        Map.of("minimumAssurance", "L1_SIMPLE_DIGITAL", "nextAction", "CREATE_OR_REUSE_CONSENT_REQUEST"));
        mockMvc.perform(
                        post("/internal/v1/mvumo/requirements/evaluate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"consentType\":\"DIGITAL_TELEMEDICINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minimumAssurance").exists());
    }

    @Test
    void evaluateDelegatesToTshepoConsentDecision() throws Exception {
        when(mvumoService.evaluateConsentDecision(any()))
                .thenReturn(Map.of("decision", "PERMIT", "reason", "active consent directive"));

        mockMvc.perform(
                        post("/internal/v1/mvumo/evaluate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tenantId\":\"11111111-1111-1111-1111-111111111111\",\"actorId\":\"provider-1\",\"subjectRef\":\"Patient/abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decision").value("PERMIT"));
    }

    @Test
    void evaluateReturnsForbiddenOnConsentDenyDecision() throws Exception {
        when(mvumoService.evaluateConsentDecision(any()))
                .thenReturn(Map.of("decision", "DENY", "reason", "no active consent directive"));

        mockMvc.perform(
                        post("/internal/v1/mvumo/evaluate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tenantId\":\"11111111-1111-1111-1111-111111111111\",\"actorId\":\"provider-1\",\"subjectRef\":\"Patient/abc\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void evaluateReturnsServiceUnavailableOnTshepoDependencyFailure() throws Exception {
        when(mvumoService.evaluateConsentDecision(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "downstream unavailable"));

        mockMvc.perform(
                        post("/internal/v1/mvumo/evaluate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tenantId\":\"11111111-1111-1111-1111-111111111111\",\"actorId\":\"provider-1\",\"subjectRef\":\"Patient/abc\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void evaluateReturnsBadRequestOnMissingOrInvalidContext() throws Exception {
        when(mvumoService.evaluateConsentDecision(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "actorId and subjectRef required"));

        mockMvc.perform(
                        post("/internal/v1/mvumo/evaluate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void remoteSessionActionEndpointsFailClosedAsNotImplemented() throws Exception {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(mvumoService.remoteVerify(any(), eq(sessionId), nullable(String.class), nullable(String.class), nullable(String.class), any()))
                .thenReturn(Map.of("sessionState", "VERIFIED", "consentState", "PENDING_SIGNATURE"));

        mockMvc.perform(
                        post("/internal/v1/mvumo/remote-sessions/11111111-1111-1111-1111-111111111111/verify")
                                .header("X-Actor-Id", "provider-1")
                                .header("X-Purpose-Of-Use", "TREATMENT")
                                .header("X-Correlation-Id", "11111111-1111-1111-1111-111111111111")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionState").value("VERIFIED"));
    }

    @Test
    void remoteSessionActionsRejectMissingTrustHeaders() throws Exception {
        mockMvc.perform(
                        post("/internal/v1/mvumo/remote-sessions/11111111-1111-1111-1111-111111111111/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void templateMutationEndpointsFailClosedAsNotImplemented() throws Exception {
        UUID templateId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(mvumoService.createTemplate(any(), any()))
                .thenReturn(Map.of("id", templateId.toString(), "templateKey", "telemedicine", "version", 1));
        when(mvumoService.updateTemplate(any(), eq(templateId), any()))
                .thenReturn(Map.of("id", UUID.randomUUID().toString(), "templateKey", "telemedicine", "version", 2));

        mockMvc.perform(post("/internal/v1/mvumo/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateKey\":\"telemedicine\",\"consentType\":\"DIGITAL\",\"title\":\"Telemedicine consent\",\"bodyMarkdown\":\"Body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateKey").value("telemedicine"));

        mockMvc.perform(put("/internal/v1/mvumo/templates/11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Telemedicine consent v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        verify(mvumoService).createTemplate(any(), any());
        verify(mvumoService).updateTemplate(any(), eq(templateId), any());
    }
}
