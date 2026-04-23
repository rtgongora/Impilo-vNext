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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import zw.gov.mohcc.impilo.search.persistence.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.search.persistence.repository.SearchIndexRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Optional ANN integration test against real PostgreSQL + pgvector (Docker).
 * Skipped automatically when Docker is unavailable ({@code disabledWithoutDocker}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SearchPgvectorAnnIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void registerPg(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("impilo.search.pgvector.enabled", () -> "true");
        r.add("impilo.search.embeddings.hash-dimension", () -> "1536");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SearchIndexRepository searchIndexRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void clean() {
        searchIndexRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("semantic rank uses pgvector ANN recall when enabled")
    void semanticUsesPgvectorAnn() throws Exception {
        String tenant = "t-pgv";
        index(tenant, "patient", "p1", "{\"name\": \"alpha beta gamma\", \"ward\": \"A\"}");
        index(tenant, "patient", "p2", "{\"name\": \"delta epsilon\", \"ward\": \"B\"}");

        var res = mockMvc.perform(get("/internal/v1/search")
                        .param("q", "alpha beta")
                        .param("rankMode", "semantic")
                        .param("page", "0")
                        .param("size", "10")
                        .header("X-Tenant-ID", tenant)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(res.getResponse().getContentAsString());
        assertThat(root.get("rankModeApplied").asText()).isEqualTo("semantic-pgvector-ann");
        assertThat(root.get("results").size()).isGreaterThanOrEqualTo(1);
    }

    private void index(String tenant, String entityType, String entityId, String contentJson) throws Exception {
        String body = """
                {
                  "entityType": "%s",
                  "entityId": "%s",
                  "contentJson": %s,
                  "tags": null,
                  "sourceEvent": null
                }
                """.formatted(entityType, entityId, contentJson);
        mockMvc.perform(post("/internal/v1/search/index")
                        .header("X-Tenant-ID", tenant)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "idx-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
