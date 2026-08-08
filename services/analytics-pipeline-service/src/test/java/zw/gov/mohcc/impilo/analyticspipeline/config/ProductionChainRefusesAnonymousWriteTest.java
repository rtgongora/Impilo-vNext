package zw.gov.mohcc.impilo.analyticspipeline.config;

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
 * <p>Every other suite here runs with {@code disable-oauth-for-tests=true}, i.e. on the test
 * chain, so none of them says anything about production. This one forces the flag to
 * {@code false} and asserts against the chain the estate actually runs.</p>
 *
 * <p><b>The headers are the point.</b> A bare anonymous POST returns
 * {@code 400 MISSING_REQUIRED_HEADER} from the companion filter, which sits in FRONT of Spring
 * Security, so asserting on that 400 would pass whether or not authentication existed.</p>
 *
 * <p>Regression under test: this service had no authentication at all — Spring Security was not
 * even on the classpath — and returned 202 ACCEPTED and minted an event id for an unauthenticated in-cluster caller (Phase 0 E probe sweep, 2026-08-07).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "impilo.security.disable-oauth-for-tests=false")
class ProductionChainRefusesAnonymousWriteTest {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousWriteIsRefused() throws Exception {
        mvc.perform(post("/internal/v1/telemedicine/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "test-pod")
                        .header("X-Request-ID", "test-req-1")
                        .header("X-Correlation-ID", "test-corr-1")
                        .header("Idempotency-Key", "test-idem-1")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Negative control for the fix: proves the chain was tightened, not closed. Requiring a
     * credential on the readiness probe fails the pod and takes the service down.
     */
    @Test
    void actuatorHealthStaysOpenToAnonymous() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
