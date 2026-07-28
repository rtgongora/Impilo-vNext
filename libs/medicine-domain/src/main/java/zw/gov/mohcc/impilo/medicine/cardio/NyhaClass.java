package zw.gov.mohcc.impilo.medicine.cardio;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

/**
 * The New York Heart Association functional classification, I to IV.
 *
 * <p><strong>Source.</strong> The Criteria Committee of the New York Heart Association.
 * <em>Nomenclature and Criteria for Diagnosis of Diseases of the Heart and Great Vessels</em>,
 * 9th edition, 1994, pp. 253–256; reproduced in the 2022 AHA/ACC/HFSA heart-failure guideline and
 * the 2021 ESC acute and chronic heart-failure guidelines. The primary text is <strong>not
 * vendored</strong> under {@code docs/reference}; the criteria below are transcribed from the
 * published classification and are engineering seeds pending MoHCC ratification.</p>
 *
 * <p>The classification is a description of what a person can do, not a decision about their care.
 * Which class starts a medicine, warrants a device, or triggers referral is policy and lives in
 * governed CKP content. Naming the class from the symptoms is the arithmetic, and it is here.</p>
 *
 * <p><strong>Three-valued by necessity.</strong> {@link #classify} takes boxed Booleans so that
 * "not asked" is distinct from "asked and denied". A patient nobody questioned about rest symptoms
 * is not a patient without them, and reading an unasked question as a negative walks a class IV
 * patient down to class I — which is the direction that removes them from a follow-up list.</p>
 *
 * <p>The classification is also famously observer-dependent: two clinicians assessing the same
 * patient agree on the class only about half the time in published studies. That is a property of
 * the instrument, not of this code, but it is why a recorded class should carry who assessed it.</p>
 */
public enum NyhaClass {

    /**
     * Class I — no limitation of physical activity. Ordinary physical activity does not cause undue
     * breathlessness, fatigue or palpitations.
     */
    CLASS_I(1, "I",
            "No limitation of physical activity. Ordinary physical activity does not cause undue"
                    + " breathlessness, fatigue or palpitations."),

    /**
     * Class II — slight limitation of physical activity. Comfortable at rest, but ordinary physical
     * activity results in undue breathlessness, fatigue or palpitations.
     */
    CLASS_II(2, "II",
            "Slight limitation of physical activity. Comfortable at rest, but ordinary physical"
                    + " activity results in undue breathlessness, fatigue or palpitations."),

    /**
     * Class III — marked limitation of physical activity. Comfortable at rest, but less than
     * ordinary activity results in undue breathlessness, fatigue or palpitations.
     */
    CLASS_III(3, "III",
            "Marked limitation of physical activity. Comfortable at rest, but less than ordinary"
                    + " activity results in undue breathlessness, fatigue or palpitations."),

    /**
     * Class IV — unable to carry on any physical activity without discomfort. Symptoms may be
     * present at rest, and any physical activity undertaken increases the discomfort.
     */
    CLASS_IV(4, "IV",
            "Unable to carry on any physical activity without discomfort. Symptoms may be present at"
                    + " rest. If any physical activity is undertaken, discomfort increases.");

    /** Bumped whenever the criteria text or the classification logic changes. */
    public static final String CONTENT_VERSION = "impilo-nyha-1994-1.0.0";

    private final int numeral;
    private final String romanNumeral;
    private final String criteria;

    NyhaClass(int numeral, String romanNumeral, String criteria) {
        this.numeral = numeral;
        this.romanNumeral = romanNumeral;
        this.criteria = criteria;
    }

    /** 1 to 4. Ordinal severity: a higher number is a more limited patient. */
    public int numeral() {
        return numeral;
    }

    /** "I" to "IV" — the form the classification is always written in clinically. */
    public String romanNumeral() {
        return romanNumeral;
    }

    /** The published criteria for this class, for display beside the class itself. */
    public String criteria() {
        return criteria;
    }

    /** The class for a numeral 1–4, or null outside that range. Null is never class I. */
    public static NyhaClass fromNumeral(Integer numeral) {
        if (numeral == null) {
            return null;
        }
        for (NyhaClass value : values()) {
            if (value.numeral == numeral) {
                return value;
            }
        }
        return null;
    }

