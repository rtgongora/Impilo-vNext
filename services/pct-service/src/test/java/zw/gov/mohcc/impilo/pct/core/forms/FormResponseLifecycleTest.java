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
import zw.gov.mohcc.impilo.pct.integration.VitoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormResponseLifecycleTest {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String CPID = "it-forms-cpid-001";

    @Autowired private MockMvc mockMvc;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private ObjectMapper mapper;

    @MockBean private FormsCatalogIntegration formsCatalogIntegration;
    @MockBean private VitoIntegration vitoIntegration;

    private Long encounterId;

    private static FormCatalogEntry catalogForm(String key, String workflow, boolean countersign) {
        return new FormCatalogEntry("schema-" + key, key, "Form " + key, null, 3, "ver-" + key + "-v3",
                "PUBLISHED", "TEST", List.of("OUTPATIENT"), List.of("ASSESSMENT"), List.of("GENERAL"),
                List.of(), List.of("outpatient"), workflow, "MANDATORY", List.of("NURSE"),
                null, null, "ALL", null, List.of(), List.of(), countersign, true, "STANDARD", "{}", "{\"mappings\":[]}");
    }

    @BeforeEach
    void setup() {
        EncounterEntity enc = new EncounterEntity();
        enc.setTenantId(UUID.fromString(TENANT));
        enc.setJourneyId("jny-" + UUID.randomUUID());
        enc.setSubjectCpid(CPID);
        enc.setEncounterRef(UUID.randomUUID());
        enc.setCareSetting("OUTPATIENT");
        enc.setEncounterContext("outpatient");
        enc.setStatus("STARTED");
        encounterId = encounterRepository.save(enc).getId();

        when(vitoIntegration.resolvePatientByCpid(CPID)).thenReturn(Map.of("gender", "FEMALE"));
        when(formsCatalogIntegration.fetchCatalog()).thenReturn(List.of(
                catalogForm("impilo.test.triage", "TRIAGE", false),
                // Author-flagged prescribing form (e.g. controlled drug) → countersign required.
                catalogForm("impilo.test.prescribe", "PRESCRIBE", true)));
    }

    private MockHttpServletRequestBuilder trust(MockHttpServletRequestBuilder b) {
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", "nurse-it-001")
                .header("X-Actor-Type", "PROVIDER")
                .header("X-Purpose-Of-Use", "TREATMENT");
    }

    @Test
    void resolve_draft_submit_amend_void_roundTrip() throws Exception {
        // 1) Resolve: NURSE → triage MANDATORY; the author-flagged prescribing form → COUNTERSIGN.
        String resolveBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "cadre", "NURSE", "role", "PROVIDER",
                "careSetting", "OUTPATIENT", "context", "outpatient"));
        MvcResult resolve = mockMvc.perform(trust(post("/v1/forms/resolve"))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody))
                .andExpect(status().isOk()).andReturn();
        JsonNode res = mapper.readTree(resolve.getResponse().getContentAsString()).get("data");
        assertThat(res.get("mandatory").toString()).contains("impilo.test.triage");
        assertThat(res.get("countersignRequired").toString()).contains("impilo.test.prescribe");

        // 2) Create draft (version-locked).
        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.triage",
                "answers", Map.of("triageCategory", "3")));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        JsonNode draft = mapper.readTree(created.getResponse().getContentAsString()).get("data");
        String responseId = draft.get("responseId").asText();
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("formSchemaVersionId").asText()).isEqualTo("ver-impilo.test.triage-v3");
        assertThat(draft.get("formVersion").asInt()).isEqualTo(3);

        // 3) Update answers → IN_PROGRESS.
        mockMvc.perform(trust(patch("/v1/forms/responses/" + responseId + "/answers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("answers", Map.of("triageCategory", "2")))))
                .andExpect(status().isOk());

        // 4) Submit → SUBMITTED.
        MvcResult submitted = mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(submitted.getResponse().getContentAsString())
                .get("data").get("status").asText()).isEqualTo("SUBMITTED");

        // 5) Amend (reason required) → AMENDED.
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/amend"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "answers", Map.of("triageCategory", "1"), "reason", "correction"))))
                .andExpect(status().isOk());

        // 6) List for encounter.
        MvcResult list = mockMvc.perform(trust(get("/v1/encounters/" + encounterId + "/form-responses")))
                .andExpect(status().isOk()).andReturn();
        assertThat(list.getResponse().getContentAsString()).contains(responseId);

        // 7) Void (reason required).
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/void"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "entered in error"))))
                .andExpect(status().isOk());
    }

    @Test
    void submitEmptyResponse_isRejected() throws Exception {
        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.triage", "answers", Map.of()));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        String responseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("responseId").asText();
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countersignRequiredForm_defersThenCompletes() throws Exception {
        when(formsCatalogIntegration.fetchCatalog())
                .thenReturn(List.of(catalogForm("impilo.test.cs", "ASSESS", true)));
        String createBody = mapper.writeValueAsString(Map.of(
                "encounterId", encounterId, "formKey", "impilo.test.cs",
                "answers", Map.of("field", "value")));
        MvcResult created = mockMvc.perform(trust(post("/v1/forms/responses"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        JsonNode draft = mapper.readTree(created.getResponse().getContentAsString()).get("data");
        String responseId = draft.get("responseId").asText();
        assertThat(draft.get("countersignRequired").asBoolean()).isTrue();

        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/submit"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        // Countersign completes.
        mockMvc.perform(trust(post("/v1/forms/responses/" + responseId + "/countersign"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }
}
