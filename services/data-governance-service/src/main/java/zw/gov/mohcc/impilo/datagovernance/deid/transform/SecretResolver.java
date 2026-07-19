package zw.gov.mohcc.impilo.datagovernance.deid.transform;

/**
 * Resolution seam for per-dataset HMAC secrets.
 *
 * <p>The schema stores only a key <em>reference</em>
 * ({@code deid_dataset.secret_ref}); the secret material itself lives in
 * tshepo-keys custody. Implementations resolve the reference to material at
 * run time and must fail closed (throw) when the reference cannot be
 * resolved — a run must never proceed with a missing or blank secret.</p>
 *
 * @see ConfigSecretResolver default implementation (config/env backed)
 */
public interface SecretResolver {

    /**
     * Resolve a secret reference to key material.
     *
     * @throws IllegalStateException when the reference cannot be resolved
     */
    String resolve(String secretRef);
}
