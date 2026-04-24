package zw.gov.mohcc.impilo.datawarehouse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GoldQueryApiTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String TENANT = "00000000-0000-0000-0000-000000000001";

    @Test
    @Order(1)
    @DisplayName("GET /external/v1/gold/datasets returns dataset listing")
    void listDatasetsReturnsListing() throws Exception {
        mockMvc.perform(get("/external/v1/gold/datasets")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("encounters"))
                .andExpect(jsonPath("$[1].name").value("medications"))
                .andExpect(jsonPath("$[2].name").value("labs"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /internal/v1/gold/query with unknown dataset returns 400")
    void queryUnknownDatasetReturns400() throws Exception {
        mockMvc.perform(get("/internal/v1/gold/query")
                        .param("dataset", "nonexistent")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-2")
                        .header("X-Correlation-ID", "corr-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNKNOWN_DATASET"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /internal/v1/gold/query with encounters returns empty page")
    void queryEncountersReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/internal/v1/gold/query")
                        .param("dataset", "encounters")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-3")
                        .header("X-Correlation-ID", "corr-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataset").value("encounters"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /internal/v1/gold/stats returns materializer stats")
    void statsReturnsStats() throws Exception {
        mockMvc.perform(get("/internal/v1/gold/stats")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-4")
                        .header("X-Correlation-ID", "corr-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gold_encounters").value(0));
    }

    @Test
    @Order(5)
    @DisplayName("POST /internal/v1/gold/materialize processes encounter event")
    void materializeEncounterEvent() throws Exception {
        String body = """
                {
                    "eventId": "evt-mat-1",
                    "eventType": "impilo.clinical.encounter.created.v1",
                    "envelopeJson": "{\\"data\\": {\\"encounter_id\\": \\"enc-mat-1\\", \\"patient_id\\": \\"pat-1\\"}}"
                }
                """;

        mockMvc.perform(post("/internal/v1/gold/materialize")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-5")
                        .header("X-Correlation-ID", "corr-5")
                        .header("Idempotency-Key", "idem-mat-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("evt-mat-1"))
                .andExpect(jsonPath("$.upsertedCount").value(1));
    }
}
