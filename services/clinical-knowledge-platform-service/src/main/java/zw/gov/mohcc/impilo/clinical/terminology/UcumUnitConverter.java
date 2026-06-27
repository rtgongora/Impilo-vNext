package zw.gov.mohcc.impilo.clinical.terminology;

import org.fhir.ucum.Decimal;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Unit-safe conversion for comparing a measured value against a reference range, backed by the real
 * UCUM library (bundles {@code ucum-essence.xml}). Handles dimensionally-consistent conversions only
 * (e.g. mg/dL↔g/L, mmol/L↔umol/L, mmHg, Cel). Mass↔molar (e.g. mg/dL↔mmol/L) is dimensionally
 * different and requires an analyte molar mass — see {@link #convertWithMolarMass}.
 *
 * <p>Fail-closed: any unconvertible/incomparable/parse failure returns {@link Optional#empty()} so
 * the caller flags UNIT_MISMATCH rather than performing a silent (wrong) comparison. If UCUM fails to
 * initialise, all non-identity conversions return empty.
 */
@Component
public class UcumUnitConverter {

    private static final Logger log = LoggerFactory.getLogger(UcumUnitConverter.class);

    /** Common clinical unit spellings → canonical UCUM. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("°C", "Cel"), Map.entry("oC", "Cel"), Map.entry("degC", "Cel"), Map.entry("Cel", "Cel"),
            Map.entry("mmHg", "mm[Hg]"),
            Map.entry("IU/L", "[IU]/L"), Map.entry("IU/mL", "[IU]/mL"), Map.entry("mIU/L", "m[IU]/L"),
            Map.entry("mEq/L", "meq/L"), Map.entry("mequiv/L", "meq/L"),
            Map.entry("bpm", "/min"), Map.entry("beats/min", "/min"), Map.entry("breaths/min", "/min"));

    private final UcumService ucum;

    public UcumUnitConverter() {
        UcumService svc = null;
        try (InputStream in = getClass().getResourceAsStream("/ucum-essence.xml")) {
            if (in != null) {
                svc = new UcumEssenceService(in);
            } else {
                log.warn("ucum-essence.xml not on classpath — UCUM conversion disabled (fail-closed).");
            }
        } catch (Throwable t) {
            log.warn("UCUM init failed — conversion disabled (fail-closed): {}", t.getMessage());
        }
        this.ucum = svc;
    }

    /** Convert a value between dimensionally-consistent units. Empty when incomparable/unknown. */
    public Optional<BigDecimal> convert(BigDecimal value, String fromUnit, String toUnit) {
        if (value == null || fromUnit == null || toUnit == null) {
            return Optional.empty();
        }
        String from = canonical(fromUnit);
        String to = canonical(toUnit);
        if (from.equals(to)) {
            return Optional.of(value);
        }
        if (ucum == null) {
            return Optional.empty();
        }
        try {
            if (ucum.validate(from) != null || ucum.validate(to) != null) {
                return Optional.empty();
            }
            Decimal converted = ucum.convert(new Decimal(value.toPlainString()), from, to);
            return Optional.of(new BigDecimal(converted.asDecimal()));
        } catch (Throwable t) {
            // incompatible dimensions / parse error → UNIT_MISMATCH (never a silent wrong compare)
            return Optional.empty();
        }
    }

    /**
     * Bridge mass-concentration ↔ molar-concentration for a known analyte (molar mass g/mol).
     * Used by the interpretation engine when an analyte's value and range differ across the mass/molar
     * divide (e.g. glucose mg/dL vs mmol/L). Returns empty when neither a direct conversion nor a
     * molar bridge applies.
     */
    public Optional<BigDecimal> convertWithMolarMass(BigDecimal value, String fromUnit, String toUnit, double molarMassGramPerMol) {
        Optional<BigDecimal> direct = convert(value, fromUnit, toUnit);
        if (direct.isPresent()) {
            return direct;
        }
        if (molarMassGramPerMol <= 0) {
            return Optional.empty();
        }
        // Normalise both sides to grams/L and moles/L, then bridge via molar mass.
        Optional<BigDecimal> asGramsPerL = convert(value, fromUnit, "g/L");
        if (asGramsPerL.isPresent()) {
            // g/L → mol/L = (g/L) / molarMass ; then mol/L → target
            BigDecimal molPerL = asGramsPerL.get().divide(BigDecimal.valueOf(molarMassGramPerMol), java.math.MathContext.DECIMAL64);
            return convert(molPerL, "mol/L", toUnit);
        }
        Optional<BigDecimal> asMolPerL = convert(value, fromUnit, "mol/L");
        if (asMolPerL.isPresent()) {
            // mol/L → g/L = mol/L * molarMass ; then g/L → target
            BigDecimal gPerL = asMolPerL.get().multiply(BigDecimal.valueOf(molarMassGramPerMol));
            return convert(gPerL, "g/L", toUnit);
        }
        return Optional.empty();
    }

    public boolean isComparable(String fromUnit, String toUnit) {
        return convert(BigDecimal.ONE, fromUnit, toUnit).isPresent();
    }

    static String canonical(String unit) {
        if (unit == null) {
            return "";
        }
        String u = unit.trim();
        String mapped = ALIASES.get(u);
        if (mapped != null) {
            return mapped;
        }
        u = u.replace("µ", "u").replace("μ", "u");
        u = u.replace("×", "x").replace("⁹", "9").replace("¹²", "12").replace("⁶", "6").replace("³", "3");
        u = u.replace("x10^12", "10*12").replace("10^12", "10*12")
                .replace("x10^9", "10*9").replace("10^9", "10*9")
                .replace("x10^6", "10*6").replace("10^6", "10*6")
                .replace("x10*9", "10*9").replace("x10*12", "10*12").replace("x10*6", "10*6");
        mapped = ALIASES.get(u);
        return mapped != null ? mapped : u;
    }
}
