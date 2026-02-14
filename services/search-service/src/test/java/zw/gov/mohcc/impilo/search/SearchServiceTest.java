package zw.gov.mohcc.impilo.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.search.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.search.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    // ── Helpers ──────────────────────────────────────────────────────

    private static String indexBody() {
        return """
                {
                  "name": "patient-index",
                  "sourceType": "vito-patient",
                  "fields": [
                    {"name": "given_name", "type": "text", "searchable": true, "filterable": false},
                    {"name": "family_name", "type": "text", "searchable": true, "filterable": true}
                  ],
                  "enabled": true
                }
                """;
    }

    private static String indexBody(String name, String sourceType) {
        return """
                {
                  "name": "%s",
                  "sourceType": "%s",
                  "fields": [
                    {"name": "content", "type": "text", "searchable": true, "filterable": false}
                  ],
                  "enabled": true
                }
                """.formatted(name, sourceType);
    }

    private static String documentBody(String indexId) {
        return """
                {
                  "indexId": "%s",
                  "externalId": "ext-123",
                  "title": "John Doe Patient Record",
                  "bodyText": "Patient John Doe presented with fever and headache",
                  "metadata": {"facility": "central-hospital"}
                }
                """.formatted(indexId);
    }

    private static String searchBody(String query, String indexId) {
        if (indexId != null) {
            return """
                    {
                      "query": "%s",
                      "indexId": "%s",
                      "page": 0,
                      "size": 20
                    }
                    """.formatted(query, indexId);
        }
        return """
                {
                  "query": "%s",
                  "page": 0,
                  "size": 20
                }
                """.formatted(query);
    }

    private String createIndex() throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/indexes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "idx-setup-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(indexBody()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }

    // ── Test 1: Index CRUD ──────────────────────────────────────────

    @Test
    @DisplayName("POST /internal/v1/indexes creates index and returns 201 with id")
    void indexCreateReturns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/indexes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "idx-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(indexBody()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.has("id")).isTrue();
        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("name").asText()).isEqualTo("patient-index");
        assertThat(root.get("sourceType").asText()).isEqualTo("vito-patient");
        assertThat(root.get("enabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("GET /internal/v1/indexes returns all indexes for tenant")
    void listIndexesReturnsList() throws Exception {
        mockMvc.perform(post("/internal/v1/indexes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "idx-list-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(indexBody("encounter-index", "pct-encounter")))
                .andExpect(status().isCreated());

        MvcResult listResult = mockMvc.perform(get("/internal/v1/indexes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode array = MAPPER.readTree(listResult.getResponse().getContentAsString());
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isGreaterThanOrEqualTo(1);
    }

    // ── Test 2: Document upsert ─────────────────────────────────────

    @Test
    @DisplayName("POST /internal/v1/documents upserts document and returns 201")
    void documentUpsertReturns201() throws Exception {
        String indexId = createIndex();

        MvcResult result = mockMvc.perform(post("/internal/v1/documents")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "doc-upsert-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentBody(indexId)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.has("id")).isTrue();
        assertThat(root.get("externalId").asText()).isEqualTo("ext-123");
        assertThat(root.get("title").asText()).isEqualTo("John Doe Patient Record");
    }

    // ── Test 3: Search query ────────────────────────────────────────

    @Test
    @DisplayName("POST /internal/v1/search returns matching documents")
    void searchReturnsHits() throws Exception {
        String indexId = createIndex();

        mockMvc.perform(post("/internal/v1/documents")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "doc-search-setup-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentBody(indexId)))
                .andExpect(status().isCreated());

        MvcResult searchResult = mockMvc.perform(post("/internal/v1/search")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "search-query-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchBody("John Doe", indexId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(searchResult.getResponse().getContentAsString());
        assertThat(root.has("hits")).isTrue();
        assertThat(root.get("hits").isArray()).isTrue();
        assertThat(root.get("totalHits").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(root.get("hits").get(0).get("title").asText()).contains("John Doe");
    }

    // ── Test 4: Outbox event on document upsert ─────────────────────

    @Test
    @DisplayName("Document upsert persists outbox event with type impilo.search.document.upserted.v1")
    void documentUpsertCreatesOutboxEvent() throws Exception {
        String indexId = createIndex();

        long outboxCountBefore = outboxEventRepository.count();

        mockMvc.perform(post("/internal/v1/documents")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "doc-outbox-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentBody(indexId)))
                .andExpect(status().isCreated());

        List<OutboxEventEntity> allOutboxEvents = outboxEventRepository.findAll();
        assertThat(allOutboxEvents.size()).isGreaterThan((int) outboxCountBefore);

        boolean hasUpsertEvent = allOutboxEvents.stream()
                .anyMatch(e -> "impilo.search.document.upserted.v1".equals(e.getEventType()));
        assertThat(hasUpsertEvent)
                .as("Outbox should contain 'impilo.search.document.upserted.v1'")
                .isTrue();
    }

    // ── Test 5: Index create persists outbox event ──────────────────

    @Test
    @DisplayName("Index create persists outbox event with type impilo.search.index.created.v1")
    void indexCreatePersistsOutboxEvent() throws Exception {
        long outboxCountBefore = outboxEventRepository.count();

        mockMvc.perform(post("/internal/v1/indexes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "idx-outbox-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(indexBody("outbox-test-index", "test-source")))
                .andExpect(status().isCreated());

        List<OutboxEventEntity> allOutboxEvents = outboxEventRepository.findAll();
        assertThat(allOutboxEvents.size()).isGreaterThan((int) outboxCountBefore);

        boolean hasCreatedEvent = allOutboxEvents.stream()
                .anyMatch(e -> "impilo.search.index.created.v1".equals(e.getEventType()));
        assertThat(hasCreatedEvent)
                .as("Outbox should contain 'impilo.search.index.created.v1'")
                .isTrue();
    }

    // ── Test 6: Idempotency replay ──────────────────────────────────

    @Test
    @DisplayName("Same Idempotency-Key with same body replays original response")
    void idempotencyReplayReturnsSameId() throws Exception {
        String idempotencyKey = "idx-replay-" + UUID.randomUUID();
        String body = indexBody();

        MvcResult first = mockMvc.perform(post("/internal/v1/indexes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/internal/v1/indexes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        assertThat(second.getResponse().getStatus()).isEqualTo(first.getResponse().getStatus());
        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
    }

    // ── Test 7: Idempotency conflict ────────────────────────────────

    @Test
    @DisplayName("Same Idempotency-Key with different body returns 409 IDENTITY_CONFLICT")
    void idempotencyConflictReturns409() throws Exception {
        String idempotencyKey = "idx-conflict-" + UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/indexes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(indexBody("first-index", "first-source")))
                .andExpect(status().isCreated());

        MvcResult conflict = mockMvc.perform(post("/internal/v1/indexes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(indexBody("different-index", "different-source")))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode root = MAPPER.readTree(conflict.getResponse().getContentAsString());
        assertThat(root.has("error")).isTrue();
        assertThat(root.get("error").get("code").asText()).isEqualTo("IDENTITY_CONFLICT");
    }
}
