package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeCompositionService;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeResult;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeSection;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkHomeController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkHomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The BFF session filters (SessionCsrfFilter, RecoverySessionFilter) are Filter

    // components, which @WebMvcTest instantiates; they need the session service bean.

    @MockBean

    private zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService oidcSessionService;


    @MockBean
    private WorkHomeCompositionService compositionService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private OidcSessionService oidcSessionService;

    @Test
    void workHome_returns200EvenWhenFriendlyStateIsUnavailable() throws Exception {
        when(compositionService.compose(eq("actor-1"), any(), eq("ctx-1"), any()))
                .thenReturn(WorkHomeResult.unavailable("work_context_unavailable"));

        mockMvc.perform(get("/internal/v1/work-home")
                        .param("context_id", "ctx-1")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("X-Actor-ID", "actor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.friendlyState").value("work_context_unavailable"))
                .andExpect(jsonPath("$.data.attributes.sections").isEmpty());
    }

    @Test
    void workHome_returnsSectionsFromTheCompositionService() throws Exception {
        WorkHomeSection section = WorkHomeSection.ok("clinical-worklist", "Clinical worklist",
                List.of(Map.of("id", "1", "status", "OPEN", "priority", "HIGH")), Map.of("total", 1));
        when(compositionService.compose(eq("actor-1"), any(), eq("ctx-1"), any()))
                .thenReturn(new WorkHomeResult("ctx-1", "FACILITY_CLINICAL", "CLINICAL_CARE", null, List.of(section)));

        mockMvc.perform(get("/internal/v1/work-home")
                        .param("context_id", "ctx-1")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("X-Actor-ID", "actor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.family").value("FACILITY_CLINICAL"))
                .andExpect(jsonPath("$.data.attributes.sections[0].sectionId").value("clinical-worklist"))
                .andExpect(jsonPath("$.data.attributes.sections[0].status").value("OK"));
    }

    @Test
    void workHomeSection_returnsTheSingleRequestedSection() throws Exception {
        WorkHomeSection section = WorkHomeSection.degraded("clinical-worklist", "Clinical worklist", "timed out");
        when(compositionService.composeSection(eq("actor-1"), any(), eq("ctx-1"), any(), eq("clinical-worklist")))
                .thenReturn(section);

        mockMvc.perform(get("/internal/v1/work-home/sections/clinical-worklist")
                        .param("context_id", "ctx-1")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("X-Actor-ID", "actor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.status").value("DEGRADED"))
                .andExpect(jsonPath("$.data.attributes.note").value("timed out"));
    }
}
