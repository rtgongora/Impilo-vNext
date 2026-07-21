package zw.gov.mohcc.impilo.pct.core.telemedicine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the telemedicine safety/authority gates (TM-B8): consent-on-submit
 * and provider-authority-on-accept.
 *
 * <p>Rolled out shadow-then-enforce, independently of the state-machine guard, because
 * the consent/authority timing of every live flow is not yet proven: {@code SHADOW}
 * records whether each gate would pass without blocking; {@code ENFORCE} rejects
 * (409/403); {@code OFF} disables recording.</p>
 */
@Component
@ConfigurationProperties(prefix = "pct.telemedicine.gates")
public class ReferralGateProperties {

    public enum Mode { OFF, SHADOW, ENFORCE }

    private Mode mode = Mode.SHADOW;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }
}
