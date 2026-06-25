package zw.gov.mohcc.impilo.clinical.interpretation;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretationContext;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ObservationInput;
import zw.gov.mohcc.impilo.clinical.interpretation.model.QualifiedInterval;
import zw.gov.mohcc.impilo.clinical.interpretation.model.RangeSource;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ResolutionStatus;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ResolvedRange;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Sex;
import zw.gov.mohcc.impilo.clinical.terminology.UcumUnitConverter;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RangeResolver}: source precedence, context-keyed selection (age/sex/specimen),
 * pregnancy gating, UCUM normalisation, and fail-closed NO_REFERENCE_RANGE / UNIT_MISMATCH.
 */
class RangeResolverTest {

    private final UcumUnitConverter ucum = new UcumUnitConverter();

    /** Provider returning a fixed interval list (the ZIBO seam, faked). */
    private RangeResolver resolverWith(List<QualifiedInterval> intervals) {
        return new RangeResolver((loinc, local) -> intervals, ucum, false);
    }

    private RangeResolver resolverPregnancyOn(List<QualifiedInterval> intervals) {
        return new RangeResolver((loinc, local) -> intervals, ucum, true);
    }

    private static ObservationInput obs(String loinc, BigDecimal value, String unit) {
        return new ObservationInput(loinc, null, "x", "LAB", value, unit, null, null, null,
                null, null, null, null, null, null, 0d);
    }

    private static QualifiedInterval interval(BigDecimal low, BigDecimal high, String unit, Sex sex,
                                              Double ageLow, Double ageHigh) {
        return new QualifiedInterval(low, high, unit, null, null, sex, ageLow, ageHigh,
                false, null, null, null, "reference", "art-1", "1.0.0",
                "http://impilo.health.zw/ObservationDefinition/test");
    }

    private static InterpretationContext ctx(Double ageYears, Sex sex) {
        return new InterpretationContext(ageYears, null, sex, null, null, null);
    }

    // ---- Lab-delivered precedence ----

    @Test
    void labDelivered_preferredOverGoverned_andNormalised() {
        // Governed says 3-5; lab-delivered says 3.5-5.1 mmol/L. Lab wins.
        var governed = List.of(interval(bd("3.0"), bd("5.0"), "mmol/L", Sex.UNKNOWN, null, null));
        var resolver = resolverWith(governed);
        ObservationInput o = new ObservationInput("2823-3", null, "Potassium", "LAB",
                bd("4.2"), "mmol/L", null, null, null,
                bd("3.5"), bd("5.1"), "mmol/L", null, null, null, 0d);

        ResolvedRange r = resolver.resolve(o, ctx(40.0, Sex.MALE));

        assertThat(r.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(r.range().source()).isEqualTo(RangeSource.LAB_DELIVERED);
        assertThat(r.range().low()).isEqualByComparingTo("3.5");
        assertThat(r.range().high()).isEqualByComparingTo("5.1");
    }

    @Test
    void labDelivered_unconvertibleUnit_failsClosedUnitMismatch() {
        var resolver = resolverWith(List.of());
        // value in mmol/L, delivered interval in g/dL — dimensionally different, no molar mass → mismatch.
        ObservationInput o = new ObservationInput("2823-3", null, "Potassium", "LAB",
                bd("4.2"), "mmol/L", null, null, null,
                bd("3.5"), bd("5.1"), "g/dL", null, null, null, 0d);

        ResolvedRange r = resolver.resolve(o, ctx(40.0, Sex.MALE));

        assertThat(r.status()).isEqualTo(ResolutionStatus.UNIT_MISMATCH);
    }

    // ---- Governed selection ----

    @Test
    void governed_noIntervals_noReferenceRange() {
        ResolvedRange r = resolverWith(List.of()).resolve(obs("x", bd("5"), "mmol/L"), ctx(40.0, Sex.MALE));
        assertThat(r.status()).isEqualTo(ResolutionStatus.NO_REFERENCE_RANGE);
    }

    @Test
    void governed_sexSpecific_selectsMatchingSex() {
        var male = interval(bd("13.0"), bd("17.0"), "g/dL", Sex.MALE, null, null);
        var female = interval(bd("12.0"), bd("15.0"), "g/dL", Sex.FEMALE, null, null);
        var resolver = resolverWith(List.of(male, female));

        ResolvedRange r = resolver.resolve(obs("718-7", bd("14"), "g/dL"), ctx(30.0, Sex.FEMALE));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.range().low()).isEqualByComparingTo("12.0");
        assertThat(r.range().high()).isEqualByComparingTo("15.0");
        assertThat(r.range().source()).isEqualTo(RangeSource.OBSERVATION_DEFINITION);
        assertThat(r.range().version()).isEqualTo("1.0.0");
    }

    @Test
    void governed_sexUnknown_excludesSexSpecific_fallsToNoReferenceRange() {
        var male = interval(bd("13.0"), bd("17.0"), "g/dL", Sex.MALE, null, null);
        var female = interval(bd("12.0"), bd("15.0"), "g/dL", Sex.FEMALE, null, null);
        var resolver = resolverWith(List.of(male, female));

        ResolvedRange r = resolver.resolve(obs("718-7", bd("14"), "g/dL"), ctx(30.0, Sex.UNKNOWN));

        assertThat(r.status()).isEqualTo(ResolutionStatus.NO_REFERENCE_RANGE);
    }

