package zw.gov.mohcc.impilo.experience.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServiceClientConfigTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void trustForwarderCopiesActorAndOperationalContextHeaders() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();
        HttpServletRequest inbound = requestWithHeaders();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes((MockHttpServletRequest) inbound));

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("http://example.test"));
        config.trustHeaderForwardingInterceptor().intercept(outbound, new byte[0], (request, body) ->
                new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertEquals("tenant-1", outbound.getHeaders().getFirst(CompanionHeaders.TENANT_ID));
        assertEquals("pod-1", outbound.getHeaders().getFirst(CompanionHeaders.POD_ID));
        assertEquals("req-1", outbound.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", outbound.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("Bearer token", outbound.getHeaders().getFirst(CompanionHeaders.AUTHORIZATION));
        assertEquals("user-1", outbound.getHeaders().getFirst(CompanionHeaders.ACTOR_ID));
        assertEquals("PROVIDER", outbound.getHeaders().getFirst(CompanionHeaders.ACTOR_TYPE));
        assertEquals("TREATMENT", outbound.getHeaders().getFirst(CompanionHeaders.PURPOSE_OF_USE));
        assertEquals("facility-1", outbound.getHeaders().getFirst(CompanionHeaders.FACILITY_ID));
        assertEquals("workspace-1", outbound.getHeaders().getFirst(CompanionHeaders.WORKSPACE_ID));
        assertEquals("shift-1", outbound.getHeaders().getFirst(CompanionHeaders.SHIFT_ID));
        assertEquals("idem-1", outbound.getHeaders().getFirst(CompanionHeaders.IDEMPOTENCY_KEY));
        assertEquals("5000", outbound.getHeaders().getFirst(CompanionHeaders.CLIENT_TIMEOUT_MS));
    }

    /**
     * The clinical episode correlation id must survive the BFF hop.
     *
     * <p>Regression test for a live defect: the shell set {@code X-Trauma-Episode-ID} on every
     * resuscitation, ED and blood write and pct/inpatient/madi all read it, but the BFF never
     * forwarded it — so every resus event reached inpatient-service unstamped and the cross-service
     * episode timeline was assembled from nothing. The pre-existing shell-side test asserted only
     * that the shell SET the header, which is exactly why the gap survived review.
     */
    @Test
    void trustForwarderCopiesTraumaEpisodeCorrelationId() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();
        MockHttpServletRequest inbound = (MockHttpServletRequest) requestWithHeaders();
        inbound.addHeader(CompanionHeaders.TRAUMA_EPISODE_ID, "episode-77");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.POST, java.net.URI.create("http://example.test"));
        config.trustHeaderForwardingInterceptor().intercept(outbound, new byte[0], (request, body) ->
                new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertEquals("episode-77", outbound.getHeaders().getFirst(CompanionHeaders.TRAUMA_EPISODE_ID));
    }

    /**
     * The BFF forwards the episode id, it never invents one. An absent correlation id must stay
     * absent so a downstream service can tell "no episode" from "some episode the BFF guessed".
     */
    @Test
    void trustForwarderDoesNotSynthesizeAnAbsentEpisodeId() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes((MockHttpServletRequest) requestWithHeaders()));

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.POST, java.net.URI.create("http://example.test"));
        config.trustHeaderForwardingInterceptor().intercept(outbound, new byte[0], (request, body) ->
                new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertNull(outbound.getHeaders().getFirst(CompanionHeaders.TRAUMA_EPISODE_ID));
    }

    private HttpServletRequest requestWithHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CompanionHeaders.TENANT_ID, "tenant-1");
        request.addHeader(CompanionHeaders.POD_ID, "pod-1");
        request.addHeader(CompanionHeaders.REQUEST_ID, "req-1");
        request.addHeader(CompanionHeaders.CORRELATION_ID, "corr-1");
        request.addHeader(CompanionHeaders.AUTHORIZATION, "Bearer token");
        request.addHeader(CompanionHeaders.ACTOR_ID, "user-1");
        request.addHeader(CompanionHeaders.ACTOR_TYPE, "PROVIDER");
        request.addHeader(CompanionHeaders.PURPOSE_OF_USE, "TREATMENT");
        request.addHeader(CompanionHeaders.FACILITY_ID, "facility-1");
        request.addHeader(CompanionHeaders.WORKSPACE_ID, "workspace-1");
        request.addHeader(CompanionHeaders.SHIFT_ID, "shift-1");
        request.addHeader(CompanionHeaders.IDEMPOTENCY_KEY, "idem-1");
        request.addHeader(CompanionHeaders.CLIENT_TIMEOUT_MS, "5000");
        return request;
    }
}
