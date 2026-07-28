package zw.gov.mohcc.impilo.medicine.frailty;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

/**
 * The Rockwood Clinical Frailty Scale, 1 to 9.
 *
 * <p><strong>Source.</strong> Rockwood K, Song X, MacKnight C, et al. <em>A global clinical measure
 * of fitness and frailty in elderly people.</em> CMAJ 2005;173:489–495; level names as revised in
 * Clinical Frailty Scale version 2.0 (2020), Geriatric Medicine Research, Dalhousie University. The
 * primary text is <strong>not vendored</strong> under {@code docs/reference} and — see below — the
 * official descriptors deliberately are not reproduced here. The level names and the summaries below
 * are engineering seeds pending MoHCC ratification.</p>
 *
 * <p><strong>The official descriptors are not in this file, and that is not an oversight.</strong>
 * The Clinical Frailty Scale and its accompanying descriptive text are copyright Dalhousie
 * University and are made available under a licence that governs reproduction. Impilo holds no such
 * licence today. {@link #summary()} therefore returns a short paraphrase written for this codebase,
 * sufficient to disambiguate the levels in a picker, and explicitly not the wording a clinician
 * should be scoring against. A deployment that displays the CFS to clinicians needs the licensed
 * chart, and {@link #OFFICIAL_DESCRIPTORS_LICENSED} exists so a build can assert whether one has
 * been obtained.</p>
 *
 * <p>The scale is a judgement recorded, not a judgement made: this class converts a number a
 * clinician assigned into a named level and refuses numbers that are not on the scale. What a level
 * means for a care plan, an operation or an admission decision is policy and lives in governed CKP
 * content.</p>
 *
 * <p><strong>Applicability.</strong> The CFS was developed and validated in adults aged 65 and over.
 * Its authors state it is not appropriate for younger people, nor for people of any age with stable
 * single-system disability such as cerebral palsy or spinal cord injury, whose limitations are not
 * frailty and who score high on a scale that then implies a trajectory they do not have.
 * {@link #applicabilityCaveat(Integer)} surfaces that as text; it does not block, because a
 * clinician may have a documented reason and this library does not overrule them.</p>
 */
public enum ClinicalFrailtyScale {

    /** 1 — Very Fit. */
    VERY_FIT(1, "Very Fit",
            "Robust, active and energetic; among the fittest for their age and exercising regularly."),

    /** 2 — Fit. Named "Well" before version 2.0 of the scale. */
    FIT(2, "Fit",
            "No active disease symptoms but less fit than category 1; active occasionally, for example"
                    + " seasonally."),

    /** 3 — Managing Well. */
    MANAGING_WELL(3, "Managing Well",
            "Medical problems are well controlled, but the person is not regularly active beyond"
                    + " routine walking."),

    /** 4 — Living with Very Mild Frailty. Named "Vulnerable" before version 2.0. */
    LIVING_WITH_VERY_MILD_FRAILTY(4, "Living with Very Mild Frailty",
            "Not dependent on others day to day, but symptoms limit activity; commonly described as"
                    + " slowed up or tired during the day."),

    /** 5 — Living with Mild Frailty. Named "Mildly Frail" before version 2.0. */
    LIVING_WITH_MILD_FRAILTY(5, "Living with Mild Frailty",
            "More evident slowing; needs help with higher-order tasks such as finances, transport,"
                    + " heavy housework or medicines."),

    /** 6 — Living with Moderate Frailty. */
    LIVING_WITH_MODERATE_FRAILTY(6, "Living with Moderate Frailty",
            "Needs help with all outside activities and with keeping house; often has difficulty with"
                    + " stairs and needs help bathing."),

    /** 7 — Living with Severe Frailty. */
    LIVING_WITH_SEVERE_FRAILTY(7, "Living with Severe Frailty",
            "Completely dependent for personal care from any cause, physical or cognitive, but stable"
                    + " and not at high risk of dying within about six months."),

    /** 8 — Living with Very Severe Frailty. */
    LIVING_WITH_VERY_SEVERE_FRAILTY(8, "Living with Very Severe Frailty",
            "Completely dependent for personal care and approaching the end of life; typically could"
                    + " not recover even from a minor illness."),

    /** 9 — Terminally Ill. */
    TERMINALLY_ILL(9, "Terminally Ill",
            "Life expectancy under about six months, in someone who is not otherwise living with"
                    + " evident frailty.");

    /** Bumped whenever a level name, summary or bound changes. */
    public static final String CONTENT_VERSION = "impilo-cfs-2020-v2-1.0.0";

