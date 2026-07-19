package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Per-dataset subject tokeniser (design principle 1).
 *
 * <p>{@code subject_token = HMAC-SHA256(dataset_secret, cpid)} rendered as
 * lower-case hex. Because the secret is per-dataset, the same person is
 * unlinkable across datasets (no cross-dataset join key) and no token is
 * reversible to a CPID without the trust core. The CPID itself must never
 * appear in any produced dataset.</p>
 */
@Component
public class DatasetTokeniser {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretResolver secretResolver;

    public DatasetTokeniser(SecretResolver secretResolver) {
        this.secretResolver = secretResolver;
    }

    /**
     * Compute the dataset-scoped subject token for a CPID.
     *
     * @param secretRef the dataset's secret reference (tshepo-keys custody)
     * @param cpid      the pseudonymous clinical person identifier
     */
    public String subjectToken(String secretRef, String cpid) {
        if (cpid == null || cpid.isBlank()) {
            throw new IllegalArgumentException("cpid is required to derive a subject token");
        }
        String secret = secretResolver.resolve(secretRef);
        return hmacSha256Hex(secret, cpid);
    }

    static String hmacSha256Hex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
