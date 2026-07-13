package zw.gov.mohcc.impilo.mushex.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fail-closed startup validation for MusheX's money-security configuration.
 *
 * <p>Two historically-silent weaknesses are rejected at boot unless the
 * sandbox is explicitly enabled (rig/preview only — never production):</p>
 * <ul>
 *   <li><b>Blank HMAC pepper</b> — {@code mushex.hmac-pepper} defaults to the
 *       empty string when {@code MUSHEX_HMAC_PEPPER} is not injected, and every
 *       webhook/remittance HMAC would silently be computed with an empty key.
 *       The config comment claimed "fail-closed"; this makes it true.</li>
 *   <li><b>Payee credential verification disabled</b> —
 *       {@code impilo.services.credential-payee-verification-enabled=false}
 *       returns {@code allowed=true/SKIPPED} for every payee (fail-open).
 *       Acceptable only alongside the sandbox simulation flags.</li>
 * </ul>
 */
@Component
public class MushexSecurityStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(MushexSecurityStartupValidator.class);

    /** Known dev placeholder that must never authenticate real webhooks. */
    static final String DEV_PEPPER = "mushex-dev-pepper-change-in-prod";

    private final MushexProperties properties;
    private final boolean payeeVerificationEnabled;

    public MushexSecurityStartupValidator(
            MushexProperties properties,
            @Value("${impilo.services.credential-payee-verification-enabled:true}") boolean payeeVerificationEnabled) {
        this.properties = properties;
        this.payeeVerificationEnabled = payeeVerificationEnabled;
    }

    @PostConstruct
    void validate() {
        // Any of the sandbox simulation flags declares a non-production (rig/preview)
        // posture; the guard exists for the all-flags-off production posture only.
        MushexProperties.Sandbox sandboxProps = properties.getAdapters().getSandbox();
        boolean sandbox = sandboxProps.isEnabled()
                || sandboxProps.isApplySimulationOutcomeOnCreate()
                || sandboxProps.isBypassCredentialCheckForSimulation();

        String pepper = properties.getHmacPepper();
        boolean pepperWeak = pepper == null || pepper.isBlank() || DEV_PEPPER.equals(pepper);
        if (pepperWeak && !sandbox) {
            throw new IllegalStateException(
                    "MUSHEX_HMAC_PEPPER is blank or the dev placeholder. Refusing to start: webhook and "
                            + "remittance HMACs would be computed with a weak key. Inject a real secret, or "
                            + "enable mushex.adapters.sandbox.enabled for rig/preview use only.");
        }
        if (pepperWeak) {
            log.warn("MUSHEX_HMAC_PEPPER is blank/dev-default — tolerated ONLY because sandbox mode is enabled");
        }

        if (!payeeVerificationEnabled && !sandbox) {
            throw new IllegalStateException(
                    "impilo.services.credential-payee-verification-enabled=false is fail-open (every payee "
                            + "is allowed as SKIPPED). Refusing to start outside sandbox mode.");
        }
        if (!payeeVerificationEnabled) {
            log.warn("Payee credential verification is DISABLED — tolerated ONLY because sandbox mode is enabled");
        }
    }
}
