package zw.gov.mohcc.impilo.companion.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ConsistencyClassFilter;
import zw.gov.mohcc.impilo.companion.consistency.PdpClient;
import zw.gov.mohcc.impilo.companion.consistency.StalenessProvider;
import zw.gov.mohcc.impilo.companion.filter.CompanionExceptionHandler;
import zw.gov.mohcc.impilo.companion.filter.IdempotencyFilter;
import zw.gov.mohcc.impilo.companion.filter.CorrelationMdcFilter;
import zw.gov.mohcc.impilo.companion.filter.TimeoutEnforcementFilter;
import zw.gov.mohcc.impilo.companion.filter.V11HeaderFilter;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;
import zw.gov.mohcc.impilo.companion.idempotency.InMemoryIdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * Spring Boot auto-configuration for the Tech Companion v1.1 filter chain.
 *
 * <p>Activates automatically when:
 * <ul>
 *   <li>Running in a servlet web application</li>
 *   <li>{@link V11HeaderFilter} is on the classpath (tech-companion dependency present)</li>
 *   <li>{@code impilo.companion.enabled} is not explicitly set to {@code false}</li>
 * </ul>
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link V11HeaderFilter} at order 10 — enforces all four required v1.1 headers</li>
 *   <li>{@link IdempotencyFilter} at order 11 — enforces Idempotency-Key on commands</li>
 *   <li>{@link TimeoutEnforcementFilter} at order 12 — client timeout enforcement</li>
 *   <li>{@link CompanionExceptionHandler} — federation + timeout exception mapping</li>
 * </ul>
 *
 * <p>URL patterns cover both flat and nested v1.1 paths:
 * {@code /internal/v1/*}, {@code /internal/v1/**}, {@code /external/v1/*}, {@code /external/v1/**}.
 * The filters themselves contain internal path checks (startsWith) so over-matching is safe.
 *
 * <p>Services can disable via {@code impilo.companion.enabled=false}.
 * Services can override any bean (e.g. supply their own schema-prefixed IdempotencyRepository).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(V11HeaderFilter.class)
@ConditionalOnProperty(name = "impilo.companion.enabled", havingValue = "true", matchIfMissing = true)
@Import(CompanionExceptionHandler.class)
public class TechCompanionAutoConfiguration {

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

    // ── JDBC idempotency (preferred when JdbcTemplate is available) ──

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    static class JdbcIdempotencyConfiguration {

        @Bean
        IdempotencyRepository companionJdbcIdempotencyRepository(JdbcTemplate jdbc) {
            return new JdbcIdempotencyRepository(jdbc, "idempotency_keys", 24);
        }
    }

    // ── In-memory fallback (dev/test with no DataSource) ────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    static class InMemoryIdempotencyConfiguration {

        @Bean
        InMemoryIdempotencyRepository companionInMemoryIdempotencyRepository() {
            return new InMemoryIdempotencyRepository();
        }
    }

    // ── Idempotency Service ────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(IdempotencyService.class)
    public IdempotencyService companionIdempotencyService(IdempotencyRepository repository) {
        return new IdempotencyService(repository);
    }

    // ── Filter Registrations ───────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(name = "companionV11HeaderFilter")
    public FilterRegistrationBean<V11HeaderFilter> companionV11HeaderFilter() {
        FilterRegistrationBean<V11HeaderFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new V11HeaderFilter());
        reg.addUrlPatterns(V11_URL_PATTERNS);
        reg.setOrder(10);
        reg.setName("companionV11HeaderFilter");
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean(name = "companionIdempotencyFilter")
    public FilterRegistrationBean<IdempotencyFilter> companionIdempotencyFilter(
            IdempotencyService idempotencyService) {
        FilterRegistrationBean<IdempotencyFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new IdempotencyFilter(idempotencyService));
        reg.addUrlPatterns(IDEMPOTENCY_URL_PATTERNS);
        reg.setOrder(11);
        reg.setName("companionIdempotencyFilter");
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean(name = "companionTimeoutFilter")
    public FilterRegistrationBean<TimeoutEnforcementFilter> companionTimeoutFilter() {
        FilterRegistrationBean<TimeoutEnforcementFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new TimeoutEnforcementFilter());
        reg.addUrlPatterns(V11_URL_PATTERNS);
        reg.setOrder(12);
        reg.setName("companionTimeoutFilter");
        return reg;
    }

    // ── Correlation MDC Filter (structured logging) ────────────

    @Bean
    @ConditionalOnMissingBean(name = "companionCorrelationMdcFilter")
    public FilterRegistrationBean<CorrelationMdcFilter> companionCorrelationMdcFilter() {
        FilterRegistrationBean<CorrelationMdcFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new CorrelationMdcFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(15);
        reg.setName("companionCorrelationMdcFilter");
        return reg;
    }

    // ── Consistency Class Enforcement ───────────────────────────

    @Bean
    @ConditionalOnMissingBean(name = "companionConsistencyClassFilter")
    @ConditionalOnBean(ActionRegistry.class)
    public FilterRegistrationBean<ConsistencyClassFilter> companionConsistencyClassFilter(
            ActionRegistry actionRegistry,
            @Nullable PdpClient pdpClient,
            @Nullable StalenessProvider stalenessProvider) {
        FilterRegistrationBean<ConsistencyClassFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new ConsistencyClassFilter(actionRegistry, pdpClient, stalenessProvider));
        reg.addUrlPatterns(V11_URL_PATTERNS);
        reg.setOrder(13);
        reg.setName("companionConsistencyClassFilter");
        return reg;
    }
}
