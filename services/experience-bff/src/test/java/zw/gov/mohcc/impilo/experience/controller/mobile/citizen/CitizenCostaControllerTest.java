package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CitizenCostaController.class)
@AutoConfigureMockMvc(addFilters = false)
class CitizenCostaControllerTest {

    @Autowired private MockMvc mockMvc;
    // The BFF session filters (SessionCsrfFilter, RecoverySessionFilter) are Filter
    // components, which @WebMvcTest instantiates; they need the session service bean.
    @MockBean private CostaServiceClient costaClient;
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private OidcSessionService oidcSessionService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void pending_charges_passes_through_real_costa_outstanding_bills() throws Exception {
        var outstanding = MAPPER.readTree("""
                [{"billId":"BILL-1","facilityId":"f-1","balanceDue":42.50,"currency":"USD"}]
                """);
        when(costaClient.getFinancePatientOutstanding(eq("HID-1"))).thenReturn(outstanding);

        mockMvc.perform(get("/internal/v1/mobile/citizen/costa/charges/pending")
                        .header("X-Tenant-ID", "t-1")
                        .header("X-Request-ID", "r-1")
                        .header("X-Correlation-ID", "c-1")
                        .header("X-Actor-ID", "HID-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].billId").value("BILL-1"))
                .andExpect(jsonPath("$.data[0].balanceDue").value(42.50))
                .andExpect(jsonPath("$.meta.request_id").value("r-1"));
    }

    @Test
    void pending_charges_fails_soft_to_empty_when_costa_unavailable() throws Exception {
        when(costaClient.getFinancePatientOutstanding(eq("HID-2")))
                .thenThrow(new RuntimeException("COSTA down"));

        mockMvc.perform(get("/internal/v1/mobile/citizen/costa/charges/pending")
                        .header("X-Tenant-ID", "t-1")
                        .header("X-Request-ID", "r-1")
                        .header("X-Correlation-ID", "c-1")
                        .header("X-Actor-ID", "HID-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
