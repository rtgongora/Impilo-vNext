package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClinicalKnowledgeControllerRouteCompatibilityTest {

    private static final String ASK_PAYLOAD = "{\"question\":\"dose guidance\"}";

    @Test
    void supportsCanonicalAndCompatibilityPrefixesForAssistantAsk() throws Exception {
        ClinicalKnowledgePlatformClient clinicalClient = mock(ClinicalKnowledgePlatformClient.class);
        ObjectNode data = new ObjectMapper().createObjectNode().put("answer", "ok");
        when(clinicalClient.assistantAsk(anyMap())).thenReturn(data);

        ClinicalKnowledgeController controller = new ClinicalKnowledgeController(clinicalClient);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        assertAskRoute(mockMvc, "/internal/v1/clinical/assistant/ask");
        assertAskRoute(mockMvc, "/internal/v1/clinical-knowledge/assistant/ask");

        verify(clinicalClient, times(2)).assistantAsk(anyMap());
    }

    private static void assertAskRoute(MockMvc mockMvc, String path) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CompanionHeaders.TENANT_ID, "tenant-a")
                        .header(CompanionHeaders.REQUEST_ID, "req-1")
                        .header(CompanionHeaders.CORRELATION_ID, "corr-1")
                        .content(ASK_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("ok"));
    }
}
