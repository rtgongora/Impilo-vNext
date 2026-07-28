package zw.gov.mohcc.impilo.mentalhealth.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import zw.gov.mohcc.impilo.security.ratelimit.RateLimitGuard;
import zw.gov.mohcc.impilo.security.secrets.EnvSecretProvider;
import zw.gov.mohcc.impilo.security.secrets.SecretProvider;
import zw.gov.mohcc.impilo.shared.security.RateLimitFilter;
import zw.gov.mohcc.impilo.shared.security.SecurityBaselineExceptionHandler;

/** Security baseline shared across every v1.1-native service (rate limit + secret provider). */
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
        return new EnvSecretProvider("IMPILO_MENTAL_HEALTH_");
    }

    @Bean
    public SecurityBaselineExceptionHandler securityBaselineExceptionHandler() {
        return new SecurityBaselineExceptionHandler();
    }
}
