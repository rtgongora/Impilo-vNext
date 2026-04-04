package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Configures RestTemplate beans for communicating with sovereign platform services.
 *
 * <p>Each RestTemplate is pre-configured with an interceptor that forwards the
 * v1.1 trust headers (X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID,
 * Authorization) from the inbound request to the outbound service call. This
 * ensures the sovereign service receives the same trust context as the BFF.</p>
 */
@Configuration
@EnableConfigurationProperties(ServiceClientConfig.ServiceEndpoints.class)
public class ServiceClientConfig {

    @ConfigurationProperties(prefix = "impilo.services")
    public record ServiceEndpoints(
            String pctBaseUrl,
            String orosBaseUrl,
            String pharmacyBaseUrl,
            String vitoBaseUrl,
            String tusoBaseUrl,
            String varapiBaseUrl,
            String documentStoreBaseUrl
    ) {
        public ServiceEndpoints {
            if (pctBaseUrl == null) pctBaseUrl = "http://localhost:8088";
            if (orosBaseUrl == null) orosBaseUrl = "http://localhost:8089";
            if (pharmacyBaseUrl == null) pharmacyBaseUrl = "http://localhost:8096";
            if (vitoBaseUrl == null) vitoBaseUrl = "http://localhost:8082";
            if (tusoBaseUrl == null) tusoBaseUrl = "http://localhost:8084";
            if (varapiBaseUrl == null) varapiBaseUrl = "http://localhost:8083";
            if (documentStoreBaseUrl == null) documentStoreBaseUrl = "http://localhost:8093";
        }
    }

    /**
     * Interceptor that copies v1.1 trust headers from the current inbound
     * HTTP request onto every outbound RestTemplate call.
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
                forwardHeader(inbound, request, CompanionHeaders.AUTHORIZATION);
                forwardHeader(inbound, request, CompanionHeaders.IDEMPOTENCY_KEY);
                forwardHeader(inbound, request, CompanionHeaders.CLIENT_TIMEOUT_MS);
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    public RestTemplate serviceRestTemplate(ClientHttpRequestInterceptor trustHeaderForwardingInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(trustHeaderForwardingInterceptor));
        return restTemplate;
    }

    private static void forwardHeader(HttpServletRequest inbound,
                                       org.springframework.http.HttpRequest outbound,
                                       String headerName) {
        String value = inbound.getHeader(headerName);
        if (value != null && !value.isBlank()) {
            outbound.getHeaders().set(headerName, value);
        }
    }
}
