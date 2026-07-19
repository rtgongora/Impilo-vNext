package zw.gov.mohcc.impilo.tshepo.identity.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.Duration;
import java.util.List;

/**
 * RestTemplate beans for outbound service-to-service calls.
 *
 * <p>Separate beans for VITO (identity resolution), Varapi (council/provider
 * resolution) and keys-service (Ed25519 signing) so each can have independent
 * timeout and retry configuration.</p>
 *
 * <p>The VITO and Varapi templates forward the inbound request's trust headers
 * (X-Tenant-ID and the actor/purpose context) onto the outbound call. Without
 * this, downstream resolvers (e.g. Varapi's {@code CouncilRegistrationResolverController},
 * which calls {@code TrustContextHolder.require()}) reject every call for a
 * missing tenant and silent provider/council resolution always returns
 * {@code notResolved()}. The interceptor is mirrored from the experience-bff
 * {@code ServiceClientConfig.trustHeaderForwardingInterceptor} convention.</p>
 */
@Configuration
public class RestClientConfig {

    @Bean(name = "vitoRestTemplate")
    public RestTemplate vitoRestTemplate(RestTemplateBuilder builder,
                                          IdentityProperties properties,
                                          ClientHttpRequestInterceptor trustHeaderForwardingInterceptor) {
        return builder
                .rootUri(properties.vitoServiceUrl())
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .additionalInterceptors(trustHeaderForwardingInterceptor)
                .build();
    }

    @Bean(name = "keysRestTemplate")
    public RestTemplate keysRestTemplate(RestTemplateBuilder builder,
                                          IdentityProperties properties,
                                          ClientHttpRequestInterceptor trustHeaderForwardingInterceptor) {
        // keys-service /v1/sign is .authenticated() — forward the inbound
        // caller's bearer (like the vito/varapi templates) or every scoped-token
        // signing call 401s and ALL estate token issuance (patient-context,
        // WORK_CONTEXT, appointment check-in) 500s. Live-harness-flagged.
        return builder
                .rootUri(properties.keysServiceUrl())
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .additionalInterceptors(trustHeaderForwardingInterceptor)
                .build();
    }

    @Bean(name = "varapiRestTemplate")
    public RestTemplate varapiRestTemplate(RestTemplateBuilder builder,
                                           IdentityProperties properties,
                                           ClientHttpRequestInterceptor trustHeaderForwardingInterceptor) {
        return builder
                .rootUri(properties.varapiServiceUrl())
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .additionalInterceptors(trustHeaderForwardingInterceptor)
                .build();
    }

    /**
     * Copies the current inbound request's trust headers onto every outbound
     * service call so the downstream service receives the same trust context
     * (tenant, actor, purpose) and its {@code TrustContextHolder.require()}
     * succeeds. Only non-blank inbound values are forwarded.
     */
    @Bean
    public ClientHttpRequestInterceptor trustHeaderForwardingInterceptor() {
        return (request, body, execution) -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest inbound = attrs.getRequest();
                forwardHeader(inbound, request, CompanionHeaders.TENANT_ID);
                forwardHeader(inbound, request, CompanionHeaders.POD_ID);
                forwardHeader(inbound, request, CompanionHeaders.REQUEST_ID);
                forwardHeader(inbound, request, CompanionHeaders.CORRELATION_ID);
                forwardHeader(inbound, request, CompanionHeaders.ACTOR_ID);
                forwardHeader(inbound, request, CompanionHeaders.ACTOR_TYPE);
                forwardHeader(inbound, request, CompanionHeaders.PURPOSE_OF_USE);
                forwardHeader(inbound, request, CompanionHeaders.AUTHORIZATION);
                forwardHeader(inbound, request, CompanionHeaders.ACCESS_MODE);
            }
            return execution.execute(request, body);
        };
    }

    private static void forwardHeader(HttpServletRequest inbound,
                                      HttpRequest outbound,
                                      String headerName) {
        String value = inbound.getHeader(headerName);
        if (value != null && !value.isBlank()) {
            outbound.getHeaders().set(headerName, value);
        }
    }

    /**
     * Test seam: build the forwarding interceptor as a plain list so unit tests
     * can attach it to a RestTemplate without the Spring context.
     */
    public List<ClientHttpRequestInterceptor> forwardingInterceptors() {
        return List.of(trustHeaderForwardingInterceptor());
    }
}
