package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationContentLoader;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Robson Ten-Group Classification.
 *
 * <p>Two properties matter for surveillance: the groups are mutually exclusive (an overriding
 * feature wins — a breech twin is group 8, not a breech group), and a birth missing a defining
 * feature is INDETERMINATE rather than forced into a group, because a misclassified denominator
 * silently corrupts another group's caesarean rate.
 */
class RobsonClassificationTest {

    private final RobsonClassificationService service =
            new RobsonClassificationService(new ClassificationContentLoader(new ObjectMapper()));

    private RobsonClassificationService.RobsonInputs inputs(String parity, String prevCs,
            String presentation, String gestation, String onset, Boolean multiple) {
        return new RobsonClassificationService.RobsonInputs(parity, prevCs, presentation, gestation,
                onset, multiple);
    }

    private String group(RobsonClassificationService.RobsonInputs in) {
        return service.classify(in).classificationCode();
    }

    @Test
    @DisplayName("the ten standard cases each land in their group")
    void tenGroups() {
        assertThat(group(inputs("NULLIPAROUS", "NO", "CEPHALIC", "TERM", "SPONTANEOUS", false))).isEqualTo("ROBSON_1");
        assertThat(group(inputs("NULLIPAROUS", "NO", "CEPHALIC", "TERM", "INDUCED", false))).isEqualTo("ROBSON_2");
        assertThat(group(inputs("MULTIPAROUS", "NO", "CEPHALIC", "TERM", "SPONTANEOUS", false))).isEqualTo("ROBSON_3");
        assertThat(group(inputs("MULTIPAROUS", "NO", "CEPHALIC", "TERM", "PRELABOUR_CS", false))).isEqualTo("ROBSON_4");
        assertThat(group(inputs("MULTIPAROUS", "YES", "CEPHALIC", "TERM", "SPONTANEOUS", false))).isEqualTo("ROBSON_5");
        assertThat(group(inputs("NULLIPAROUS", "NO", "BREECH", "TERM", "SPONTANEOUS", false))).isEqualTo("ROBSON_6");
        assertThat(group(inputs("MULTIPAROUS", "YES", "BREECH", "TERM", "SPONTANEOUS", false))).isEqualTo("ROBSON_7");
        assertThat(group(inputs("NULLIPAROUS", "NO", "CEPHALIC", "TERM", "SPONTANEOUS", true))).isEqualTo("ROBSON_8");
        assertThat(group(inputs("MULTIPAROUS", "NO", "TRANSVERSE_OBLIQUE", "TERM", "SPONTANEOUS", false))).isEqualTo("ROBSON_9");
        assertThat(group(inputs("MULTIPAROUS", "YES", "CEPHALIC", "PRETERM", "SPONTANEOUS", false))).isEqualTo("ROBSON_10");
    }

    @Test
    @DisplayName("multiple pregnancy overrides breech and preterm — a breech preterm twin is group 8")
    void multipleOverridesEverything() {
        assertThat(group(inputs("NULLIPAROUS", "YES", "BREECH", "PRETERM", "INDUCED", true)))
                .isEqualTo("ROBSON_8");
    }

    @Test
    @DisplayName("abnormal lie overrides parity and previous caesarean — a previous-CS transverse is group 9")
    void abnormalLieOverridesParityAndPreviousCs() {
        assertThat(group(inputs("MULTIPAROUS", "YES", "TRANSVERSE_OBLIQUE", "TERM", "PRELABOUR_CS", false)))
                .isEqualTo("ROBSON_9");
    }

    @Test
    @DisplayName("group 5 (previous CS) is checked before the parity groups, so a previous-CS term cephalic is 5 not 3/4")
    void previousCsBeforeParityGroups() {
        assertThat(group(inputs("MULTIPAROUS", "YES", "CEPHALIC", "TERM", "INDUCED", false)))
                .isEqualTo("ROBSON_5");
    }

    @Test
    @DisplayName("preterm single cephalic is group 10 even with a previous caesarean")
    void pretermBeforePreviousCs() {
        assertThat(group(inputs("MULTIPAROUS", "YES", "CEPHALIC", "PRETERM", "INDUCED", false)))
                .isEqualTo("ROBSON_10");
    }

    @Test
    @DisplayName("a birth missing a defining feature is INDETERMINATE, never forced into a group")
    void missingFeatureIsIndeterminate() {
        // No multiplePregnancy, no gestation, no onset — cannot safely classify.
        var o = service.classify(inputs("NULLIPAROUS", "NO", "CEPHALIC", null, null, null));
        assertThat(o.status()).isEqualTo(ClassificationEngine.Status.INDETERMINATE);
        assertThat(o.classificationCode()).isNull();
    }

    @Test
    @DisplayName("the ten standard cases exhaust the classification — none is unclassifiable with full data")
    void fullDataAlwaysClassifies() {
        for (String parity : new String[]{"NULLIPAROUS", "MULTIPAROUS"}) {
            for (String prevCs : new String[]{"YES", "NO"}) {
                for (String pres : new String[]{"CEPHALIC", "BREECH", "TRANSVERSE_OBLIQUE"}) {
                    for (String gest : new String[]{"TERM", "PRETERM"}) {
                        for (String onset : new String[]{"SPONTANEOUS", "INDUCED", "PRELABOUR_CS"}) {
                            for (Boolean mult : new Boolean[]{true, false}) {
                                // Nulliparous + previous caesarean is a contradiction; skip it.
                                if ("NULLIPAROUS".equals(parity) && "YES".equals(prevCs)) {
                                    continue;
                                }
                                var o = service.classify(inputs(parity, prevCs, pres, gest, onset, mult));
                                assertThat(o.status())
                                        .as("%s/%s/%s/%s/%s/mult=%s must classify", parity, prevCs,
                                                pres, gest, onset, mult)
                                        .isEqualTo(ClassificationEngine.Status.CLASSIFIED);
                            }
                        }
                    }
                }
            }
        }
    }
}
