package zw.gov.mohcc.impilo.companion.consistency;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import zw.gov.mohcc.impilo.companion.error.ErrorCodes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConsistencyClassFilter} covering all three consistency classes
 * and their error codes.
 */
class ConsistencyClassFilterTest {

    private ActionRegistry registry;
    private PdpClient pdpClient;
    private StalenessProvider stalenessProvider;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        registry = new ActionRegistry();
        pdpClient = mock(PdpClient.class);
        stalenessProvider = mock(StalenessProvider.class);
        filterChain = mock(FilterChain.class);
    }

    private ConsistencyClassFilter createFilter() {
        return new ConsistencyClassFilter(registry, pdpClient, stalenessProvider);
    }

    private ConsistencyClassFilter createFilterNoPdp() {
        return new ConsistencyClassFilter(registry, null, stalenessProvider);
    }

    private MockHttpServletRequest buildRequest(String path, String method) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.addHeader("X-Tenant-ID", "tenant-1");
        req.addHeader("X-Pod-ID", "national");
        req.addHeader("X-Request-ID", "req-123");
        req.addHeader("X-Correlation-ID", "corr-456");
        req.addHeader("Authorization", "Bearer test-token");
        return req;
    }

    // ──────────────────────────────────────────────────────────
    // Non-v1.1 paths should pass through
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Non-v1.1 paths pass through without enforcement")
    void nonV11PathPassesThrough() throws Exception {
        registry.register(ActionRegistryEntry.classA("/internal/v1/patients/merge", "POST", "PATIENT_MERGE", false));
        ConsistencyClassFilter filter = createFilter();

        MockHttpServletRequest req = buildRequest("/actuator/health", "GET");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Unregistered route passes through without enforcement")
    void unregisteredRoutePassesThrough() throws Exception {
        ConsistencyClassFilter filter = createFilter();

        MockHttpServletRequest req = buildRequest("/internal/v1/unregistered", "GET");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
    }

    // ──────────────────────────────────────────────────────────
    // Class A tests
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Class A — Synchronous PDP decision required")
    class ClassATests {

        @BeforeEach
        void registerClassAAction() {
            registry.register(ActionRegistryEntry.classA(
                    "/internal/v1/patients/merge", "POST", "PATIENT_MERGE", false));
        }

        @Test
        @DisplayName("ALLOW when gateway policy headers present")
        void allowWithGatewayHeaders() throws Exception {
            ConsistencyClassFilter filter = createFilter();
            MockHttpServletRequest req = buildRequest("/internal/v1/patients/merge", "POST");
            req.addHeader("X-Policy-Decision", "ALLOW");
            req.addHeader("X-Policy-Version", "v1.1.0");
            req.addHeader("X-Decision-Reason", "POLICY_SATISFIED");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("ALLOW when PDP client returns ALLOW")
        void allowWithPdpAllow() throws Exception {
            when(pdpClient.evaluate(anyString(), anyString(), eq("PATIENT_MERGE"), anyString(), anyString()))
                    .thenReturn(new PdpClient.PdpDecision("ALLOW", "v1.1.0", "POLICY_SATISFIED", null));

            ConsistencyClassFilter filter = createFilter();
            MockHttpServletRequest req = buildRequest("/internal/v1/patients/merge", "POST");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain).doFilter(req, res);
            assertThat(res.getHeader("X-Policy-Decision")).isEqualTo("ALLOW");
            assertThat(res.getHeader("X-Policy-Version")).isEqualTo("v1.1.0");
        }

        @Test
        @DisplayName("403 FORBIDDEN when PDP denies")
        void denyWhenPdpDenies() throws Exception {
            when(pdpClient.evaluate(anyString(), anyString(), eq("PATIENT_MERGE"), anyString(), anyString()))
                    .thenReturn(new PdpClient.PdpDecision("DENY", "v1.1.0", "FACILITY_SCOPE", null));

            ConsistencyClassFilter filter = createFilter();
            MockHttpServletRequest req = buildRequest("/internal/v1/patients/merge", "POST");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain, never()).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(403);
            assertThat(res.getContentAsString()).contains(ErrorCodes.FORBIDDEN);
        }

        @Test
        @DisplayName("503 PDP_UNAVAILABLE when PDP is unreachable")
        void pdpUnavailable() throws Exception {
            when(pdpClient.evaluate(anyString(), anyString(), eq("PATIENT_MERGE"), anyString(), anyString()))
                    .thenThrow(new PdpClient.PdpUnavailableException("Connection refused"));

            ConsistencyClassFilter filter = createFilter();
            MockHttpServletRequest req = buildRequest("/internal/v1/patients/merge", "POST");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain, never()).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(503);
            assertThat(res.getContentAsString()).contains(ErrorCodes.PDP_UNAVAILABLE);
        }

        @Test
        @DisplayName("Break-glass override when PDP unavailable and allowed")
        void breakGlassOverride() throws Exception {
            registry = new ActionRegistry();
            registry.register(ActionRegistryEntry.classA(
                    "/internal/v1/patients/merge", "POST", "PATIENT_MERGE", true));

            when(pdpClient.evaluate(anyString(), anyString(), eq("PATIENT_MERGE"), anyString(), anyString()))
                    .thenThrow(new PdpClient.PdpUnavailableException("Connection refused"));

            ConsistencyClassFilter filter = new ConsistencyClassFilter(registry, pdpClient, stalenessProvider);
            MockHttpServletRequest req = buildRequest("/internal/v1/patients/merge", "POST");
            req.addHeader("X-Break-Glass", "true");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain).doFilter(req, res);
            assertThat(res.getHeader("X-Policy-Decision")).isEqualTo("ALLOW_BREAK_GLASS");
        }

        @Test
        @DisplayName("503 when no PDP client and no gateway headers")
        void noPdpNoHeaders() throws Exception {
            ConsistencyClassFilter filter = createFilterNoPdp();
            MockHttpServletRequest req = buildRequest("/internal/v1/patients/merge", "POST");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain, never()).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(503);
            assertThat(res.getContentAsString()).contains(ErrorCodes.PDP_UNAVAILABLE);
        }
    }

    // ──────────────────────────────────────────────────────────
    // Class B tests
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Class B — Bounded staleness")
    class ClassBTests {

        @BeforeEach
        void registerClassBAction() {
            registry.register(ActionRegistryEntry.classB(
                    "/internal/v1/inventory/stock", "GET", "STOCK_QUERY", 60_000));
        }

        @Test
        @DisplayName("ALLOW when staleness within threshold")
        void allowWithinThreshold() throws Exception {
            when(stalenessProvider.currentStalenessMs("STOCK_QUERY")).thenReturn(5_000L);

            ConsistencyClassFilter filter = createFilter();
            MockHttpServletRequest req = buildRequest("/internal/v1/inventory/stock", "GET");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain).doFilter(req, res);
            assertThat(res.getHeader("X-Staleness-Ms")).isEqualTo("5000");
        }

        @Test
        @DisplayName("409 STALE_CONTEXT when staleness exceeds threshold")
        void staleContextExceeded() throws Exception {
            when(stalenessProvider.currentStalenessMs("STOCK_QUERY")).thenReturn(120_000L);

            ConsistencyClassFilter filter = createFilter();
            MockHttpServletRequest req = buildRequest("/internal/v1/inventory/stock", "GET");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain, never()).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(409);
            assertThat(res.getContentAsString()).contains(ErrorCodes.STALE_CONTEXT);
        }

        @Test
        @DisplayName("Uses default threshold when entry has 0")
        void defaultThreshold() throws Exception {
            registry = new ActionRegistry();
            registry.register(ActionRegistryEntry.classB(
                    "/internal/v1/data/read", "GET", "DATA_READ", 0));
            when(stalenessProvider.currentStalenessMs("DATA_READ")).thenReturn(50_000L);

            ConsistencyClassFilter filter = new ConsistencyClassFilter(registry, pdpClient, stalenessProvider);
            MockHttpServletRequest req = buildRequest("/internal/v1/data/read", "GET");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            // Default threshold is 30_000ms, staleness is 50_000ms → should reject
            verify(filterChain, never()).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(409);
        }
    }

    // ──────────────────────────────────────────────────────────
    // Class C tests
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Class C — Offline with entitlement")
    class ClassCTests {

        @BeforeEach
        void registerClassCAction() {
            registry.register(ActionRegistryEntry.classC(
                    "/internal/v1/offline/actions", "POST", "CAPTURE_VITALS"));
        }

        @Test
        @DisplayName("ALLOW when Offline-Entitlement header present")
        void allowWithEntitlement() throws Exception {
            ConsistencyClassFilter filter = createFilter();
            MockHttpServletRequest req = buildRequest("/internal/v1/offline/actions", "POST");
            req.addHeader("Offline-Entitlement", "signed-token-abc123");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("403 OFFLINE_ENTITLEMENT_REQUIRED when header missing")
        void entitlementRequired() throws Exception {
            ConsistencyClassFilter filter = createFilter();
            MockHttpServletRequest req = buildRequest("/internal/v1/offline/actions", "POST");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain, never()).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(403);
            assertThat(res.getContentAsString()).contains(ErrorCodes.OFFLINE_ENTITLEMENT_REQUIRED);
        }

        @Test
        @DisplayName("403 when Offline-Entitlement header is blank")
        void entitlementBlank() throws Exception {
            ConsistencyClassFilter filter = createFilter();
            MockHttpServletRequest req = buildRequest("/internal/v1/offline/actions", "POST");
            req.addHeader("Offline-Entitlement", "  ");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            verify(filterChain, never()).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(403);
        }
    }

    // ──────────────────────────────────────────────────────────
    // ActionRegistry path matching
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ActionRegistry path matching")
    class PathMatchingTests {

        @Test
        @DisplayName("Exact path match")
        void exactMatch() {
            assertThat(ActionRegistry.matchesPath("/internal/v1/patients/merge", "/internal/v1/patients/merge"))
                    .isTrue();
        }

        @Test
        @DisplayName("Wildcard suffix match")
        void wildcardMatch() {
            assertThat(ActionRegistry.matchesPath("/internal/v1/patients/**", "/internal/v1/patients/123/merge"))
                    .isTrue();
        }

        @Test
        @DisplayName("Single segment wildcard")
        void singleSegmentWildcard() {
            assertThat(ActionRegistry.matchesPath("/internal/v1/patients/{id}", "/internal/v1/patients/123"))
                    .isTrue();
        }

        @Test
        @DisplayName("No match for different path")
        void noMatch() {
            assertThat(ActionRegistry.matchesPath("/internal/v1/patients/merge", "/internal/v1/orders"))
                    .isFalse();
        }
    }
}
