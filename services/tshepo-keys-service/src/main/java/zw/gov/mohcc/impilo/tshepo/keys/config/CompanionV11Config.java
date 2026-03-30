package zw.gov.mohcc.impilo.tshepo.keys.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;

/**
 * TSHEPO Keys consistency class registration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        // Key management
        registry.register(ActionRegistryEntry.classA("/v1/keys/rotate", "POST", "KEY_ROTATE", true));
        registry.register(ActionRegistryEntry.classC("/v1/keys", "GET", "KEY_LIST"));
        registry.register(ActionRegistryEntry.classC("/v1/keys/{keyId}", "GET", "KEY_GET"));
        registry.register(ActionRegistryEntry.classA("/v1/keys/{keyId}", "DELETE", "KEY_REVOKE", true));

        // Signing
        registry.register(ActionRegistryEntry.classC("/v1/sign", "POST", "SIGN_PAYLOAD"));
        registry.register(ActionRegistryEntry.classB("/v1/sign/token", "POST", "TOKEN_ISSUE", 0));

        // Certificate trust
        registry.register(ActionRegistryEntry.classB("/v1/certificates", "POST", "CERTIFICATE_IMPORT", 0));
        registry.register(ActionRegistryEntry.classC("/v1/certificates", "GET", "CERTIFICATE_LIST"));
        registry.register(ActionRegistryEntry.classA("/v1/certificates/{id}", "DELETE", "CERTIFICATE_REVOKE", true));

        // JWKS
        registry.register(ActionRegistryEntry.classC("/v1/keys/jwks", "GET", "JWKS_GET"));

        return registry;
    }
}
