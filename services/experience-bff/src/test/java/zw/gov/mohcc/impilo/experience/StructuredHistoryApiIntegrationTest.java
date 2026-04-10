package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flyway V32 structured history endpoints for EHR continuity (seeded patient from V4).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class StructuredHistoryApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("experience_bff_structured_history")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    private static final String TENANT = "moh-zw";
    private static final String POD = "national";
    /** Tatenda Moyo — V4 golden path */
    private static final String PATIENT_ID = "a1000000-0000-0000-0000-000000000001";

    @Test
    @DisplayName("GET structured history endpoints return seeded data for golden-path patient")
    void structuredHistoryGoldenPatient() throws Exception {
        String rid = UUID.randomUUID().toString();
        String cid = UUID.randomUUID().toString();

        mockMvc.perform(get("/internal/v1/ehr/social-history")
                        .param("patient_id", PATIENT_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", rid)
                        .header("X-Correlation-ID", cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].category").exists());

        mockMvc.perform(get("/internal/v1/ehr/family-history")
                        .param("patient_id", PATIENT_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", rid)
                        .header("X-Correlation-ID", cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].conditions").isArray());

        mockMvc.perform(get("/internal/v1/ehr/functional-assessments")
                        .param("patient_id", PATIENT_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", rid)
                        .header("X-Correlation-ID", cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].activities").isArray());

        mockMvc.perform(get("/internal/v1/ehr/procedures")
                        .param("patient_id", PATIENT_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", rid)
                        .header("X-Correlation-ID", cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").exists());

        mockMvc.perform(get("/internal/v1/ehr/advance-directives")
                        .param("patient_id", PATIENT_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", rid)
                        .header("X-Correlation-ID", cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").exists());
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
