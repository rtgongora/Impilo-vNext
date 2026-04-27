package zw.gov.mohcc.impilo.mvumo.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.mvumo.service.CommunicationPreferenceService;
import zw.gov.mohcc.impilo.mvumo.service.MvumoService;
import zw.gov.mohcc.impilo.mvumo.service.TenantIds;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
