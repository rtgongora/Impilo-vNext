package zw.gov.mohcc.impilo.security.secrets;

/**
 * Thrown when a required secret is not available from the {@link SecretProvider}.
 * The message contains only the key name, never the secret value.
 */
public class SecretNotFoundException extends RuntimeException {

    private final String key;

    public SecretNotFoundException(String key) {
        super("Required secret not found: " + key);
        this.key = key;
    }

    public String getKey() { return key; }
}
