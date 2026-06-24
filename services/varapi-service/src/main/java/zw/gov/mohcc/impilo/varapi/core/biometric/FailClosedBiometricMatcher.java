package zw.gov.mohcc.impilo.varapi.core.biometric;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricTemplateEntity;

import java.util.List;

/**
 * Default {@link BiometricMatcher}: fails closed. No real biometric matching engine
 * is configured, so this refuses to fabricate a match — it returns {@code UNAVAILABLE}
 * with zero confidence rather than the previous SHA-256 template-hash equality that
 * reported {@code MATCH}/confidence 1.0 (G017). Verification therefore denies (a
 * non-MATCH result) and remains fully auditable until a genuine matcher is registered.
 */
@Component
public class FailClosedBiometricMatcher implements BiometricMatcher {

    @Override
    public MatchOutcome match(byte[] probe, List<ProviderBiometricTemplateEntity> templates) {
        return new MatchOutcome(
                MatchOutcome.UNAVAILABLE,
                0.0,
                "No biometric matching engine configured — verification fails closed "
                        + "(no fabricated match). Register a live BiometricMatcher to enable matching.");
    }

    @Override
    public boolean isLiveCapable() {
        return false;
    }
}
