package zw.gov.mohcc.impilo.tshepo.config;

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
 * Wave 14 security hardening configuration for TSHEPO.
 * Registers rate limiting, admin audit emission, and secret provider beans.
 */
@Configuration
public class SecurityBaselineConfig {

    @Bean
    public RateLimitGuard rateLimitGuard(RateLimitConfig rateLimitConfig) {
        // Convert requests/minute to tokens/second
        long maxTokens = rateLimitConfig.getMaxRequestsPerMinute();
        long refillPerSecond = Math.max(1, maxTokens / 60);
        return new RateLimitGuard(maxTokens, refillPerSecond);
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
        return new AdminAuditEmitter("tshepo", new AdminAuditOutboxSink(dataSource, "tshepo"));
    }

    @Bean
    public SecretProvider secretProvider() {
        return new EnvSecretProvider("IMPILO_TSHEPO_");
    }

    @Bean
    public SecurityBaselineExceptionHandler securityBaselineExceptionHandler() {
        return new SecurityBaselineExceptionHandler();
    }
}
