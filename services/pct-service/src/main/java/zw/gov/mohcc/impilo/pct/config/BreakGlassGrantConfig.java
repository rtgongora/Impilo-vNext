package zw.gov.mohcc.impilo.pct.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenProvider;
import zw.gov.mohcc.impilo.shared.security.HttpBreakGlassGrantClient;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantClient;

import java.time.Duration;

/**
 * Wires {@code ClinicalAccessGuard}'s emergency waiver to the tshepo-authz grant-validation seam.
 *
 * <p>The RestTemplate is deliberately its own rather than pct's general-purpose one, which carries
 * timeouts sized for ordinary integration. This call sits in front of an emergency clinical write,
 * where a long wait for a policy service is worse for the patient than a prompt UNREACHABLE — which
 * allows the write and records an ungoverned override for review. Short timeouts are a safety
 * property on this path, not a performance tweak.</p>
 */
@Configuration
public class BreakGlassGrantConfig {

    @Bean
    public BreakGlassGrantClient breakGlassGrantClient(
            RestTemplateBuilder builder,
            @Value("${impilo.trust.tshepo-authz.base-url:${TSHEPO_AUTHZ_BASE_URL:}}") String baseUrl,
            @Value("${impilo.trust.break-glass.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${impilo.trust.break-glass.read-timeout-ms:3000}") long readTimeoutMs,
            ObjectProvider<WorkloadTokenProvider> tokenProvider) {

        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();

        // Optional: most services have no per-workload Keycloak client yet. When absent the client
        // falls back to the inbound caller's own bearer, and when neither exists it reports
        // UNREACHABLE naming the misconfiguration.
        return new HttpBreakGlassGrantClient(restTemplate, baseUrl, tokenProvider.getIfAvailable());
    }
}
