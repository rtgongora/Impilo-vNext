package zw.gov.mohcc.impilo.fhirgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "fhir_gateway.idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        registry.register(ActionRegistryEntry.classC("/fhir/**", "GET", "fhir_read"));
        registry.register(ActionRegistryEntry.classB("/fhir/**", "POST", "fhir_create", 0));
        registry.register(ActionRegistryEntry.classB("/fhir/**", "PUT", "fhir_update", 0));
        registry.register(ActionRegistryEntry.classA("/fhir/**", "DELETE", "fhir_delete", false));

        registry.register(ActionRegistryEntry.classC("/internal/v1/gateway/routes", "GET", "list_routes"));
        registry.register(ActionRegistryEntry.classB("/internal/v1/gateway/routes", "POST", "create_route", 0));
        registry.register(ActionRegistryEntry.classB("/internal/v1/gateway/forward", "POST", "forward_request", 0));
        registry.register(ActionRegistryEntry.classC("/internal/v1/gateway/audit", "GET", "audit_logs"));

        return registry;
    }
}
