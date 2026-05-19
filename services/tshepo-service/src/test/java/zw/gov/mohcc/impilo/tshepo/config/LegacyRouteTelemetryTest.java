package zw.gov.mohcc.impilo.tshepo.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.tshepo.api.AuthorizeController;
import zw.gov.mohcc.impilo.tshepo.api.LegacyRouteUsageController;
import zw.gov.mohcc.impilo.tshepo.core.PolicyEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegacyRouteTelemetryTest {

    private MockMvc mockMvc;
    private LegacyRouteUsageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new LegacyRouteUsageTracker();
        var filter = new LegacyRouteDeprecationFilter(tracker);
        var authorize = new AuthorizeController(mock(PolicyEngine.class));
        var usage = new LegacyRouteUsageController(tracker);

        mockMvc = MockMvcBuilders
                .standaloneSetup(authorize, usage)
                .addFilters(filter)
                .build();
    }

    @Test
    void legacyRouteAccessProducesDeprecationHeadersAndUsageEvidence() throws Exception {
        mockMvc.perform(post("/v1/authorize")
                        .header("X-Actor-Id", "provider-1")
                        .header("X-Caller-Service", "vito-service")
                        .header("X-Correlation-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Deprecation", "true"));

        var snapshot = mockMvc.perform(get("/internal/v1/legacy/route-usage"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(snapshot).contains("POST /v1/authorize");
        assertThat(snapshot).contains("provider-1");
        assertThat(snapshot).contains("vito-service");
        assertThat(snapshot).contains("11111111-1111-1111-1111-111111111111");
    }
}
