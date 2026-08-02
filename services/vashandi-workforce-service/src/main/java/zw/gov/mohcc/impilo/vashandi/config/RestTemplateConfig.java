package zw.gov.mohcc.impilo.vashandi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenInterceptor;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenProperties;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenProvider;

import java.util.List;

/**
 * Outbound HTTP for Vashandi's service-to-service calls.
 *
 * <p>This was a bare {@code new RestTemplate()} carrying no credential at all. Vashandi calls
 * {@code workforce-governance-service} directly, so the moment that callee turned its resource
 * server on every Vashandi call would have been rejected — which is exactly what blocked the first
 * enforcement cohort. Trust headers are context, not authentication: with no token there is
 * nothing for the callee to verify.</p>
 *
 * <p>The interceptor attaches Vashandi's <em>own</em> workload token, and only when no
 * {@code Authorization} header is already present, so a delegated human token is never overwritten
 * by the stronger service credential.</p>
 *
 * <p>The token provider is given a separate, un-intercepted {@link RestTemplate}: handing it the
 * intercepted one would mean acquiring a token requires a token.</p>
 */
@Configuration
@EnableConfigurationProperties(WorkloadTokenProperties.class)
public class RestTemplateConfig {

    /** Un-intercepted, and used only to reach the identity provider. */
    @Bean
    public RestTemplate workloadTokenRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public WorkloadTokenProvider workloadTokenProvider(RestTemplate workloadTokenRestTemplate,
                                                       WorkloadTokenProperties properties) {
        return new WorkloadTokenProvider(workloadTokenRestTemplate, properties);
    }

    @Bean
    public RestTemplate vashandiRestTemplate(WorkloadTokenProvider workloadTokenProvider) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(new WorkloadTokenInterceptor(workloadTokenProvider)));
        return restTemplate;
    }
}
