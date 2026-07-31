package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.pct.integration.FormsCatalogIntegration;
import zw.gov.mohcc.impilo.pct.integration.OrosIntegration;
import zw.gov.mohcc.impilo.pct.integration.VitoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormExtractedResourceEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FormExtractedResourceRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormExtractionTest {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String CPID = "it-extract-cpid-001";

    private static final String MAPPINGS = """
            {"mappings":[
              {"linkId":"primaryDiagnosis","resourceType":"CONDITION","routeTarget":"PCT_PROBLEM","codeSystem":"ICD-10"},
              {"linkId":"managementPlan","resourceType":"CARE_PLAN","routeTarget":"PCT_CARE_PLAN"},
              {"linkId":"labOrder","resourceType":"SERVICE_REQUEST","routeTarget":"OROS","orderCategory":"laboratory"},
              {"linkId":"temp","resourceType":"OBSERVATION","routeTarget":"BUTANO","code":{"system":"http://loinc.org","code":"8310-5"},"unit":"Cel"}
            ]}""";

    @Autowired private MockMvc mockMvc;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private FormExtractedResourceRepository extractedRepository;
    @Autowired private EventOutboxRepository outboxRepository;
    @Autowired private ObjectMapper mapper;

    @MockBean private FormsCatalogIntegration formsCatalogIntegration;
    @MockBean private VitoIntegration vitoIntegration;
    @MockBean private OrosIntegration orosIntegration;

    private Long encounterId;

    private FormCatalogEntry consultForm() {
        return new FormCatalogEntry("schema-consult", "impilo.test.consult", "Consult", null, 1, "ver-consult-1",
                "PUBLISHED", "CONSULTATION", List.of("OUTPATIENT"), List.of("CONSULTATION"), List.of("GENERAL"),
                List.of(), List.of("outpatient"), "DIAGNOSE", "MANDATORY", List.of("PHYSICIAN"),
                null, null, "ALL", null, List.of(), List.of(), false, true, "STANDARD", "{}", MAPPINGS);
    }

    @BeforeEach
    void setup() {
        EncounterEntity enc = new EncounterEntity();
        enc.setTenantId(UUID.fromString(TENANT));
        enc.setJourneyId("jny-" + UUID.randomUUID());
        enc.setSubjectCpid(CPID);
        enc.setEncounterRef(UUID.randomUUID());
        enc.setButanoEncounterRef("butano-enc-1");
        enc.setCareSetting("OUTPATIENT");
        enc.setEncounterContext("outpatient");
        enc.setStatus("STARTED");
        encounterId = encounterRepository.save(enc).getId();

        when(formsCatalogIntegration.fetchCatalog()).thenReturn(List.of(consultForm()));
        when(orosIntegration.submitOrder(any(), any())).thenReturn(Map.of("orderId", "ord-123", "status", "CREATED"));
    }

    private MockHttpServletRequestBuilder trust(MockHttpServletRequestBuilder b) {
        return trustAs(b, "doctor-it-001");
    }

    private MockHttpServletRequestBuilder trustAs(MockHttpServletRequestBuilder b, String actorId) {
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", actorId)
                .header("X-Actor-Type", "PROVIDER")
                .header("X-Purpose-Of-Use", "TREATMENT");
    }

    @Test
    void submit_extractsConditionCarePlanServiceRequestObservation_withProvenance() throws Exception {
        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.consult",
                "answers", Map.of(
                        "primaryDiagnosis", "Malaria",
                        "managementPlan", "Admit and treat",
                        "labOrder", "FBC",
                        "temp", 38.5)));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        String responseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("responseId").asText();

        MvcResult submitted = mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn();

        // QuestionnaireResponse projection stored on the response.
        JsonNode data = mapper.readTree(submitted.getResponse().getContentAsString()).get("data");
        assertThat(data.get("questionnaireResponse").asText()).contains("QuestionnaireResponse");

        List<FormExtractedResourceEntity> rows = extractedRepository.findByResponseId(UUID.fromString(responseId));
        assertThat(rows).hasSize(4);

        FormExtractedResourceEntity condition = byType(rows, "CONDITION");
        assertThat(condition.getRouteTarget()).isEqualTo("PCT_PROBLEM");
        assertThat(condition.getStatus()).isEqualTo("CONFIRMED");
        assertThat(condition.getLocalRef()).isNotBlank();           // real problem id — provenance
        assertThat(condition.getSourceLinkIds()).contains("primaryDiagnosis");

        FormExtractedResourceEntity carePlan = byType(rows, "CARE_PLAN");
        assertThat(carePlan.getStatus()).isEqualTo("CONFIRMED");
        assertThat(carePlan.getLocalRef()).isNotBlank();

        FormExtractedResourceEntity order = byType(rows, "SERVICE_REQUEST");
        assertThat(order.getRouteTarget()).isEqualTo("OROS");
        assertThat(order.getStatus()).isEqualTo("ROUTED");
        assertThat(order.getExternalRef()).isEqualTo("ord-123");

        FormExtractedResourceEntity obs = byType(rows, "OBSERVATION");
        assertThat(obs.getRouteTarget()).isEqualTo("BUTANO");
        // Was PENDING while the Butano write was still a deferred seam. The observation registry
        // (659690ed2) closed that seam: the value is recorded and the row carries the observation id.
        assertThat(obs.getStatus()).isEqualTo("ROUTED");
        assertThat(obs.getExternalRef()).isNotBlank();

        // Every provenance row ties back to the immutable form version.
        assertThat(rows).allMatch(r -> r.getFormSchemaVersionId().equals("ver-consult-1"));
    }

    @Test
    void extractionIsIdempotent_onRepeatTrigger() throws Exception {
        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.consult",
                "answers", Map.of("primaryDiagnosis", "Anaemia")));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        String responseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("responseId").asText();

        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        int afterFirst = extractedRepository.findByResponseId(UUID.fromString(responseId)).size();

        // Amend then countersign would not re-extract; a direct re-run is guarded too.
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/amend"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "answers", Map.of("primaryDiagnosis", "Anaemia (corrected)"), "reason", "typo"))))
                .andExpect(status().isOk());
        assertThat(extractedRepository.findByResponseId(UUID.fromString(responseId))).hasSize(afterFirst);
    }

    @Test
    void safetyEventAnswer_emitsRitoProvenance() throws Exception {
        String safetyMappings = "{\"mappings\":[{\"linkId\":\"adverseEvent\",\"resourceType\":\"SAFETY_EVENT\","
                + "\"routeTarget\":\"RITO\",\"safetyCategory\":\"ADVERSE_DRUG_REACTION\"}]}";
        FormCatalogEntry safetyForm = new FormCatalogEntry("schema-safety", "impilo.test.safety", "Safety", null, 1,
                "ver-safety-1", "PUBLISHED", "SAFETY", List.of("OUTPATIENT"), List.of(), List.of(), List.of(),
                List.of("outpatient"), "ASSESS", "OPTIONAL", List.of(), null, null, "ALL", null, List.of(), List.of(),
                false, true, "STANDARD", "{}", safetyMappings);
        when(formsCatalogIntegration.fetchCatalog()).thenReturn(List.of(safetyForm));

        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.safety",
                "answers", Map.of("adverseEvent", "ANAPHYLAXIS")));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        String responseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("responseId").asText();
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        FormExtractedResourceEntity safety = byType(
                extractedRepository.findByResponseId(UUID.fromString(responseId)), "SAFETY_EVENT");
        assertThat(safety.getRouteTarget()).isEqualTo("RITO");
        assertThat(safety.getStatus()).isEqualTo("ROUTED");
        assertThat(safety.getResourcePayload()).contains("ADVERSE_DRUG_REACTION");
    }

    // ── PRESCRIBE seam: MEDICATION_REQUEST extraction → OROS (P3 care tracker) ──────────

    private static final String RX_MAPPINGS = """
            {"mappings":[
              {"linkId":"medication","resourceType":"MEDICATION_REQUEST","routeTarget":"OROS","orderCategory":"medication"}
            ]}""";

    private FormCatalogEntry prescribeForm(boolean requiresCountersign) {
        return new FormCatalogEntry("schema-rx", "impilo.test.prescribe", "Prescribe", null, 1, "ver-rx-1",
                "PUBLISHED", "PRESCRIPTION", List.of("OUTPATIENT"), List.of(), List.of(), List.of(),
                List.of("outpatient"), "PRESCRIBE", "OPTIONAL", List.of("PHYSICIAN"),
                null, null, "ALL", null, List.of(), List.of(), requiresCountersign, true, "STANDARD",
                "{}", RX_MAPPINGS);
    }

    @Test
    void medicationRequestAnswer_routesToOros_withStructuredDosageAndAuditEvent() throws Exception {
        when(formsCatalogIntegration.fetchCatalog()).thenReturn(List.of(prescribeForm(false)));

        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.prescribe",
                "answers", Map.of("medication", Map.of(
                        "drug", "Amoxicillin", "code", "AMOX500",
                        "dose", "500 mg", "route", "PO", "frequency", "TDS", "duration", "5 days"))));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        String responseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("responseId").asText();
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        FormExtractedResourceEntity rx = byType(
                extractedRepository.findByResponseId(UUID.fromString(responseId)), "MEDICATION_REQUEST");
        assertThat(rx.getRouteTarget()).isEqualTo("OROS");
        assertThat(rx.getStatus()).isEqualTo("ROUTED");
        assertThat(rx.getExternalRef()).isEqualTo("ord-123");
        assertThat(rx.getResourcePayload()).contains("\"category\":\"medication\"");
        assertThat(rx.getResourcePayload()).contains("AMOX500").contains("500 mg").contains("TDS");

        // Auditable prescribing trace event for downstream consumers.
        assertThat(outboxRepository.findAll()).anyMatch(e ->
                "pct.form.medication.requested".equals(e.getEventType())
                        && e.getPayload().contains(responseId));
    }

    @Test
    void medicationRequest_onCountersignRequiredForm_isDeferredUntilCountersign() throws Exception {
        when(formsCatalogIntegration.fetchCatalog()).thenReturn(List.of(prescribeForm(true)));

        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.prescribe",
                "answers", Map.of("medication", "AMOX500")));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        String responseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("responseId").asText();
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        // Extraction — and therefore the medication order — is deferred pending countersignature.
        assertThat(extractedRepository.findByResponseId(UUID.fromString(responseId))).isEmpty();

        mockMvc.perform(trustAs(post("/v1/forms/responses/" + responseId + "/countersign"), "supervisor-it-001")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        FormExtractedResourceEntity rx = byType(
                extractedRepository.findByResponseId(UUID.fromString(responseId)), "MEDICATION_REQUEST");
        assertThat(rx.getStatus()).isEqualTo("ROUTED");
        assertThat(rx.getExternalRef()).isEqualTo("ord-123");
    }

    @Test
    void medicationRequest_orosDown_recordsHonestFailedProvenance() throws Exception {
        when(formsCatalogIntegration.fetchCatalog()).thenReturn(List.of(prescribeForm(false)));
        when(orosIntegration.submitOrder(any(), any())).thenReturn(Map.of()); // OROS unavailable

        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.prescribe",
                "answers", Map.of("medication", "AMOX500")));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        String responseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("responseId").asText();
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        FormExtractedResourceEntity rx = byType(
                extractedRepository.findByResponseId(UUID.fromString(responseId)), "MEDICATION_REQUEST");
        assertThat(rx.getStatus()).isEqualTo("FAILED"); // no fake success; provenance is honest
        assertThat(rx.getExternalRef()).isNull();
        assertThat(outboxRepository.findAll()).noneMatch(e ->
                "pct.form.medication.requested".equals(e.getEventType())
                        && e.getPayload().contains(responseId));
    }

    @Test
    void medicationRequest_flatFormCompanionLinkIds_assembleTheDosagePayload() throws Exception {
        // Flat DAK forms capture drug/dose/route as separate fields; the mapping's companionLinkIds
        // stitch the sibling answers into one medication request payload.
        String flatMappings = """
                {"mappings":[
                  {"linkId":"drug","resourceType":"MEDICATION_REQUEST","routeTarget":"OROS","orderCategory":"medication",
                   "companionLinkIds":{"dose":"dose","route":"route","frequency":"frequency"}}
                ]}""";
        FormCatalogEntry flatRx = new FormCatalogEntry("schema-rx-flat", "impilo.test.prescribe.flat", "Prescribe",
                null, 1, "ver-rx-flat-1", "PUBLISHED", "MEDICATION_REQUEST", List.of("OUTPATIENT"), List.of(),
                List.of(), List.of(), List.of("outpatient"), "PRESCRIBE", "OPTIONAL", List.of("PHYSICIAN"),
                null, null, "ALL", null, List.of(), List.of(), false, true, "STANDARD", "{}", flatMappings);
        when(formsCatalogIntegration.fetchCatalog()).thenReturn(List.of(flatRx));

        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.prescribe.flat",
                "answers", Map.of("drug", "Amoxicillin", "dose", "500 mg", "route", "PO", "frequency", "TDS")));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        String responseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("responseId").asText();
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        FormExtractedResourceEntity rx = byType(
                extractedRepository.findByResponseId(UUID.fromString(responseId)), "MEDICATION_REQUEST");
        assertThat(rx.getStatus()).isEqualTo("ROUTED");
        assertThat(rx.getResourcePayload())
                .contains("Amoxicillin").contains("500 mg").contains("\"route\":\"PO\"").contains("TDS");
    }

    @Test
    void encounterFormExtractions_endpoint_returnsProvenanceForTheCockpit() throws Exception {
        // A diagnosis no other method in this class uses. @BeforeEach opens a fresh encounter but the
        // subject CPID is a class constant, and the problem list is patient-scoped — so reusing the
        // first test's "Malaria" trips the duplicate-problem guard on the same patient. That guard
        // failing here does not just skip one item: see FormExtractionService.extractAll's per-item
        // catch, which cannot recover a transaction the inner service already marked rollback-only.
        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.consult",
                "answers", Map.of("primaryDiagnosis", "Typhoid fever", "labOrder", "FBC")));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        String responseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("responseId").asText();
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        MvcResult listed = mockMvc.perform(trust(
                        get("/v1/encounters/" + encounterId + "/form-extractions")))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = mapper.readTree(listed.getResponse().getContentAsString()).get("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isEqualTo(2);
        List<String> types = new java.util.ArrayList<>();
        data.forEach(n -> types.add(n.get("resourceType").asText()));
        assertThat(types).containsExactlyInAnyOrder("CONDITION", "SERVICE_REQUEST");

        // A different encounter has no extractions — tenant/encounter scoping.
        mockMvc.perform(trust(get("/v1/encounters/999999/form-extractions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void duplicateDiagnosis_submitStillSucceeds_andFailedProvenanceIsPersisted() throws Exception {
        // F9 reproduction: submit a diagnosis already ACTIVE on the patient. Before REQUIRES_NEW,
        // ProblemService.add threw DuplicateProblemException, marked the submit tx rollback-only,
        // and commit threw UnexpectedRollbackException → 500 and the form was lost. Now the item
        // is isolated: submit returns 200, the form response persists, and FAILED provenance is kept.
        String firstBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.consult",
                "answers", Map.of("primaryDiagnosis", "Duplicate Malaria")));
        MvcResult first = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isCreated()).andReturn();
        String firstId = mapper.readTree(first.getResponse().getContentAsString())
                .get("data").get("responseId").asText();
        mockMvc.perform(trust(post("/v1/forms/responses/" + firstId + "/submit"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        // Fresh encounter, same patient CPID — problem list is patient-scoped.
        EncounterEntity enc2 = new EncounterEntity();
        enc2.setTenantId(UUID.fromString(TENANT));
        enc2.setJourneyId("jny-" + UUID.randomUUID());
        enc2.setSubjectCpid(CPID);
        enc2.setEncounterRef(UUID.randomUUID());
        enc2.setButanoEncounterRef("butano-enc-2");
        enc2.setCareSetting("OUTPATIENT");
        enc2.setEncounterContext("outpatient");
        enc2.setStatus("STARTED");
        Long encounterId2 = encounterRepository.save(enc2).getId();

        String secondBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId2, "formKey", "impilo.test.consult",
                "answers", Map.of("primaryDiagnosis", "Duplicate Malaria", "labOrder", "FBC")));
        MvcResult second = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isCreated()).andReturn();
        String secondId = mapper.readTree(second.getResponse().getContentAsString())
                .get("data").get("responseId").asText();
        mockMvc.perform(trust(post("/v1/forms/responses/" + secondId + "/submit"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        List<FormExtractedResourceEntity> rows =
                extractedRepository.findByResponseId(UUID.fromString(secondId));
        assertThat(rows).as("extraction provenance must survive the rejected item").isNotEmpty();
        FormExtractedResourceEntity condition = byType(rows, "CONDITION");
        assertThat(condition.getStatus()).isEqualTo("FAILED");
        assertThat(condition.getFailureReason()).isNotBlank();
        // Sibling items must still be attempted: a poisoned shared transaction used to lose them all.
        assertThat(rows.stream().map(FormExtractedResourceEntity::getResourceType).toList())
                .as("later mappings must still run after a rejected condition")
                .contains("SERVICE_REQUEST");
    }

    private static FormExtractedResourceEntity byType(List<FormExtractedResourceEntity> rows, String type) {
        return rows.stream().filter(r -> r.getResourceType().equals(type)).findFirst().orElseThrow();
    }
}
