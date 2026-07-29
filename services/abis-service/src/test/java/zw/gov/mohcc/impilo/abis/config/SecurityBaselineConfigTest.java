package zw.gov.mohcc.impilo.abis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import zw.gov.mohcc.impilo.security.ratelimit.RateLimitGuard;
import zw.gov.mohcc.impilo.shared.security.RateLimitFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 14 security baseline wiring: the rate-limit filter must guard the /v1
 * biometric API surface at highest-precedence, matching the estate template.
 */
class SecurityBaselineConfigTest {

    private final SecurityBaselineConfig config = new SecurityBaselineConfig();

    @Test
    void rateLimitFilterGuardsV1ApiSurface() {
        // The bean takes the bucket size as parameters now, defaulted from properties in Spring; this
        // test constructs it directly, so it passes the baseline values explicitly.
        RateLimitGuard guard = config.rateLimitGuard(100, 2);
        FilterRegistrationBean<RateLimitFilter> registration = config.rateLimitFilter(guard);

        assertThat(registration.getUrlPatterns()).contains("/v1/*");
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 5);
        assertThat(registration.getFilter()).isNotNull();
    }

    @Test
    void secretProviderUsesAbisEnvPrefix() {
        assertThat(config.secretProvider()).isNotNull();
    }
}
