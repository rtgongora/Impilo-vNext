package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService;
import zw.gov.mohcc.impilo.experience.service.patientlane.PatientLaneService;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CitizenVisitStatusController.class)
@AutoConfigureMockMvc(addFilters = false)
class CitizenVisitStatusControllerTest {

    @Autowired private MockMvc mockMvc;
    // The BFF session filters (SessionCsrfFilter, RecoverySessionFilter) are Filter
    // components, which @WebMvcTest instantiates; they need the session service bean.
    @MockBean private PatientLaneService patientLane;
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private OidcSessionService oidcSessionService;

    @Test
    void visit_status_returns_composed_patient_facing_body() throws Exception {
        when(patientLane.visitStatus("TX-1")).thenReturn(Map.of(
                "transactionId", "TX-1", "available", true, "state", "QUEUED",
                "statusLabel", "In the queue", "message", "You're in the queue."));

        mockMvc.perform(get("/internal/v1/mobile/citizen/visit/TX-1/status")
                        .header("X-Tenant-ID", "t-1")
                        .header("X-Request-ID", "r-1")
                        .header("X-Correlation-ID", "c-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("QUEUED"))
                .andExpect(jsonPath("$.data.statusLabel").value("In the queue"))
                .andExpect(jsonPath("$.meta.request_id").value("r-1"));
    }

    @Test
    void inpatient_status_returns_composed_body() throws Exception {
        when(patientLane.inpatientStatus("ADM-1")).thenReturn(Map.of(
                "admissionRef", "ADM-1", "available", true, "statusLabel", "On the ward"));

        mockMvc.perform(get("/internal/v1/mobile/citizen/inpatient/ADM-1/status")
                        .header("X-Tenant-ID", "t-1")
                        .header("X-Request-ID", "r-1")
                        .header("X-Correlation-ID", "c-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusLabel").value("On the ward"));
    }
}
