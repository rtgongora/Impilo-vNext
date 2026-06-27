package zw.gov.mohcc.impilo.cardprint.service;

import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SecureElementKeyServiceTest {

    private final SecureElementKeyService service = new SecureElementKeyService();

    @Test
    void generatesPemEncodedPublicKey() {
        String pem = service.generatePublicKeyPem(42L);

        assertThat(pem)
                .startsWith("-----BEGIN PUBLIC KEY-----")
                .contains("-----END PUBLIC KEY-----");
        // Must not be a placeholder fabrication — VITO fails activation closed on those (G032).
        assertThat(pem).doesNotContain("DEV-PLACEHOLDER-");
    }

    @Test
    void emitsARealParseableEcPublicKey() {
        String pem = service.generatePublicKeyPem(7L);

        String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);

        assertThatCode(() -> {
            PublicKey key = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(der));
            assertThat(key.getAlgorithm()).isEqualTo("EC");
        }).doesNotThrowAnyException();
    }

    @Test
    void generatesAFreshKeyEachCall() {
        assertThat(service.generatePublicKeyPem(1L))
                .isNotEqualTo(service.generatePublicKeyPem(1L));
    }
}
