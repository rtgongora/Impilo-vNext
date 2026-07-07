package zw.gov.mohcc.impilo.simba.social;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.simba.social.core.MilestoneSanitiser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MilestoneSanitiser must let celebratory wellness milestones through but block clinical values. */
class MilestoneSanitiserTest {

    @Test
    void allowsCelebratoryMilestones() {
        assertThat(MilestoneSanitiser.containsClinicalValue("Walked 10,000 steps today!")).isFalse();
        assertThat(MilestoneSanitiser.containsClinicalValue("7-day mindfulness streak 🎉")).isFalse();
        assertThat(MilestoneSanitiser.containsClinicalValue("Finished my 30-day walking journey")).isFalse();
        // requireSafe returns the text unchanged for safe content.
        assertThat(MilestoneSanitiser.requireSafe("Down to 0 cigarettes a day!"))
                .isEqualTo("Down to 0 cigarettes a day!");
    }

    @Test
    void blocksBloodPressureReadings() {
        assertThat(MilestoneSanitiser.containsClinicalValue("My BP is 150/95 today")).isTrue();
        assertThatThrownBy(() -> MilestoneSanitiser.requireSafe("BP 150/95"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blocksClinicalUnits() {
        assertThat(MilestoneSanitiser.containsClinicalValue("glucose 12 mmol/L")).isTrue();
        assertThat(MilestoneSanitiser.containsClinicalValue("weight is 92 kg now")).isTrue();
        assertThat(MilestoneSanitiser.containsClinicalValue("resting 55 bpm")).isTrue();
    }

    @Test
    void blocksClinicalTerms() {
        assertThat(MilestoneSanitiser.containsClinicalValue("my blood pressure came down")).isTrue();
        assertThat(MilestoneSanitiser.containsClinicalValue("HbA1c improved")).isTrue();
    }

    @Test
    void tolerantOfNullAndBlank() {
        assertThat(MilestoneSanitiser.containsClinicalValue(null)).isFalse();
        assertThat(MilestoneSanitiser.containsClinicalValue("  ")).isFalse();
    }
}
