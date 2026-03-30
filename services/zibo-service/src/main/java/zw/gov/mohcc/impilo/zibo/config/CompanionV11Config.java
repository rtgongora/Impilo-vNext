package zw.gov.mohcc.impilo.zibo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * ZIBO-specific Tech Companion configuration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "zibo.idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        // Artifacts
        registry.register(ActionRegistryEntry.classB("/v1/artifacts", "POST", "create_artifact_draft", 0));
        registry.register(ActionRegistryEntry.classB("/v1/artifacts/{id}", "PUT", "update_artifact_draft", 0));
        registry.register(ActionRegistryEntry.classA("/v1/artifacts/{id}/publish", "POST", "publish_artifact", true));
        registry.register(ActionRegistryEntry.classA("/v1/artifacts/{id}/deprecate", "POST", "deprecate_artifact", true));
        registry.register(ActionRegistryEntry.classA("/v1/artifacts/{id}/retire", "POST", "retire_artifact", true));
        registry.register(ActionRegistryEntry.classC("/v1/artifacts/{id}", "GET", "get_artifact"));
        registry.register(ActionRegistryEntry.classC("/v1/artifacts", "GET", "list_artifacts"));
        registry.register(ActionRegistryEntry.classC("/v1/artifacts/versions", "GET", "list_artifact_versions"));
        registry.register(ActionRegistryEntry.classC("/v1/artifacts/resolve", "GET", "resolve_artifact"));

        // Snapshots
        registry.register(ActionRegistryEntry.classC("/internal/v1/snapshots/artifacts", "GET", "artifact_snapshot"));
        registry.register(ActionRegistryEntry.classC("/internal/v1/snapshots/artifacts/emit", "POST", "emit_artifact_snapshot"));

        return registry;
    }
}
