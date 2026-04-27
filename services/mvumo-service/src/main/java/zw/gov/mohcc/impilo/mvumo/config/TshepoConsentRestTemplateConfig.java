package zw.gov.mohcc.impilo.mvumo.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.mvumo.integration.ClientCredentialsTokenProvider;

import java.time.Duration;
import java.util.List;

/**
 * Outbound client to {@code tshepo-consent-service} — forwards v1.2 trust and auth from the inbound request,
 * or uses OAuth2 client-credentials when there is no servlet context or no {@code Authorization} header.
 */
@Configuration
@EnableConfigurationProperties(MvumoTshepoClientCredentialsProperties.class)
public class TshepoConsentRestTemplateConfig {

    private static final List<String> FORWARD = List.of(
            CompanionHeaders.TENANT_ID,
            CompanionHeaders.POD_ID,
            CompanionHeaders.REQUEST_ID,
            CompanionHeaders.CORRELATION_ID,
            CompanionHeaders.AUTHORIZATION,
            CompanionHeaders.ACTOR_ID,
            CompanionHeaders.ACTOR_TYPE,
            CompanionHeaders.PROVIDER_ID,
            CompanionHeaders.PURPOSE_OF_USE,
            CompanionHeaders.FACILITY_ID,
            CompanionHeaders.TUSO_FACILITY_ID,
            CompanionHeaders.WORKSPACE_ID,
            CompanionHeaders.ASSURANCE_LEVEL);

    @Bean
    @Qualifier("tshepoConsentRestTemplate")
    public RestTemplate tshepoConsentRestTemplate(
            ClientCredentialsTokenProvider tokenProvider, MvumoTshepoClientCredentialsProperties clientCredentials) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(
                List.of(
                        trustHeaderForwardingInterceptor(),
                        clientCredentialsInterceptor(tokenProvider, clientCredentials)));
        return restTemplate;
    }

    private static ClientHttpRequestInterceptor trustHeaderForwardingInterceptor() {
        return (outRequest, body, execution) -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest inbound = attrs.getRequest();
                for (String h : FORWARD) {
                    String v = inbound.getHeader(h);
                    if (v != null && !v.isBlank()) {
                        outRequest.getHeaders().set(h, v);
                    }
                }
            }
            return execution.execute(outRequest, body);
        };
    }

    private static ClientHttpRequestInterceptor clientCredentialsInterceptor(
            ClientCredentialsTokenProvider tokenProvider, MvumoTshepoClientCredentialsProperties clientCredentials) {
        return (outRequest, body, execution) -> {
            if (StringUtils.hasText(outRequest.getHeaders().getFirst(CompanionHeaders.AUTHORIZATION))) {
                return execution.execute(outRequest, body);
            }
            if (clientCredentials.isEnabled() && clientCredentials.isConfigured()) {
                tokenProvider.getAccessToken().ifPresent(t -> outRequest.getHeaders().setBearerAuth(t));
            }
            return execution.execute(outRequest, body);
        };
    }
}
