package zw.gov.mohcc.impilo.jobs.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the PRODUCTION filter chain refuses an anonymous write.
 *
 * <p>The other suites in this module run with {@code disable-oauth-for-tests=true}, i.e. on the
 * test chain, so they say nothing about production. This one forces the flag to {@code false}
 * and asserts against the chain the estate actually runs.</p>
 *
 * <p><b>The headers are the point.</b> A bare anonymous POST to this endpoint returns
 * {@code 400 MISSING_REQUIRED_HEADER} from the companion filter, which sits in front of Spring
 * Security — asserting on that 400 would pass whether or not authentication existed. The v1.1
 * trust headers and the idempotency key are supplied so the request actually reaches the
 * security layer and the 401 means what it says. This is the same trap that made the estate
 * probe sweep report 36 false refusals on its first pass.</p>
 *
 * <p>Regression under test: {@code POST /internal/v1/jobs} returned <b>201 and persisted a job
 * definition</b> to an unauthenticated in-cluster caller (Phase 0 E, 2026-08-07). A job
 * definition is a scheduled-execution primitive.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "impilo.security.disable-oauth-for-tests=false")
class ProductionChainRefusesAnonymousWriteTest {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    /**
     * The production chain configures {@code oauth2ResourceServer}, so the context needs a
     * {@link JwtDecoder}. It is never invoked here — an anonymous request carries no bearer to
     * decode — but without the bean the context would fail to start, which is the G046 trap.
     */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousJobCreationIsRefused() throws Exception {
        mvc.perform(post("/internal/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "test-pod")
                        .header("X-Request-ID", "test-req-1")
                        .header("X-Correlation-ID", "test-corr-1")
                        .header("Idempotency-Key", "test-idem-1")
                        .content("{\"tenantId\":\"" + TENANT + "\",\"name\":\"guard-probe\","
                                + "\"jobType\":\"GUARD_PROBE\",\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Negative control for the fix itself: proves the chain was tightened rather than simply
     * closed. Requiring a credential on the readiness probe fails the pod and takes the service
     * down — a self-inflicted outage in the name of a fix.
     */
    @Test
    void actuatorHealthStaysOpenToAnonymous() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
