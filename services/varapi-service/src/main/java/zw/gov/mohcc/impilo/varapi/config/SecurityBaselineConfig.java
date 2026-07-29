package zw.gov.mohcc.impilo.varapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import zw.gov.mohcc.impilo.security.audit.AdminAuditEmitter;
import zw.gov.mohcc.impilo.security.ratelimit.RateLimitGuard;
import zw.gov.mohcc.impilo.security.secrets.EnvSecretProvider;
import zw.gov.mohcc.impilo.security.secrets.SecretProvider;
import zw.gov.mohcc.impilo.shared.security.AdminAuditOutboxSink;
import zw.gov.mohcc.impilo.shared.security.RateLimitFilter;
import zw.gov.mohcc.impilo.shared.security.SecurityBaselineExceptionHandler;

import javax.sql.DataSource;

/**
 * Wave 14 security hardening configuration for VARAPI.
 */
@Configuration
public class SecurityBaselineConfig {

    // VARAPI: 100 requests/min capacity, ~2 tokens/sec refill
    // Capacity and refill are properties so a test profile can widen the bucket: it is
    // in-memory and keyed by actor, and every MockMvc request in a Spring test context
    // presents the same actor, so a suite of more than 100 requests throttles itself.
    // Defaults are this service's own previous values -- runtime behaviour is unchanged.
    @Bean
    public RateLimitGuard rateLimitGuard(
            @Value("${impilo.security.rate-limit.max-tokens:100}") long maxTokens,
            @Value("${impilo.security.rate-limit.refill-per-second:2}") long refillPerSecond) {
        return new RateLimitGuard(maxTokens, refillPerSecond);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitGuard guard) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        // The anonymous user rate limit (100 burst, ~2/sec) protects public/user lanes.
        // Internal service-to-service calls (/v1/internal/*) are trusted and network-
        // isolated; a legitimate bulk internal operation must not be throttled as if it
        // were an abusive anonymous client, so exempt them.
        registration.setFilter(new RateLimitFilter(guard) {
            @Override
            protected boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest request) {
                String uri = request.getRequestURI();
                return (uri != null && uri.startsWith("/v1/internal/")) || super.shouldNotFilter(request);
            }
        });
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    @Bean
    public AdminAuditEmitter adminAuditEmitter(DataSource dataSource) {
        return new AdminAuditEmitter("varapi", new AdminAuditOutboxSink(dataSource, "varapi"));
    }

    @Bean
    public SecretProvider secretProvider() {
        return new EnvSecretProvider("IMPILO_VARAPI_");
    }

    @Bean
    public SecurityBaselineExceptionHandler securityBaselineExceptionHandler() {
        return new SecurityBaselineExceptionHandler();
    }
}
