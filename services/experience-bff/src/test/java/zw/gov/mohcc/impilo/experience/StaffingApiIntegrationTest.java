package zw.gov.mohcc.impilo.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Smoke tests for staffing endpoints (Flyway V29+): roster-week, on-call, swaps.
 * Uses UI-default tenant and V2 Harare Central facility (V30 seed).
 * <p>
 * <b>Windows + Docker Desktop 29+:</b> the bundled docker-java client often gets HTTP 400 on the
 * named pipe. Set {@code EXPERIENCE_BFF_TEST_JDBC_URL} to a reachable Postgres (e.g. compose
 * {@code experience-db} on {@code jdbc:postgresql://localhost:5433/experience_bff}) and optional
 * {@code EXPERIENCE_BFF_TEST_DB_USER} / {@code EXPERIENCE_BFF_TEST_DB_PASSWORD} (default {@code impilo}).
 * When unset, an embedded Testcontainers Postgres is started (typical Linux CI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StaffingApiIntegrationTest {

    private static final String EXTERNAL_JDBC = System.getenv("EXPERIENCE_BFF_TEST_JDBC_URL");
    private static final PostgreSQLContainer<?> POSTGRES;
    private static final boolean USE_TESTCONTAINERS;

    static {
        boolean useTc = EXTERNAL_JDBC == null || EXTERNAL_JDBC.isBlank();
        USE_TESTCONTAINERS = useTc;
        if (useTc) {
            PostgreSQLContainer<?> c = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("experience_bff_staffing")
                    .withUsername("test")
                    .withPassword("test");
            c.start();
            POSTGRES = c;
        } else {
            POSTGRES = null;
        }
    }

    @AfterAll
    static void stopContainer() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        if (USE_TESTCONTAINERS) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_JDBC);
            registry.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("EXPERIENCE_BFF_TEST_DB_USER", "impilo"));
            registry.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("EXPERIENCE_BFF_TEST_DB_PASSWORD", "impilo"));
            // application-test.yml defaults to ContainerDatabaseDriver — not valid for a plain Postgres URL.
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TENANT = "tenant-moh-zw";
    private static final String POD = "national-spine";
    /** V2 Harare Central Hospital */
    private static final String FACILITY_ID = "a1b2c3d4-0001-4000-8000-000000000001";

    private static String requestId() {
        return UUID.randomUUID().toString();
    }

    private static String correlationId() {
        return UUID.randomUUID().toString();
    }

    @Test
    @Order(1)
    @DisplayName("GET roster-week returns JSON:API staffing shifts array")
    void rosterWeekOk() throws Exception {
        mockMvc.perform(get("/internal/v1/staffing/roster-week")
                        .param("facility_id", FACILITY_ID)
                        .param("week_start", "2026-04-06")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", requestId())
                        .header("X-Correlation-ID", correlationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.week_start").value("2026-04-06"));
    }

    @Test
    @Order(2)
    @DisplayName("GET on-call returns assignments for tenant-moh-zw facility")
    void onCallWeekOk() throws Exception {
        mockMvc.perform(get("/internal/v1/staffing/on-call")
                        .param("facility_id", FACILITY_ID)
                        .param("week_start", "2026-04-06")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", requestId())
                        .header("X-Correlation-ID", correlationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].type").value("OnCallAssignment"));
    }

    @Test
    @Order(3)
    @DisplayName("GET swaps lists seeded pending request")
    void listSwapsOk() throws Exception {
        mockMvc.perform(get("/internal/v1/staffing/on-call/swaps")
                        .param("facility_id", FACILITY_ID)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", requestId())
                        .header("X-Correlation-ID", correlationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].attributes.status").exists());
    }

    @Test
    @Order(4)
    @DisplayName("POST swap then PATCH approve")
    void createAndPatchSwap() throws Exception {
        MvcResult created = mockMvc.perform(post("/internal/v1/staffing/on-call/swaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "facility_id", FACILITY_ID,
                                "requestor_name", "Dr. A",
                                "requestee_name", "Dr. B",
                                "original_date", "2026-05-01",
                                "swap_date", "2026-05-02",
                                "specialty", "Test Specialty"
                        )))
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", requestId())
                        .header("X-Correlation-ID", correlationId())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = root.path("data").path("id").asText();

        mockMvc.perform(patch("/internal/v1/staffing/on-call/swaps/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", requestId())
                        .header("X-Correlation-ID", correlationId())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.status").value("APPROVED"));
    }
}
