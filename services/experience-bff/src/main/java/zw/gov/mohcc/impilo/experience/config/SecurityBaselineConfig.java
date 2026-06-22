package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import zw.gov.mohcc.impilo.security.ratelimit.RateLimitGuard;
import zw.gov.mohcc.impilo.security.secrets.EnvSecretProvider;
import zw.gov.mohcc.impilo.security.secrets.SecretProvider;
import zw.gov.mohcc.impilo.shared.security.RateLimitFilter;
import zw.gov.mohcc.impilo.shared.security.SecurityBaselineExceptionHandler;

/**
 * Wave 14 security baseline — rate limiting, secret provider, exception handling.
 * <p>
 * experience-bff is a composition/orchestration layer with no datasource (no JPA/JDBC on
 * the classpath), so it does NOT wire an {@code AdminAuditEmitter} backed by a JDBC outbox.
 * Admin audit for sensitive actions is emitted by the owning domain service of record, which
 * holds the {@code event_outbox} table. Wiring a DataSource-backed emitter here would crash
 * startup ("required a bean of type 'javax.sql.DataSource'").
 */
@Configuration
public class SecurityBaselineConfig {

    @Bean
    public RateLimitGuard rateLimitGuard() {
        return new RateLimitGuard(100, 2);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitGuard guard) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(guard));
        registration.addUrlPatterns("/v1/*", "/internal/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    @Bean
    public SecretProvider secretProvider() {
        return new EnvSecretProvider("IMPILO_EXPERIENCEBFF_");
    }

    @Bean
    public SecurityBaselineExceptionHandler securityBaselineExceptionHandler() {
        return new SecurityBaselineExceptionHandler();
    }
}
