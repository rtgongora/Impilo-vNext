package zw.gov.mohcc.impilo.varapi.core.biometric;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricTemplateEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G017: the default matcher must never fabricate a MATCH. It fails closed
 * (UNAVAILABLE, zero confidence) so provider biometric verification denies until a
 * real matching engine is registered.
 */
class FailClosedBiometricMatcherTest {

    private final BiometricMatcher matcher = new FailClosedBiometricMatcher();

    @Test
    void neverReturnsMatch() {
        ProviderBiometricTemplateEntity t = new ProviderBiometricTemplateEntity();
        BiometricMatcher.MatchOutcome outcome =
                matcher.match("probe".getBytes(StandardCharsets.UTF_8), List.of(t));

        assertThat(outcome.result()).isEqualTo(BiometricMatcher.MatchOutcome.UNAVAILABLE);
        assertThat(outcome.result()).isNotEqualTo(BiometricMatcher.MatchOutcome.MATCH);
        assertThat(outcome.confidence()).isZero();
        assertThat(outcome.summary()).contains("fails closed");
    }

    @Test
    void notLiveCapable() {
        assertThat(matcher.isLiveCapable()).isFalse();
    }
}
