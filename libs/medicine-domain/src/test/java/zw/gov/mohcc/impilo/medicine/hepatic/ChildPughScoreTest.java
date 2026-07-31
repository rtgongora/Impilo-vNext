package zw.gov.mohcc.impilo.medicine.hepatic;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Child-Pugh class gates operative risk assessment and transplant discussion. Each test names
 * the harm the assertion stands between a patient and.
 */
class ChildPughScoreTest {

    @Test
    void aWellCompensatedCirrhoticScoresFiveAndClassifiesA() {
        var result = ChildPughScore.of(BigDecimal.valueOf(20d), BigDecimal.valueOf(40d),
                BigDecimal.valueOf(1.2d), ChildPughScore.Ascites.NONE,
                ChildPughScore.Encephalopathy.NONE);

        assertThat(result.computed()).isTrue();
        assertThat(result.score()).isEqualTo(ChildPughScore.MIN_SCORE);
        assertThat(result.pughClass()).isEqualTo(ChildPughScore.PughClass.A);
        assertThat(result.contentVersion()).isEqualTo(ChildPughScore.CONTENT_VERSION);
    }

    @Test
    void aDecompensatedCirrhoticScoresFifteenAndClassifiesC() {
        var result = ChildPughScore.of(BigDecimal.valueOf(60d), BigDecimal.valueOf(25d),
                BigDecimal.valueOf(2.5d), ChildPughScore.Ascites.MODERATE_OR_REFRACTORY,
                ChildPughScore.Encephalopathy.GRADE_3_OR_4);

        assertThat(result.score()).isEqualTo(ChildPughScore.MAX_SCORE);
        assertThat(result.pughClass()).isEqualTo(ChildPughScore.PughClass.C);
    }

    @Test
    void unassessedAscitesIsRefusedRatherThanScoredAsAbsent() {
        // The consequential default. A patient nobody examined for ascites scored as free of it
        // loses two points, and a class B cirrhotic is presented as class A — which is the
        // difference between being discussed for transplant and not being discussed at all.
        var result = ChildPughScore.of(BigDecimal.valueOf(45d), BigDecimal.valueOf(30d),
                BigDecimal.valueOf(2.0d), null, ChildPughScore.Encephalopathy.NONE);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("without ascites");
    }

