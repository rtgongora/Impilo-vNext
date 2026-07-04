package zw.gov.mohcc.impilo.rtc.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter;
import zw.gov.mohcc.impilo.companion.filter.V11HeaderFilter;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;

import java.io.IOException;

/**
 * Overrides the tech-companion auto-configured V1.1 header + idempotency filter
 * registrations (same bean names, so the auto-config backs off) to exempt exactly
 * the LiveKit webhook path. LiveKit is an external-origin caller: it sends neither
 * companion trust headers nor Idempotency-Key — authenticity is enforced by
 * {@code LiveKitWebhookVerifier} (HS256 signature + body hash) and replay safety by
 * the {@code lk_event_id} unique key in rtc.session_events.
 *
 * <p>All other {@code /internal/v1/**} routes keep full companion enforcement.
 */
@Configuration
public class CompanionWebhookExemptionConfig {

    private static final String WEBHOOK_PATH_PREFIX = "/internal/v1/rtc/webhooks/";

    private static final String[] V11_URL_PATTERNS = {
            "/internal/v1/*",
            "/internal/v1/**",
            "/external/v1/*",
            "/external/v1/**"
    };

    private static final String[] IDEMPOTENCY_URL_PATTERNS = {
            "/internal/v1/*",
            "/internal/v1/**"
    };

    @Bean
    public FilterRegistrationBean<Filter> companionV11HeaderFilter() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(exemptWebhooks(new V11HeaderFilter()));
        reg.addUrlPatterns(V11_URL_PATTERNS);
        reg.setOrder(10);
        reg.setName("companionV11HeaderFilter");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<Filter> companionIdempotencyFilter(IdempotencyService idempotencyService) {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(exemptWebhooks(new IdempotencyFilter(idempotencyService)));
        reg.addUrlPatterns(IDEMPOTENCY_URL_PATTERNS);
        reg.setOrder(11);
        reg.setName("companionIdempotencyFilter");
        return reg;
    }

    private static Filter exemptWebhooks(Filter delegate) {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                if (request instanceof HttpServletRequest http
                        && http.getRequestURI() != null
                        && http.getRequestURI().startsWith(WEBHOOK_PATH_PREFIX)) {
                    chain.doFilter(request, response);
                    return;
                }
                delegate.doFilter(request, response, chain);
            }
        };
    }
}
