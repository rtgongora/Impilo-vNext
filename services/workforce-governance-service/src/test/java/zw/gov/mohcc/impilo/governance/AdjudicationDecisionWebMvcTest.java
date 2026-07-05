package zw.gov.mohcc.impilo.governance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end MVC test for the IATG adjudication decision record API.
 *
 * <p>All trust headers are supplied on every call, so the test passes whether
 * or not the shared {@code TrustContextFilter} is registered for
 * {@code /internal/v1/*} (a sibling Wave-1 branch extends the registration;
 * this controller tolerates both by falling back to raw headers).</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wgv_adjudication;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdjudicationDecisionWebMvcTest {

    private static final String BASE = "/internal/v1/workforce-governance/adjudications/decisions";

    @Autowired
    MockMvc mockMvc;

    private MockHttpServletRequestBuilder withTrustHeaders(MockHttpServletRequestBuilder builder, UUID tenant) {
        return builder
                .header("X-Tenant-ID", tenant.toString())
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", "adjudicator-1")
                .header("X-Actor-Type", "OPERATOR")
                .header("X-Purpose-Of-Use", "OPERATIONS")
                .header("X-Device-Fingerprint", "test")
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                // Required by the companion v1.1 contract for POST on /internal/v1/*; harmless on GET.
                .header("Idempotency-Key", "adj-test-" + System.nanoTime());
    }

    @Test
    void recordSupersedeAndQueryLatestEffectiveWithHistory() throws Exception {
        UUID tenant = UUID.randomUUID();
        String subjectRef = "prov-" + UUID.randomUUID();

        // First decision: DENIED, effective in the past.
        mockMvc.perform(withTrustHeaders(post(BASE), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectType": "PROVIDER",
                                  "subjectRef": "%s",
                                  "decision": "DENIED",
                                  "reason": "Licence could not be verified with council",
                                  "authorityRole": "REGISTRAR",
                                  "effectiveAt": "2026-01-01T00:00:00Z"
                                }
                                """.formatted(subjectRef)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decision").value("DENIED"))
                .andExpect(jsonPath("$.data.id").exists());

        // Supersede: APPROVED, newer effective_at — a NEW append-only row.
        mockMvc.perform(withTrustHeaders(post(BASE), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workflowInstanceId": "%s",
                                  "subjectType": "PROVIDER",
                                  "subjectRef": "%s",
                                  "decision": "APPROVED",
                                  "reason": "Council confirmed licence in good standing",
                                  "authorityRole": "REGISTRAR",
                                  "effectiveAt": "2026-02-01T00:00:00Z",
                                  "evidenceRefs": ["doc://council/confirmation/1"]
                                }
                                """.formatted(UUID.randomUUID(), subjectRef)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        // Query: latest-effective is APPROVED; history preserves both rows.
        mockMvc.perform(withTrustHeaders(get(BASE), tenant)
                        .queryParam("subjectType", "PROVIDER")
                        .queryParam("subjectRef", subjectRef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latestEffective.decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.history.length()").value(2))
                .andExpect(jsonPath("$.data.history[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.history[1].decision").value("DENIED"));
    }

    @Test
    void blankReasonIsRejected() throws Exception {
        UUID tenant = UUID.randomUUID();
        mockMvc.perform(withTrustHeaders(post(BASE), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectType": "IDENTITY",
                                  "subjectRef": "hid-1",
                                  "decision": "APPROVED",
                                  "reason": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownDecisionIsRejected() throws Exception {
        UUID tenant = UUID.randomUUID();
        mockMvc.perform(withTrustHeaders(post(BASE), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectType": "FACILITY",
                                  "subjectRef": "fac-1",
                                  "decision": "MAYBE",
                                  "reason": "r"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queryWithNoDecisionsReturnsEmptyHistoryAndNullLatest() throws Exception {
        UUID tenant = UUID.randomUUID();
        mockMvc.perform(withTrustHeaders(get(BASE), tenant)
                        .queryParam("subjectType", "ORGANIZATION")
                        .queryParam("subjectRef", "org-none"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestEffective").doesNotExist())
                .andExpect(jsonPath("$.data.history.length()").value(0));
    }
}
