package zw.gov.mohcc.impilo.pct.core.forms;

import java.util.List;

/**
 * De-PII'd patient facts used for form applicability. Mirrors the frontend
 * {@code ClinicalFormPatientContext} (types.ts) field semantics so the FE visibility rules and the
 * backend obligation rules agree. Never carries name/DOB/contact — only derived facts.
 *
 * <p>Age is carried in days as well as months because paediatric pathways change within days
 * of birth: the sick-young-infant assessment applies to 0-59 days and the neonatal bands to
 * the first week and the first month. Months cannot express those boundaries. Gestational age
 * at birth travels with the facts so a form intended for preterm infants can be selected on
 * corrected rather than chronological age.</p>
 *
 * @param ageMonths            completed months; null = unknown (do not age-filter)
 * @param sex                  MALE | FEMALE | UNKNOWN | OTHER
 * @param pregnant             null = unknown
 * @param ageDays              completed days of life; null = unknown
 * @param gestationalAgeWeeks  gestational age at birth in completed weeks; null = unknown
 */
public record PatientFacts(
        Integer ageMonths,
        String sex,
        Boolean pregnant,
        List<String> programmes,
        List<String> conditionCodes,
        Integer ageDays,
        Integer gestationalAgeWeeks) {

    /** Retained for callers that have no day-level age or gestational age to offer. */
    public PatientFacts(Integer ageMonths, String sex, Boolean pregnant,
                        List<String> programmes, List<String> conditionCodes) {
        this(ageMonths, sex, pregnant, programmes, conditionCodes, null, null);
    }

    public static PatientFacts unknown() {
        return new PatientFacts(null, "UNKNOWN", null, List.of(), List.of(), null, null);
    }

    public List<String> programmesOrEmpty() { return programmes == null ? List.of() : programmes; }
    public List<String> conditionCodesOrEmpty() { return conditionCodes == null ? List.of() : conditionCodes; }
}
