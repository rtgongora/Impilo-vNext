package zw.gov.mohcc.impilo.experience.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter;
import zw.gov.mohcc.impilo.companion.filter.V11HeaderFilter;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;

import java.io.IOException;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<Filter> v11HeaderFilter() {
        V11HeaderFilter delegate = new V11HeaderFilter();
        Filter corsAware = (request, response, chain) -> {
            HttpServletRequest req = (HttpServletRequest) request;
            if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
                chain.doFilter(request, response);
                return;
            }
            delegate.doFilter(request, response, chain);
        };

        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(corsAware);
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
