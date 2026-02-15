package zw.gov.mohcc.impilo.dags.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.dags.config.TestSecurityConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String TENANT_ID = UUID.randomUUID().toString();
    private static final String CORRELATION_ID = UUID.randomUUID().toString();

    @Nested
    @DisplayName("POST /internal/v1/policies")
    class CreatePolicy {

        @Test
        void createsPolicySuccessfully() throws Exception {
            String body = """
                    {
                        "name": "PHI Access Policy",
                        "description": "Controls access to patient health information",
                        "resourceType": "PATIENT_RECORD",
                        "conditions": "{\\"role\\": \\"doctor\\"}",
                        "effect": "ALLOW"
                    }
                    """;

            mockMvc.perform(post("/internal/v1/policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "pod-1")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", CORRELATION_ID)
                            .header("X-Actor-ID", "admin-1")
                            .header("X-Actor-Type", "USER")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("PHI Access Policy"))
                    .andExpect(jsonPath("$.data.resourceType").value("PATIENT_RECORD"))
                    .andExpect(jsonPath("$.data.effect").value("ALLOW"));
        }
    }

    @Nested
    @DisplayName("GET /internal/v1/policies")
    class ListPolicies {

        @Test
        void listsPolicies() throws Exception {
            mockMvc.perform(get("/internal/v1/policies")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "pod-1")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", CORRELATION_ID)
                            .header("X-Actor-ID", "admin-1")
                            .header("X-Actor-Type", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
