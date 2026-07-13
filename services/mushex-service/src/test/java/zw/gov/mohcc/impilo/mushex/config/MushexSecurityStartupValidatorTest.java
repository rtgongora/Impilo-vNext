package zw.gov.mohcc.impilo.mushex.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The validator's contract: the all-flags-off production posture refuses to boot
 * with a weak HMAC pepper or fail-open payee verification; any declared sandbox
 * posture tolerates both (rig/preview only).
 */
class MushexSecurityStartupValidatorTest {

    private static MushexProperties props(String pepper, boolean sandboxEnabled) {
        MushexProperties p = new MushexProperties();
        p.setHmacPepper(pepper);
        p.getAdapters().getSandbox().setEnabled(sandboxEnabled);
        // Java field defaults are true; the production YAML pins them false — mirror that here.
        p.getAdapters().getSandbox().setApplySimulationOutcomeOnCreate(false);
        p.getAdapters().getSandbox().setBypassCredentialCheckForSimulation(false);
        return p;
    }

    @Test
    void blankPepper_outsideSandbox_refusesBoot() {
        var ex = assertThrows(IllegalStateException.class,
                () -> new MushexSecurityStartupValidator(props("", false), true).validate());
        assertTrue(ex.getMessage().contains("MUSHEX_HMAC_PEPPER"));
    }

    @Test
    void devPlaceholderPepper_outsideSandbox_refusesBoot() {
        assertThrows(IllegalStateException.class,
                () -> new MushexSecurityStartupValidator(
                        props(MushexSecurityStartupValidator.DEV_PEPPER, false), true).validate());
    }

    @Test
    void blankPepper_withSandboxEnabled_boots() {
        assertDoesNotThrow(
                () -> new MushexSecurityStartupValidator(props("", true), true).validate());
    }

    @Test
    void blankPepper_withSimulationOutcomeFlag_boots() {
        MushexProperties p = props("", false);
        p.getAdapters().getSandbox().setApplySimulationOutcomeOnCreate(true);
        assertDoesNotThrow(() -> new MushexSecurityStartupValidator(p, true).validate());
    }

    @Test
    void payeeVerificationDisabled_outsideSandbox_refusesBoot() {
        var ex = assertThrows(IllegalStateException.class,
                () -> new MushexSecurityStartupValidator(props("real-pepper", false), false).validate());
        assertTrue(ex.getMessage().contains("credential-payee-verification-enabled"));
    }

    @Test
    void payeeVerificationDisabled_withSandbox_boots() {
        assertDoesNotThrow(
                () -> new MushexSecurityStartupValidator(props("real-pepper", true), false).validate());
    }

    @Test
    void productionPosture_withRealSecretAndVerificationOn_boots() {
        assertDoesNotThrow(
                () -> new MushexSecurityStartupValidator(props("real-pepper", false), true).validate());
    }
}
