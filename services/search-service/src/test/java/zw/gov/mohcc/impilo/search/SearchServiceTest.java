package zw.gov.mohcc.impilo.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.search.persistence.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.search.persistence.repository.SearchIndexRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Search Service — index, search, list-by-type,
 * remove, tenant isolation, and upsert behaviour.
 *
 * Uses H2 in-memory database via the "test" profile with create-drop DDL
 * and Flyway disabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SearchIndexRepository searchIndexRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    @BeforeEach
    void cleanUp() {
        searchIndexRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private MvcResult indexEntity(String tenantId, String entityType, String entityId,
                                  String contentJson, String tagsJson, String sourceEvent) throws Exception {
        String tags = tagsJson != null ? tagsJson : "null";
        String source = sourceEvent != null ? "\"" + sourceEvent + "\"" : "null";

        String body = """
                {
                  "entityType": "%s",
                  "entityId": "%s",
                  "contentJson": %s,
                  "tags": %s,
                  "sourceEvent": %s
                }
                """.formatted(entityType, entityId, contentJson, tags, source);

        return mockMvc.perform(post("/internal/v1/search/index")
                        .header("X-Tenant-ID", tenantId)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "idx-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult indexEntity(String entityType, String entityId,
                                  String contentJson) throws Exception {
        return indexEntity(TENANT_ID, entityType, entityId, contentJson, null, null);
    }

    // ── Test 1: POST /internal/v1/search/index — index an entity ────────

    @Test
    @DisplayName("POST /internal/v1/search/index creates index entry and returns 201 with all fields")
    void indexEntityReturnsCreated() throws Exception {
        MvcResult result = indexEntity("patient", "pat-001",
                "{\"firstName\": \"John\", \"lastName\": \"Doe\", \"nationalId\": \"63-123456-A-01\"}");

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());

        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("entityType").asText()).isEqualTo("patient");
        assertThat(root.get("entityId").asText()).isEqualTo("pat-001");
        assertThat(root.get("contentJson").get("firstName").asText()).isEqualTo("John");
        assertThat(root.get("contentJson").get("lastName").asText()).isEqualTo("Doe");
        assertThat(root.get("searchableText").asText()).isNotBlank();
        assertThat(root.get("indexedAt").asText()).isNotBlank();

        // Verify outbox event emitted
        assertThat(outboxEventRepository.findAll())
                .anyMatch(e -> "impilo.search.index.created.v1".equals(e.getEventType()));
    }

    // ── Test 2: GET /internal/v1/search?q=query — search returns indexed content ──

    @Test
    @DisplayName("GET /internal/v1/search?q=query returns matching indexed entries")
    void searchReturnsIndexedContent() throws Exception {
        indexEntity("patient", "pat-search-1",
                "{\"name\": \"Alice Moyo\", \"facility\": \"Parirenyatwa\"}");
        indexEntity("patient", "pat-search-2",
                "{\"name\": \"Bob Ncube\", \"facility\": \"Mpilo\"}");
        indexEntity("facility", "fac-search-1",
                "{\"name\": \"Parirenyatwa Hospital\", \"district\": \"Harare\"}");

        // Search for "Parirenyatwa" — should match patient and facility
        MvcResult result = mockMvc.perform(get("/internal/v1/search")
                        .param("q", "Parirenyatwa")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("results").isArray()).isTrue();
        assertThat(root.get("results").size()).isEqualTo(2);
        assertThat(root.get("totalElements").asLong()).isEqualTo(2);
    }

    // ── Test 3: GET /internal/v1/search?q=query&entityType=filter ───────

    @Test
    @DisplayName("GET /internal/v1/search?q=query&entityType=X filters results by entity type")
    void searchWithEntityTypeFilter() throws Exception {
        indexEntity("patient", "pat-filter-1",
                "{\"name\": \"Grace Mutasa\", \"facility\": \"Chitungwiza\"}");
        indexEntity("facility", "fac-filter-1",
                "{\"name\": \"Chitungwiza Central\", \"province\": \"Harare\"}");

        // Search for "Chitungwiza" filtered to entity type "facility"
        MvcResult result = mockMvc.perform(get("/internal/v1/search")
                        .param("q", "Chitungwiza")
                        .param("entityType", "facility")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("results").size()).isEqualTo(1);
        assertThat(root.get("results").get(0).get("entityType").asText()).isEqualTo("facility");
    }

    // ── Test 4: GET /internal/v1/search/index/{entityType} — list by type ─

    @Test
    @DisplayName("GET /internal/v1/search/index/{entityType} returns entries of that type with paging")
    void listByTypeReturnsPaged() throws Exception {
        indexEntity("lab-result", "lr-001",
                "{\"test\": \"CBC\", \"result\": \"Normal\"}");
        indexEntity("lab-result", "lr-002",
                "{\"test\": \"HIV\", \"result\": \"Negative\"}");
        indexEntity("patient", "pat-other-1",
                "{\"name\": \"Other Patient\"}");

        MvcResult result = mockMvc.perform(get("/internal/v1/search/index/lab-result")
                        .param("page", "0")
                        .param("size", "10")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("results").size()).isEqualTo(2);
        assertThat(root.get("totalElements").asLong()).isEqualTo(2);

        // All results must be lab-result type
        for (JsonNode entry : root.get("results")) {
            assertThat(entry.get("entityType").asText()).isEqualTo("lab-result");
        }
    }

    // ── Test 5: DELETE /internal/v1/search/index/{entityType}/{entityId} ─

    @Test
    @DisplayName("DELETE /internal/v1/search/index/{entityType}/{entityId} removes entry and returns 204")
    void removeEntityFromIndex() throws Exception {
        indexEntity("patient", "pat-del-1",
                "{\"name\": \"To Delete\"}");

        // Verify it exists
        assertThat(searchIndexRepository.findByEntityTypeAndEntityIdAndTenantId(
                "patient", "pat-del-1", TENANT_ID)).isPresent();

        // Delete it
        mockMvc.perform(delete("/internal/v1/search/index/patient/pat-del-1")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());

        // Verify it's gone
        assertThat(searchIndexRepository.findByEntityTypeAndEntityIdAndTenantId(
                "patient", "pat-del-1", TENANT_ID)).isEmpty();

        // Verify outbox event for removal
        assertThat(outboxEventRepository.findAll())
                .anyMatch(e -> "impilo.search.index.removed.v1".equals(e.getEventType()));
    }

    // ── Test 6: Tenant isolation ─────────────────────────────────────────

    @Test
    @DisplayName("Different tenant cannot see other tenant's indexed data")
    void tenantIsolation() throws Exception {
        String tenantA = "moh-zw";
        String tenantB = "moh-other";

        indexEntity(tenantA, "patient", "pat-iso-1",
                "{\"name\": \"Tenant A Patient\"}", null, null);
        indexEntity(tenantB, "patient", "pat-iso-2",
                "{\"name\": \"Tenant B Patient\"}", null, null);

        // Search as tenant A — should only see tenant A data
        MvcResult resultA = mockMvc.perform(get("/internal/v1/search")
                        .param("q", "Patient")
                        .header("X-Tenant-ID", tenantA)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rootA = MAPPER.readTree(resultA.getResponse().getContentAsString());
        assertThat(rootA.get("results").size()).isEqualTo(1);
        assertThat(rootA.get("results").get(0).get("entityId").asText()).isEqualTo("pat-iso-1");

        // Search as tenant B — should only see tenant B data
        MvcResult resultB = mockMvc.perform(get("/internal/v1/search")
                        .param("q", "Patient")
                        .header("X-Tenant-ID", tenantB)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rootB = MAPPER.readTree(resultB.getResponse().getContentAsString());
        assertThat(rootB.get("results").size()).isEqualTo(1);
        assertThat(rootB.get("results").get(0).get("entityId").asText()).isEqualTo("pat-iso-2");
    }

    // ── Test 7: Upsert — same entity type + entity ID + tenant updates ──

    @Test
    @DisplayName("Indexing same entityType + entityId + tenant updates existing entry (upsert)")
    void upsertUpdatesExistingEntry() throws Exception {
        // Index the first time
        MvcResult first = indexEntity("patient", "pat-upsert-1",
                "{\"name\": \"Original Name\", \"status\": \"active\"}");

        JsonNode firstRoot = MAPPER.readTree(first.getResponse().getContentAsString());
        String firstId = firstRoot.get("id").asText();
        assertThat(firstRoot.get("contentJson").get("name").asText()).isEqualTo("Original Name");

        // Index again with updated content
        MvcResult second = indexEntity("patient", "pat-upsert-1",
                "{\"name\": \"Updated Name\", \"status\": \"inactive\"}");

        JsonNode secondRoot = MAPPER.readTree(second.getResponse().getContentAsString());
        String secondId = secondRoot.get("id").asText();

        // Same row should be updated, not a new one created
        assertThat(secondId).isEqualTo(firstId);
        assertThat(secondRoot.get("contentJson").get("name").asText()).isEqualTo("Updated Name");
        assertThat(secondRoot.get("contentJson").get("status").asText()).isEqualTo("inactive");

        // Only one entry should exist for this entity
        assertThat(searchIndexRepository.count()).isEqualTo(1);

        // Second index should emit updated event
        assertThat(outboxEventRepository.findAll())
                .anyMatch(e -> "impilo.search.index.updated.v1".equals(e.getEventType()));
    }

    // ── Test 8: Index with tags ──────────────────────────────────────────

    @Test
    @DisplayName("POST /internal/v1/search/index with tags stores them and returns them in response")
    void indexWithTags() throws Exception {
        MvcResult result = indexEntity(TENANT_ID, "appointment", "appt-001",
                "{\"date\": \"2026-03-20\", \"facility\": \"Harare Central\"}",
                "[\"urgent\", \"follow-up\"]", "impilo.appt.created.v1");

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("tags").isArray()).isTrue();
        assertThat(root.get("tags").size()).isEqualTo(2);
        assertThat(root.get("tags").get(0).asText()).isEqualTo("urgent");
        assertThat(root.get("tags").get(1).asText()).isEqualTo("follow-up");
    }

    // ── Test 9: Search paging ────────────────────────────────────────────

    @Test
    @DisplayName("Search results respect page and size parameters")
    void searchPaging() throws Exception {
        // Index 5 entries with the same searchable text
        for (int i = 0; i < 5; i++) {
            indexEntity("patient", "pat-page-" + i,
                    "{\"name\": \"Paging Test " + i + "\", \"ward\": \"Ward-A\"}");
        }

        // Request page 0 with size 2
        MvcResult page0 = mockMvc.perform(get("/internal/v1/search")
                        .param("q", "Paging Test")
                        .param("page", "0")
                        .param("size", "2")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root0 = MAPPER.readTree(page0.getResponse().getContentAsString());
        assertThat(root0.get("results").size()).isEqualTo(2);
        assertThat(root0.get("page").asInt()).isEqualTo(0);
        assertThat(root0.get("size").asInt()).isEqualTo(2);
        assertThat(root0.get("totalElements").asLong()).isEqualTo(5);
        assertThat(root0.get("totalPages").asInt()).isEqualTo(3);

        // Request page 2 with size 2 — should get 1 result
        MvcResult page2 = mockMvc.perform(get("/internal/v1/search")
                        .param("q", "Paging Test")
                        .param("page", "2")
                        .param("size", "2")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root2 = MAPPER.readTree(page2.getResponse().getContentAsString());
        assertThat(root2.get("results").size()).isEqualTo(1);
    }

    // ── Test 10: Delete non-existent entity returns 204 silently ────────

    @Test
    @DisplayName("DELETE on non-existent entity returns 204 without error")
    void deleteNonExistentEntityReturns204() throws Exception {
        mockMvc.perform(delete("/internal/v1/search/index/patient/non-existent-id")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());
    }
}
