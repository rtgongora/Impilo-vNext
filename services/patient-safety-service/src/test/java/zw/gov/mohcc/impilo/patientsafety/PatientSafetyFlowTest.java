package zw.gov.mohcc.impilo.patientsafety;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.patientsafety.repository.OutboxEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end pharmacovigilance flows over the internal API: provider ADR → case → triage → follow-up
 * → manual VigiFlow → E2B(R3)-aligned export-ready; serious AEFI → investigation; honest adapter
 * posture; idempotent submit; outbox emission. Every request carries the mandatory v1.1 trust
 * headers and every mutation an Idempotency-Key (both enforced by tech-companion).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientSafetyFlowTest {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxRepository;

    /** Attaches the mandatory v1.1 trust headers + actor context (globally-unique request id). */
    private MockHttpServletRequestBuilder trusted(MockHttpServletRequestBuilder b) {
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .header("X-Correlation-ID", "11111111-1111-4111-8111-111111111111")
                .header("X-Actor-ID", "actor-1")
                .header("X-Actor-Type", "STAFF")
                .header("X-Purpose-Of-Use", "PHARMACOVIGILANCE");
    }

    /** Trusted builder + a globally-unique Idempotency-Key (required on all mutations). */
    private MockHttpServletRequestBuilder mutate(MockHttpServletRequestBuilder b) {
        return trusted(b).header("Idempotency-Key", "idem-" + java.util.UUID.randomUUID());
    }

    private MvcResult postJson(String url, String body) throws Exception {
        return mockMvc.perform(mutate(post(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body)).andReturn();
    }

    private MvcResult postJsonWithKey(String url, String body, String idemKey) throws Exception {
        return mockMvc.perform(trusted(post(url))
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body)).andReturn();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString()).get("data");
    }

    @Test
    void citizen_can_only_access_own_report() throws Exception {
        // Citizen "owner" creates a report (reporterActorId is taken from X-Actor-ID).
        MvcResult created = mockMvc.perform(post("/internal/v1/patient-safety/reports")
                .header("X-Tenant-ID", TENANT).header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .header("X-Correlation-ID", "11111111-1111-4111-8111-111111111111")
                .header("X-Actor-ID", "citizen-owner").header("X-Actor-Type", "CITIZEN")
                .header("X-Purpose-Of-Use", "PHARMACOVIGILANCE")
                .header("Idempotency-Key", "idem-" + java.util.UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reportType":"ADR","reporterKind":"PROVIDER","sourceContext":"CLINICAL_ENCOUNTER",
                         "narrative":"Nausea after a new medicine","serious":false}
                        """)).andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String reportId = data(created).get("id").asText();

        // A DIFFERENT citizen cannot read it — subject-relationship binding (dimension 6) -> 403.
        mockMvc.perform(citizenGet("/internal/v1/patient-safety/reports/" + reportId, "citizen-other"))
                .andExpect(status().isForbidden());

        // The owner can read their own report.
        mockMvc.perform(citizenGet("/internal/v1/patient-safety/reports/" + reportId, "citizen-owner"))
                .andExpect(status().isOk());

        // The other citizen's list returns only their OWN reports (none) — no cross-subject leak.
        mockMvc.perform(citizenGet("/internal/v1/patient-safety/reports", "citizen-other"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /** A GET with the mandatory v1.1 trust headers and a CITIZEN actor context. */
    private MockHttpServletRequestBuilder citizenGet(String url, String actorId) {
        return get(url)
                .header("X-Tenant-ID", TENANT).header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .header("X-Correlation-ID", "11111111-1111-4111-8111-111111111111")
                .header("X-Actor-ID", actorId).header("X-Actor-Type", "CITIZEN");
    }

    @Test
    void suppress_fields_obligation_redacts_pii_on_read() throws Exception {
        MvcResult created = postJson("/internal/v1/patient-safety/reports", """
                {"reportType":"ADR","reporterKind":"PROVIDER","sourceContext":"CLINICAL_ENCOUNTER",
                 "reporterName":"Dr Confidential","narrative":"sensitive free text","serious":false}
                """);
        String id = data(created).get("id").asText();

        // No obligation -> full body (reporter identity + narrative visible).
        mockMvc.perform(trusted(get("/internal/v1/patient-safety/reports/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reporterName").value("Dr Confidential"))
                .andExpect(jsonPath("$.data.narrative").value("sensitive free text"));

        // suppressFields obligation -> those paths are removed from the representation.
        mockMvc.perform(trusted(get("/internal/v1/patient-safety/reports/" + id))
                        .header("x-suppress-fields", "reporterName,narrative"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.reporterName").doesNotExist())
                .andExpect(jsonPath("$.data.narrative").doesNotExist());
    }

    @Test
    void facility_scope_obligation_narrows_staff_list_to_own_facility() throws Exception {
        // Two reports at distinct (test-unique) facilities, created by staff.
        MvcResult a = postJson("/internal/v1/patient-safety/reports", """
                {"reportType":"ADR","reporterKind":"PROVIDER","sourceContext":"CLINICAL_ENCOUNTER",
                 "reporterFacilityId":"FAC-SCOPE-A","narrative":"event at A","serious":false}
                """);
        MvcResult b = postJson("/internal/v1/patient-safety/reports", """
                {"reportType":"ADR","reporterKind":"PROVIDER","sourceContext":"CLINICAL_ENCOUNTER",
                 "reporterFacilityId":"FAC-SCOPE-B","narrative":"event at B","serious":false}
                """);
        String idA = data(a).get("id").asText();
        String idB = data(b).get("id").asText();

        // Without a facility-scope obligation, staff sees both facilities' reports.
        mockMvc.perform(trusted(get("/internal/v1/patient-safety/reports")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + idA + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='" + idB + "')]").exists());

        // With maxScope=FACILITY_SCOPE + X-Facility-ID=FAC-SCOPE-A, only A's facility is visible.
        mockMvc.perform(trusted(get("/internal/v1/patient-safety/reports"))
                        .header("x-max-scope", "FACILITY_SCOPE")
                        .header("X-Facility-ID", "FAC-SCOPE-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + idA + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='" + idB + "')]").doesNotExist());

        // Facility-scoped but no facility on the context: deny exposure (empty), never leak.
        mockMvc.perform(trusted(get("/internal/v1/patient-safety/reports"))
                        .header("x-max-scope", "FACILITY_SCOPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void provider_adr_full_lifecycle_to_export_ready() throws Exception {
        MvcResult created = postJson("/internal/v1/patient-safety/reports", """
                {
                  "reportType": "ADR",
                  "reporterKind": "PROVIDER",
                  "sourceContext": "CLINICAL_ENCOUNTER",
                  "reporterName": "Dr Moyo",
                  "reporterFacilityId": "FAC-001",
                  "subjectCpid": "CPID-555",
                  "subjectSex": "FEMALE",
                  "narrative": "Generalised rash after starting amoxicillin",
                  "serious": false
                }
                """);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String reportId = data(created).get("id").asText();
        assertThat(data(created).get("status").asText()).isEqualTo("DRAFT");

        postJson("/internal/v1/patient-safety/reports/" + reportId + "/products", """
                {"productName":"Amoxicillin 500mg","productKind":"DRUG","batchNumber":"AMX-22","route":"Oral","dechallenge":"POSITIVE"}
                """);
        postJson("/internal/v1/patient-safety/reports/" + reportId + "/events", """
                {"term":"Generalised maculopapular rash","severity":"MODERATE","outcome":"RECOVERING"}
                """);

        MvcResult submitted = postJsonWithKey("/internal/v1/patient-safety/reports/" + reportId + "/submit",
                "", "idem-submit-1");
        assertThat(submitted.getResponse().getStatus()).isEqualTo(200);
        JsonNode submittedData = data(submitted);
        assertThat(submittedData.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(submittedData.get("referenceCode").asText()).startsWith("PSR-");
        String caseId = submittedData.get("caseId").asText();
        assertThat(submittedData.get("caseReference").asText()).startsWith("PSC-");

        // submit is idempotent: replay with the same key returns the same case reference
        MvcResult replay = postJsonWithKey("/internal/v1/patient-safety/reports/" + reportId + "/submit",
                "", "idem-submit-1");
        assertThat(data(replay).get("caseReference").asText())
                .isEqualTo(submittedData.get("caseReference").asText());

        // MCAZ triage
        mockMvc.perform(mutate(post("/internal/v1/patient-safety/cases/" + caseId + "/triage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority":"HIGH","assignedReviewerId":"mcaz-reviewer-1","note":"Probable causal link"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TRIAGED"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"));

        // request follow-up via Comms Hub
        MvcResult fu = mockMvc.perform(mutate(post("/internal/v1/patient-safety/cases/" + caseId + "/follow-up"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestText":"Please confirm concomitant medicines","channelHint":"COMMS_HUB","conversationRef":"conv-abc"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_FOLLOW_UP"))
                .andReturn();
        String followUpId = data(fu).get("followUps").get(0).get("id").asText();

        // reporter responds
        mockMvc.perform(mutate(post("/internal/v1/patient-safety/cases/" + caseId
                        + "/follow-up/" + followUpId + "/response"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"responseText":"No concomitant medicines","responderKind":"PROVIDER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));

        // manual VigiFlow entry (HONEST: MANUAL, never automated submission)
        mockMvc.perform(mutate(post("/internal/v1/patient-safety/cases/" + caseId + "/vigiflow-manual-entry"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vigiflowReferenceId":"ZW-2026-0001","note":"Keyed into VigiFlow"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MANUAL_ENTRY_COMPLETED"))
                .andExpect(jsonPath("$.data.vigiflowSubmissions[0].entryMode").value("MANUAL"))
                .andExpect(jsonPath("$.data.vigiflowSubmissions[0].vigiflowReferenceId").value("ZW-2026-0001"));

        // mark export-ready → adapter disabled → honest "export pending", never "submitted"
        mockMvc.perform(mutate(post("/internal/v1/patient-safety/cases/" + caseId + "/mark-export-ready"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("EXPORT_READY"))
                .andExpect(jsonPath("$.data.latestExport.packageFormat").value("E2B_R3_ALIGNED"))
                .andExpect(jsonPath("$.data.latestExport.adapterEnabled").value(false))
                .andExpect(jsonPath("$.data.latestExport.message")
                        .value(org.hamcrest.Matchers.containsString("Adapter not configured")));

        assertThat(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().size())
                .isGreaterThanOrEqualTo(6);
    }

    @Test
    void serious_aefi_opens_investigation() throws Exception {
        MvcResult created = postJson("/internal/v1/patient-safety/reports", """
                {
                  "reportType": "AEFI",
                  "reporterKind": "PROVIDER",
                  "sourceContext": "VACCINATION",
                  "subjectCpid": "CPID-777",
                  "narrative": "High fever and seizure after measles vaccination",
                  "serious": true,
                  "seriousnessReasons": ["HOSPITALISATION","LIFE_THREATENING"]
                }
                """);
        String reportId = data(created).get("id").asText();
        postJson("/internal/v1/patient-safety/reports/" + reportId + "/products", """
                {"productName":"Measles vaccine","productKind":"VACCINE","batchNumber":"MV-9","doseNumber":"1","vaccinationSite":"Left arm"}
                """);
        postJson("/internal/v1/patient-safety/reports/" + reportId + "/events", """
                {"term":"Febrile seizure","severity":"SEVERE","outcome":"RECOVERED"}
                """);
        MvcResult submitted = postJson("/internal/v1/patient-safety/reports/" + reportId + "/submit", "");
        String caseId = data(submitted).get("caseId").asText();

        // serious AEFI → URGENT priority
        mockMvc.perform(trusted(get("/internal/v1/patient-safety/cases/" + caseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("URGENT"))
                .andExpect(jsonPath("$.data.serious").value(true));

        MvcResult inv = mockMvc.perform(mutate(post("/internal/v1/patient-safety/cases/" + caseId + "/investigation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedTo":"district-pho-1","note":"Field investigation required"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.investigationReference").value(org.hamcrest.Matchers.startsWith("PSI-")))
                .andReturn();
        String investigationId = data(inv).get("id").asText();

        mockMvc.perform(mutate(patch("/internal/v1/patient-safety/investigations/" + investigationId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"FINAL","finalClassification":"Vaccine product-related reaction","summary":"Consistent with known reactogenicity"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINAL"))
                .andExpect(jsonPath("$.data.finalClassification").value("Vaccine product-related reaction"));
    }

    @Test
    void submit_requires_an_implicated_product() throws Exception {
        MvcResult created = postJson("/internal/v1/patient-safety/reports", """
                {"reportType":"ADR","reporterKind":"CITIZEN","narrative":"Felt unwell"}
                """);
        String reportId = data(created).get("id").asText();
        mockMvc.perform(mutate(post("/internal/v1/patient-safety/reports/" + reportId + "/submit")))
                .andExpect(status().isConflict());
    }

    @Test
    void config_surfaces_honest_adapter_posture() throws Exception {
        mockMvc.perform(trusted(get("/internal/v1/patient-safety/config/adapters")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("vigiflow-e2b"))
                .andExpect(jsonPath("$.data[0].enabled").value(false));

        mockMvc.perform(trusted(get("/internal/v1/patient-safety/config/vigimobile-links")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("EXTERNAL_LINK_OUT"))
                .andExpect(jsonPath("$.data.disclaimer")
                        .value(org.hamcrest.Matchers.containsString("Impilo does not receive")));
    }

    @Test
    void mcaz_workbench_lists_incoming_cases() throws Exception {
        MvcResult created = postJson("/internal/v1/patient-safety/reports", """
                {"reportType":"ADR","reporterKind":"PROVIDER","subjectCpid":"CPID-900","narrative":"Nausea"}
                """);
        String reportId = data(created).get("id").asText();
        postJson("/internal/v1/patient-safety/reports/" + reportId + "/products",
                "{\"productName\":\"Metformin\"}");
        postJson("/internal/v1/patient-safety/reports/" + reportId + "/submit", "");

        mockMvc.perform(trusted(get("/internal/v1/patient-safety/mcaz/workbench")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats").exists())
                .andExpect(jsonPath("$.data.incoming").isArray());
    }
}
