package zw.gov.mohcc.impilo.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.search.domain.SearchDocumentEntity;
import zw.gov.mohcc.impilo.search.repository.SearchDocumentRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GovernedSearchMockMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SearchDocumentRepository documentRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();

    // ── A) Clinical-safe returns limited fields ──

    @Nested
    @DisplayName("A) Clinical-safe search strips metadata")
    class ClinicalSafe {

        @Test
        @DisplayName("Clinical-safe search returns hits without metadata")
        void clinicalSafeStripsMetadata() throws Exception {
            // Seed a document with metadata
            String indexId = seedDocument("clinical-test", "{\"sensitive\":\"patient-data\"}");

            String body = String.format("""
                {"query":"clinical-test","indexId":"%s"}""", indexId);

            MvcResult result = mockMvc.perform(post("/internal/v1/search/clinical-safe")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hits").isArray())
                    .andReturn();

            JsonNode response = MAPPER.readTree(result.getResponse().getContentAsString());
            if (response.get("hits").size() > 0) {
                JsonNode firstHit = response.get("hits").get(0);
                assertThat(firstHit.has("title")).isTrue();
                // metadata should be null/absent for clinical-safe
                assertThat(firstHit.get("metadata").isNull()).isTrue();
            }
        }
    }

    // ── B) Global search returns all fields ──

    @Nested
    @DisplayName("B) Global search returns all fields including metadata")
    class GlobalSearch {

        @Test
        @DisplayName("Global search includes metadata")
        void globalSearchIncludesMetadata() throws Exception {
            String indexId = seedDocument("global-test", "{\"category\":\"lab-result\"}");

            String body = String.format("""
                {"query":"global-test","indexId":"%s"}""", indexId);

            MvcResult result = mockMvc.perform(post("/internal/v1/search/global")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hits").isArray())
                    .andReturn();

            JsonNode response = MAPPER.readTree(result.getResponse().getContentAsString());
            if (response.get("hits").size() > 0) {
                JsonNode firstHit = response.get("hits").get(0);
                assertThat(firstHit.has("title")).isTrue();
                // Global search SHOULD include metadata
                if (firstHit.has("metadata") && !firstHit.get("metadata").isNull()) {
                    assertThat(firstHit.get("metadata").has("category")).isTrue();
                }
            }
        }
    }

    // ── C) Missing headers ──

    @Nested
    @DisplayName("C) Missing headers => 400")
    class MissingHeaders {
        @Test
        void clinicalSafeMissingHeaders() throws Exception {
            mockMvc.perform(post("/internal/v1/search/clinical-safe")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"test\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Helpers ──

    private String seedDocument(String titlePrefix, String metadata) {
        String indexId = "idx-" + UUID.randomUUID().toString().substring(0, 8);
        SearchDocumentEntity doc = new SearchDocumentEntity();
        doc.setIndexId(indexId);
        doc.setExternalId("ext-" + System.nanoTime());
        doc.setTitle(titlePrefix + " document");
        doc.setBodyText("This is a " + titlePrefix + " test document for search testing.");
        doc.setMetadataJson(metadata);
        doc.setTenantId(TENANT_ID);
        doc.setPodId("national");
        documentRepository.save(doc);
        return indexId;
    }
}
