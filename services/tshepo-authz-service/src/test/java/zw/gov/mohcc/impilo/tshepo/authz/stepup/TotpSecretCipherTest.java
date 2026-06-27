package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpSecretCipherTest {

    private static TotpSecretCipher cipherWithKey(String key) {
        AuthzProperties props = new AuthzProperties();
        props.getTotp().setEncryptionKey(key);
        return new TotpSecretCipher(props);
    }

    @Test
    void roundTripRecoversPlaintext() {
        TotpSecretCipher cipher = cipherWithKey("a-strong-totp-encryption-key-0123456789");
        byte[] secret = "0123456789ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8);

        TotpSecretCipher.Encrypted enc = cipher.encrypt(secret);
        byte[] back = cipher.decrypt(enc.cipherBase64(), enc.ivBase64());

        assertThat(back).isEqualTo(secret);
    }

    @Test
    void perRecordIvDiffersForSamePlaintext() {
        TotpSecretCipher cipher = cipherWithKey("a-strong-totp-encryption-key-0123456789");
        byte[] secret = "same-secret-bytes-xx".getBytes(StandardCharsets.UTF_8);
        assertThat(cipher.encrypt(secret).ivBase64())
                .isNotEqualTo(cipher.encrypt(secret).ivBase64());
    }

    @Test
    void tamperedCiphertextFailsToDecrypt() {
        TotpSecretCipher cipher = cipherWithKey("a-strong-totp-encryption-key-0123456789");
        TotpSecretCipher.Encrypted enc = cipher.encrypt("0123456789ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8));
        byte[] ct = Base64.getDecoder().decode(enc.cipherBase64());
        ct[0] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(ct);

        assertThatThrownBy(() -> cipher.decrypt(tampered, enc.ivBase64()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesWeakOrAbsentKey() {
        assertThatThrownBy(() -> cipherWithKey(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
        assertThatThrownBy(() -> cipherWithKey("too-short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
