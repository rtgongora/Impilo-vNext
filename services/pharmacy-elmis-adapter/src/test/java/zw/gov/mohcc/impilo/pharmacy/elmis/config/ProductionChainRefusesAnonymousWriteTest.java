package zw.gov.mohcc.impilo.pharmacy.elmis.config;

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
 * <p>Every other suite in this module runs with {@code disable-oauth-for-tests=true}, i.e. on
 * the test chain, so none of them says anything about production. This one forces the flag to
 * {@code false} and asserts against the chain the estate actually runs.</p>
 *
 * <p><b>The headers are the point.</b> A bare anonymous POST here returns
 * {@code 400 MISSING_REQUIRED_HEADER} from the companion filter, which sits in front of Spring
 * Security — asserting on that 400 would pass whether or not authentication existed. The v1.1
 * trust headers and idempotency key are supplied so the request reaches the security layer and
 * the 401 means what it says.</p>
 *
 * <p>Regression under test: this service ran {@code anyRequest().permitAll()} with no
 * {@code oauth2ResourceServer}, so a dispense synchronisation was writable by any unauthenticated in-cluster
 * caller (Phase 0 E probe sweep, 2026-08-07).</p>
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
     * {@link JwtDecoder}. It is never invoked — an anonymous request carries no bearer to
     * decode — but without the bean the context would fail to start (the G046 trap).
     */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousWriteIsRefused() throws Exception {
        mvc.perform(post("/internal/v1/dispense-sync/trigger")
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
     * Negative control for the fix itself: proves the chain was tightened rather than simply
     * closed. Requiring a credential on the readiness probe fails the pod and takes the service
     * down — a self-inflicted outage in the name of a fix.
     */
    @Test
    void actuatorHealthStaysOpenToAnonymous() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
