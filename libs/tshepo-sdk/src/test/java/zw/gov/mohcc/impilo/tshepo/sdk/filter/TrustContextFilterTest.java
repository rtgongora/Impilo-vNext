package zw.gov.mohcc.impilo.tshepo.sdk.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;
import zw.gov.mohcc.impilo.tshepo.sdk.TrustContext;
import zw.gov.mohcc.impilo.tshepo.sdk.TrustContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrustContextFilterTest {

    private TrustContextFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filter = new TrustContextFilter();
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void validHeaders_populatesTrustContextHolder_andCallsChain() throws ServletException, IOException {
        stubMandatoryHeaders();
        when(request.getHeader(TrustHeaders.FACILITY_ID)).thenReturn(FACILITY_ID.toString());
        when(request.getHeader(TrustHeaders.WORKSPACE_ID)).thenReturn(WORKSPACE_ID.toString());
        when(request.getHeader(TrustHeaders.SHIFT_ID)).thenReturn("shift-001");
        when(request.getHeader(TrustHeaders.ACCESS_MODE)).thenReturn("INTERNAL");

        // Capture the context during chain.doFilter because filter clears it in finally
        doAnswer(invocation -> {
            TrustContext ctx = TrustContextHolder.get();
            assertThat(ctx).isNotNull();
            assertThat(ctx.tenantId()).isEqualTo(TENANT_ID);
            assertThat(ctx.actorId()).isEqualTo("actor-1");
            assertThat(ctx.actorType()).isEqualTo("PROVIDER");
            assertThat(ctx.purposeOfUse()).isEqualTo("TREATMENT");
            assertThat(ctx.deviceFingerprint()).isEqualTo("fp-abc123");
            assertThat(ctx.correlationId()).isEqualTo(CORRELATION_ID);
            assertThat(ctx.facilityId()).isEqualTo(FACILITY_ID);
            assertThat(ctx.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(ctx.shiftId()).isEqualTo("shift-001");
            assertThat(ctx.isInternal()).isTrue();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        // Context should be cleared after the filter completes
        assertThat(TrustContextHolder.get()).isNull();
    }

    @Test
    void missingMandatoryHeader_returns400() throws ServletException, IOException {
        // Only provide some of the mandatory headers — purposeOfUse is missing
        when(request.getHeader(TrustHeaders.TENANT_ID)).thenReturn(TENANT_ID.toString());
        when(request.getHeader(TrustHeaders.ACTOR_ID)).thenReturn("actor-1");
        when(request.getHeader(TrustHeaders.ACTOR_TYPE)).thenReturn("PROVIDER");
        when(request.getHeader(TrustHeaders.PURPOSE_OF_USE)).thenReturn(null);
        when(request.getHeader(TrustHeaders.DEVICE_FINGERPRINT)).thenReturn("fp-abc123");
        when(request.getHeader(TrustHeaders.CORRELATION_ID)).thenReturn(CORRELATION_ID.toString());
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/patients");

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(eq(400), eq("Missing mandatory trust headers"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validMandatoryHeadersOnly_defaultsToExternalMode() throws ServletException, IOException {
        stubMandatoryHeaders();
        // No optional headers — facility, workspace, shift, accessMode all null

        doAnswer(invocation -> {
            TrustContext ctx = TrustContextHolder.get();
            assertThat(ctx).isNotNull();
            assertThat(ctx.facilityId()).isNull();
            assertThat(ctx.workspaceId()).isNull();
            assertThat(ctx.shiftId()).isNull();
            assertThat(ctx.isInternal()).isFalse();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private void stubMandatoryHeaders() {
        when(request.getHeader(TrustHeaders.TENANT_ID)).thenReturn(TENANT_ID.toString());
        when(request.getHeader(TrustHeaders.ACTOR_ID)).thenReturn("actor-1");
        when(request.getHeader(TrustHeaders.ACTOR_TYPE)).thenReturn("PROVIDER");
        when(request.getHeader(TrustHeaders.PURPOSE_OF_USE)).thenReturn("TREATMENT");
        when(request.getHeader(TrustHeaders.DEVICE_FINGERPRINT)).thenReturn("fp-abc123");
        when(request.getHeader(TrustHeaders.CORRELATION_ID)).thenReturn(CORRELATION_ID.toString());
    }
}
