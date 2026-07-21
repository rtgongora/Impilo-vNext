package zw.gov.mohcc.impilo.pct.core.telemedicine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the referral transition guard (TM-B1).
 *
 * <p>Rollout is shadow-then-enforce: in {@code SHADOW} the guard records every
 * transition and flags violations without blocking them (protects the live spine
 * while the matrix is validated against real traffic); {@code ENFORCE} rejects
 * illegal transitions with 409; {@code OFF} is a kill switch (apply, no ledger).</p>
 */
@Component
@ConfigurationProperties(prefix = "pct.telemedicine.transitions")
public class ReferralTransitionGuardProperties {

    public enum Mode { OFF, SHADOW, ENFORCE }

    /** Default SHADOW: observe and record, never block — safe to deploy onto the live spine. */
    private Mode mode = Mode.SHADOW;

    /**
     * Escape hatch: extra edges to permit without a matrix redeploy, each as
     * {@code "FROM->TO"} (e.g. {@code "SCHEDULED->COMPLETED"}). Applied on top of
     * the built-in matrix.
     */
    private List<String> allowExtra = new ArrayList<>();

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public List<String> getAllowExtra() {
        return allowExtra;
    }

    public void setAllowExtra(List<String> allowExtra) {
        this.allowExtra = allowExtra == null ? new ArrayList<>() : allowExtra;
    }
}
