package zw.gov.mohcc.impilo.varapi.core.biometric;

import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricTemplateEntity;

import java.util.List;

/**
 * Swappable provider-biometric matching seam. Implementations compare a probe
 * capture against a provider's enrolled templates and return a real match outcome.
 *
 * <p>The default {@link FailClosedBiometricMatcher} never fabricates a match — it
 * returns {@code UNAVAILABLE} so verification fails closed until a real matcher is
 * configured. A genuine matcher (vendor SDK / external matching service) can replace
 * the default by registering a {@code @Primary} bean of this type.</p>
 */
public interface BiometricMatcher {

    /** Outcome of a match attempt. {@code result} is MATCH | NO_MATCH | UNAVAILABLE. */
    record MatchOutcome(String result, double confidence, String summary) {
        public static final String MATCH = "MATCH";
        public static final String NO_MATCH = "NO_MATCH";
        public static final String UNAVAILABLE = "UNAVAILABLE";
    }

    MatchOutcome match(byte[] probe, List<ProviderBiometricTemplateEntity> templates);

    /** True only when this matcher can produce a genuine biometric comparison. */
    boolean isLiveCapable();
}
