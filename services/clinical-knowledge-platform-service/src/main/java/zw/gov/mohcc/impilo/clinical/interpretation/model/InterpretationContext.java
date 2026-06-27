package zw.gov.mohcc.impilo.clinical.interpretation.model;

import java.util.Map;

/**
 * Patient context that conditions reference-interval selection. All fields nullable / fail-open to the
 * widest applicable interval — never inferred. Sex/pregnancy are supplied from governed demographics only.
 *
 * <p>Phase 1 ships age + sex resolution; pregnancy/gestation are carried for forward-compatibility but
 * pregnancy-specific intervals are gated OFF until Phase 6 (see {@code pregnancyRangesEnabled} on the
 * resolver). They are never guessed.</p>
 *
 * @param ageYears             age in years (nullable)
 * @param ageDays              age in days for neonates/infants (nullable; preferred when &lt; 1y)
 * @param sex                  biological sex ({@link Sex#UNKNOWN} when not supplied)
 * @param pregnancyStatus      pregnancy status when governed-known (nullable = unknown)
 * @param gestationalAgeWeeks  gestational age in weeks when pregnant (nullable)
 * @param specimen             specimen for labs (e.g. SERUM, PLASMA, BLOOD) — nullable
 */
public record InterpretationContext(
        Double ageYears,
        Integer ageDays,
        Sex sex,
        Boolean pregnancyStatus,
        Integer gestationalAgeWeeks,
        String specimen
) {

    public InterpretationContext {
        if (sex == null) {
            sex = Sex.UNKNOWN;
        }
    }

    /** Effective age in years, derived from ageDays when ageYears is absent. Null when neither supplied. */
    public Double effectiveAgeYears() {
        if (ageYears != null) {
            return ageYears;
        }
        if (ageDays != null) {
            return ageDays / 365.25;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static InterpretationContext fromMap(Map<String, Object> map) {
        if (map == null) {
            return new InterpretationContext(null, null, Sex.UNKNOWN, null, null, null);
        }
        Double ageYears = toDouble(map.get("ageYears"));
        Integer ageDays = toInt(map.get("ageDays"));
        Sex sex = Sex.fromString(map.get("sex") == null ? null : map.get("sex").toString());
        Boolean pregnancy = map.get("pregnancyStatus") instanceof Boolean b ? b : null;
        Integer gestation = toInt(map.get("gestationalAgeWeeks"));
        String specimen = map.get("specimen") == null ? null : map.get("specimen").toString();
        return new InterpretationContext(ageYears, ageDays, sex, pregnancy, gestation, specimen);
    }

    private static Double toDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
