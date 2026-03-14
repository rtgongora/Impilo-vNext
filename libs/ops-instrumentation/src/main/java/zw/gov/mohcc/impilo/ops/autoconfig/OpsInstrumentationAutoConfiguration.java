package zw.gov.mohcc.impilo.ops.autoconfig;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.ops.health.DatabaseHealthIndicator;
import zw.gov.mohcc.impilo.ops.health.OutboxHealthIndicator;
import zw.gov.mohcc.impilo.ops.mdc.MdcFilter;
import zw.gov.mohcc.impilo.ops.metrics.GoldenSignalsFilter;
import zw.gov.mohcc.impilo.ops.metrics.IdempotencyMetrics;
import zw.gov.mohcc.impilo.ops.metrics.OutboxLagProbe;
import zw.gov.mohcc.impilo.ops.otel.OtelPropagationFilter;

/**
 * Auto-configuration for Impilo ops instrumentation.
 *
 * <p>Activates when running in a servlet web application with
 * {@code impilo.ops.enabled=true} (default: true).
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link OtelPropagationFilter} at order 4 — trace context propagation</li>
 *   <li>{@link MdcFilter} at order 5 — structured logging MDC injection</li>
 *   <li>{@link GoldenSignalsFilter} at order 6 — latency/error metrics (when Micrometer available)</li>
 *   <li>{@link IdempotencyMetrics} — replay/conflict counters (when Micrometer available)</li>
 *   <li>{@link OutboxLagProbe} — outbox lag gauge (when JDBC + Micrometer available)</li>
 *   <li>{@link DatabaseHealthIndicator} — DB connectivity check (when JDBC + Actuator available)</li>
 *   <li>{@link OutboxHealthIndicator} — outbox lag health (when JDBC + Actuator available)</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "impilo.ops.enabled", havingValue = "true", matchIfMissing = true)
public class OpsInstrumentationAutoConfiguration {

    private static final String[] ALL_URL_PATTERNS = {"/*"};

    // ── OTel Propagation Filter (order 4) ─────────────────────────

    @Bean
    @ConditionalOnMissingBean(name = "opsOtelPropagationFilter")
    public FilterRegistrationBean<OtelPropagationFilter> opsOtelPropagationFilter() {
        FilterRegistrationBean<OtelPropagationFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new OtelPropagationFilter());
        reg.addUrlPatterns(ALL_URL_PATTERNS);
        reg.setOrder(4);
        reg.setName("opsOtelPropagationFilter");
        return reg;
    }

    // ── MDC Filter (order 5) ──────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(name = "opsMdcFilter")
    public FilterRegistrationBean<MdcFilter> opsMdcFilter() {
        FilterRegistrationBean<MdcFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new MdcFilter());
        reg.addUrlPatterns(ALL_URL_PATTERNS);
        reg.setOrder(5);
        reg.setName("opsMdcFilter");
        return reg;
    }

    // ── Golden Signals Metrics (order 6, requires Micrometer) ─────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "opsGoldenSignalsFilter")
        public FilterRegistrationBean<GoldenSignalsFilter> opsGoldenSignalsFilter(MeterRegistry registry) {
            FilterRegistrationBean<GoldenSignalsFilter> reg = new FilterRegistrationBean<>();
            reg.setFilter(new GoldenSignalsFilter(registry));
            reg.addUrlPatterns(ALL_URL_PATTERNS);
            reg.setOrder(6);
            reg.setName("opsGoldenSignalsFilter");
            return reg;
        }

        @Bean
        @ConditionalOnMissingBean(IdempotencyMetrics.class)
        public IdempotencyMetrics opsIdempotencyMetrics(MeterRegistry registry) {
            return new IdempotencyMetrics(registry);
        }
    }

    // ── Outbox Lag Probe (requires JDBC + Micrometer) ─────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({JdbcTemplate.class, MeterRegistry.class})
    @ConditionalOnBean({JdbcTemplate.class, MeterRegistry.class})
    static class OutboxLagConfiguration {

        @Bean
        @ConditionalOnMissingBean(OutboxLagProbe.class)
        public OutboxLagProbe opsOutboxLagProbe(
                JdbcTemplate jdbc,
                MeterRegistry registry,
                @Value("${impilo.ops.outbox-table:event_outbox}") String outboxTable) {
            return new OutboxLagProbe(jdbc, registry, outboxTable);
        }
    }

    // ── Health Indicators (requires JDBC + Actuator) ──────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(org.springframework.boot.actuate.health.HealthIndicator.class)
    @ConditionalOnBean(JdbcTemplate.class)
    static class HealthConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "opsDatabaseHealth")
        public DatabaseHealthIndicator opsDatabaseHealth(JdbcTemplate jdbc) {
            return new DatabaseHealthIndicator(jdbc);
        }

        @Bean
        @ConditionalOnMissingBean(name = "opsOutboxHealth")
        public OutboxHealthIndicator opsOutboxHealth(
                JdbcTemplate jdbc,
                @Value("${impilo.ops.outbox-table:event_outbox}") String outboxTable,
                @Value("${impilo.ops.outbox-lag-threshold:1000}") int threshold) {
            return new OutboxHealthIndicator(jdbc, outboxTable, threshold);
        }
    }
}
