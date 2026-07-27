package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter;
import zw.gov.mohcc.impilo.companion.filter.V11HeaderFilter;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;

@Configuration
@EnableConfigurationProperties(TenantAuthorityProperties.class)
public class FilterConfig {

    /**
     * Server-authoritative tenant (SHADOW by default — observes, never overrides).
     *
     * <p><b>Why here and not in {@code SecurityConfig} beside {@link ActorContextFilter}.</b>
     * ActorContextFilter is registered inside {@code if (jwtDecoder != null)}, so on a deployment
     * with no JwtDecoder it is never registered at all — which today includes full-preview, where
     * {@code OAuth2ResourceServerAutoConfiguration} is excluded outright. A filter that vanishes
     * exactly where the estate runs cannot measure anything there. Registering it as an ordinary
     * filter bean makes it present on every deployment; it reads the SecurityContext rather than
     * depending on how the chain was assembled, and reports "unauthenticated" honestly when there
     * is no principal to be authoritative about.</p>
     *
     * <p>Order 0 places it INSIDE Spring Security's chain (registered at
     * {@code SecurityProperties.DEFAULT_FILTER_ORDER = -100}), so the SecurityContext is populated
     * when it runs, and inside the DispatcherServlet's view of the request, so an ENFORCE override
     * is what {@code RequestContextHolder} — and therefore the outbound trust-forwarding
     * interceptor — sees.</p>
     */
    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilter(
            TenantAuthorityProperties properties) {
        FilterRegistrationBean<TenantContextFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new TenantContextFilter(properties));
        reg.addUrlPatterns("/internal/v1/*");
        reg.setOrder(0);
        reg.setName("tenantContextFilter");
        return reg;
    }

    /**
     * Anonymous public gateway lane: synthesize missing platform headers on GETs under
     * the single public namespace so the V11HeaderFilter below does not 400 genuinely
     * anonymous callers (ADR gateway-public-lane-security; rig-caught W1 defect).
     * Must stay ordered before {@link #v11HeaderFilter()}.
     */
    @Bean
    public FilterRegistrationBean<PublicGatewayAnonymousDefaultsFilter> publicGatewayAnonymousDefaultsFilter() {
        FilterRegistrationBean<PublicGatewayAnonymousDefaultsFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new PublicGatewayAnonymousDefaultsFilter());
        reg.addUrlPatterns("/internal/v1/public/gateway/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 9);
        reg.setName("publicGatewayAnonymousDefaultsFilter");
        return reg;
    }

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
