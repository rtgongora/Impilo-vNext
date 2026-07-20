package zw.gov.mohcc.impilo.tshepo.keys.core.custody;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;

/**
 * Default KEK source: the hex-encoded 32-byte key configured at {@code tshepo.keys.kek}.
 *
 * <p>This preserves the exact behaviour the service has always had — the KEK is read from
 * configuration (which in production is injected from a secret manager into the environment).
 * It is selected by default ({@code impilo.keys.kek-source} unset or {@code config}).</p>
 */
@Component
@ConditionalOnProperty(name = "impilo.keys.kek-source", havingValue = "config", matchIfMissing = true)
public class ConfigKekProvider implements KekProvider {

    private final KeysProperties keysProperties;

    public ConfigKekProvider(KeysProperties keysProperties) {
        this.keysProperties = keysProperties;
    }

    @Override
    public byte[] getKek() {
        String hex = keysProperties.getKek();
        if (hex == null || hex.isBlank()) {
            throw new IllegalStateException(
                    "tshepo.keys.kek is not configured — a 32-byte hex AES-256 KEK is required for software custody.");
        }
        return hexToBytes(hex);
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