    @Test
    void governed_sexSpecificPreferredOverGeneral_bySpecificity() {
        var general = interval(bd("12.0"), bd("16.0"), "g/dL", Sex.UNKNOWN, null, null);
        var male = interval(bd("13.0"), bd("17.0"), "g/dL", Sex.MALE, null, null);
        var resolver = resolverWith(List.of(general, male));

        ResolvedRange r = resolver.resolve(obs("718-7", bd("14"), "g/dL"), ctx(30.0, Sex.MALE));

        assertThat(r.range().low()).isEqualByComparingTo("13.0"); // the sex-specific one
    }

    @Test
    void governed_ageBand_selectsPaediatric() {
        var paeds = interval(bd("3.4"), bd("4.7"), "mmol/L", Sex.UNKNOWN, 0.0, 12.0);
        var adult = interval(bd("3.5"), bd("5.1"), "mmol/L", Sex.UNKNOWN, 12.0, 200.0);
        var resolver = resolverWith(List.of(paeds, adult));

        ResolvedRange r = resolver.resolve(obs("2823-3", bd("4"), "mmol/L"), ctx(5.0, Sex.UNKNOWN));

        assertThat(r.range().low()).isEqualByComparingTo("3.4");
        assertThat(r.range().high()).isEqualByComparingTo("4.7");
    }

    @Test
    void governed_ageBoundedButAgeMissing_failsClosed() {
        var adult = interval(bd("3.5"), bd("5.1"), "mmol/L", Sex.UNKNOWN, 12.0, 200.0);
        var resolver = resolverWith(List.of(adult));

        ResolvedRange r = resolver.resolve(obs("2823-3", bd("4"), "mmol/L"),
                new InterpretationContext(null, null, Sex.UNKNOWN, null, null, null));

        assertThat(r.status()).isEqualTo(ResolutionStatus.NO_REFERENCE_RANGE);
    }

    @Test
    void governed_unitNormalisation_intervalConvertedToValueUnit() {
        // interval mmol/L, value umol/L → bounds scaled x1000.
        var qi = interval(bd("3.5"), bd("5.1"), "mmol/L", Sex.UNKNOWN, null, null);
        var resolver = resolverWith(List.of(qi));

        ResolvedRange r = resolver.resolve(obs("2823-3", bd("4200"), "umol/L"), ctx(40.0, Sex.UNKNOWN));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.range().unit()).isEqualTo("umol/L");
        assertThat(r.range().low().doubleValue()).isEqualTo(3500.0, org.assertj.core.api.Assertions.within(1e-3));
        assertThat(r.range().high().doubleValue()).isEqualTo(5100.0, org.assertj.core.api.Assertions.within(1e-3));
    }

    @Test
    void governed_unconvertibleUnit_failsClosedUnitMismatch() {
        var qi = interval(bd("3.5"), bd("5.1"), "g/dL", Sex.UNKNOWN, null, null);
        var resolver = resolverWith(List.of(qi));

        ResolvedRange r = resolver.resolve(obs("2823-3", bd("4"), "mmol/L"), ctx(40.0, Sex.UNKNOWN));

        assertThat(r.status()).isEqualTo(ResolutionStatus.UNIT_MISMATCH);
    }

    @Test
    void governed_specimenSpecific_requiresMatchingSpecimen() {
        var serum = new QualifiedInterval(bd("3.5"), bd("5.1"), "mmol/L", null, null, Sex.UNKNOWN,
                null, null, false, null, null, "SERUM", "reference", "a", "1", "u");
        var resolver = resolverWith(List.of(serum));

        ResolvedRange match = resolver.resolve(obs("2823-3", bd("4"), "mmol/L"),
                new InterpretationContext(40.0, null, Sex.UNKNOWN, null, null, "SERUM"));
        assertThat(match.isResolved()).isTrue();

        ResolvedRange noSpec = resolver.resolve(obs("2823-3", bd("4"), "mmol/L"),
                new InterpretationContext(40.0, null, Sex.UNKNOWN, null, null, null));
        assertThat(noSpec.status()).isEqualTo(ResolutionStatus.NO_REFERENCE_RANGE);
    }

    // ---- Pregnancy gating ----

    @Test
    void pregnancyInterval_gatedOff_excludedInPhase1() {
        var preg = new QualifiedInterval(bd("3.0"), bd("4.5"), "mmol/L", null, null, Sex.FEMALE,
                null, null, true, null, null, null, "pregnancy", "a", "1", "u");
        var resolver = resolverWith(List.of(preg)); // pregnancyRangesEnabled = false

        ResolvedRange r = resolver.resolve(obs("2823-3", bd("4"), "mmol/L"),
                new InterpretationContext(28.0, null, Sex.FEMALE, true, 24, null));

        assertThat(r.status()).isEqualTo(ResolutionStatus.NO_REFERENCE_RANGE);
    }

    @Test
    void pregnancyInterval_whenEnabledAndPregnant_selected() {
        var preg = new QualifiedInterval(bd("3.0"), bd("4.5"), "mmol/L", null, null, Sex.FEMALE,
                null, null, true, null, null, null, "pregnancy", "a", "1", "u");
        var resolver = resolverPregnancyOn(List.of(preg));

        ResolvedRange r = resolver.resolve(obs("2823-3", bd("4"), "mmol/L"),
                new InterpretationContext(28.0, null, Sex.FEMALE, true, 24, null));

        assertThat(r.isResolved()).isTrue();
        assertThat(r.range().low()).isEqualByComparingTo("3.0");
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
