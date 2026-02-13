package zw.gov.mohcc.impilo.tuso.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.filter.CompanionExceptionHandler;
import zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter;
import zw.gov.mohcc.impilo.companion.filter.V11HeaderFilter;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * Registers the Tech Companion v1.1 filter chain for TUSO.
 *
 * Filter order:
 *   10 — V11HeaderFilter (mandatory headers)
 *   11 — IdempotencyFilter (POST/PUT/PATCH idempotency on /internal/v1/**)
 *
 * Scoped to /internal/v1/* and /external/v1/* URL patterns only.
 * Legacy /v1/internal/** routes are NOT affected.
 */
@Configuration
@Import(CompanionExceptionHandler.class)
public class CompanionV11Config {

    @Bean
    public JdbcIdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "tuso.idempotency_keys", 24);
    }

    @Bean
    public IdempotencyService companionIdempotencyService(JdbcIdempotencyRepository repo) {
        return new IdempotencyService(repo);
    }

    @Bean
    public FilterRegistrationBean<V11HeaderFilter> v11HeaderFilter() {
        FilterRegistrationBean<V11HeaderFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new V11HeaderFilter());
        reg.addUrlPatterns("/internal/v1/*", "/external/v1/*");
        reg.setOrder(10);
        reg.setName("companionV11HeaderFilter");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> v11IdempotencyFilter(
            IdempotencyService companionIdempotencyService) {
        FilterRegistrationBean<IdempotencyFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new IdempotencyFilter(companionIdempotencyService));
        reg.addUrlPatterns("/internal/v1/*");
        reg.setOrder(11);
        reg.setName("companionIdempotencyFilter");
        return reg;
    }
}
