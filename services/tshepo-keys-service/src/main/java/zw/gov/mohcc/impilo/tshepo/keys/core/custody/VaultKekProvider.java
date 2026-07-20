package zw.gov.mohcc.impilo.tshepo.keys.core.custody;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Vault-backed KEK source (stub).
 *
 * <p>Selected by {@code impilo.keys.kek-source=vault}. It intentionally throws until a real
 * Vault/Transit (or KMS) integration is wired: the seam exists so the KEK can be moved out of
 * config, but no unaudited "silent fallback to config" is allowed — declaring the vault source
 * without wiring it must fail loudly rather than quietly weaken KEK custody.</p>
 */
@Component
@ConditionalOnProperty(name = "impilo.keys.kek-source", havingValue = "vault")
public class VaultKekProvider implements KekProvider {

    @Override
    public byte[] getKek() {
        throw new UnsupportedOperationException(
                "impilo.keys.kek-source=vault is selected but the Vault KEK integration is not wired yet. "
                        + "Wire VaultKekProvider (Vault Transit / KMS unwrap) or set impilo.keys.kek-source=config.");
    }

    @Override
    public String sourceType() {
        return "VAULT";
    }
}
