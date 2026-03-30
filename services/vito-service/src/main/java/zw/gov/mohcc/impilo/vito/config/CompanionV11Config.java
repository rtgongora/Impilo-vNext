package zw.gov.mohcc.impilo.vito.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * VITO-specific Tech Companion configuration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "vito.idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        // Snapshots (v1.1)
        registry.register(ActionRegistryEntry.classC("/internal/v1/clients/snapshot", "GET", "client_snapshot"));

        // Legacy Snapshots (v1.0)
        registry.register(ActionRegistryEntry.classC("/internal/v1/snapshots/clients", "GET", "legacy_client_snapshot"));
        registry.register(ActionRegistryEntry.classC("/internal/v1/snapshots/clients/emit", "POST", "emit_client_snapshot"));

        // Patient Merge (v1.1)
        registry.register(ActionRegistryEntry.classA("/internal/v1/patients/merge", "POST", "merge_patients", true));

        return registry;
    }
}
