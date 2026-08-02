package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService;
import zw.gov.mohcc.impilo.experience.workhome.ProfessionalAlertsComposer;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeSection;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfessionalAlertsController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProfessionalAlertsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The BFF session filters (SessionCsrfFilter, RecoverySessionFilter) are Filter

    // components, which @WebMvcTest instantiates; they need the session service bean.

    @MockBean
    private ProfessionalAlertsComposer composer;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private OidcSessionService oidcSessionService;

    @Test
    void alerts_returnsTheComposersSectionWithoutRequiringAWorkContext() throws Exception {
        WorkHomeSection section = WorkHomeSection.ok("professional-alerts", "Professional alerts",
                List.of(Map.of("id", "licence:1", "kind", "LICENCE_ALERT", "priority", "URGENT")),
                Map.of("total", 1));
        when(composer.compose(eq("actor-1"))).thenReturn(section);

        mockMvc.perform(get("/internal/v1/professional/alerts")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("X-Actor-ID", "actor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.status").value("OK"))
                .andExpect(jsonPath("$.data.attributes.items[0].kind").value("LICENCE_ALERT"));
    }

    @Test
    void alerts_composerReturnsEmpty_stillReturns200WithEmptyStatus() throws Exception {
        WorkHomeSection section = WorkHomeSection.empty("professional-alerts", "Professional alerts",
                "No provider identity resolved for this actor");
        when(composer.compose(eq("actor-2"))).thenReturn(section);

        mockMvc.perform(get("/internal/v1/professional/alerts")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("X-Actor-ID", "actor-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.status").value("EMPTY"))
                .andExpect(jsonPath("$.data.attributes.note").value("No provider identity resolved for this actor"));
    }
}
