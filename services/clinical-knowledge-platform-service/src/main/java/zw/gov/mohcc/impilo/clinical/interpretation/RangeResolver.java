package zw.gov.mohcc.impilo.clinical.interpretation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretationContext;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ObservationInput;
import zw.gov.mohcc.impilo.clinical.interpretation.model.QualifiedInterval;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ReferenceRange;
import zw.gov.mohcc.impilo.clinical.interpretation.model.RangeSource;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ResolvedRange;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Sex;
import zw.gov.mohcc.impilo.clinical.terminology.UcumUnitConverter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the reference interval to interpret a value against, by code + patient context, honouring the
 * authoritative-source doctrine:
 *
 * <ol>
 *   <li><b>Lab-delivered interval</b> carried on the result — authoritative for labs (assay/method/
 *       population-specific). Preferred whenever present and unit-comparable.</li>
 *   <li><b>Governed FHIR ObservationDefinition.qualifiedInterval</b> (ZIBO, cited) matched by LOINC then
 *       local code, filtered/ranked against age/sex/(gestation)/specimen with wildcard fallback.</li>
 *   <li>Otherwise <b>{@code NO_REFERENCE_RANGE}</b> — never a fabricated "normal".</li>
 * </ol>
 *
 * <p>All bounds are UCUM-normalised into the observation's own unit (mass↔molar bridged when a molar
 * mass is supplied); an unconvertible unit yields {@code UNIT_MISMATCH} (fail-closed — never a silent
 * wrong comparison). Pregnancy-specific intervals are gated OFF in Phase 1
 * ({@code impilo.clinical.interpretation.pregnancy-ranges-enabled=false}); sex/pregnancy are never
 * inferred.</p>
 */
@Service
public class RangeResolver {

    private static final Logger log = LoggerFactory.getLogger(RangeResolver.class);

    private final ObservationDefinitionProvider definitionProvider;
    private final UcumUnitConverter ucum;
    private final boolean pregnancyRangesEnabled;

    public RangeResolver(ObservationDefinitionProvider definitionProvider,
                         UcumUnitConverter ucum,
                         @Value("${impilo.clinical.interpretation.pregnancy-ranges-enabled:false}") boolean pregnancyRangesEnabled) {
        this.definitionProvider = definitionProvider;
        this.ucum = ucum;
        this.pregnancyRangesEnabled = pregnancyRangesEnabled;
    }

    public ResolvedRange resolve(ObservationInput obs, InterpretationContext ctx) {
        if (obs == null || obs.value() == null || isBlank(obs.unit())) {
            return ResolvedRange.noReferenceRange();
        }
        InterpretationContext context = ctx == null
                ? new InterpretationContext(null, null, Sex.UNKNOWN, null, null, null) : ctx;

        // 1. Lab-delivered interval — authoritative for labs.
        if (obs.hasDeliveredInterval()) {
            return resolveDelivered(obs);
        }

        // 2. Governed ObservationDefinition.
        List<QualifiedInterval> intervals = definitionProvider.findIntervals(obs.loincCode(), obs.localCode());
        if (intervals == null || intervals.isEmpty()) {
            return ResolvedRange.noReferenceRange();
        }

        QualifiedInterval best = null;
        int bestScore = Integer.MIN_VALUE;
        for (QualifiedInterval qi : intervals) {
            if (!applicable(qi, context)) {
                continue;
            }
            int score = specificity(qi);
            if (score > bestScore) {
                bestScore = score;
                best = qi;
            }
        }
        if (best == null) {
            return ResolvedRange.noReferenceRange();
        }
        return normaliseGoverned(best, obs);
    }

    // ------------------------------------------------------------------
    // Lab-delivered
    // ------------------------------------------------------------------

    private ResolvedRange resolveDelivered(ObservationInput obs) {
        Optional<BigDecimal> low = convertBound(obs.deliveredLow(), obs.deliveredUnit(), obs.unit(), obs.molarMassGramPerMol());
        Optional<BigDecimal> high = convertBound(obs.deliveredHigh(), obs.deliveredUnit(), obs.unit(), obs.molarMassGramPerMol());
        if (low == null || high == null) {
            return ResolvedRange.unitMismatch();
        }
        return ResolvedRange.resolved(
                ReferenceRange.labDelivered(low.orElse(null), high.orElse(null), obs.unit()));
    }

