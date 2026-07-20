package zw.gov.mohcc.impilo.tshepo.keys.core.custody;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W4c: PKCS#11 (HSM) custody provider. Proves the non-exportable invariant and that the
 * provider is inert (fails closed, never mints software keys) without a real module.
 */
class Pkcs11KeyCustodyProviderTest {

    @Test
    void exportPrivate_alwaysForbidden() {
        Pkcs11KeyCustodyProvider provider = new Pkcs11KeyCustodyProvider("");
        assertThatThrownBy(() -> provider.exportPrivate(new byte[0]))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("non-exportable");
    }

    @Test
    void supportsExport_isFalse_forcingCustodySignJws() {
        assertThat(new Pkcs11KeyCustodyProvider("").supportsExport()).isFalse();
    }

    @Test
    void custodyType_isHsm() {
        assertThat(new Pkcs11KeyCustodyProvider("").custodyType()).isEqualTo("HSM");
    }

    @Test
    void inertWithoutModule_failsClosedOnGenerateAndSign() {
        Pkcs11KeyCustodyProvider provider = new Pkcs11KeyCustodyProvider("");
        // No HSM module configured → must fail closed, not degrade to software keys.
        assertThatThrownBy(provider::generate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inert");
        assertThatThrownBy(() -> provider.sign(new byte[0], new byte[0]))
                .isInstanceOf(IllegalStateException.class);
    }
}
