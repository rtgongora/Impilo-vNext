package zw.gov.mohcc.impilo.federation.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import zw.gov.mohcc.impilo.federation.identity.PodIdentityVerifier;
import zw.gov.mohcc.impilo.federation.identity.PodRevocationChecker;
import zw.gov.mohcc.impilo.federation.identity.PodVerificationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Wave 21 — Tests for revocation-aware FederationIdentityFilter.
 *
 * Proves:
 * - Revoked pod is rejected (403 POD_REVOKED) even with valid JWT
 * - Non-revoked pod passes through normally
 * - Null revocation checker gracefully degrades (no revocation check)
 */
@DisplayName("Wave 21 — Federation Revocation Filter")
class FederationRevocationFilterTest {

    private PodIdentityVerifier verifier;
    private PodRevocationChecker revocationChecker;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        verifier = mock(PodIdentityVerifier.class);
        revocationChecker = mock(PodRevocationChecker.class);
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
    @DisplayName("Revoked pod is blocked with 403 POD_REVOKED")
    void revokedPodBlocked() throws Exception {
        when(revocationChecker.isRevoked("revoked-pod")).thenReturn(true);

        FederationIdentityFilter filter = new FederationIdentityFilter(verifier, revocationChecker);
        MockHttpServletRequest req = buildRequest("/internal/v1/patients", "revoked-pod");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("POD_REVOKED");
        // Verifier should NOT have been called — revocation check is first
        verify(verifier, never()).verify(any(), any());
    }

    @Test
    @DisplayName("Non-revoked pod proceeds to identity verification")
    void nonRevokedPodProceedsToVerification() throws Exception {
        when(revocationChecker.isRevoked("active-pod")).thenReturn(false);
        when(verifier.verify(any(), eq("test-jwt-token")))
                .thenReturn(PodVerificationResult.success("active-pod"));

        FederationIdentityFilter filter = new FederationIdentityFilter(verifier, revocationChecker);
        MockHttpServletRequest req = buildRequest("/internal/v1/patients", "active-pod");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        verify(verifier).verify(any(), eq("test-jwt-token"));
    }

    @Test
    @DisplayName("Null revocation checker degrades gracefully")
    void nullRevocationCheckerDegrades() throws Exception {
        when(verifier.verify(any(), eq("test-jwt-token")))
                .thenReturn(PodVerificationResult.success("some-pod"));

        FederationIdentityFilter filter = new FederationIdentityFilter(verifier, null);
        MockHttpServletRequest req = buildRequest("/internal/v1/patients", "some-pod");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
    }

    @Test
    @DisplayName("National pod bypasses revocation check")
    void nationalPodBypassesRevocationCheck() throws Exception {
        FederationIdentityFilter filter = new FederationIdentityFilter(verifier, revocationChecker);
        MockHttpServletRequest req = buildRequest("/internal/v1/patients", "national");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        verify(revocationChecker, never()).isRevoked(any());
    }
}
