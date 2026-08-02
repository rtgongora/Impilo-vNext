package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkContextController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The BFF session filters (SessionCsrfFilter, RecoverySessionFilter) are Filter

    // components, which @WebMvcTest instantiates; they need the session service bean.

    @MockBean
    private VashandiServiceClient vashandiServiceClient;

    @MockBean
    private zw.gov.mohcc.impilo.experience.client.VarapiServiceClient varapiServiceClient;

    @MockBean
    private zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient tshepoIdentityServiceClient;

    @MockBean
    private zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient organizationRegistryServiceClient;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private OidcSessionService oidcSessionService;

    @MockBean
    private zw.gov.mohcc.impilo.experience.workcontext.WorkContextResolutionService resolutionService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void returnsLiveWorkContextEnvelope() throws Exception {
        String upstream = """
                {
                  "workforceProfileId": "00000000-0000-0000-0000-000000000001",
                  "impiloHealthId": "HID-1",
                  "activeAssignments": [
                    {"assignmentId": "00000000-0000-0000-0000-0000000000a1", "status": "ACTIVE", "scope": "DEPARTMENT"}
                  ],
                  "checkIn": {"state": "CHECKED_IN"},
                  "affiliations": [],
                  "requiresContextChooser": false,
                  "resolved": true
                }
                """;
        when(vashandiServiceClient.fetchWorkContext(anyString())).thenReturn(MAPPER.readTree(upstream));

        mockMvc.perform(get("/internal/v1/work-context")
                        .header("X-Request-Id", "req-1")
                        .header("X-Correlation-Id", "corr-1")
                        .header("X-Actor-Id", "HID-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("work-context"))
                .andExpect(jsonPath("$.data.attributes.resolved").value(true))
                .andExpect(jsonPath("$.data.attributes.integrationStatus").value("LIVE"))
                .andExpect(jsonPath("$.data.attributes.activeAssignments[0].scope").value("DEPARTMENT"));
    }

    @Test
    void degradesHonestlyWhenUpstreamUnavailable() throws Exception {
        when(vashandiServiceClient.fetchWorkContext(anyString())).thenReturn(null);

        mockMvc.perform(get("/internal/v1/work-context")
                        .header("X-Request-Id", "req-2")
                        .header("X-Correlation-Id", "corr-2")
                        .header("X-Actor-Id", "HID-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.resolved").value(false))
                .andExpect(jsonPath("$.data.attributes.integrationStatus").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.attributes.checkIn.state").value("CHECKED_OUT"));
    }
}
