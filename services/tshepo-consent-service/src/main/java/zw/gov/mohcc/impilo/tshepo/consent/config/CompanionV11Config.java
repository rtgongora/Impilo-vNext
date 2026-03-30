package zw.gov.mohcc.impilo.tshepo.consent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;

/**
 * TSHEPO Consent consistency class registration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        // Consent CRUD
        registry.register(ActionRegistryEntry.classB("/v1/consent", "POST", "CONSENT_CREATE", 0));
        registry.register(ActionRegistryEntry.classC("/v1/consent/{id}", "GET", "CONSENT_GET"));
        registry.register(ActionRegistryEntry.classA("/v1/consent/{id}", "PUT", "CONSENT_UPDATE", true));
        registry.register(ActionRegistryEntry.classA("/v1/consent/{id}", "DELETE", "CONSENT_REVOKE", true));
        registry.register(ActionRegistryEntry.classC("/v1/consent/patient/{patientRef}", "GET", "CONSENT_LIST_PATIENT"));

        // Evaluation
        registry.register(ActionRegistryEntry.classC("/v1/consent/evaluate", "GET", "CONSENT_EVALUATE"));

        // Share links
        registry.register(ActionRegistryEntry.classB("/v1/consent/share-links", "POST", "SHARE_LINK_CREATE", 0));
        registry.register(ActionRegistryEntry.classA("/v1/consent/share-links/{token}", "GET", "SHARE_LINK_VALIDATE", true));
        registry.register(ActionRegistryEntry.classA("/v1/consent/share-links/{id}", "DELETE", "SHARE_LINK_REVOKE", true));

        // Portal
        registry.register(ActionRegistryEntry.classC("/v1/consent/portal/my-consents", "GET", "PORTAL_CONSENT_LIST"));
        registry.register(ActionRegistryEntry.classA("/v1/consent/portal/revoke/{id}", "POST", "PORTAL_CONSENT_REVOKE", true));

        return registry;
    }
}
