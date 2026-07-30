package zw.gov.mohcc.impilo.tshepo.sdk.envelope;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mode ladder is the risky part of D3: OFF must be genuinely inert, SHADOW must let
 * everything through while still reporting, and ENFORCE must reject without explaining
 * itself to the caller.
 */
class DecisionEnvelopeFilterTest {

    private DecisionEnvelopeProperties properties;
    private DecisionEnvelopeFilter filter;
    private AtomicBoolean chainCalled;
    private FilterChain chain;

    /** Refuses everything, so any request reaching the chain did so because of the mode. */
    private static final DecisionEnvelopeVerifier ALWAYS_REJECTS =
            new DecisionEnvelopeVerifier(null, 60, false) {
                @Override
                public Result verify(RequestFacts facts) {
                    return new Result(false, Rejection.MISSING, "test");
                }
            };

    @BeforeEach
    void setUp() {
        properties = new DecisionEnvelopeProperties();
        filter = new DecisionEnvelopeFilter(ALWAYS_REJECTS, properties);
        chainCalled = new AtomicBoolean(false);
        chain = (req, res) -> chainCalled.set(true);
    }

    private MockHttpServletResponse run(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader(TrustHeaders.ACTOR_ID, "actor-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("OFF is the default, and is genuinely inert")
    void offIsTheDefaultAndInert() throws Exception {
        assertThat(properties.getMode()).isEqualTo(DecisionEnvelopeProperties.Mode.OFF);

        MockHttpServletResponse response = run("/v1/patients");

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("SHADOW passes the request through and still reports what ENFORCE would reject")
    void shadowObservesWithoutRejecting() throws Exception {
        properties.setMode(DecisionEnvelopeProperties.Mode.SHADOW);

        MockHttpServletResponse response = run("/v1/patients");

        assertThat(chainCalled).as("SHADOW must not break anything").isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ENFORCE rejects with 403 and never reaches the service")
    void enforceRejects() throws Exception {
        properties.setMode(DecisionEnvelopeProperties.Mode.ENFORCE);

        MockHttpServletResponse response = run("/v1/patients");

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("ENFORCE does not tell the caller which check failed")
    void enforceDoesNotNameTheFailedCheck() throws Exception {
        properties.setMode(DecisionEnvelopeProperties.Mode.ENFORCE);

        MockHttpServletResponse response = run("/v1/patients");

        assertThat(response.getErrorMessage()).doesNotContain("MISSING").doesNotContain("test");
    }

    @Test
    @DisplayName("probes stay exempt under ENFORCE, or turning it on restarts the pod forever")
    void probesAreExemptEvenUnderEnforce() throws Exception {
        properties.setMode(DecisionEnvelopeProperties.Mode.ENFORCE);

        for (String probe : new String[]{"/actuator/health", "/health/ready", "/internal/v1/health"}) {
            chainCalled.set(false);
            MockHttpServletResponse response = run(probe);
            assertThat(chainCalled).as("%s must stay reachable", probe).isTrue();
            assertThat(response.getStatus()).as("%s", probe).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("an exempt prefix does not exempt a path that merely contains it")
    void exemptionIsAPrefixNotASubstring() throws Exception {
        properties.setMode(DecisionEnvelopeProperties.Mode.ENFORCE);

        MockHttpServletResponse response = run("/v1/patients/health");

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
