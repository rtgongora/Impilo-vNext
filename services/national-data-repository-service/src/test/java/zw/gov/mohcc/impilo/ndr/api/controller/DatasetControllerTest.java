package zw.gov.mohcc.impilo.ndr.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(NdrTestSecurityBeans.class)
class DatasetControllerTest {

    @MockBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String POD_ID = "national";

    /** Synthetic JWT accepted by {@link zw.gov.mohcc.impilo.ndr.config.NdrTestSecurityBeans}. */
    private static final String TEST_BEARER = "Bearer ndr-test-token";

    @Test
    @DisplayName("POST /internal/v1/datasets creates dataset and returns 201")
    void createDatasetReturns201() throws Exception {
        String idempotencyKey = "create-ds-" + System.nanoTime();

        mockMvc.perform(post("/internal/v1/datasets")
                        .header(HttpHeaders.AUTHORIZATION, TEST_BEARER)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetKey\":\"test_ds_" + System.nanoTime()
                                + "\",\"name\":\"Test Dataset\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datasetKey").exists())
                .andExpect(jsonPath("$.name").value("Test Dataset"));
    }

    @Test
    @DisplayName("GET /internal/v1/datasets returns 200 with list")
    void listDatasetsReturns200() throws Exception {
        mockMvc.perform(get("/internal/v1/datasets")
                        .header(HttpHeaders.AUTHORIZATION, TEST_BEARER)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-2")
                        .header("X-Correlation-ID", "corr-2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("POST /internal/v1/datasets/{key}/versions returns 404 for nonexistent key")
    void createVersionNotFound() throws Exception {
        String idempotencyKey = "version-nf-" + System.nanoTime();

        mockMvc.perform(post("/internal/v1/datasets/nonexistent/versions")
                        .header(HttpHeaders.AUTHORIZATION, TEST_BEARER)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-3")
                        .header("X-Correlation-ID", "corr-3")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schemaJson\":\"{}\",\"rowCount\":0}"))
                .andExpect(status().isNotFound());
    }
}
