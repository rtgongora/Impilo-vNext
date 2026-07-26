package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flyway V32 structured history endpoints for EHR continuity (seeded patient from V4).
 *
 * <p>Uses shared Redis preflight: Testcontainers when Docker is available, or external Redis via
 * EXPERIENCE_BFF_TEST_REDIS_HOST and optional EXPERIENCE_BFF_TEST_REDIS_PORT.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(DockerOrExternalPostgresCondition.class)
class StructuredHistoryApiIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        ExperienceBffTestRedisSupport.configure(StructuredHistoryApiIntegrationTest.class, registry);
    }

    @Autowired
    private MockMvc mockMvc;

    private static final String TENANT = "moh-zw";
    private static final String POD = "national";
    /** Tatenda Moyo — V4 golden path */
    private static final String PATIENT_ID = "a1000000-0000-0000-0000-000000000001";

    @Test
    @DisplayName("GET structured history reports 502 when PCT is unreachable, not an empty history")
    void structuredHistoryGoldenPatient() throws Exception {
        String rid = UUID.randomUUID().toString();
        String cid = UUID.randomUUID().toString();

        mockMvc.perform(get("/internal/v1/ehr/social-history")
                        .param("patient_id", PATIENT_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", rid)
                        .header("X-Correlation-ID", cid))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("social_history_unavailable"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/internal/v1/ehr/family-history")
                        .param("patient_id", PATIENT_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", rid)
                        .header("X-Correlation-ID", cid))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("family_history_unavailable"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/internal/v1/ehr/functional-assessments")
                        .param("patient_id", PATIENT_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", rid)
                        .header("X-Correlation-ID", cid))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("functional_assessments_unavailable"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/internal/v1/ehr/procedures")
                        .param("patient_id", PATIENT_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", rid)
                        .header("X-Correlation-ID", cid))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("procedure_history_unavailable"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/internal/v1/ehr/advance-directives")
                        .param("patient_id", PATIENT_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", rid)
                        .header("X-Correlation-ID", cid))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("advance_directives_unavailable"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("GET structured history with invalid patient_id returns 400")
    void invalidPatientId() throws Exception {
        mockMvc.perform(get("/internal/v1/ehr/social-history")
                        .param("patient_id", "not-a-uuid")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }
}
