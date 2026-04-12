package zw.gov.mohcc.impilo.fhirgateway.config;

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
 * Configures the RestTemplate used by {@link zw.gov.mohcc.impilo.fhirgateway.core.ConsentEnforcementService}
 * to call the tshepo-consent-service.
 *
 * <p>The RestTemplate forwards v1.2 trust headers from the inbound request
 * so that the consent service receives the same trust context.</p>
 */
@Configuration
public class ConsentClientConfig {

    @Bean
    public RestTemplate consentRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(trustHeaderForwarder()));
        return restTemplate;
    }

    private ClientHttpRequestInterceptor trustHeaderForwarder() {
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
                forwardHeader(inbound, request, CompanionHeaders.ACTOR_ID);
                forwardHeader(inbound, request, CompanionHeaders.ACTOR_TYPE);
                forwardHeader(inbound, request, CompanionHeaders.PURPOSE_OF_USE);
            }
            return execution.execute(request, body);
        };
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
