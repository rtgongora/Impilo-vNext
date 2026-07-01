package zw.gov.mohcc.impilo.forms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.forms.api.dto.FormCatalogEntry;
import zw.gov.mohcc.impilo.forms.config.ClinicalFormSeedLoader;
import zw.gov.mohcc.impilo.forms.service.FormSchemaService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClinicalFormCatalogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FormSchemaService formSchemaService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    private ObjectNode clinicalBody(String formKey, String definitionTitle) {
        ObjectNode def = objectMapper.createObjectNode();
        def.put("id", formKey);
        def.put("version", "1.0.0");
        def.put("title", definitionTitle);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("formKey", formKey);
        body.put("name", "Clinical " + definitionTitle);
        body.put("formType", "TRIAGE");
        body.putArray("careSettings").add("OUTPATIENT");
        body.putArray("careStages").add("TRIAGE");
        body.putArray("specialties").add("NURSING");
        body.put("requiredWorkflow", "TRIAGE");
        body.put("obligationDefault", "MANDATORY");
        body.putArray("permittedCadres").add("NURSE");
        body.put("requiresCountersign", false);
        body.put("offlineCapable", true);
        body.put("sensitivity", "STANDARD");
        body.put("publish", true);
        // definition is a string column; embed the DAK object as a string
        body.put("definition", def.toString());
        return body;
    }

    private void perform(ObjectNode body, org.springframework.test.web.servlet.ResultMatcher expect) throws Exception {
        mockMvc.perform(post("/internal/v1/forms/clinical")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "clin-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(expect);
    }

    @Test
    @DisplayName("Clinical form upsert publishes and appears in the resolver catalog with metadata + DAK JSON")
    void upsertAndCatalog() throws Exception {
        String formKey = "impilo.test.triage." + UUID.randomUUID().toString().substring(0, 8);
        perform(clinicalBody(formKey, "Triage"), status().isCreated());

        MvcResult res = mockMvc.perform(get("/internal/v1/forms/catalog")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode catalog = objectMapper.readTree(res.getResponse().getContentAsString());
        JsonNode entry = null;
        for (JsonNode e : catalog) {
            if (formKey.equals(e.get("formKey").asText())) {
                entry = e;
                break;
            }
        }
        assertThat(entry).as("catalog contains the upserted clinical form").isNotNull();
        assertThat(entry.get("requiredWorkflow").asText()).isEqualTo("TRIAGE");
        assertThat(entry.get("obligationDefault").asText()).isEqualTo("MANDATORY");
        assertThat(entry.get("careSettings").get(0).asText()).isEqualTo("OUTPATIENT");
        assertThat(entry.get("permittedCadres").get(0).asText()).isEqualTo("NURSE");
        assertThat(entry.get("formSchemaVersionId").asText()).isNotBlank();
        assertThat(entry.get("version").asInt()).isEqualTo(1);
        assertThat(entry.get("definitionJson").asText()).contains("Triage");
    }

    @Test
    @DisplayName("Re-upsert with a changed definition adds an immutable new version")
    void upsertVersionsImmutably() throws Exception {
        String formKey = "impilo.test.ver." + UUID.randomUUID().toString().substring(0, 8);
        perform(clinicalBody(formKey, "First"), status().isCreated());
        perform(clinicalBody(formKey, "Second"), status().isCreated());

        List<FormCatalogEntry> catalog = formSchemaService.catalog(TENANT_ID);
        FormCatalogEntry entry = catalog.stream()
                .filter(e -> e.formKey().equals(formKey)).findFirst().orElseThrow();
        assertThat(entry.version()).isEqualTo(2);
        assertThat(entry.definitionJson()).contains("Second");
    }

    @Test
    @DisplayName("Seed loader loads the WHO-DAK exemplar catalog idempotently")
    void seedLoaderPopulatesCatalog() {
        String seedTenant = "00000000-0000-4000-8000-000000000001";
        ClinicalFormSeedLoader loader = new ClinicalFormSeedLoader(
                formSchemaService, objectMapper, true, seedTenant, "national-spine");
        loader.run(null);
        loader.run(null); // idempotent second pass

        List<FormCatalogEntry> catalog = formSchemaService.catalog(seedTenant);
        assertThat(catalog).as("all 10 exemplar forms seeded").hasSizeGreaterThanOrEqualTo(10);
        assertThat(catalog).anyMatch(e -> e.formKey().equals("impilo.opd.triage.v1"));
        assertThat(catalog).anyMatch(e -> e.formKey().equals("impilo.inpatient.discharge.summary.v1"));
        // idempotent: still version 1 after two passes with identical definitions
        FormCatalogEntry triage = catalog.stream()
                .filter(e -> e.formKey().equals("impilo.opd.triage.v1")).findFirst().orElseThrow();
        assertThat(triage.version()).isEqualTo(1);
        assertThat(triage.requiredWorkflow()).isEqualTo("TRIAGE");
    }
}
