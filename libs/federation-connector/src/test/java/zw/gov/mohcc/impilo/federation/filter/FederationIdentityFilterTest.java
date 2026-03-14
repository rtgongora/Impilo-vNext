package zw.gov.mohcc.impilo.federation.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import zw.gov.mohcc.impilo.federation.identity.PodIdentityVerifier;
import zw.gov.mohcc.impilo.federation.identity.PodVerificationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FederationIdentityFilter}.
 *
 * Covers:
 * - National pod bypass
 * - Verified non-national pod allowed
 * - Unknown pod denied
 * - Wrong audience denied
 * - Non-v1.1 paths pass through
 * - Pod ID mismatch between header and token
 */
class FederationIdentityFilterTest {

    private PodIdentityVerifier verifier;
    private FederationIdentityFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        verifier = mock(PodIdentityVerifier.class);
        filter = new FederationIdentityFilter(verifier);
        filterChain = mock(FilterChain.class);
    }

    private MockHttpServletRequest buildRequest(String path, String podId) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.addHeader("X-Tenant-ID", "tenant-1");
        req.addHeader("X-Pod-ID", podId);
        req.addHeader("X-Request-ID", "req-123");
        req.addHeader("X-Correlation-ID", "corr-456");
        req.addHeader("Authorization", "Bearer test-jwt-token");
        return req;
    }

    @Test
    @DisplayName("National pod bypasses identity verification")
    void nationalPodAllowed() throws Exception {
        MockHttpServletRequest req = buildRequest("/internal/v1/patients", "national");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        verify(verifier, never()).verify(any(), any());
    }

    @Test
    @DisplayName("National pod (case-insensitive) bypasses verification")
    void nationalPodCaseInsensitive() throws Exception {
        MockHttpServletRequest req = buildRequest("/internal/v1/patients", "NATIONAL");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
    }

    @Test
    @DisplayName("Verified non-national pod is allowed")
    void verifiedPodAllowed() throws Exception {
        when(verifier.verify(any(), eq("test-jwt-token")))
                .thenReturn(PodVerificationResult.success("harare-pod"));

        MockHttpServletRequest req = buildRequest("/internal/v1/patients", "harare-pod");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Unknown/unverified pod is denied with 403")
    void unknownPodDenied() throws Exception {
        when(verifier.verify(any(), eq("test-jwt-token")))
                .thenReturn(PodVerificationResult.failure("JWT does not contain required audience 'federation'"));

        MockHttpServletRequest req = buildRequest("/internal/v1/patients", "rogue-pod");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains(FederationIdentityFilter.ERROR_CODE);
        assertThat(res.getContentAsString()).contains("federation");
    }

    @Test
    @DisplayName("Pod ID mismatch between header and token is denied")
    void podIdMismatchDenied() throws Exception {
        when(verifier.verify(any(), eq("test-jwt-token")))
                .thenReturn(PodVerificationResult.success("actual-pod-123"));

        MockHttpServletRequest req = buildRequest("/internal/v1/patients", "claimed-pod-456");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("claimed-pod-456");
        assertThat(res.getContentAsString()).contains("actual-pod-123");
    }

    @Test
    @DisplayName("Non-v1.1 paths pass through without verification")
    void nonV11PathPassesThrough() throws Exception {
        MockHttpServletRequest req = buildRequest("/actuator/health", "rogue-pod");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        verify(verifier, never()).verify(any(), any());
    }

    @Test
    @DisplayName("Missing pod ID header lets request pass (V11HeaderFilter handles it)")
    void missingPodIdPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/v1/patients");
        req.addHeader("X-Tenant-ID", "tenant-1");
        req.addHeader("X-Request-ID", "req-123");
        req.addHeader("X-Correlation-ID", "corr-456");
        // No X-Pod-ID header
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
    }

    @Test
    @DisplayName("No bearer token results in verification failure")
    void noBearerTokenDenied() throws Exception {
        when(verifier.verify(any(), isNull()))
                .thenReturn(PodVerificationResult.failure("No JWT token provided"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/v1/patients");
        req.addHeader("X-Tenant-ID", "tenant-1");
        req.addHeader("X-Pod-ID", "some-pod");
        req.addHeader("X-Request-ID", "req-123");
        req.addHeader("X-Correlation-ID", "corr-456");
        // No Authorization header
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(403);
    }
}
