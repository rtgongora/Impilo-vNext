package zw.gov.mohcc.impilo.tshepo.keys.core.custody;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;

import java.util.Locale;
import java.util.Set;

/**
 * Default KEK source: the hex-encoded 32-byte key configured at {@code tshepo.keys.kek}.
 *
 * <p>This preserves the exact behaviour the service has always had — the KEK is read from
 * configuration (which in production is injected from a secret manager into the environment).
 * It is selected by default ({@code impilo.keys.kek-source} unset or {@code config}).</p>
 *
 * <p>Known placeholder values (the former application.yml hex default and the helm
 * {@code change-me-in-production-use-vault} literal) are rejected: they were published in
 * this repository, so encrypting under them is not custody.</p>
 */
@Component
@ConditionalOnProperty(name = "impilo.keys.kek-source", havingValue = "config", matchIfMissing = true)
public class ConfigKekProvider implements KekProvider {

    /** Former committed defaults — anyone reading the repo holds the "secret". */
    private static final Set<String> REJECTED = Set.of(
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
            "change-me-in-production-use-vault"
    );

    private final KeysProperties keysProperties;

    public ConfigKekProvider(KeysProperties keysProperties) {
        this.keysProperties = keysProperties;
    }

    @Override
    public byte[] getKek() {
        String hex = keysProperties.getKek();
        if (hex == null || hex.isBlank()) {
            throw new IllegalStateException(
                    "tshepo.keys.kek is not configured — a 32-byte hex AES-256 KEK is required for software custody. "
                            + "Provision via scripts/secrets/bootstrap-secrets.sh (impilo-app-secrets/tshepo-keys-kek).");
        }
        String trimmed = hex.strip();
        if (REJECTED.contains(trimmed.toLowerCase(Locale.ROOT))
                || trimmed.toLowerCase(Locale.ROOT).contains("change-me")) {
            throw new IllegalStateException(
                    "tshepo.keys.kek is a known placeholder, not a secret. Refusing to start: "
                            + "software-custody private keys would be decryptable by anyone with the repository. "
                            + "Provision a strong value via scripts/secrets/bootstrap-secrets.sh.");
        }
        if (trimmed.length() != 64) {
            throw new IllegalStateException(
                    "tshepo.keys.kek must be exactly 64 hex characters (32 bytes AES-256), got "
                            + trimmed.length() + ".");
        }
        return hexToBytes(trimmed);
    }

    @Override
    public String sourceType() {
        return "CONFIG";
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
