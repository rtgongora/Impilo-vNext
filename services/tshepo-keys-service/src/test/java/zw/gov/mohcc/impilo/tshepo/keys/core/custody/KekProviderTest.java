package zw.gov.mohcc.impilo.tshepo.keys.core.custody;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W4c: KEK-source seam. Proves the default {@link ConfigKekProvider} yields the configured
 * 32-byte KEK, that {@link SoftwareKeyCustodyProvider} works through the provider, and that
 * the {@link VaultKekProvider} stub fails loudly until wired.
 */
class KekProviderTest {

    private static final String KEK_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private KeysProperties props(String kek) {
        KeysProperties p = new KeysProperties();
        p.setKek(kek);
        return p;
    }

    @Test
    void configKekProvider_returnsConfiguredKek() {
        ConfigKekProvider provider = new ConfigKekProvider(props(KEK_HEX));
        assertThat(provider.getKek()).hasSize(32);
        assertThat(provider.sourceType()).isEqualTo("CONFIG");
    }

    @Test
    void configKekProvider_failsWhenKekMissing() {
        ConfigKekProvider provider = new ConfigKekProvider(props(null));
        assertThatThrownBy(provider::getKek)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tshepo.keys.kek");
    }

    @Test
    void softwareCustody_worksThroughKekProvider() {
        // Software custody wired via the config KEK provider must still encrypt/sign as before.
        SoftwareKeyCustodyProvider custody =
                new SoftwareKeyCustodyProvider(new ConfigKekProvider(props(KEK_HEX)));
        var material = custody.generate();
        byte[] sig = custody.sign(material.custodyBlob(), "payload".getBytes());
        assertThat(sig).hasSize(64); // Ed25519 signature
        assertThat(custody.exportPrivate(material.custodyBlob())).hasSize(32);
        assertThat(custody.supportsExport()).isTrue();
        assertThat(custody.custodyType()).isEqualTo("SOFTWARE");
    }

    @Test
    void vaultKekProvider_throwsUntilWired() {
        VaultKekProvider provider = new VaultKekProvider();
        assertThat(provider.sourceType()).isEqualTo("VAULT");
        assertThatThrownBy(provider::getKek)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not wired");
    }
}