    @Test
    void unassessedEncephalopathyIsRefusedRatherThanScoredAsAbsent() {
        var result = ChildPughScore.of(BigDecimal.valueOf(45d), BigDecimal.valueOf(30d),
                BigDecimal.valueOf(2.0d), ChildPughScore.Ascites.NONE, null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void theBilirubinCutPointsFallOnThePublishedBoundaries() {
        // Bilirubin scores 1 below 34 µmol/L, 2 up to and including 50, and 3 above. Each boundary
        // moves a point, and two points move a class.
        assertThat(scoreWith(BigDecimal.valueOf(33d), NORMAL_ALBUMIN, NORMAL_INR).bilirubinPoints())
                .isEqualTo(1);
        assertThat(scoreWith(BigDecimal.valueOf(34d), NORMAL_ALBUMIN, NORMAL_INR).bilirubinPoints())
                .isEqualTo(2);
        assertThat(scoreWith(BigDecimal.valueOf(50d), NORMAL_ALBUMIN, NORMAL_INR).bilirubinPoints())
                .isEqualTo(2);
        assertThat(scoreWith(BigDecimal.valueOf(51d), NORMAL_ALBUMIN, NORMAL_INR).bilirubinPoints())
                .isEqualTo(3);
    }

    @Test
    void theAlbuminCutPointsFallOnThePublishedBoundariesAndRunTheOtherWay() {
        // Albumin is the one laboratory item where a lower value is worse, so an implementation that
        // reuses the bilirubin comparison inverts the whole item.
        assertThat(scoreWith(NORMAL_BILIRUBIN, BigDecimal.valueOf(36d), NORMAL_INR).albuminPoints())
                .isEqualTo(1);
        assertThat(scoreWith(NORMAL_BILIRUBIN, BigDecimal.valueOf(35d), NORMAL_INR).albuminPoints())
                .isEqualTo(2);
        assertThat(scoreWith(NORMAL_BILIRUBIN, BigDecimal.valueOf(28d), NORMAL_INR).albuminPoints())
                .isEqualTo(2);
        assertThat(scoreWith(NORMAL_BILIRUBIN, BigDecimal.valueOf(27d), NORMAL_INR).albuminPoints())
                .isEqualTo(3);
    }

    @Test
    void theInrCutPointsFallOnThePublishedBoundaries() {
        assertThat(scoreWith(NORMAL_BILIRUBIN, NORMAL_ALBUMIN, BigDecimal.valueOf(1.69d)).inrPoints())
                .isEqualTo(1);
        assertThat(scoreWith(NORMAL_BILIRUBIN, NORMAL_ALBUMIN, BigDecimal.valueOf(1.7d)).inrPoints())
                .isEqualTo(2);
        assertThat(scoreWith(NORMAL_BILIRUBIN, NORMAL_ALBUMIN, BigDecimal.valueOf(2.3d)).inrPoints())
                .isEqualTo(2);
        assertThat(scoreWith(NORMAL_BILIRUBIN, NORMAL_ALBUMIN, BigDecimal.valueOf(2.31d)).inrPoints())
                .isEqualTo(3);
    }

    private static final BigDecimal NORMAL_BILIRUBIN = BigDecimal.valueOf(20d);
    private static final BigDecimal NORMAL_ALBUMIN = BigDecimal.valueOf(40d);
    private static final BigDecimal NORMAL_INR = BigDecimal.valueOf(1.2d);

    private static ChildPughScore.Scoring scoreWith(BigDecimal bilirubin, BigDecimal albumin,
                                                    BigDecimal inr) {
        return ChildPughScore.of(bilirubin, albumin, inr, ChildPughScore.Ascites.NONE,
                ChildPughScore.Encephalopathy.NONE);
    }

    @Test
    void theClassBoundariesFallOnThePublishedTotals() {
        assertThat(ChildPughScore.PughClass.fromScore(6)).isEqualTo(ChildPughScore.PughClass.A);
        assertThat(ChildPughScore.PughClass.fromScore(7)).isEqualTo(ChildPughScore.PughClass.B);
        assertThat(ChildPughScore.PughClass.fromScore(9)).isEqualTo(ChildPughScore.PughClass.B);
        assertThat(ChildPughScore.PughClass.fromScore(10)).isEqualTo(ChildPughScore.PughClass.C);
    }

    @Test
    void aMissingTotalHasNoClassRatherThanTheWorstOne() {
        // Null must never render as class C. A missing assessment and end-stage liver disease are
        // opposite situations and must never share a display.
        assertThat(ChildPughScore.PughClass.fromScore(null)).isNull();
        assertThat(ChildPughScore.PughClass.fromScore(4)).isNull();
        assertThat(ChildPughScore.PughClass.fromScore(16)).isNull();
    }

    @Test
    void theItemPointsAreCarriedSoAClinicianCanCheckTheTotal() {
        // A bare class with no working shown is a number a clinician cannot challenge.
        var result = ChildPughScore.of(BigDecimal.valueOf(60d), BigDecimal.valueOf(25d),
                BigDecimal.valueOf(2.5d), ChildPughScore.Ascites.MILD_DIURETIC_RESPONSIVE,
                ChildPughScore.Encephalopathy.GRADE_1_OR_2);

        assertThat(result.bilirubinPoints()).isEqualTo(3);
        assertThat(result.albuminPoints()).isEqualTo(3);
        assertThat(result.inrPoints()).isEqualTo(3);
        assertThat(result.ascitesPoints()).isEqualTo(2);
        assertThat(result.encephalopathyPoints()).isEqualTo(2);
        assertThat(result.score()).isEqualTo(13);
    }

    @Test
    void aMissingLaboratoryValueIsRefused() {
        assertThat(ChildPughScore.of(null, BigDecimal.TEN, BigDecimal.ONE,
                ChildPughScore.Ascites.NONE, ChildPughScore.Encephalopathy.NONE).reason())
                .isEqualTo(RefusalReason.INPUT_MISSING);
    }
}
