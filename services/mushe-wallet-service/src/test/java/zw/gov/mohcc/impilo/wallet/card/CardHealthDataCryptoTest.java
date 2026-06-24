package zw.gov.mohcc.impilo.wallet.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the E3 fix (G030): card health data is encrypted with a key derived from a configured
 * SECRET master key (HMAC-SHA256(masterKey, keyRef)), not SHA-256(keyRef). The old key was a
 * pure function of the stored encryption_key_ref, so anyone with DB access could reconstruct it.
 */
class CardHealthDataCryptoTest {

    private static final String MASTER_KEY = "wallet-card-master-secret-key-of-sufficient-length-2026";

    private CardHealthDataService service() {
        return new CardHealthDataService(null, null, null, new ObjectMapper(), MASTER_KEY);
    }

    @Test
    void encrypt_producesIvPrefixedCiphertext_notPlaintext() {
        byte[] plaintext = "{\"bloodType\":\"O+\",\"allergies\":[\"penicillin\"]}".getBytes(StandardCharsets.UTF_8);
        byte[] out = service().encrypt(plaintext, "card-123:summary");
        assertThat(out).isNotEqualTo(plaintext);
        assertThat(out.length).isGreaterThan(plaintext.length + 12); // 12-byte IV + GCM tag
    }

    @Test
    void keyIsNotDerivableFromStoredRef_oldSha256KeyFails() throws Exception {
        String keyRef = "card-123:summary";
        byte[] plaintext = "sensitive-health-data".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = service().encrypt(plaintext, keyRef);

        // The OLD scheme: key = SHA-256(keyRef). An attacker with the stored keyRef would try this.
        byte[] oldKey = Arrays.copyOf(
                MessageDigest.getInstance("SHA-256").digest(keyRef.getBytes(StandardCharsets.UTF_8)), 32);
        assertThatThrownBy(() -> decrypt(ciphertext, oldKey))
                .as("the stored key reference alone must NOT decrypt the data")
                .isInstanceOf(Exception.class);
    }

    @Test
    void roundTrip_withMasterKeyDerivedKey_recoversPlaintext() throws Exception {
        String keyRef = "card-123:summary";
        byte[] plaintext = "{\"ips\":\"bundle\"}".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = service().encrypt(plaintext, keyRef);

        byte[] realKey = hmac(MASTER_KEY, keyRef);
        assertThat(decrypt(ciphertext, realKey)).isEqualTo(plaintext);
    }

    @Test
    void constructor_failsClosed_onBlankOrWeakMasterKey() {
        ObjectMapper om = new ObjectMapper();
        assertThatThrownBy(() -> new CardHealthDataService(null, null, null, om, ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CardHealthDataService(null, null, null, om, "too-short"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CardHealthDataService(null, null, null, om, null))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- helpers (mirror the service's derivation / GCM layout) ---

    private static byte[] hmac(String masterKey, String keyRef) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(masterKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(keyRef.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] decrypt(byte[] ivAndCipher, byte[] key) throws Exception {
        byte[] iv = Arrays.copyOfRange(ivAndCipher, 0, 12);
        byte[] cipher = Arrays.copyOfRange(ivAndCipher, 12, ivAndCipher.length);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return c.doFinal(cipher);
    }
}
