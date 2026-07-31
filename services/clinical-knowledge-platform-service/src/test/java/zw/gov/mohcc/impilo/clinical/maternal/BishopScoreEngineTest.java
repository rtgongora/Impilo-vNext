package zw.gov.mohcc.impilo.clinical.maternal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BishopScoreEngineTest {

    @Test
    @DisplayName("a complete favourable cervix scores 13 and is FAVOURABLE")
    void favourableScore() {
        var result = BishopScoreEngine.assess(new BishopScoreEngine.Input(
                "DIL_5_PLUS", "EFF_80_PLUS", "STATION_MINUS2_PLUS", "SOFT", "ANTERIOR"));

        assertThat(result.score()).isEqualTo(13);
        assertThat(result.interpretation()).isEqualTo(BishopScoreEngine.Interpretation.FAVOURABLE);
        assertThat(result.missing()).isEmpty();
    }

    @Test
    @DisplayName("score 7 is INTERMEDIATE")
    void intermediateScore() {
        var result = BishopScoreEngine.assess(new BishopScoreEngine.Input(
                2, 2, 2, 0, 1));

        assertThat(result.score()).isEqualTo(7);
        assertThat(result.interpretation()).isEqualTo(BishopScoreEngine.Interpretation.INTERMEDIATE);
    }

    @Test
    @DisplayName("score 6 is UNFAVOURABLE")
    void unfavourableScore() {
        var result = BishopScoreEngine.assess(new BishopScoreEngine.Input(
                0, 0, 0, 0, 0));

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.interpretation()).isEqualTo(BishopScoreEngine.Interpretation.UNFAVOURABLE);
    }

    @Test
    @DisplayName("a blank component is INCOMPLETE — never treated as zero")
    void blankIsIncomplete() {
        var result = BishopScoreEngine.assess(new BishopScoreEngine.Input(
                "DIL_3_4", null, "STATION_PLUS2", "MEDIUM", "POSTERIOR"));

        assertThat(result.score()).isNull();
        assertThat(result.interpretation()).isEqualTo(BishopScoreEngine.Interpretation.INCOMPLETE);
        assertThat(result.missing()).containsExactly("effacement");
        assertThat(result.components()).containsEntry("dilation", 2);
        assertThat(result.components()).doesNotContainKey("effacement");
    }

    @Test
    @DisplayName("raw clinical values map to standard bands")
    void rawClinicalValues() {
        var result = BishopScoreEngine.assess(new BishopScoreEngine.Input(
                4, 65, -1, "soft", "mid"));

        assertThat(result.score()).isEqualTo(9);
        assertThat(result.interpretation()).isEqualTo(BishopScoreEngine.Interpretation.INTERMEDIATE);
    }
}
