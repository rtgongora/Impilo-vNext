package zw.gov.mohcc.impilo.vito.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.filter.CompanionExceptionHandler;
import zw.gov.mohcc.impilo.companion.filter.V11HeaderFilter;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * Registers the Tech Companion v1.1 filter chain for VITO.
 *
 * Replaces the hand-rolled V1_1HeaderFilter and IdempotencyFilter
 * (whose @Component annotations have been removed).
 *
 * Filter order:
 *   10 — V11HeaderFilter (mandatory headers)
 *   11 — IdempotencyFilter (POST/PUT/PATCH idempotency on /internal/v1/**)
 *
 * Uses the existing vito.idempotency_keys table (V016 migration).
 */
@Configuration
@Import(CompanionExceptionHandler.class)
public class CompanionV11Config {

    @Bean
    public JdbcIdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "vito.idempotency_keys", 24);
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
    public FilterRegistrationBean<zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter> v11IdempotencyFilter(
            IdempotencyService companionIdempotencyService) {
        FilterRegistrationBean<zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter> reg =
                new FilterRegistrationBean<>();
        reg.setFilter(new zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter(companionIdempotencyService));
        reg.addUrlPatterns("/internal/v1/*");
        reg.setOrder(11);
        reg.setName("companionIdempotencyFilter");
        return reg;
    }
}
