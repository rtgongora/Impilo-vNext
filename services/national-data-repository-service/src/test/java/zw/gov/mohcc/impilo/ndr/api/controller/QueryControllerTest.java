package zw.gov.mohcc.impilo.ndr.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String POD_ID = "national";

    @Test
    @DisplayName("POST /internal/v1/query returns 200 with results")
    void queryReturns200() throws Exception {
        String idempotencyKey = "query-1-" + System.nanoTime();

        mockMvc.perform(post("/internal/v1/query")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-q1")
                        .header("X-Correlation-ID", "corr-q1")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetKey\":\"facility_summary\",\"filters\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetKey").value("facility_summary"))
                .andExpect(jsonPath("$.totalRows").isNumber())
                .andExpect(jsonPath("$.rows").isArray());
    }

    @Test
    @DisplayName("POST /internal/v1/query returns empty rows for unknown dataset")
    void queryUnknownDataset() throws Exception {
        String idempotencyKey = "query-2-" + System.nanoTime();

        mockMvc.perform(post("/internal/v1/query")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-q2")
                        .header("X-Correlation-ID", "corr-q2")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetKey\":\"no_such_key\",\"filters\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(0))
                .andExpect(jsonPath("$.rows").isEmpty());
    }
}
