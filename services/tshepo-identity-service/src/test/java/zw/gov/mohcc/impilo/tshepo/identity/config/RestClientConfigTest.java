package zw.gov.mohcc.impilo.tshepo.identity.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Regression coverage for the trust-header forwarding interceptor (Finding 2).
 *
 * <p>Without forwarding, the outbound Varapi/VITO call carried no X-Tenant-ID,
 * so the downstream {@code TrustContextHolder.require()} threw and silent
 * provider/council resolution always returned {@code notResolved()}. These
 * tests prove the inbound trust headers are propagated to the outbound call.</p>
 */
class RestClientConfigTest {

    private final RestClientConfig config = new RestClientConfig();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private RestTemplate templateWithForwarding() {
        ClientHttpRequestInterceptor interceptor = config.trustHeaderForwardingInterceptor();
        return new RestTemplateBuilder()
                .additionalInterceptors(interceptor)
                .build();
    }

    private void bindInbound(MockHttpServletRequest inbound) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));
    }

    @Test
    void forwardsTenantAndActorHeadersOnOutboundCall() {
        String tenant = UUID.randomUUID().toString();
        String actor = UUID.randomUUID().toString();

        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(CompanionHeaders.TENANT_ID, tenant);
        inbound.addHeader(CompanionHeaders.ACTOR_ID, actor);
        inbound.addHeader(CompanionHeaders.ACTOR_TYPE, "PROVIDER");
        inbound.addHeader(CompanionHeaders.PURPOSE_OF_USE, "IDENTITY_RESOLUTION");
        bindInbound(inbound);

        RestTemplate template = templateWithForwarding();
        MockRestServiceServer server = MockRestServiceServer.createServer(template);
        server.expect(requestTo("http://varapi/v1/internal/providers/resolve-council-number"))
                .andExpect(header(CompanionHeaders.TENANT_ID, tenant))
                .andExpect(header(CompanionHeaders.ACTOR_ID, actor))
                .andExpect(header(CompanionHeaders.ACTOR_TYPE, "PROVIDER"))
                .andExpect(header(CompanionHeaders.PURPOSE_OF_USE, "IDENTITY_RESOLUTION"))
                .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));

        template.getForEntity("http://varapi/v1/internal/providers/resolve-council-number", String.class);
        server.verify();
    }

    @Test
    void doesNotSetTenantHeaderWhenNoInboundRequestBound() {
        // No servlet request context (e.g. background thread): the interceptor
        // must not blow up and must not invent a tenant header.
        RestTemplate template = templateWithForwarding();
        MockRestServiceServer server = MockRestServiceServer.createServer(template);
        server.expect(requestTo("http://vito/v1/registry/resolve"))
                .andExpect(noTenant())
                .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));

        template.getForEntity("http://vito/v1/registry/resolve", String.class);
        server.verify();
    }

    private static org.springframework.test.web.client.RequestMatcher noTenant() {
        return request -> {
            if (request.getHeaders().containsKey(CompanionHeaders.TENANT_ID)) {
                throw new AssertionError("X-Tenant-ID must not be set without an inbound request");
            }
        };
    }
}
