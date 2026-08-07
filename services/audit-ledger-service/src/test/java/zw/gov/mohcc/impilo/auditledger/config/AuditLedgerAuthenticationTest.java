package zw.gov.mohcc.impilo.auditledger.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit ledger refuses anonymous callers.
 *
 * <h2>Why this test is written the way it is</h2>
 * <p>The exposure it guards hid behind a <b>400</b>. A bare request was rejected with
 * {@code MISSING_REQUIRED_HEADER}, which reads exactly like a refusal — so "the service rejects
 * uncredentialed calls" looked true and was false. Supply the four v1.1 headers, all of them
 * client-supplied and unverified, and the same endpoint returned <b>200</b> with no credential at
 * all.</p>
 *
 * <p>So these tests <b>send the headers</b>. A test that omitted them would pass against a service
 * with no security chain whatsoever, which is precisely the state this is here to prevent
 * recurring. Asserting on 401 specifically — not merely "not 2xx" — is the other half: 400 must not
 * be allowed to satisfy the assertion.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // A real issuer is not needed: every assertion here is about requests that carry no
        // credential, so nothing is ever decoded. Set only so the resource server can build.
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.invalid/realms/impilo",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.invalid/certs",
        // The 'test' profile sets this TRUE so the other suites can exercise the API. It must be
        // FALSE here, or every assertion below would pass against a wide-open service — which is
        // the state this test exists to prevent. Overriding it back on is the point.
        "impilo.security.disable-oauth-for-tests=false"
})
class AuditLedgerAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    /** The four mandatory v1.1 headers — well-formedness only, and proof of nothing. */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            wellFormed(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("X-Tenant-ID", "00000000-0000-0000-0000-000000000001")
                .header("X-Pod-ID", "test")
                .header("X-Request-ID", "11111111-1111-4111-8111-111111111111")
                .header("X-Correlation-ID", "22222222-2222-4222-8222-222222222222");
    }

    /** The exact request measured returning 200 in preview with no credential. */
    @Test
    void chainVerifyRefusesAnAnonymousCaller() throws Exception {
        mockMvc.perform(wellFormed(get("/internal/v1/audit/chain/verify")
                        .param("from_seq", "1").param("to_seq", "1")))
                .andExpect(status().isUnauthorized());
    }

    /** Reads disclose the access history of every subject in the tenant. */
    @Test
    void listingRecordsRefusesAnAnonymousCaller() throws Exception {
        mockMvc.perform(wellFormed(get("/internal/v1/audit/records")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void queryByCorrelationRefusesAnAnonymousCaller() throws Exception {
        mockMvc.perform(wellFormed(get("/internal/v1/audit/query")
                        .param("correlation_id", "22222222-2222-4222-8222-222222222222")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The one that matters most. An append-only record that anyone may append to can be forged,
     * and — with only a rate limiter in front — flooded, which is how an entry gets buried.
     */
    @Test
    void appendingARecordRefusesAnAnonymousCaller() throws Exception {
        mockMvc.perform(wellFormed(post("/internal/v1/audit/records"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Readiness must stay open. Requiring a credential on the probe path would fail readiness and
     * take the service down — a self-inflicted outage in the name of a security fix.
     */
    @Test
    void livenessAndReadinessStayReachable() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
