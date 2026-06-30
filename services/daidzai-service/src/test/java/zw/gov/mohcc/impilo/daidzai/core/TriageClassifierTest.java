package zw.gov.mohcc.impilo.daidzai.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TriageClassifierTest {

    private final TriageClassifier triage = new TriageClassifier();

    @Test
    void criticalSeverityIsRed() {
        assertThat(triage.classify("MEDICAL", "CRITICAL")).isEqualTo("RED");
    }

    @Test
    void timeCriticalCategoryIsRedEvenWhenSeverityUnknown() {
        assertThat(triage.classify("CARDIAC", "UNKNOWN")).isEqualTo("RED");
        assertThat(triage.classify("OBSTETRIC", null)).isEqualTo("RED");
    }

    @Test
    void severityLadderMapsToCategories() {
        assertThat(triage.classify("MEDICAL", "HIGH")).isEqualTo("ORANGE");
        assertThat(triage.classify("MEDICAL", "MODERATE")).isEqualTo("YELLOW");
        assertThat(triage.classify("MEDICAL", "LOW")).isEqualTo("GREEN");
    }

    @Test
    void deceasedIsBlack() {
        assertThat(triage.classify("DECEASED", "CRITICAL")).isEqualTo("BLACK");
    }

    @Test
    void unknownSeverityNonCriticalIsPrecautionaryYellow() {
        assertThat(triage.classify("MINOR_INJURY", "UNKNOWN")).isEqualTo("YELLOW");
    }

    @Test
    void sensitiveCategoriesAreFlagged() {
        assertThat(triage.isSensitiveCategory("GBV")).isTrue();
        assertThat(triage.isSensitiveCategory("MENTAL_HEALTH")).isTrue();
        assertThat(triage.isSensitiveCategory("CHILD_PROTECTION")).isTrue();
        assertThat(triage.isSensitiveCategory("MEDICAL")).isFalse();
    }

    @Test
    void severityForTriageIsConsistent() {
        assertThat(triage.severityForTriage("RED")).isEqualTo("CRITICAL");
        assertThat(triage.severityForTriage("GREEN")).isEqualTo("LOW");
    }
}
