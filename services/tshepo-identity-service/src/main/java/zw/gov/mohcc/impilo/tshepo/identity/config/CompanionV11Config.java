package zw.gov.mohcc.impilo.tshepo.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;

/**
 * TSHEPO Identity consistency class registration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        // CPID
        registry.register(ActionRegistryEntry.classC("/v1/identity/cpid/generate", "POST", "CPID_GENERATE"));
        registry.register(ActionRegistryEntry.classB("/v1/identity/cpid/provisional", "POST", "CPID_PROVISIONAL", 0));

        // Resolution & Mapping
        registry.register(ActionRegistryEntry.classC("/v1/identity/resolve", "POST", "IDENTITY_RESOLVE"));
        registry.register(ActionRegistryEntry.classC("/v1/identity/mapping/{healthId}", "GET", "IDENTITY_MAPPING_GET"));
        registry.register(ActionRegistryEntry.classB("/v1/identity/mapping", "POST", "IDENTITY_MAPPING_CREATE", 0));

        // Tokens
        registry.register(ActionRegistryEntry.classB("/v1/identity/tokens", "POST", "TOKEN_ISSUE", 0));
        registry.register(ActionRegistryEntry.classC("/v1/identity/tokens/introspect", "POST", "TOKEN_INTROSPECT"));
        registry.register(ActionRegistryEntry.classA("/v1/identity/tokens/{jti}", "DELETE", "TOKEN_REVOKE", true));

        // MOSIP
        registry.register(ActionRegistryEntry.classB("/v1/identity/mosip/link", "POST", "MOSIP_LINK_CREATE", 0));
        registry.register(ActionRegistryEntry.classA("/v1/identity/mosip/verify", "POST", "MOSIP_VERIFY", true));

        // Reconciliation
        registry.register(ActionRegistryEntry.classA("/v1/identity/reconcile", "POST", "IDENTITY_RECONCILE", true));
        registry.register(ActionRegistryEntry.classC("/v1/identity/provisional", "GET", "IDENTITY_PROVISIONAL_LIST"));

        // Probe (v1.1)
        registry.register(ActionRegistryEntry.classC("/internal/v1/health", "GET", "PROBE_HEALTH"));
        registry.register(ActionRegistryEntry.classB("/internal/v1/test-command", "POST", "PROBE_TEST", 0));

        return registry;
    }
}
