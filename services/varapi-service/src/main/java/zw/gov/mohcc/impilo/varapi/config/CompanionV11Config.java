package zw.gov.mohcc.impilo.varapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * VARAPI-specific Tech Companion configuration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "varapi.idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        // Providers
        registry.register(ActionRegistryEntry.classB("/v1/internal/providers", "POST", "create_provider", 0));
        registry.register(ActionRegistryEntry.classC("/v1/internal/providers/{id}", "GET", "get_provider"));
        registry.register(ActionRegistryEntry.classC("/v1/internal/providers/search", "POST", "search_providers"));
        registry.register(ActionRegistryEntry.classB("/v1/internal/providers/{id}", "PUT", "update_provider", 0));
        registry.register(ActionRegistryEntry.classA("/v1/internal/providers/{id}/status", "POST", "change_provider_status", true));

        // Licenses
        registry.register(ActionRegistryEntry.classB("/v1/internal/providers/{id}/licenses", "POST", "issue_license", 0));
        registry.register(ActionRegistryEntry.classB("/v1/internal/providers/{id}/licenses/{licenseId}/renew", "POST", "renew_license", 0));
        registry.register(ActionRegistryEntry.classA("/v1/internal/providers/{id}/licenses/{licenseId}/suspend", "POST", "suspend_license", true));
        registry.register(ActionRegistryEntry.classA("/v1/internal/providers/{id}/licenses/{licenseId}/revoke", "POST", "revoke_license", true));
        registry.register(ActionRegistryEntry.classC("/v1/internal/providers/{id}/licenses", "GET", "get_license_history"));

        // Privileges
        registry.register(ActionRegistryEntry.classB("/v1/internal/providers/{id}/privileges/grant", "POST", "grant_privilege", 0));
        registry.register(ActionRegistryEntry.classA("/v1/internal/providers/{id}/privileges/revoke", "POST", "revoke_privilege", true));
        registry.register(ActionRegistryEntry.classC("/v1/internal/providers/{id}/privileges", "GET", "list_privileges"));
        registry.register(ActionRegistryEntry.classC("/v1/internal/privileges/pending", "GET", "list_pending_privileges"));
        registry.register(ActionRegistryEntry.classA("/v1/internal/privileges/{id}/decide", "POST", "decide_privilege", true));

        // Councils
        registry.register(ActionRegistryEntry.classB("/v1/internal/councils", "POST", "create_council", 0));
        registry.register(ActionRegistryEntry.classC("/v1/internal/councils/{id}", "GET", "get_council"));
        registry.register(ActionRegistryEntry.classC("/v1/internal/councils", "GET", "list_councils"));
        registry.register(ActionRegistryEntry.classA("/v1/internal/councils/{id}/imports", "POST", "trigger_council_import", true));

        // Snapshots
        registry.register(ActionRegistryEntry.classC("/internal/v1/snapshots/providers", "GET", "provider_snapshot"));
        registry.register(ActionRegistryEntry.classC("/internal/v1/snapshots/providers/emit", "POST", "emit_provider_snapshot"));

        return registry;
    }
}
