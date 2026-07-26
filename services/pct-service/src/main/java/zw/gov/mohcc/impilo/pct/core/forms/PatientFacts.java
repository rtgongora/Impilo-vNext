package zw.gov.mohcc.impilo.pct.core.forms;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * De-PII'd patient facts used for form applicability and clinical decision support. Mirrors the
 * frontend {@code ClinicalFormPatientContext} (types.ts) field semantics so the FE visibility rules
 * and the backend obligation rules agree. Never carries name/DOB/contact — only derived facts.
 *
 * <p>Age is carried in days as well as months because paediatric pathways change within days
 * of birth: the sick-young-infant assessment applies to 0-59 days and the neonatal bands to
 * the first week and the first month. Months cannot express those boundaries. Gestational age
 * at birth travels with the facts so a form intended for preterm infants can be selected on
 * corrected rather than chronological age.</p>
 *
 * <p><strong>Adult clinical facts.</strong> This record was paediatric-shaped — age, sex,
 * pregnancy, programmes, conditions — and adult decision support cannot be built on it. Renal
 * dosing, hepatic dosing, interaction checking, duplicate-therapy detection and deprescribing all
 * need facts that had no way to reach an engine at all, so those requirements were unimplementable
 * rather than merely unimplemented. Weight, renal function, hepatic impairment and the current
 * medicine list are added for that reason.</p>
 *
 * <p>Each carries <em>when it was true</em> rather than a bare number, because these decay: a
 * weight from two years ago is not a dosing weight, and an eGFR from before an acute illness is not
 * the kidney function you are dosing against today. See {@link DatedMeasurement} — the fact reports
 * its age, and governed rule content decides how old is too old.</p>
 *
 * <p><strong>Absent is never normal.</strong> Every added fact is null when unknown, and null must
 * not be resolved to a reassuring default. An unmeasured eGFR is not a normal eGFR; the correct
 * behaviour for a rule that needs one and does not have it is to say so and decline to score, which
 * is the paediatric pack's rule about not calculating on missing foundations, applied to adults.</p>
 *
 * @param ageMonths            completed months; null = unknown (do not age-filter)
 * @param sex                  MALE | FEMALE | UNKNOWN | OTHER
 * @param pregnant             null = unknown. Derived only TRUE, never FALSE — "no pregnancy has
 *                             been recorded" and "she is not pregnant" are different statements and
 *                             only the first is one this system can make (RMNP lane convention)
 * @param ageDays              completed days of life; null = unknown
 * @param gestationalAgeWeeks  gestational age at birth in completed weeks; null = unknown
 * @param weight               body weight; null = never measured, never assume a default adult weight
 * @param renalFunction        estimated GFR, with the equation in {@code source}; null = unknown
 * @param hepaticImpairment    NONE | MILD | MODERATE | SEVERE; null = unknown, not "none"
 * @param currentMedicationCodes codes of medicines the patient is currently on, for interaction and
 *                             duplicate-therapy checking; empty and null both mean "no list was
 *                             supplied", which is not the same as "takes no medicines"
 */
