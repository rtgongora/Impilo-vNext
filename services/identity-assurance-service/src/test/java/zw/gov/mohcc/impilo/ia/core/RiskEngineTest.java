package zw.gov.mohcc.impilo.ia.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves the deterministic server-side risk computation (real logic, no mocks). */
class RiskEngineTest {

    @Test
    void noFactors_isLowAllow() {
        RiskEngine.Result r = RiskEngine.evaluate(List.of());
        assertThat(r.score().intValue()).isZero();
        assertThat(r.level()).isEqualTo(RiskLevel.LOW);
        assertThat(r.recommendation()).isEqualTo(Recommendation.ALLOW);
    }

    @Test
    void nullFactors_isLowAllow_noNpe() {
        RiskEngine.Result r = RiskEngine.evaluate(null);
        assertThat(r.level()).isEqualTo(RiskLevel.LOW);
        assertThat(r.recommendation()).isEqualTo(Recommendation.ALLOW);
    }

    @Test
    void lowWeightSignal_staysLow() {
        // NEW_DEVICE = 20 (< 25) → LOW.
        RiskEngine.Result r = RiskEngine.evaluate(List.of("NEW_DEVICE"));
        assertThat(r.score().intValue()).isEqualTo(20);
        assertThat(r.level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void mediumBand_recommendsStepUp() {
        // 20 + 25 = 45 → MEDIUM → STEP_UP.
        RiskEngine.Result r = RiskEngine.evaluate(List.of("NEW_DEVICE", "UNRECOGNIZED_LOCATION"));
        assertThat(r.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(r.recommendation()).isEqualTo(Recommendation.STEP_UP);
    }

    @Test
    void highBand_recommendsReview() {
        // 60 → HIGH → REVIEW.
        RiskEngine.Result r = RiskEngine.evaluate(List.of("KNOWN_COMPROMISED_CREDENTIAL"));
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.recommendation()).isEqualTo(Recommendation.REVIEW);
    }

    @Test
    void criticalBand_recommendsDeny_andScoreIsCapped() {
        // 60 + 40 + 30 = 130, capped at 100 → CRITICAL → DENY.
        RiskEngine.Result r = RiskEngine.evaluate(
                List.of("KNOWN_COMPROMISED_CREDENTIAL", "IMPOSSIBLE_TRAVEL", "MULTIPLE_FAILED_AUTH"));
        assertThat(r.score().intValue()).isEqualTo(100);
        assertThat(r.level()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(r.recommendation()).isEqualTo(Recommendation.DENY);
    }

    @Test
    void unknownSignal_addsConservativeWeight_notIgnored() {
        RiskEngine.Result r = RiskEngine.evaluate(List.of("SOMETHING_UNRECOGNISED"));
        assertThat(r.score().intValue()).isEqualTo(5);
    }

    @Test
    void factorMatchingIsCaseInsensitive() {
        assertThat(RiskEngine.evaluate(List.of("new_device")).score().intValue()).isEqualTo(20);
    }
}
