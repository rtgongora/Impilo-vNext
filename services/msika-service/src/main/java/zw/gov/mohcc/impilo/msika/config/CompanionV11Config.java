package zw.gov.mohcc.impilo.msika.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * MSIKA-specific Tech Companion configuration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "msika.idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        // Catalogs
        registry.register(ActionRegistryEntry.classB("/v1/catalogs", "POST", "create_catalog", 0));
        registry.register(ActionRegistryEntry.classC("/v1/catalogs", "GET", "list_catalogs"));
        registry.register(ActionRegistryEntry.classC("/v1/catalogs/{id}", "GET", "get_catalog"));
        registry.register(ActionRegistryEntry.classB("/v1/catalogs/{id}/submit-review", "POST", "submit_catalog_review", 0));
        registry.register(ActionRegistryEntry.classA("/v1/catalogs/{id}/approve", "POST", "approve_catalog", true));
        registry.register(ActionRegistryEntry.classA("/v1/catalogs/{id}/publish", "POST", "publish_catalog", true));
        registry.register(ActionRegistryEntry.classA("/v1/catalogs/{id}/rollback/{version}", "POST", "rollback_catalog", true));

        // Snapshots
        registry.register(ActionRegistryEntry.classC("/internal/v1/snapshots/catalogs", "GET", "catalog_snapshot"));
        registry.register(ActionRegistryEntry.classC("/internal/v1/snapshots/catalogs/emit", "POST", "emit_catalog_snapshot"));

        // Products
        registry.register(ActionRegistryEntry.classB("/internal/v1/products", "POST", "create_product", 0));
        registry.register(ActionRegistryEntry.classC("/internal/v1/products/snapshot", "GET", "product_snapshot"));

        // Service catalog
        registry.register(ActionRegistryEntry.classB("/internal/v1/service-catalogs/items", "POST", "create_service_item", 0));
        registry.register(ActionRegistryEntry.classC("/internal/v1/service-catalogs/snapshot", "GET", "service_snapshot"));

        // Tariffs
        registry.register(ActionRegistryEntry.classB("/internal/v1/tariffs", "POST", "create_tariff", 0));
        registry.register(ActionRegistryEntry.classC("/internal/v1/tariffs", "GET", "list_tariffs"));
        registry.register(ActionRegistryEntry.classC("/internal/v1/tariffs/{id}", "GET", "get_tariff"));
        registry.register(ActionRegistryEntry.classA("/internal/v1/tariffs/{id}", "PUT", "update_tariff", true));

        return registry;
    }
}