    /** Lowest score on the scale. */
    public static final int MIN_SCORE = 1;

    /** Highest score on the scale. There is no 10, and a 0 is not "not frail". */
    public static final int MAX_SCORE = 9;

    /** Age from which the scale was validated. */
    public static final int VALIDATED_FROM_AGE_YEARS = 65;

    /**
     * False. Flip it only in the commit that adds a Dalhousie University licence and the official
     * descriptor text; until then {@link #summary()} is an Impilo paraphrase and a clinical surface
     * that scores patients against it is scoring against the wrong words.
     */
    public static final boolean OFFICIAL_DESCRIPTORS_LICENSED = false;

    private final int score;
    private final String label;
    private final String summary;

    ClinicalFrailtyScale(int score, String label, String summary) {
        this.score = score;
        this.label = label;
        this.summary = summary;
    }

    /** The number on the scale, 1 to 9. */
    public int score() {
        return score;
    }

    /** The level name as published in version 2.0 of the scale. */
    public String label() {
        return label;
    }

    /**
     * A short Impilo-written paraphrase of the level, for disambiguation only.
     *
     * <p>Not the official Dalhousie descriptor — see the class javadoc and
     * {@link #OFFICIAL_DESCRIPTORS_LICENSED}.</p>
     */
    public String summary() {
        return summary;
    }

    /** True for levels 5 and above, the point at which the scale's own naming says "frailty". */
    public boolean livingWithFrailty() {
        return score >= 5 && score <= 8;
    }

    /**
     * The level for a score, or a stated reason there is none.
     *
     * <p>Source as for the enum. A score off the scale is refused rather than clamped: clamping 0 to
     * 1 turns a data-entry slip into a record of a very fit patient, and clamping 12 to 9 records
     * someone as terminally ill.</p>
     */
    public static Scoring fromScore(Integer score) {
        if (score == null) {
            return new Scoring(null, RefusalReason.INPUT_MISSING,
                    "No Clinical Frailty Scale score was recorded. The scale is a clinician's global"
                            + " judgement and cannot be derived from other data.",
                    CONTENT_VERSION);
        }
        if (score < MIN_SCORE || score > MAX_SCORE) {
            return new Scoring(null, RefusalReason.OUTSIDE_INSTRUMENT_RANGE,
                    "A Clinical Frailty Scale score of " + score + " is not on the scale, which runs"
                            + " from " + MIN_SCORE + " to " + MAX_SCORE + ". The value is refused rather"
                            + " than clamped, because clamping would record a plausible level that"
                            + " nobody assessed.",
                    CONTENT_VERSION);
        }
        for (ClinicalFrailtyScale level : values()) {
            if (level.score == score) {
                return new Scoring(level, null, null, CONTENT_VERSION);
            }
        }
        return new Scoring(null, RefusalReason.OUTSIDE_INSTRUMENT_RANGE,
                "No Clinical Frailty Scale level is defined for " + score + ".", CONTENT_VERSION);
    }

    /**
     * The applicability caveat for a person of this age, or null when there is none.
     *
     * <p>Returns text rather than refusing, because the scale's age limit is guidance about
     * validation, not a hard boundary of the instrument, and a geriatrician scoring a 58-year-old
     * with multiple long-term conditions may have a good reason. The caveat has to travel with the
     * score so the reason is visible to whoever reads it next.</p>
     */
    public static String applicabilityCaveat(Integer ageYears) {
        if (ageYears == null) {
            return "Age is not recorded, so it cannot be confirmed that the Clinical Frailty Scale"
                    + " applies. It was validated in adults aged " + VALIDATED_FROM_AGE_YEARS + " and over.";
        }
        if (ageYears < VALIDATED_FROM_AGE_YEARS) {
            return "The Clinical Frailty Scale was validated in adults aged " + VALIDATED_FROM_AGE_YEARS
                    + " and over, and this person is " + ageYears + ". It is also not intended for people"
                    + " of any age whose limitations come from a stable single-system disability rather"
                    + " than frailty. Record why it was used.";
        }
        return null;
    }

    /**
     * A frailty level, or a stated reason there is none.
     *
     * @param level          the scale level; null when refused
     * @param reason         machine-readable refusal code; null when scored
     * @param explanation    clinician-readable refusal text; null when scored
     * @param contentVersion the {@link #CONTENT_VERSION} that produced this result
     */
    public record Scoring(ClinicalFrailtyScale level,
                          RefusalReason reason,
                          String explanation,
                          String contentVersion) {

        /** True when a level is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return level != null;
        }
    }
}
