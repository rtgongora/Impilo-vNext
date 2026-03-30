package zw.gov.mohcc.impilo.tshepo.offline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;

/**
 * TSHEPO Offline consistency class registration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        // Offline actions
        registry.register(ActionRegistryEntry.classB("/v1/offline/actions", "POST", "OFFLINE_ACTION_LOG", 0));
        registry.register(ActionRegistryEntry.classC("/v1/offline/actions", "GET", "OFFLINE_ACTION_QUERY"));

        // Capabilities
        registry.register(ActionRegistryEntry.classB("/v1/offline/capabilities", "POST", "CAPABILITY_ISSUE", 0));
        registry.register(ActionRegistryEntry.classC("/v1/offline/capabilities/{id}", "GET", "CAPABILITY_GET"));
        registry.register(ActionRegistryEntry.classA("/v1/offline/capabilities/{id}", "DELETE", "CAPABILITY_REVOKE", true));
        registry.register(ActionRegistryEntry.classC("/v1/offline/capabilities/verify", "POST", "CAPABILITY_VERIFY"));

        // Reconciliation
        registry.register(ActionRegistryEntry.classA("/v1/offline/reconcile", "POST", "OFFLINE_RECONCILE", true));
        registry.register(ActionRegistryEntry.classC("/v1/offline/reconcile/{batchId}", "GET", "RECONCILE_STATUS"));
        registry.register(ActionRegistryEntry.classC("/v1/offline/reconcile/pending", "GET", "RECONCILE_PENDING_LIST"));

        // Packs
        registry.register(ActionRegistryEntry.classB("/v1/offline/packs", "POST", "OFFLINE_PACK_GENERATE", 0));
        registry.register(ActionRegistryEntry.classC("/v1/offline/packs/{id}", "GET", "OFFLINE_PACK_GET"));
        registry.register(ActionRegistryEntry.classC("/v1/offline/packs/facility/{facilityId}", "GET", "OFFLINE_PACK_LATEST"));

        return registry;
    }
}