public record PatientFacts(
        Integer ageMonths,
        String sex,
        Boolean pregnant,
        List<String> programmes,
        List<String> conditionCodes,
        Integer ageDays,
        Integer gestationalAgeWeeks,
        DatedMeasurement weight,
        DatedMeasurement renalFunction,
        String hepaticImpairment,
        List<String> currentMedicationCodes) {

    /** Retained for callers that have no day-level age or gestational age to offer. */
    public PatientFacts(Integer ageMonths, String sex, Boolean pregnant,
                        List<String> programmes, List<String> conditionCodes) {
        this(ageMonths, sex, pregnant, programmes, conditionCodes, null, null,
                null, null, null, null);
    }

    /** Retained for callers that carry age and gestation but no adult clinical facts. */
    public PatientFacts(Integer ageMonths, String sex, Boolean pregnant,
                        List<String> programmes, List<String> conditionCodes,
                        Integer ageDays, Integer gestationalAgeWeeks) {
        this(ageMonths, sex, pregnant, programmes, conditionCodes, ageDays, gestationalAgeWeeks,
                null, null, null, null);
    }

    public static PatientFacts unknown() {
        return new PatientFacts(null, "UNKNOWN", null, List.of(), List.of(), null, null,
                null, null, null, null);
    }

    public List<String> programmesOrEmpty() { return programmes == null ? List.of() : programmes; }
    public List<String> conditionCodesOrEmpty() { return conditionCodes == null ? List.of() : conditionCodes; }
    public List<String> currentMedicationCodesOrEmpty() {
        return currentMedicationCodes == null ? List.of() : currentMedicationCodes;
    }

    public DatedMeasurement weightOrUnknown() {
        return weight == null ? DatedMeasurement.unknown() : weight;
    }

    public DatedMeasurement renalFunctionOrUnknown() {
        return renalFunction == null ? DatedMeasurement.unknown() : renalFunction;
    }

    /** Returns a copy carrying the supplied adult clinical facts. */
    public PatientFacts withClinicalFacts(DatedMeasurement weight, DatedMeasurement renalFunction,
                                          String hepaticImpairment, List<String> medicationCodes) {
        return new PatientFacts(ageMonths, sex, pregnant, programmes, conditionCodes,
                ageDays, gestationalAgeWeeks, weight, renalFunction, hepaticImpairment, medicationCodes);
    }

    /**
     * Flattens the facts into the one map per request that the rules engines evaluate against.
     *
     * <p>Every fact gets its own key. <strong>Nothing is ever folded into {@code ageDays}</strong> —
     * the map is shared by every rule in an evaluation, so overloading a key corrupts every other
     * rule in the same request, and a fact named {@code ageDays} holding a gestation or a weight
     * reads as an infant to the dosing engine without the dosing engine ever saying so. Adult
     * banding uses these distinct keys with {@code bandKey} (emergency and RMNP lane convention).</p>
     *
     * <p>Absent facts are omitted rather than emitted as null, so a rule asking for a fact it needs
     * gets "not present" — which its three-valued evaluator reports as unassessed — instead of a
     * null that could be coerced to zero. A zero eGFR is dialysis-grade renal failure; a zero
     * weight is not a patient.</p>
     */
    public Map<String, Object> toRuleFacts(LocalDate today) {
        Map<String, Object> facts = new LinkedHashMap<>();
        putIfPresent(facts, "ageMonths", ageMonths);
        putIfPresent(facts, "ageDays", ageDays);
        putIfPresent(facts, "gestationalAgeWeeks", gestationalAgeWeeks);
        putIfPresent(facts, "sex", sex == null || "UNKNOWN".equals(sex) ? null : sex);
        putIfPresent(facts, "pregnant", pregnant);
        putIfPresent(facts, "hepaticImpairment", hepaticImpairment);

        DatedMeasurement w = weightOrUnknown();
        if (w.known()) {
            facts.put("weightKg", w.value());
            putIfPresent(facts, "weightMeasuredDaysAgo", w.ageInDays(today));
        }
        DatedMeasurement egfr = renalFunctionOrUnknown();
        if (egfr.known()) {
            facts.put("egfr", egfr.value());
            putIfPresent(facts, "egfrEquation", egfr.source());
            putIfPresent(facts, "egfrMeasuredDaysAgo", egfr.ageInDays(today));
        }
        if (!programmesOrEmpty().isEmpty()) facts.put("programmes", programmesOrEmpty());
        if (!conditionCodesOrEmpty().isEmpty()) facts.put("conditionCodes", conditionCodesOrEmpty());
        if (!currentMedicationCodesOrEmpty().isEmpty()) {
            facts.put("currentMedicationCodes", currentMedicationCodesOrEmpty());
        }
        return facts;
    }

    private static void putIfPresent(Map<String, Object> facts, String key, Object value) {
        if (value != null) {
            facts.put(key, value);
        }
    }
}
