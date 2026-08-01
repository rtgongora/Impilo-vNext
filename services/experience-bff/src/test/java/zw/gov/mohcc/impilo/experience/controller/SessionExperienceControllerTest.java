package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionExperienceController.class)
@AutoConfigureMockMvc(addFilters = false)
class SessionExperienceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The BFF session filters (SessionCsrfFilter, RecoverySessionFilter) are Filter

    // components, which @WebMvcTest instantiates; they need the session service bean.

    @MockBean

    private zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService oidcSessionService;


    @MockBean
    private SessionExperienceService sessionExperienceService;

    @MockBean
    private VarapiServiceClient varapiServiceClient;

    @MockBean
    private WorkforceGovernanceClient workforceGovernanceClient;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void getSessionExperienceReturnsContractEnvelope() throws Exception {
        org.mockito.Mockito.when(sessionExperienceService.buildExperienceContract(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of(
                        "authenticated", true,
                        "tabs", java.util.Map.of(
                                "personal", java.util.Map.of("visible", true),
                                "professional", java.util.Map.of("visible", false),
                                "work", java.util.Map.of("visible", false)
                        )
                ));

        mockMvc.perform(get("/internal/v1/session/experience")
                        .header("X-Request-Id", "req-1")
                        .header("X-Correlation-Id", "corr-1")
                        .header("X-Actor-Id", "actor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("session-experience"))
                .andExpect(jsonPath("$.data.attributes.authenticated").value(true));
    }
}
