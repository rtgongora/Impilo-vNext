package zw.gov.mohcc.impilo.clinical.interpretation;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretationCode;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretationContext;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretedObservation;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ObservationInput;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ReferenceRange;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ResolvedRange;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Trend;
import zw.gov.mohcc.impilo.clinical.terminology.UcumUnitConverter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Classifies a value against its resolved reference interval into
 * NORMAL/LOW/HIGH/CRITICAL_LOW/CRITICAL_HIGH (+ FHIR interpretation code) and computes delta/trend
 * versus a prior value.
 *
 * <p>Doctrine enforced here: {@code NO_REFERENCE_RANGE} is emitted (never NORMAL) when no interval
 * resolves; {@code UNIT_MISMATCH} when the interval is not unit-comparable; {@code DATA_QUALITY} when a
 * value is outside supplied plausible bounds (never "critical"). The result is advisory and never
 * overrides clinician judgement.</p>
 */
@Service
public class InterpretationEngine {

    private final RangeResolver rangeResolver;
    private final UcumUnitConverter ucum;

    public InterpretationEngine(RangeResolver rangeResolver, UcumUnitConverter ucum) {
        this.rangeResolver = rangeResolver;
        this.ucum = ucum;
    }

    /** Interpret a batch of observations against the same patient context. */
    public List<InterpretedObservation> interpretAll(List<ObservationInput> observations, InterpretationContext ctx) {
        List<InterpretedObservation> out = new ArrayList<>();
        if (observations == null) {
            return out;
        }
        for (ObservationInput obs : observations) {
            if (obs != null) {
                out.add(interpret(obs, ctx));
            }
        }
        return out;
    }

    public InterpretedObservation interpret(ObservationInput obs, InterpretationContext ctx) {
        if (obs == null || obs.value() == null || isBlank(obs.unit())) {
            return build(obs, InterpretationCode.NO_REFERENCE_RANGE, null, null, null,
                    "No value/unit supplied — not interpreted.");
        }

        BigDecimal delta = computeDelta(obs);
        Trend trend = delta == null ? null : trendOf(delta);

        // Data-quality (implausible) check — never reported as clinical critical/normal.
        if (isImplausible(obs)) {
            return build(obs, InterpretationCode.DATA_QUALITY, null, delta, trend,
                    "Value outside plausible physiological bounds — data-quality flag, not a clinical interpretation.");
        }

        ResolvedRange resolved = rangeResolver.resolve(obs, ctx);
        switch (resolved.status()) {
            case NO_REFERENCE_RANGE:
                return build(obs, InterpretationCode.NO_REFERENCE_RANGE, null, delta, trend,
                        "No patient-appropriate reference interval available — not assessed as normal.");
            case UNIT_MISMATCH:
                return build(obs, InterpretationCode.UNIT_MISMATCH, null, delta, trend,
                        "Reference interval units not convertible to the value's units — comparison withheld.");
            case RESOLVED:
            default:
                ReferenceRange range = resolved.range();
                InterpretationCode code = classify(obs.value(), range);
                return build(obs, code, range, delta, trend, noteFor(code, range));
        }
    }

    // ------------------------------------------------------------------
    // Classification
    // ------------------------------------------------------------------

    InterpretationCode classify(BigDecimal value, ReferenceRange r) {
        if (r.criticalLow() != null && value.compareTo(r.criticalLow()) <= 0) {
            return InterpretationCode.CRITICAL_LOW;
        }
        if (r.criticalHigh() != null && value.compareTo(r.criticalHigh()) >= 0) {
            return InterpretationCode.CRITICAL_HIGH;
        }
        if (r.low() != null && value.compareTo(r.low()) < 0) {
            return InterpretationCode.LOW;
        }
        if (r.high() != null && value.compareTo(r.high()) > 0) {
            return InterpretationCode.HIGH;
        }
        return InterpretationCode.NORMAL;
    }

    // ------------------------------------------------------------------
    // Delta / trend
    // ------------------------------------------------------------------

    private BigDecimal computeDelta(ObservationInput obs) {
        if (obs.priorValue() == null) {
            return null;
        }
        String priorUnit = isBlank(obs.priorUnit()) ? obs.unit() : obs.priorUnit();
        Optional<BigDecimal> priorConverted = obs.molarMassGramPerMol() > 0
                ? ucum.convertWithMolarMass(obs.priorValue(), priorUnit, obs.unit(), obs.molarMassGramPerMol())
                : ucum.convert(obs.priorValue(), priorUnit, obs.unit());
        return priorConverted.map(p -> obs.value().subtract(p)).orElse(null);
    }

    private Trend trendOf(BigDecimal delta) {
        int sign = delta.compareTo(BigDecimal.ZERO);
        if (sign > 0) {
            return Trend.RISING;
        }
        if (sign < 0) {
            return Trend.FALLING;
        }
        return Trend.STABLE;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean isImplausible(ObservationInput obs) {
        if (obs.plausibleMin() != null && obs.value().compareTo(obs.plausibleMin()) < 0) {
            return true;
        }
        return obs.plausibleMax() != null && obs.value().compareTo(obs.plausibleMax()) > 0;
    }

    private InterpretedObservation build(ObservationInput obs, InterpretationCode code, ReferenceRange range,
                                         BigDecimal delta, Trend trend, String note) {
        return new InterpretedObservation(
                obs == null ? null : obs.loincCode(),
                obs == null ? null : obs.localCode(),
                obs == null ? null : obs.displayName(),
                obs == null ? "LAB" : obs.category(),
                obs == null ? null : obs.value(),
                obs == null ? null : obs.unit(),
                code, range, delta, trend, note);
    }

    private String noteFor(InterpretationCode code, ReferenceRange r) {
        String src = r.source() == null ? "" : " [" + r.source().name().toLowerCase().replace('_', ' ') + "]";
        switch (code) {
            case NORMAL: return "Within reference interval" + src + ".";
            case LOW: return "Below reference interval" + src + ".";
            case HIGH: return "Above reference interval" + src + ".";
            case CRITICAL_LOW: return "Critically low" + src + " — urgent review.";
            case CRITICAL_HIGH: return "Critically high" + src + " — urgent review.";
            default: return "Interpreted" + src + ".";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