    // ------------------------------------------------------------------
    // Governed ObservationDefinition
    // ------------------------------------------------------------------

    private ResolvedRange normaliseGoverned(QualifiedInterval qi, ObservationInput obs) {
        double mm = obs.molarMassGramPerMol();
        Optional<BigDecimal> low = convertBound(qi.low(), qi.unit(), obs.unit(), mm);
        Optional<BigDecimal> high = convertBound(qi.high(), qi.unit(), obs.unit(), mm);
        Optional<BigDecimal> cLow = convertBound(qi.criticalLow(), qi.unit(), obs.unit(), mm);
        Optional<BigDecimal> cHigh = convertBound(qi.criticalHigh(), qi.unit(), obs.unit(), mm);
        if (low == null || high == null || cLow == null || cHigh == null) {
            log.debug("UNIT_MISMATCH normalising governed interval {} ({} -> {})",
                    qi.canonicalUrl(), qi.unit(), obs.unit());
            return ResolvedRange.unitMismatch();
        }
        ReferenceRange range = new ReferenceRange(
                low.orElse(null), high.orElse(null), cLow.orElse(null), cHigh.orElse(null),
                obs.unit(), RangeSource.OBSERVATION_DEFINITION,
                qi.artifactId(), qi.version(), qi.canonicalUrl(), qi.condition());
        return ResolvedRange.resolved(range);
    }

    /**
     * Convert one bound to the target unit. Returns {@code Optional.empty()} when the bound itself is
     * null (absent bound — fine), {@code null} (sentinel) when the conversion fails (UNIT_MISMATCH).
     */
    private Optional<BigDecimal> convertBound(BigDecimal bound, String fromUnit, String toUnit, double molarMass) {
        if (bound == null) {
            return Optional.empty();
        }
        Optional<BigDecimal> converted = molarMass > 0
                ? ucum.convertWithMolarMass(bound, fromUnit, toUnit, molarMass)
                : ucum.convert(bound, fromUnit, toUnit);
        return converted.isPresent() ? converted : null;
    }

    // ------------------------------------------------------------------
    // Applicability + specificity
    // ------------------------------------------------------------------

    boolean applicable(QualifiedInterval qi, InterpretationContext ctx) {
        // Pregnancy gating (Phase 1: off).
        if (qi.pregnancy()) {
            if (!pregnancyRangesEnabled || !Boolean.TRUE.equals(ctx.pregnancyStatus())) {
                return false;
            }
            if (qi.gestationLowWeeks() != null || qi.gestationHighWeeks() != null) {
                Integer ga = ctx.gestationalAgeWeeks();
                if (ga == null) {
                    return false;
                }
                if (qi.gestationLowWeeks() != null && ga < qi.gestationLowWeeks()) {
                    return false;
                }
                if (qi.gestationHighWeeks() != null && ga >= qi.gestationHighWeeks()) {
                    return false;
                }
            }
        }
        // Sex-specific intervals require a matching, supplied sex.
        if (qi.sex() != null && qi.sex() != Sex.UNKNOWN) {
            if (ctx.sex() == null || ctx.sex() == Sex.UNKNOWN || ctx.sex() != qi.sex()) {
                return false;
            }
        }
        // Age-bounded intervals require a confirmable age.
        if (qi.ageLowYears() != null || qi.ageHighYears() != null) {
            Double age = ctx.effectiveAgeYears();
            if (age == null) {
                return false;
            }
            if (qi.ageLowYears() != null && age < qi.ageLowYears()) {
                return false;
            }
            if (qi.ageHighYears() != null && age >= qi.ageHighYears()) {
                return false;
            }
        }
        // Specimen-specific intervals require a matching, supplied specimen.
        if (qi.specimen() != null && !qi.specimen().isBlank()) {
            if (ctx.specimen() == null || !qi.specimen().equalsIgnoreCase(ctx.specimen())) {
                return false;
            }
        }
        return true;
    }

    int specificity(QualifiedInterval qi) {
        int score = 0;
        if (qi.sex() != null && qi.sex() != Sex.UNKNOWN) {
            score += 2;
        }
        if (qi.specimen() != null && !qi.specimen().isBlank()) {
            score += 2;
        }
        if (qi.ageLowYears() != null || qi.ageHighYears() != null) {
            score += 1;
        }
        if (qi.pregnancy()) {
            score += 1;
        }
        return score;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
