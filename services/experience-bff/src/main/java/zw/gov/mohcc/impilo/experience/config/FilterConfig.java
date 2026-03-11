package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter;
import zw.gov.mohcc.impilo.companion.filter.V11HeaderFilter;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<V11HeaderFilter> v11HeaderFilter() {
        FilterRegistrationBean<V11HeaderFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new V11HeaderFilter());
        reg.addUrlPatterns("/internal/v1/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("v11HeaderFilter");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyService idempotencyService) {
        FilterRegistrationBean<IdempotencyFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new IdempotencyFilter(idempotencyService));
        reg.addUrlPatterns("/internal/v1/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);
        reg.setName("idempotencyFilter");
        return reg;
    }
}
