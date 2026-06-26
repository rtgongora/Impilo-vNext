package zw.gov.mohcc.impilo.rito.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RitoCaseControllerWebTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void createComplimentThenListReturnsOk() throws Exception {
        UUID tenant = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/rito/cases")
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Pod-ID", "pod-1")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-ID", "citizen-1")
                        .header("X-Actor-Type", "CITIZEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseType\":\"COMPLIMENT\",\"title\":\"Great care\",\"source\":\"WEB_PORTAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseReference").exists())
                .andExpect(jsonPath("$.caseReference").value(org.hamcrest.Matchers.startsWith("RITO-C")));

        mockMvc.perform(get("/internal/v1/rito/cases")
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Pod-ID", "pod-1")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }
}
