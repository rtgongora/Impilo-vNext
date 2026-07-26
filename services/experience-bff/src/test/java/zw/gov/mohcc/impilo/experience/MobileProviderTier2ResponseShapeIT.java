package zw.gov.mohcc.impilo.experience;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke assertions that Tier-2 mobile provider JSON shapes match
 * {@code contracts/openapi/mobile-provider.openapi.yaml} (queue, pharmacy, triage, labs).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(IntegrationEnvironmentCondition.class)
class MobileProviderTier2ResponseShapeIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        ExperienceBffTestRedisSupport.configure(MobileProviderTier2ResponseShapeIT.class, registry);
        ExperienceBffReportingWireMockSupport.register(registry);
        ExperienceBffSovereignWireMockSupport.register(registry);
    }

    private static final String TENANT_ID = "tenant-moh-zw";
    private static final String POD_ID = "national-spine";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Mandatory Health OS v1.1 headers enforced before controllers run. */
    private static MockHttpServletRequestBuilder withCompanionV11(MockHttpServletRequestBuilder b, String requestId, String correlationId) {
        return b.header("X-Tenant-ID", TENANT_ID)
                .header("X-Pod-ID", POD_ID)
                .header("X-Request-ID", requestId)
                .header("X-Correlation-ID", correlationId);
    }

    /** POST/PUT/PATCH on {@code /internal/v1/**} also require an idempotency key. */
    private static MockHttpServletRequestBuilder withIdempotency(MockHttpServletRequestBuilder b) {
        return b.header("Idempotency-Key", "idem-tier2-" + UUID.randomUUID());
    }

    @Test
    void queueEndpoints_matchContractShapes() throws Exception {
        String rid = UUID.randomUUID().toString();
        String cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/queue"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/queue/stats"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.waiting").exists())
                .andExpect(jsonPath("$.data.inProgress").exists())
                .andExpect(jsonPath("$.data.completedToday").exists());

        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withIdempotency(withCompanionV11(
                                post("/internal/v1/mobile/provider/queue/call-next")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"),
                                rid,
                                cid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());

        UUID qid = UUID.randomUUID();
        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withIdempotency(withCompanionV11(post("/internal/v1/mobile/provider/queue/complete/" + qid), rid, cid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void pharmacyEndpoints_matchContractShapes() throws Exception {
        String rid = UUID.randomUUID().toString();
        String cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/pharmacy/pending"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withIdempotency(withCompanionV11(
                                post("/internal/v1/mobile/provider/pharmacy/verify-five-rights")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"),
                                rid,
                                cid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.rights.rightPatient").value(true));

        String body = objectMapper.writeValueAsString(
                Map.of("prescriptionId", UUID.randomUUID().toString()));
        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withIdempotency(withCompanionV11(
                                post("/internal/v1/mobile/provider/pharmacy/dispense")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body),
                                rid,
                                cid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispensed").value(true));
    }

    @Test
    void triageEndpoints_matchContractShapes() throws Exception {
        String rid = UUID.randomUUID().toString();
        String cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/triage"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.meta.request_id").value(rid))
                .andExpect(jsonPath("$.meta.correlation_id").value(cid));

        String record = objectMapper.writeValueAsString(Map.of(
                "patient_id", "pat-1",
                "acuity", 2,
                "triaged_by", "provider-1"));
        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withIdempotency(withCompanionV11(
                                post("/internal/v1/mobile/provider/triage")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(record),
                                rid,
                                cid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.meta.request_id").value(rid));
    }

    @Test
    void developerHub_matchesTypedHubEnvelope() throws Exception {
        String rid = UUID.randomUUID().toString();
        String cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/developer/hub"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed_at").exists())
                .andExpect(jsonPath("$.data.sections").isArray())
                .andExpect(jsonPath("$.data.sections[0].id").exists())
                .andExpect(jsonPath("$.meta.request_id").value(rid))
                .andExpect(jsonPath("$.meta.correlation_id").value(cid));
    }

    @Test
    void providerHubs_matchTypedHubEnvelope() throws Exception {
        String rid = UUID.randomUUID().toString();
        String cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/admin-registry/hub"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections").isArray());

        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/ops-reports/hub"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections").isArray());

        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/professional-settings/hub"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections").isArray());

        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/professional-channels/hub"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections").isArray());
    }

    @Test
    void reportsVertical_matchesTypedReportEnvelope() throws Exception {
        String rid = UUID.randomUUID().toString();
        String cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/reports"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].category").exists())
                .andExpect(jsonPath("$.meta.request_id").value(rid))
                .andExpect(jsonPath("$.meta.correlation_id").value(cid));

        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/reports/facility-kpis/data"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report_id").value("facility-kpis"))
                .andExpect(jsonPath("$.data.entries").isArray())
                .andExpect(jsonPath("$.data.entries[0].key").exists())
                .andExpect(jsonPath("$.meta.request_id").value(rid));
    }

    @Test
    void labEndpoints_matchContractShapes() throws Exception {
        String rid = UUID.randomUUID().toString();
        String cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/labs"), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());

        String create = objectMapper.writeValueAsString(Map.of(
                "encounter_id", UUID.randomUUID().toString(),
                "patient_id", "pat-lab-1",
                "test_code", "CBC",
                "test_name", "Full blood count"));
        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withIdempotency(withCompanionV11(
                                post("/internal/v1/mobile/provider/labs")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(create),
                                rid,
                                cid)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("LabOrder"))
                .andExpect(jsonPath("$.data.attributes.status").value("ORDERED"))
                .andExpect(jsonPath("$.meta.request_id").value(rid));

        UUID labId = UUID.randomUUID();
        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/labs/" + labId), rid, cid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());

        String actor = "actor-health-id-1";
        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withCompanionV11(get("/internal/v1/mobile/provider/labs/results"), rid, cid)
                        .header("X-Actor-ID", actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());

        rid = UUID.randomUUID().toString();
        cid = UUID.randomUUID().toString();
        mvc.perform(withIdempotency(withCompanionV11(post("/internal/v1/mobile/provider/labs/results/" + labId + "/acknowledge"), rid, cid)
                        .header("X-Actor-ID", actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }
}
