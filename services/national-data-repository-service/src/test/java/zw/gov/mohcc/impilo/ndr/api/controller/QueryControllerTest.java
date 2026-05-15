package zw.gov.mohcc.impilo.ndr.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.ndr.config.NdrTestSecurityBeans;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(NdrTestSecurityBeans.class)
class QueryControllerTest {

    @MockBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MockMvc mockMvc;

    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String POD_ID = "national";

    private static final String TEST_BEARER = "Bearer ndr-test-token";

    @Test
    @DisplayName("POST /internal/v1/query returns conflict with canonical runtime owner")
    void queryReturnsConflictOwnerMoved() throws Exception {
        String idempotencyKey = "query-1-" + System.nanoTime();

        mockMvc.perform(post("/internal/v1/query")
                        .header(HttpHeaders.AUTHORIZATION, TEST_BEARER)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-q1")
                        .header("X-Correlation-ID", "corr-q1")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetKey\":\"facility_summary\",\"filters\":{}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NDR_RUNTIME_OWNER_CONFLICT"))
                .andExpect(jsonPath("$.error.details.runtime_owner").value("ndr-service"))
                .andExpect(jsonPath("$.error.details.requested_dataset_key").value("facility_summary"));
    }

    @Test
    @DisplayName("POST /internal/v1/query is conflict for unknown dataset too")
    void queryUnknownDatasetAlsoConflict() throws Exception {
        String idempotencyKey = "query-2-" + System.nanoTime();

        mockMvc.perform(post("/internal/v1/query")
                        .header(HttpHeaders.AUTHORIZATION, TEST_BEARER)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-q2")
                        .header("X-Correlation-ID", "corr-q2")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetKey\":\"no_such_key\",\"filters\":{}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NDR_RUNTIME_OWNER_CONFLICT"))
                .andExpect(jsonPath("$.error.details.requested_dataset_key").value("no_such_key"));
    }
}
