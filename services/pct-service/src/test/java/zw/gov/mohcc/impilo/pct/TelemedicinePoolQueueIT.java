package zw.gov.mohcc.impilo.pct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralRepository;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Virtual-hospital pool queue proof path (Lane E Wave 1): create a teleconsult referral
 * carrying POOL routing, submit it, and verify it is visible on the pool-scoped queue
 * listing — the seam the experience BFF's virtual-care lane and the provider pool
 * worklist consume. Runs the real repository derived query against H2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TelemedicinePoolQueueIT {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final UUID FACILITY = UUID.fromString("f1000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReferralRepository referralRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withTrustHeaders(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-pct-pool")
                .header("X-Correlation-ID", "corr-pct-pool")
                .header("X-Actor-ID", "citizen-it-001")
                .header("X-Actor-Type", "PATIENT")
                .header("X-Purpose-Of-Use", "TREATMENT")
                .header("X-Facility-ID", FACILITY.toString());
    }

    private String createReferral(String cpid, String poolId) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("patient_id", cpid);
        body.put("reason", "Citizen virtual-care request");
        body.put("specialty", "GENERAL_TELECONSULTATION");
        body.put("modality", "virtual");
        if (poolId != null) {
            body.put("routing_kind", "POOL");
            body.put("routing_pool_id", poolId);
        }
        MvcResult result = mockMvc.perform(withTrustHeaders(post("/v1/referrals"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return payload.path("data").path("id").asText();
    }

    private void submit(String referralId) throws Exception {
        mockMvc.perform(withTrustHeaders(post("/v1/referrals/" + referralId + "/submit")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    void createWithPoolRouting_landsOnPoolQueueAfterSubmit() throws Exception {
        String poolId = "IT_POOL_" + UUID.randomUUID().toString().substring(0, 8);
        String first = createReferral("it-pool-cpid-1", poolId);
        String second = createReferral("it-pool-cpid-2", poolId);
        // A referral routed to a DIFFERENT pool must not leak into this queue.
        String other = createReferral("it-pool-cpid-3", poolId + "-other");
        submit(first);
        submit(second);
        submit(other);
        // Draft (unsubmitted) referrals are not in the default queue view.
        String draft = createReferral("it-pool-cpid-4", poolId);

        MvcResult result = mockMvc.perform(withTrustHeaders(get("/v1/referrals/pool/" + poolId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(rows.isArray()).isTrue();
        assertThat(rows).hasSize(2);
        // Oldest first — queue semantics.
        assertThat(rows.get(0).path("id").asText()).isEqualTo(first);
        assertThat(rows.get(1).path("id").asText()).isEqualTo(second);
        assertThat(rows.get(0).path("routingPoolId").asText()).isEqualTo(poolId);
        assertThat(rows.get(0).path("routingKind").asText()).isEqualTo("POOL");

        // The V020 columns are really persisted (not just echoed).
        assertThat(referralRepository.findByReferralId(UUID.fromString(first)))
                .hasValueSatisfying(entity -> {
                    assertThat(entity.getRoutingPoolId()).isEqualTo(poolId);
                    assertThat(entity.getRoutingKind()).isEqualTo("POOL");
                    assertThat(entity.getRoutedAt()).isNotNull();
                });

        // Status-in filter widens the view to the still-draft referral.
        MvcResult widened = mockMvc.perform(withTrustHeaders(
                        get("/v1/referrals/pool/" + poolId).param("status", "SUBMITTED,DRAFT")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode widenedRows = objectMapper.readTree(widened.getResponse().getContentAsString()).path("data");
        assertThat(widenedRows).hasSize(3);
        assertThat(widenedRows.get(2).path("id").asText()).isEqualTo(draft);
    }

    @Test
    void poolQueue_isEmptyForUnroutedPool() throws Exception {
        mockMvc.perform(withTrustHeaders(get("/v1/referrals/pool/NO_SUCH_POOL_" + UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
