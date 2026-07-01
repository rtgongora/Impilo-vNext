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
import zw.gov.mohcc.impilo.pct.persistence.repository.FormExtractedResourceRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormExtractionIT {

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
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", "doctor-it-001")
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
        assertThat(obs.getStatus()).isEqualTo("PENDING");           // Butano write is the deferred SHR-bridge seam

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

    private static FormExtractedResourceEntity byType(List<FormExtractedResourceEntity> rows, String type) {
        return rows.stream().filter(r -> r.getResourceType().equals(type)).findFirst().orElseThrow();
    }
}
