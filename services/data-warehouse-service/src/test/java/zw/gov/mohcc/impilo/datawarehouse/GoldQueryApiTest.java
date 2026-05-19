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
import zw.gov.mohcc.impilo.datawarehouse.domain.GoldEncounterEntity;
import zw.gov.mohcc.impilo.datawarehouse.repository.GoldEncounterRepository;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GoldQueryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GoldEncounterRepository encounterRepository;

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
    @DisplayName("GET /internal/v1/gold/query only returns rows for request tenant")
    void queryEncountersIsTenantScoped() throws Exception {
        encounterRepository.deleteAll();

        GoldEncounterEntity tenantOne = new GoldEncounterEntity();
        tenantOne.setTenantId(UUID.fromString(TENANT));
        tenantOne.setEncounterId("enc-tenant-1");
        tenantOne.setPatientId("pat-1");
        tenantOne.setSourceEventId("evt-1");
        tenantOne.setSourceEventType("encounter.created");
        encounterRepository.save(tenantOne);

        GoldEncounterEntity tenantTwo = new GoldEncounterEntity();
        tenantTwo.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        tenantTwo.setEncounterId("enc-tenant-2");
        tenantTwo.setPatientId("pat-2");
        tenantTwo.setSourceEventId("evt-2");
        tenantTwo.setSourceEventType("encounter.created");
        encounterRepository.save(tenantTwo);

        mockMvc.perform(get("/internal/v1/gold/query")
                        .param("dataset", "encounters")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-3b")
                        .header("X-Correlation-ID", "corr-3b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].encounterId").value("enc-tenant-1"));
    }

    @Test
    @Order(5)
    @DisplayName("GET /internal/v1/gold/stats returns materializer stats")
    void statsReturnsStats() throws Exception {
        mockMvc.perform(get("/internal/v1/gold/stats")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-4")
                        .header("X-Correlation-ID", "corr-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gold_encounters").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @Order(6)
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

    @Test
    @Order(7)
    @DisplayName("POST /internal/v1/gold/materialize rejects malformed envelope")
    void materializeRejectsMalformedEnvelope() throws Exception {
        String body = """
                {
                    "eventId": "evt-mat-2",
                    "eventType": "impilo.lab.observation.v1",
                    "envelopeJson": "not-json{{"
                }
                """;

        mockMvc.perform(post("/internal/v1/gold/materialize")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-6")
                        .header("X-Correlation-ID", "corr-6")
                        .header("Idempotency-Key", "idem-mat-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.eventId").value("evt-mat-2"))
                .andExpect(jsonPath("$.status").value("INVALID_ENVELOPE"))
                .andExpect(jsonPath("$.upsertedCount").value(0));
    }
}
