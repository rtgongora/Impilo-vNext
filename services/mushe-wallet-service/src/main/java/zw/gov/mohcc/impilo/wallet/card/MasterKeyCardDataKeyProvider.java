package zw.gov.mohcc.impilo.wallet.card;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Default {@link CardDataKeyProvider}: derives each per-record AES-256 key as
 * {@code HMAC-SHA256(masterKey, keyRef)} from a fail-closed deployment master secret.
 *
 * <p>The key depends on a configured secret that is never stored alongside the ciphertext, so it
 * cannot be reconstructed from the stored {@code encryption_key_ref} alone. This is the sound
 * interim until a platform data-key/KMS service exists; a KMS-backed {@code CardDataKeyProvider}
 * bean would replace this one (this one steps aside via {@link ConditionalOnMissingBean}).</p>
 */
@Component
// `ignored = self`: on a component-scanned class the condition sees this class's OWN bean
// definition (already registered when the condition is evaluated) and would skip itself,
// leaving NO CardDataKeyProvider and failing every boot. A KMS-backed provider bean still
// displaces this one.
@ConditionalOnMissingBean(value = CardDataKeyProvider.class, ignored = MasterKeyCardDataKeyProvider.class)
public class MasterKeyCardDataKeyProvider implements CardDataKeyProvider {

    private static final String INSECURE_DEFAULT = "change-me";

    private final byte[] masterKey;

    public MasterKeyCardDataKeyProvider(
            @Value("${wallet.card.encryption-master-key:}") String encryptionMasterKey) {
        if (encryptionMasterKey == null || encryptionMasterKey.isBlank()
                || encryptionMasterKey.length() < 32
                || INSECURE_DEFAULT.equalsIgnoreCase(encryptionMasterKey)) {
            throw new IllegalStateException(
                    "wallet.card.encryption-master-key must be a strong secret of at least 32 characters. "
                    + "Refusing to start: card health data must be encrypted with key material that is NOT "
                    + "derivable from the stored encryption_key_ref.");
        }
        this.masterKey = encryptionMasterKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] dataKey(String keyRef) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
            return mac.doFinal(keyRef.getBytes(StandardCharsets.UTF_8)); // 32 bytes = AES-256
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive card data key", e);
        }
    }
}
