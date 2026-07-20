package zw.gov.mohcc.impilo.tshepo.keys.core.custody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PKCS#11 (HSM) custody provider (W4c).
 *
 * <p>Selected by {@code impilo.keys.custody-provider=pkcs11}. Hardware-held keys are
 * <b>non-exportable</b>: {@link #exportPrivate(byte[])} always throws, which is precisely the
 * invariant that forces every trust-bearing signing path onto {@link #sign(byte[], byte[])}
 * (and JWS assembly onto the custody-sign path — see {@code Ed25519SigningService.signJwsWithKey}).</p>
 *
 * <p>This implementation is deliberately <b>inert without a real PKCS#11 module</b>: no HSM
 * library ships with the estate and no SunPKCS11 config is bundled. When
 * {@code impilo.keys.pkcs11.config} is unset, {@link #generate()} and {@link #sign(byte[], byte[])}
 * fail loudly rather than silently degrading to software keys. Wiring a real HSM means providing
 * a SunPKCS11 config and completing the token session logic below; the seam (conditional bean,
 * export-forbidden, custody-sign) is already in place so the rest of the service is unaffected.</p>
 */
@Component
@ConditionalOnProperty(name = "impilo.keys.custody-provider", havingValue = "pkcs11")
public class Pkcs11KeyCustodyProvider implements KeyCustodyProvider {

    private static final Logger log = LoggerFactory.getLogger(Pkcs11KeyCustodyProvider.class);

    /** Path to a SunPKCS11 provider config (name/library/slot). Empty ⇒ inert (no HSM present). */
    private final String pkcs11ConfigPath;

    public Pkcs11KeyCustodyProvider(
            @Value("${impilo.keys.pkcs11.config:}") String pkcs11ConfigPath) {
        this.pkcs11ConfigPath = pkcs11ConfigPath == null ? "" : pkcs11ConfigPath.strip();
        if (this.pkcs11ConfigPath.isEmpty()) {
            log.warn("PKCS#11 custody selected but impilo.keys.pkcs11.config is unset — provider is INERT "
                    + "(no HSM). Key generation/signing will fail closed until a real module is wired.");
        } else {
            log.info("PKCS#11 custody selected with config {} (session wiring pending real HSM).",
                    pkcs11ConfigPath);
        }
    }

    private void requireModule() {
        if (pkcs11ConfigPath.isEmpty()) {
            throw new IllegalStateException(
                    "PKCS#11 custody is selected but no HSM module is configured (impilo.keys.pkcs11.config). "
                            + "This provider is inert without a real PKCS#11 library — it will not mint or use software keys.");
        }
        // A real HSM integration initialises the SunPKCS11 provider + token session here.
        throw new UnsupportedOperationException(
                "PKCS#11 module session is not implemented — supply an HSM integration for " + pkcs11ConfigPath);
    }

    @Override
    public GeneratedKeyMaterial generate() {
        requireModule();
        return null; // unreachable — requireModule always throws until an HSM is wired
    }

    @Override
    public byte[] sign(byte[] custodyBlob, byte[] payload) {
        requireModule();
        return null; // unreachable
    }

    /**
     * Never permitted: an HSM never releases private-key material. This is the enforced
     * invariant that keeps JWS assembly on the custody-sign path for hardware keys.
     */
    @Override
    public byte[] exportPrivate(byte[] custodyBlob) {
        throw new UnsupportedOperationException(
                "HSM-held keys are non-exportable — exportPrivate is forbidden under PKCS#11 custody.");
    }

    @Override
    public boolean supportsExport() {
        return false;
    }

    @Override
    public String custodyType() {
        return "HSM";
    }
}
