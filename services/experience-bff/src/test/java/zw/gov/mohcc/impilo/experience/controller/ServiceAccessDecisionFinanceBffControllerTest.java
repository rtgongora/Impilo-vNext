package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.finance.FinancePlaneAuthorizationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ServiceAccessDecisionFinanceBffControllerTest {

    @Mock
    private CostaServiceClient costaClient;
    @Mock
    private FinancePlaneAuthorizationService financePlaneAuthorizationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(
                        new ServiceAccessDecisionFinanceBffController(costaClient, financePlaneAuthorizationService))
                .build();
    }

    @Test
    void postProxiesToCostaAndWraps() throws Exception {
        var data = objectMapper.readTree(
                "{\"service_access_decision_id\":\"550e8400-e29b-41d4-a716-446655440000\",\"access_status\":\"EXEMPT\"}");
        when(costaClient.postServiceAccessDecision(any())).thenReturn(data);

        mockMvc().perform(post("/internal/v1/finance/service-access-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CompanionHeaders.CORRELATION_ID, "corr-x")
                        .content("{\"access_status\":\"EXEMPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_status").value("EXEMPT"))
                .andExpect(jsonPath("$.meta.correlation_id").value("corr-x"));

        verify(financePlaneAuthorizationService).assertServiceAccessDecisionAccess("POST");
    }

    @Test
    void listForwardsEncounterQuery() throws Exception {
        when(costaClient.getServiceAccessDecisions("enc-1")).thenReturn(objectMapper.readTree("[{\"id\":\"1\"}]"));

        mockMvc().perform(get("/internal/v1/finance/service-access-decisions")
                        .param("encounter_id", "enc-1")
                        .header(CompanionHeaders.CORRELATION_ID, "c2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("1"));

        verify(financePlaneAuthorizationService).assertServiceAccessDecisionAccess("GET");
        verify(costaClient).getServiceAccessDecisions("enc-1");
    }

    @Test
    void getByIdProxies() throws Exception {
        when(costaClient.getServiceAccessDecision("abc")).thenReturn(
                objectMapper.readTree("{\"service_access_decision_id\":\"abc\"}"));

        mockMvc().perform(get("/internal/v1/finance/service-access-decisions/abc")
                        .header(CompanionHeaders.CORRELATION_ID, "c3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.service_access_decision_id").value("abc"));

        verify(financePlaneAuthorizationService).assertServiceAccessDecisionAccess("GET");
    }
}