    /**
     * Classify from three recorded observations, in descending order of exertion tolerated.
     *
     * <p>Source as for the enum: NYHA 1994 criteria, not vendored. The mapping is the criteria read
     * as a ladder — symptoms at rest are class IV, symptoms on less than ordinary activity are
     * class III, symptoms on ordinary activity are class II, and none of those is class I.</p>
     *
     * <p>The ladder is walked from the top, and it stops at the first rung whose answer is missing.
     * If rest symptoms were not asked about, class IV cannot be excluded and no class is returned —
     * rather than quietly starting the walk at class III.</p>
     *
     * <p>The three answers must be monotone: activity that provokes symptoms at rest must also
     * provoke them on exertion, and less-than-ordinary exertion provoking symptoms implies ordinary
     * exertion does too. Answers that break that order are refused as inconsistent rather than
     * resolved by preferring one of them, because there is no basis for preferring either and the
     * two readings are two classes apart.</p>
     *
     * @param symptomsAtRest                     breathlessness, fatigue or palpitations at rest
     * @param symptomsOnLessThanOrdinaryActivity the same symptoms on less than ordinary exertion
     * @param symptomsOnOrdinaryActivity         the same symptoms on ordinary exertion
     */
    public static Classification classify(Boolean symptomsAtRest,
                                          Boolean symptomsOnLessThanOrdinaryActivity,
                                          Boolean symptomsOnOrdinaryActivity) {
        if (symptomsAtRest == null && symptomsOnLessThanOrdinaryActivity == null
                && symptomsOnOrdinaryActivity == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "No functional-status answers were recorded, so NYHA class cannot be assigned."
                            + " The classification is entirely a report of what the patient can do.");
        }

        if (Boolean.TRUE.equals(symptomsAtRest) && Boolean.FALSE.equals(symptomsOnOrdinaryActivity)) {
            return refused(RefusalReason.INPUTS_INCONSISTENT,
                    "Symptoms are recorded at rest but denied on ordinary activity. Someone breathless"
                            + " sitting still is breathless walking, so one of the two answers is wrong;"
                            + " they are two classes apart and neither is preferred here.");
        }
        if (Boolean.TRUE.equals(symptomsAtRest) && Boolean.FALSE.equals(symptomsOnLessThanOrdinaryActivity)) {
            return refused(RefusalReason.INPUTS_INCONSISTENT,
                    "Symptoms are recorded at rest but denied on less than ordinary activity. Rest is"
                            + " the least exertion there is, so these answers cannot both hold.");
        }
        if (Boolean.TRUE.equals(symptomsOnLessThanOrdinaryActivity)
                && Boolean.FALSE.equals(symptomsOnOrdinaryActivity)) {
            return refused(RefusalReason.INPUTS_INCONSISTENT,
                    "Symptoms are recorded on less than ordinary activity but denied on ordinary"
                            + " activity. Ordinary activity is the greater exertion, so it cannot be the"
                            + " better-tolerated one.");
        }

        if (Boolean.TRUE.equals(symptomsAtRest)) {
            return classified(CLASS_IV);
        }
        if (symptomsAtRest == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Whether there are symptoms at rest was not recorded, so class IV cannot be ruled"
                            + " out and no lower class can be assigned.");
        }

        if (Boolean.TRUE.equals(symptomsOnLessThanOrdinaryActivity)) {
            return classified(CLASS_III);
        }
        if (symptomsOnLessThanOrdinaryActivity == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Whether less than ordinary activity provokes symptoms was not recorded, so class"
                            + " III cannot be ruled out.");
        }

        if (Boolean.TRUE.equals(symptomsOnOrdinaryActivity)) {
            return classified(CLASS_II);
        }
        if (symptomsOnOrdinaryActivity == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Whether ordinary activity provokes symptoms was not recorded, so class II cannot"
                            + " be ruled out.");
        }

        return classified(CLASS_I);
    }

    private static Classification classified(NyhaClass value) {
        return new Classification(value, null, null, CONTENT_VERSION);
    }

    private static Classification refused(RefusalReason reason, String explanation) {
        return new Classification(null, reason, explanation, CONTENT_VERSION);
    }

    /**
     * A NYHA class, or a stated reason there is none.
     *
     * @param nyhaClass      the class; null when refused. Null is never read as class I.
     * @param reason         machine-readable refusal code; null when classified
     * @param explanation    clinician-readable refusal text; null when classified
     * @param contentVersion the {@link #CONTENT_VERSION} that produced this result
     */
    public record Classification(NyhaClass nyhaClass,
                                 RefusalReason reason,
                                 String explanation,
                                 String contentVersion) {

        /** True when a class is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return nyhaClass != null;
        }
    }
}
