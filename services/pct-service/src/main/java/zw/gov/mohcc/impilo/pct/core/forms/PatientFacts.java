package zw.gov.mohcc.impilo.pct.core.forms;

import java.util.List;

/**
 * De-PII'd patient facts used for form applicability. Mirrors the frontend
 * {@code ClinicalFormPatientContext} (types.ts) field semantics so the FE visibility rules and the
 * backend obligation rules agree. Never carries name/DOB/contact — only derived facts.
 */
public record PatientFacts(
        Integer ageMonths,          // null = unknown (do not age-filter)
        String sex,                 // MALE | FEMALE | UNKNOWN | OTHER
        Boolean pregnant,           // null = unknown
        List<String> programmes,
        List<String> conditionCodes) {

    public static PatientFacts unknown() {
        return new PatientFacts(null, "UNKNOWN", null, List.of(), List.of());
    }

    public List<String> programmesOrEmpty() { return programmes == null ? List.of() : programmes; }
    public List<String> conditionCodesOrEmpty() { return conditionCodes == null ? List.of() : conditionCodes; }
}
