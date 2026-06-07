package zw.gov.mohcc.impilo.wellness.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP + PostgreSQL + Flyway using a real Postgres container (no mocked controllers).
 * Skipped automatically when Docker is not available ({@link EnabledIfDockerAvailable}).
 */
@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
            "impilo.security.allow-anonymous=true",
        })
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
class WellnessCitizenApiDockerIntegrationTest {

    private static final String TENANT = "tenant-moh-zw";
    private static final String PATIENT = "it-cpid-" + UUID.randomUUID();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wellness_it")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthConnectManifest_returnsDataEnvelope() throws Exception {
        mvc.perform(get("/internal/v1/wellness/connect/v1/manifest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.api").value("impilo.wellness.connect.v1"))
                .andExpect(jsonPath("$.data.supportedRecordTypes").isArray());
    }

    @Test
    void logActivity_then_list_containsRow() throws Exception {
        mvc.perform(post("/internal/v1/mobile/citizen/wellness/activities")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", PATIENT,
                                "steps", 4200,
                                "waterMl", 750,
                                "activeMinutes", 45))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mvc.perform(get("/internal/v1/mobile/citizen/wellness/activities")
                        .header("X-Tenant-ID", TENANT)
                        .param("patientId", PATIENT)
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].steps").value(4200));
    }

    @Test
    void pairDevice_then_list_returnsDevice() throws Exception {
        mvc.perform(post("/internal/v1/mobile/citizen/monitoring/devices")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", PATIENT,
                                "deviceName", "IT BP cuff",
                                "deviceType", "BLOOD_PRESSURE",
                                "manufacturer", "ACME",
                                "model", "X1",
                                "connectionType", "BLUETOOTH"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAIRED"));

        mvc.perform(get("/internal/v1/mobile/citizen/monitoring/devices")
                        .header("X-Tenant-ID", TENANT)
                        .param("patientId", PATIENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].device_name").value("IT BP cuff"));
    }

    @Test
    void pairDevice_syncWithReading_then_vitalsContainDeviceSync() throws Exception {
        String pairResponse = mvc.perform(post("/internal/v1/mobile/citizen/monitoring/devices")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", PATIENT,
                                "deviceName", "IT BP cuff",
                                "deviceType", "BLOOD_PRESSURE",
                                "manufacturer", "ACME",
                                "model", "X1",
                                "connectionType", "BLUETOOTH"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String deviceId = objectMapper.readTree(pairResponse).path("data").path("id").asText();

        mvc.perform(post("/internal/v1/mobile/citizen/monitoring/devices/" + deviceId + "/sync")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "readings", List.of(Map.of("value", 121, "unit", "mmHg"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readingsIngested").value(1));

        mvc.perform(get("/internal/v1/mobile/citizen/wellness/vitals")
                        .header("X-Tenant-ID", TENANT)
                        .param("patientId", PATIENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].source").value("DEVICE_SYNC"));
    }
}
