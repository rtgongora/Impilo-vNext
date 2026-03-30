package zw.gov.mohcc.impilo.tshepo.authz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;

/**
 * TSHEPO Authz consistency class registration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        // Authorize (ext_authz) — Class C (read evaluation)
        registry.register(ActionRegistryEntry.classC("/v1/authorize", "*", "AUTHZ_CHECK"));

        // Break-glass
        registry.register(ActionRegistryEntry.classA("/v1/break-glass", "POST", "BREAK_GLASS_REQUEST", true));
        registry.register(ActionRegistryEntry.classC("/v1/break-glass/review", "GET", "BREAK_GLASS_LIST"));
        registry.register(ActionRegistryEntry.classA("/v1/break-glass/review/{id}", "POST", "BREAK_GLASS_REVIEW", true));

        // Device management
        registry.register(ActionRegistryEntry.classC("/v1/devices/{fingerprint}", "GET", "DEVICE_GET"));
        registry.register(ActionRegistryEntry.classA("/v1/devices/{fingerprint}/block", "POST", "DEVICE_BLOCK", true));
        registry.register(ActionRegistryEntry.classA("/v1/devices/{fingerprint}/block", "DELETE", "DEVICE_UNBLOCK", true));

        // Policy management
        registry.register(ActionRegistryEntry.classC("/v1/policies", "GET", "POLICY_LIST"));
        registry.register(ActionRegistryEntry.classB("/v1/policies", "POST", "POLICY_CREATE", 0));
        registry.register(ActionRegistryEntry.classA("/v1/policies/{id}", "PUT", "POLICY_UPDATE", true));
        registry.register(ActionRegistryEntry.classA("/v1/policies/{id}", "DELETE", "POLICY_DELETE", true));

        // Step-up
        registry.register(ActionRegistryEntry.classB("/v1/step-up/challenge", "POST", "STEP_UP_CHALLENGE", 0));
        registry.register(ActionRegistryEntry.classA("/v1/step-up/verify", "POST", "STEP_UP_VERIFY", true));
        registry.register(ActionRegistryEntry.classC("/v1/step-up/status/{id}", "GET", "STEP_UP_STATUS"));

        // Federation control (internal)
        registry.register(ActionRegistryEntry.classA("/internal/v1/federation/pods", "POST", "FEDERATION_POD_REGISTER", true));
        registry.register(ActionRegistryEntry.classC("/internal/v1/federation/pods", "GET", "FEDERATION_POD_LIST"));
        registry.register(ActionRegistryEntry.classA("/internal/v1/federation/pods/{podId}/authority", "PUT", "FEDERATION_POD_AUTHORITY_UPDATE", true));
        registry.register(ActionRegistryEntry.classC("/internal/v1/federation/pods/{podId}/obligations", "GET", "FEDERATION_POD_OBLIGATIONS_GET"));

        return registry;
    }
}
