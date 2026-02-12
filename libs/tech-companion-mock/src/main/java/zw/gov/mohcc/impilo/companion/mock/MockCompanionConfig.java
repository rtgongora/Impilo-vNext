package zw.gov.mohcc.impilo.companion.mock;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.companion.filter.CompanionExceptionHandler;
import zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter;
import zw.gov.mohcc.impilo.companion.filter.TimeoutEnforcementFilter;
import zw.gov.mohcc.impilo.companion.filter.V11HeaderFilter;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;
import zw.gov.mohcc.impilo.companion.idempotency.InMemoryIdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;

/**
 * Configuration wiring the companion filters for the mock service.
 *
 * This demonstrates the MINIMAL wiring a real service needs:
 * 1. Register V11HeaderFilter (Order 10)
 * 2. Register IdempotencyFilter (Order 11)
 * 3. Register TimeoutEnforcementFilter (Order 12)
 * 4. Provide IdempotencyRepository bean
 * 5. Register CompanionExceptionHandler (via @ComponentScan or @Import)
 */
@Configuration
public class MockCompanionConfig {

    @Bean
    public FilterRegistrationBean<V11HeaderFilter> v11HeaderFilter() {
        FilterRegistrationBean<V11HeaderFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new V11HeaderFilter());
        reg.addUrlPatterns("/internal/v1/*", "/external/v1/*");
        reg.setOrder(10);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(
            IdempotencyService idempotencyService) {
        FilterRegistrationBean<IdempotencyFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new IdempotencyFilter(idempotencyService));
        reg.addUrlPatterns("/internal/v1/*");
        reg.setOrder(11);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<TimeoutEnforcementFilter> timeoutFilter() {
        FilterRegistrationBean<TimeoutEnforcementFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new TimeoutEnforcementFilter());
        reg.addUrlPatterns("/internal/v1/*", "/external/v1/*");
        reg.setOrder(12);
        return reg;
    }

    @Bean
    public IdempotencyRepository idempotencyRepository() {
        return new InMemoryIdempotencyRepository();
    }

    @Bean
    public IdempotencyService idempotencyService(IdempotencyRepository repository) {
        return new IdempotencyService(repository);
    }

    @Bean
    public CompanionExceptionHandler companionExceptionHandler() {
        return new CompanionExceptionHandler();
    }
}
