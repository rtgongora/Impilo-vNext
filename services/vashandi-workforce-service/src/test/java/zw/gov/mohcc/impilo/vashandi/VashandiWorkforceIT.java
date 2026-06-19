package zw.gov.mohcc.impilo.vashandi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VashandiWorkforceIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void workforceProfileCreateAndList() throws Exception {
        UUID tenant = UUID.randomUUID();
        String body = """
                {
                  "healthId": "HID-1001",
                  "providerWorkerId": "PWR-1001",
                  "workerType": "CLINICAL",
                  "profession": "NURSING",
                  "cadre": "RN",
                  "currentStatus": "active"
                }
                """;

        mockMvc.perform(post("/v1/internal/vashandi/workforce-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Actor-ID", "actor-1")
                        .header("X-Actor-Type", "OPERATOR")
                        .header("X-Purpose-Of-Use", "OPERATIONS")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthId").value("HID-1001"));

        mockMvc.perform(get("/v1/internal/vashandi/workforce-profiles")
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Actor-ID", "actor-1")
                        .header("X-Actor-Type", "OPERATOR")
                        .header("X-Purpose-Of-Use", "OPERATIONS")
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].providerWorkerId").value("PWR-1001"));
    }

    @Test
    void assignmentLifecycle_precheckReturnsPendingBackendWhenUpstreamUnavailable() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID profileId = createProfile(tenant);

        String assignmentBody = """
                {
                  "workforceProfileId": "%s",
                  "assignmentType": "facility_assignment",
                  "facilityId": "%s",
                  "roleTemplateId": "vashandi_self_service_worker"
                }
                """.formatted(profileId, UUID.randomUUID());

        String assignmentJson = mockMvc.perform(post("/v1/internal/vashandi/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(trustHeaders(tenant))
                        .content(assignmentBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("requested"))
                .andReturn().getResponse().getContentAsString();

        UUID assignmentId = UUID.fromString(objectMapper.readTree(assignmentJson).get("id").asText());

        mockMvc.perform(post("/v1/internal/vashandi/assignments/{id}/precheck", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(trustHeaders(tenant))
                        .content("{\"opaDecisionId\":\"opa-test-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionStatus").value("pending_backend"));
    }

    @Test
    void analyticsEndpointsReturnDbBackedAggregates() throws Exception {
        UUID tenant = UUID.randomUUID();
        createProfile(tenant);

        mockMvc.perform(get("/v1/internal/vashandi/analytics/staffing")
                        .headers(trustHeaders(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.counts.active_profiles").value(1));
    }

    private UUID createProfile(UUID tenant) throws Exception {
        String body = """
                {
                  "healthId": "HID-%s",
                  "providerWorkerId": "PWR-%s",
                  "currentStatus": "active"
                }
                """.formatted(tenant.toString().substring(0, 8), tenant.toString().substring(9, 17));

        String json = mockMvc.perform(post("/v1/internal/vashandi/workforce-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(trustHeaders(tenant))
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private org.springframework.http.HttpHeaders trustHeaders(UUID tenant) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Tenant-ID", tenant.toString());
        headers.set("X-Actor-ID", "actor-1");
        headers.set("X-Actor-Type", "OPERATOR");
        headers.set("X-Purpose-Of-Use", "OPERATIONS");
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
        return headers;
    }
}
