package zw.gov.mohcc.impilo.clinical.interpretation.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A single observation (vital, lab analyte, or derived value) to be interpreted against a
 * patient-appropriate reference interval.
 *
 * <p>Carries optional <em>lab-delivered</em> reference-interval fields ({@code deliveredLow/High/Unit}
 * and {@code deliveredInterpretation}). Per doctrine these are <strong>authoritative for labs</strong>
 * (assay/method/population-specific) and take precedence over governed ObservationDefinitions when
 * present and unit-comparable.</p>
 *
 * <p>Optional {@code priorValue}/{@code priorUnit} drive delta/trend. Optional
 * {@code plausibleMin}/{@code plausibleMax} drive the data-quality (implausible) flag — an implausible
 * value is flagged as data-quality, never as "critical".</p>
 *
 * @param loincCode               LOINC code (preferred match key; nullable)
 * @param localCode               local/LIMS code (fallback match key; nullable)
 * @param displayName             human-readable analyte/vital name
 * @param category                LAB | VITAL | DERIVED (free string; defaults LAB)
 * @param value                   measured value (required for interpretation)
 * @param unit                    UCUM unit of {@code value} (required for unit-safe comparison)
 * @param effectiveTime           ISO-8601 instant the value was taken (nullable; for trend ordering)
 * @param priorValue              previous value for the same analyte (nullable)
 * @param priorUnit               UCUM unit of {@code priorValue} (nullable)
 * @param deliveredLow            lab-delivered lower reference bound (nullable)
 * @param deliveredHigh           lab-delivered upper reference bound (nullable)
 * @param deliveredUnit           UCUM unit of the lab-delivered interval (nullable)
 * @param deliveredInterpretation lab-delivered FHIR interpretation code, if any (nullable)
 * @param plausibleMin            implausible-below threshold (nullable)
 * @param plausibleMax            implausible-above threshold (nullable)
 * @param molarMassGramPerMol     analyte molar mass to bridge mass↔molar units (0 = none)
 */
public record ObservationInput(
        String loincCode,
        String localCode,
        String displayName,
        String category,
        BigDecimal value,
        String unit,
        String effectiveTime,
        BigDecimal priorValue,
        String priorUnit,
        BigDecimal deliveredLow,
        BigDecimal deliveredHigh,
        String deliveredUnit,
        String deliveredInterpretation,
        BigDecimal plausibleMin,
        BigDecimal plausibleMax,
        double molarMassGramPerMol
) {

    public ObservationInput {
        if (category == null || category.isBlank()) {
            category = "LAB";
        }
    }

    /** True when a usable lab-delivered interval (at least one bound + unit) is present. */
    public boolean hasDeliveredInterval() {
        return (deliveredLow != null || deliveredHigh != null) && deliveredUnit != null && !deliveredUnit.isBlank();
    }

    public static ObservationInput fromMap(Map<String, Object> m) {
        if (m == null) {
            return new ObservationInput(null, null, null, "LAB", null, null, null, null, null,
                    null, null, null, null, null, null, 0d);
        }
        return new ObservationInput(
                str(m.get("loincCode")),
                str(m.get("localCode")),
                str(m.get("displayName")),
                str(m.get("category")),
                dec(m.get("value")),
                str(m.get("unit")),
                str(m.get("effectiveTime")),
                dec(m.get("priorValue")),
                str(m.get("priorUnit")),
                dec(m.get("deliveredLow")),
                dec(m.get("deliveredHigh")),
                str(m.get("deliveredUnit")),
                str(m.get("deliveredInterpretation")),
                dec(m.get("plausibleMin")),
                dec(m.get("plausibleMax")),
                m.get("molarMassGramPerMol") instanceof Number n ? n.doubleValue() : 0d);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static BigDecimal dec(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
