package zw.gov.mohcc.impilo.pharmacy.config;

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
 * Wave 14 security hardening configuration for PHARMACY.
 */
@Configuration
public class SecurityBaselineConfig {

    @Bean
    public RateLimitGuard rateLimitGuard() {
        return new RateLimitGuard(150, 2);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitGuard guard) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(guard));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    @Bean
    public AdminAuditEmitter adminAuditEmitter(DataSource dataSource) {
        return new AdminAuditEmitter("pharmacy", new AdminAuditOutboxSink(dataSource, "pharmacy"));
    }

    @Bean
    public SecretProvider secretProvider() {
        return new EnvSecretProvider("IMPILO_PHARMACY_");
    }

    @Bean
    public SecurityBaselineExceptionHandler securityBaselineExceptionHandler() {
        return new SecurityBaselineExceptionHandler();
    }
}
