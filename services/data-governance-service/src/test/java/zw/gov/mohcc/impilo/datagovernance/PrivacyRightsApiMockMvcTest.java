package zw.gov.mohcc.impilo.datagovernance;

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
import zw.gov.mohcc.impilo.datagovernance.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.datagovernance.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the data-subject rights (account-deletion / erasure) lifecycle persists
 * and emits the right domain events — submit → status → cancel — replacing the
 * former experience-bff fabrication (G015) that persisted nothing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivacyRightsApiMockMvcTest {

    private static final String PATH = "/internal/v1/governance/data-subject-requests";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxRepository;

    private String tenantId;
    private String subjectId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID().toString();
        subjectId = "user-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("submit persists a PENDING request and emits a submitted event")
    void submitPersistsAndEmits() throws Exception {
        MvcResult res = submit("idem-submit-" + subjectId, "I no longer use the service")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.subjectId").value(subjectId))
                .andExpect(jsonPath("$.requestType").value("ACCOUNT_DELETION"))
                .andReturn();

        String requestId = MAPPER.readTree(res.getResponse().getContentAsString()).get("requestId").asText();
        assertThat(requestId).isNotBlank();

        assertThat(eventsOfType("impilo.data.governance.data-subject-request.submitted.v1"))
                .anyMatch(e -> subjectId.equals(e.getSubjectId()));
    }

    @Test
    @DisplayName("submit is idempotent: a second submit returns the same in-flight request, no duplicate event")
    void submitIdempotent() throws Exception {
        String first = MAPPER.readTree(submit("idem-1-" + subjectId, "reason")
                .andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("requestId").asText();

        String second = MAPPER.readTree(submit("idem-2-" + subjectId, "reason again")
                .andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("requestId").asText();

        assertThat(second).isEqualTo(first);
        assertThat(eventsOfType("impilo.data.governance.data-subject-request.submitted.v1")
                .stream().filter(e -> subjectId.equals(e.getSubjectId())).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("status returns the real persisted request; 404 when none exists")
    void statusReflectsPersistedState() throws Exception {
        mockMvc.perform(get(PATH).param("subjectId", subjectId)
                        .header("X-Tenant-ID", tenantId)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-s")
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        submit("idem-st-" + subjectId, "reason").andExpect(status().isCreated());

        mockMvc.perform(get(PATH).param("subjectId", subjectId)
                        .header("X-Tenant-ID", tenantId)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-s2")
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("cancel flips PENDING to CANCELLED and emits a cancelled event; second cancel is 404")
    void cancelLifecycle() throws Exception {
        submit("idem-c-" + subjectId, "reason").andExpect(status().isCreated());

        mockMvc.perform(post(PATH + "/cancel")
                        .header("X-Tenant-ID", tenantId)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-c")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "idem-cancel-" + subjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"" + subjectId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        assertThat(eventsOfType("impilo.data.governance.data-subject-request.cancelled.v1"))
                .anyMatch(e -> subjectId.equals(e.getSubjectId()));

        // Nothing pending now → 404
        mockMvc.perform(post(PATH + "/cancel")
                        .header("X-Tenant-ID", tenantId)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-c2")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "idem-cancel2-" + subjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"" + subjectId + "\"}"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──

    private org.springframework.test.web.servlet.ResultActions submit(String idempotencyKey, String reason)
            throws Exception {
        return mockMvc.perform(post(PATH)
                .header("X-Tenant-ID", tenantId)
                .header("X-Pod-ID", "national")
                .header("X-Request-ID", "req-1")
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectId\":\"" + subjectId + "\",\"reason\":\"" + reason + "\"}"));
    }

    private List<OutboxEventEntity> eventsOfType(String eventType) {
        return outboxRepository.findAll().stream()
                .filter(e -> eventType.equals(e.getEventType()))
                .toList();
    }
}
