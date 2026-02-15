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
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.dags.config.TestSecurityConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AccessRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String TENANT_ID = UUID.randomUUID().toString();
    private static final String CORRELATION_ID = UUID.randomUUID().toString();

    @Nested
    @DisplayName("POST /internal/v1/access-requests")
    class SubmitRequest {

        @Test
        void submitsAccessRequestSuccessfully() throws Exception {
            String body = """
                    {
                        "resourceType": "PATIENT_RECORD",
                        "resourceId": "patient-abc",
                        "purpose": "Clinical review",
                        "justification": "Need to review for treatment plan"
                    }
                    """;

            mockMvc.perform(post("/internal/v1/access-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "pod-1")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", CORRELATION_ID)
                            .header("X-Actor-ID", "user-1")
                            .header("X-Actor-Type", "USER")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data.resourceType").value("PATIENT_RECORD"));
        }
    }

    @Nested
    @DisplayName("POST /internal/v1/access-requests/{id}/approve")
    class ApproveRequest {

        @Test
        void approvesSubmittedRequest() throws Exception {
            Long requestId = createAccessRequest();

            String body = """
                    { "reason": "Approved by supervisor" }
                    """;

            mockMvc.perform(post("/internal/v1/access-requests/" + requestId + "/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "pod-1")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", CORRELATION_ID)
                            .header("X-Actor-ID", "approver-1")
                            .header("X-Actor-Type", "USER")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("APPROVED"))
                    .andExpect(jsonPath("$.data.permitToken").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("POST /internal/v1/access-requests/{id}/deny")
    class DenyRequest {

        @Test
        void deniesSubmittedRequest() throws Exception {
            Long requestId = createAccessRequest();

            String body = """
                    { "reason": "Insufficient justification" }
                    """;

            mockMvc.perform(post("/internal/v1/access-requests/" + requestId + "/deny")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "pod-1")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", CORRELATION_ID)
                            .header("X-Actor-ID", "denier-1")
                            .header("X-Actor-Type", "USER")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("DENIED"));
        }
    }

    @Nested
    @DisplayName("GET /internal/v1/access-requests")
    class ListRequests {

        @Test
        void listsAccessRequests() throws Exception {
            createAccessRequest();

            mockMvc.perform(get("/internal/v1/access-requests")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "pod-1")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", CORRELATION_ID)
                            .header("X-Actor-ID", "user-1")
                            .header("X-Actor-Type", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items").isArray());
        }
    }

    private Long createAccessRequest() throws Exception {
        String body = """
                {
                    "resourceType": "PATIENT_RECORD",
                    "resourceId": "patient-xyz",
                    "purpose": "Treatment planning",
                    "justification": "Required for care"
                }
                """;

        MvcResult result = mockMvc.perform(post("/internal/v1/access-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "pod-1")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .header("X-Actor-ID", "user-1")
                        .header("X-Actor-Type", "USER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        // Extract id from JSON response: {"success":true,"data":{"id":1,...},...}
        int idStart = responseBody.indexOf("\"id\":") + 5;
        int idEnd = responseBody.indexOf(",", idStart);
        return Long.parseLong(responseBody.substring(idStart, idEnd));
    }
}
