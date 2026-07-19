package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Default {@link SecretResolver}: resolves {@code secret_ref} through Spring
 * configuration under {@code impilo.deid.secrets.<secret-ref>} (relaxed
 * binding also picks up {@code IMPILO_DEID_SECRETS_<SECRET_REF>} env vars).
 *
 * <p><strong>Production custody is tshepo-keys:</strong> in deployed
 * environments the property values are projected from tshepo-keys managed
 * secrets — key material is never committed to configuration files and never
 * stored in this service's database. This bean is the seam to swap for a
 * direct tshepo-keys client without touching any transform.</p>
 */
@Component
public class ConfigSecretResolver implements SecretResolver {

    static final String PROPERTY_PREFIX = "impilo.deid.secrets.";

    private final Environment environment;

    public ConfigSecretResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String resolve(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new IllegalStateException("De-identification secret reference is required");
        }
        String secret = environment.getProperty(PROPERTY_PREFIX + secretRef);
        if (secret == null || secret.isBlank()) {
            // Fail closed — never tokenise with a missing/blank secret.
            throw new IllegalStateException(
                    "De-identification secret not available for reference '" + secretRef
                            + "' (production custody: tshepo-keys)");
        }
        return secret;
    }
}
