package zw.gov.mohcc.impilo.emergency.triage;

import java.util.ArrayList;
import java.util.List;

import static zw.gov.mohcc.impilo.emergency.triage.TriageFacts.Ternary;

/**
 * The two published IITT charts, encoded.
 *
 * <p>Transcribed from the vendored, hashed PDFs in
 * {@code docs/reference/who-emergency-care-toolkit/} and verified line by line against the
 * {@code pdftotext -layout} extractions committed beside them. The charts carry no version number or
 * date, so the SHA-256 recorded in that folder's README is the version identity.
 *
 * <p><b>The paediatric chart is not a scaled adult chart.</b> Three differences are load-bearing and
 * are encoded as such:
 * <ol>
 *   <li>the adult numeric heart-rate red criterion ({@code HR <50 or >150}) <b>does not exist</b> on
 *       the paediatric chart — paediatric heart rate appears only in the age-banded step-3 vitals;</li>
 *   <li>the adult disability red criterion is <b>any two of</b> four findings, while the paediatric
 *       one is a <b>conjunction</b> — altered mental status <i>with</i> stiff neck, hypothermia or
 *       fever. Different logical shapes, not different wording;</li>
 *   <li>the paediatric "any two of" cluster is a <b>dehydration</b> cluster under CIRCULATION
 *       (lethargy, sunken eyes, very slow skin pinch, drinks poorly), not the adult's neurological
 *       one.</li>
 * </ol>
 *
 * <p><b>Two distinct heart-rate bands exist on the adult chart and must never be merged.</b>
 * {@code HR <50 or >150} is a RED criterion in its own right; {@code HR <60 or >130} is a step-3
 * high-risk vital sign that triggers up-triage or immediate clinician review. During transcription a
 * web search returned the step-3 numbers as the RED criterion — encoding that would have moved the
 * immediate-resuscitation threshold to a materially less abnormal heart rate.
 */
public final class IittChart {

    // ── Sign codes ────────────────────────────────────────────────────────────────────────────
    public static final String UNRESPONSIVE = "unresponsive";
    public static final String STRIDOR = "stridor";
    public static final String RESPIRATORY_DISTRESS = "respiratory_distress";
    public static final String CENTRAL_CYANOSIS = "central_cyanosis";
    public static final String CAPILLARY_REFILL_OVER_3S = "capillary_refill_over_3s";
    public static final String WEAK_AND_FAST_PULSE = "weak_and_fast_pulse";
    public static final String HEAVY_BLEEDING = "heavy_bleeding";
    public static final String COLD_EXTREMITIES = "cold_extremities";
    public static final String ACTIVE_CONVULSIONS = "active_convulsions";
    public static final String ALTERED_MENTAL_STATUS = "altered_mental_status";
    public static final String HYPOTHERMIA_OR_FEVER = "hypothermia_or_fever";
    public static final String STIFF_NECK = "stiff_neck";
    public static final String HEADACHE = "headache";
    public static final String HYPOGLYCAEMIA = "hypoglycaemia";
    public static final String LETHARGY = "lethargy";
    public static final String SUNKEN_EYES = "sunken_eyes";
    public static final String VERY_SLOW_SKIN_PINCH = "very_slow_skin_pinch";
    public static final String DRINKS_POORLY = "drinks_poorly";
    public static final String HIGH_RISK_TRAUMA = "high_risk_trauma";
    public static final String POISONING_OR_EXPOSURE = "poisoning_or_exposure";
    public static final String THREATENED_LIMB = "threatened_limb";
    public static final String SNAKE_BITE = "snake_bite";
    public static final String CHEST_OR_ABDO_PAIN_OVER_50 = "acute_chest_or_abdominal_pain_over_50";
    public static final String ECG_ACUTE_ISCHAEMIA = "ecg_acute_ischaemia";
    public static final String VIOLENT_OR_AGGRESSIVE = "violent_or_aggressive";
    public static final String INFANT_UNDER_8_DAYS = "infant_under_8_days";
    public static final String AGE_UNDER_2_MONTHS_ABNORMAL_TEMP = "age_under_2_months_abnormal_temp";
    public static final String ACUTE_TESTICULAR_PAIN_OR_PRIAPISM = "acute_testicular_pain_or_priapism";

    // Pregnancy red cluster
    public static final String PREG_HEAVY_BLEEDING = "pregnant_heavy_bleeding";
    public static final String PREG_SEVERE_ABDO_PAIN = "pregnant_severe_abdominal_pain";
    public static final String PREG_SEIZURES_OR_ALTERED = "pregnant_seizures_or_altered_mental_status";
    public static final String PREG_SEVERE_HEADACHE = "pregnant_severe_headache";
    public static final String PREG_VISUAL_CHANGES = "pregnant_visual_changes";
    public static final String PREG_SEVERE_HYPERTENSION = "pregnant_sbp_160_or_dbp_110";
    public static final String PREG_ACTIVE_LABOUR = "pregnant_active_labour";
    public static final String PREG_TRAUMA = "pregnant_trauma";

    // Yellow signs
    public static final String MOUTH_THROAT_NECK_SWELLING = "mouth_throat_neck_swelling";
    public static final String WHEEZING = "wheezing";
    public static final String VOMITS_EVERYTHING = "vomits_everything";
    public static final String ONGOING_DIARRHOEA = "ongoing_diarrhoea";
    public static final String UNABLE_TO_FEED_OR_DRINK = "unable_to_feed_or_drink";
    public static final String SEVERE_PALLOR = "severe_pallor";
    public static final String ONGOING_BLEEDING = "ongoing_bleeding";
    public static final String RECENT_FAINTING = "recent_fainting";
    public static final String DEHYDRATION = "dehydration";
    public static final String AGITATION = "agitation";
    public static final String RESTLESS_IRRITABLE_OR_LETHARGIC = "restless_irritable_or_lethargic";
    public static final String ACUTE_GENERAL_WEAKNESS = "acute_general_weakness";
    public static final String ACUTE_FOCAL_NEURO = "acute_focal_neurologic_complaint";
    public static final String ACUTE_VISUAL_DISTURBANCE = "acute_visual_disturbance";
    public static final String SEVERE_PAIN = "severe_pain";
    public static final String NEW_RASH_WORSENING_OR_PEELING = "new_rash_worsening_or_peeling";
    public static final String VISIBLE_LIMB_DEFORMITY = "visible_acute_limb_deformity";
    public static final String OPEN_FRACTURE = "open_fracture";
    public static final String SUSPECTED_DISLOCATION = "suspected_dislocation";
    public static final String OTHER_TRAUMA_OR_BURNS = "other_trauma_or_burns";
    public static final String URGENT_SURGICAL_DIAGNOSIS = "known_diagnosis_requiring_urgent_surgery";
    public static final String SEXUAL_ASSAULT = "sexual_assault";
    public static final String UNABLE_TO_PASS_URINE = "unable_to_pass_urine";
    public static final String TIME_SENSITIVE_PROPHYLAXIS = "exposure_requiring_time_sensitive_prophylaxis";
    public static final String PREGNANCY_REFERRED_FOR_COMPLICATIONS = "pregnancy_referred_for_complications";
    public static final String INFANT_8_DAYS_TO_6_MONTHS = "infant_8_days_to_6_months";
    public static final String MALNUTRITION_WASTING_OR_OEDEMA = "malnutrition_wasting_or_bilateral_oedema";

    // ── Vital codes ───────────────────────────────────────────────────────────────────────────
    public static final String HR = "heart_rate";
    public static final String RR = "respiratory_rate";
    public static final String TEMP = "temperature_c";
    public static final String SPO2 = "spo2_percent";
    /** AVPU as 0=Alert, 1=Voice, 2=Pain, 3=Unresponsive. Anything other than Alert is high-risk. */
    public static final String AVPU = "avpu";

    private IittChart() {
    }

    /** True for the adult chart (age >= 12 years). Age is required; see {@link WhoIittEngine}. */
    public static boolean isAdultChart(int ageDays) {
        return ageDays >= 12 * 365;
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    //  ADULT — Age >= 12
    // ─────────────────────────────────────────────────────────────────────────────────────────

    public static List<IittCriterion> adultRed() {
        List<IittCriterion> c = new ArrayList<>();
        c.add(sign("ADULT_RED_UNRESPONSIVE", TriagePriority.RED, "GENERAL", "Unresponsive", UNRESPONSIVE));
        c.add(sign("ADULT_RED_STRIDOR", TriagePriority.RED, "AIRWAY_BREATHING", "Stridor", STRIDOR));
        c.add(anyOf("ADULT_RED_RESP_DISTRESS_OR_CYANOSIS", TriagePriority.RED, "AIRWAY_BREATHING",
                "Respiratory distress or central cyanosis", RESPIRATORY_DISTRESS, CENTRAL_CYANOSIS));
        c.add(sign("ADULT_RED_CAP_REFILL", TriagePriority.RED, "CIRCULATION", "Capillary refill >3 sec", CAPILLARY_REFILL_OVER_3S));
        c.add(sign("ADULT_RED_WEAK_FAST_PULSE", TriagePriority.RED, "CIRCULATION", "Weak and fast pulse", WEAK_AND_FAST_PULSE));
        c.add(sign("ADULT_RED_HEAVY_BLEEDING", TriagePriority.RED, "CIRCULATION", "Heavy bleeding", HEAVY_BLEEDING));
        // The RED band. Distinct from the step-3 high-risk band of <60/>130 — never merge them.
        c.add(new IittCriterion("ADULT_RED_HR_50_150", TriagePriority.RED, "CIRCULATION",
                "HR <50 or >150", List.of(),
                f -> f.vitalMeasured(HR) && (f.vital(HR) < 50 || f.vital(HR) > 150)));
        c.add(sign("ADULT_RED_ACTIVE_CONVULSIONS", TriagePriority.RED, "DISABILITY", "Active convulsions", ACTIVE_CONVULSIONS));
        c.add(new IittCriterion("ADULT_RED_ANY_TWO_NEURO", TriagePriority.RED, "DISABILITY",
                "Any two of: altered mental status, hypothermia or fever, stiff neck, headache",
                List.of(ALTERED_MENTAL_STATUS, HYPOTHERMIA_OR_FEVER, STIFF_NECK, HEADACHE),
                f -> countTrue(f, ALTERED_MENTAL_STATUS, HYPOTHERMIA_OR_FEVER, STIFF_NECK, HEADACHE) >= 2));
        c.add(sign("ADULT_RED_HYPOGLYCAEMIA", TriagePriority.RED, "DISABILITY", "Hypoglycaemia", HYPOGLYCAEMIA));
        c.add(sign("ADULT_RED_HIGH_RISK_TRAUMA", TriagePriority.RED, "OTHER", "High-risk trauma", HIGH_RISK_TRAUMA));
        c.add(sign("ADULT_RED_POISONING", TriagePriority.RED, "OTHER", "Poisoning/ingestion or dangerous chemical exposure", POISONING_OR_EXPOSURE));
        c.add(sign("ADULT_RED_THREATENED_LIMB", TriagePriority.RED, "OTHER", "Threatened limb", THREATENED_LIMB));
        c.add(sign("ADULT_RED_SNAKE_BITE", TriagePriority.RED, "OTHER", "Snake bite", SNAKE_BITE));
        c.add(sign("ADULT_RED_CHEST_ABDO_PAIN_OVER_50", TriagePriority.RED, "OTHER", "Acute chest or abdominal pain (>50 years old)", CHEST_OR_ABDO_PAIN_OVER_50));
        c.add(sign("ADULT_RED_ECG_ISCHAEMIA", TriagePriority.RED, "OTHER", "ECG with acute ischaemia (if done)", ECG_ACUTE_ISCHAEMIA));
        c.add(sign("ADULT_RED_VIOLENT", TriagePriority.RED, "OTHER", "Violent or aggressive", VIOLENT_OR_AGGRESSIVE));
        for (String[] p : new String[][]{
                {"ADULT_RED_PREG_HEAVY_BLEEDING", "Heavy bleeding", PREG_HEAVY_BLEEDING},
                {"ADULT_RED_PREG_SEVERE_ABDO_PAIN", "Severe abdominal pain", PREG_SEVERE_ABDO_PAIN},
                {"ADULT_RED_PREG_SEIZURES", "Seizures or altered mental status", PREG_SEIZURES_OR_ALTERED},
                {"ADULT_RED_PREG_SEVERE_HEADACHE", "Severe headache", PREG_SEVERE_HEADACHE},
                {"ADULT_RED_PREG_VISUAL_CHANGES", "Visual changes", PREG_VISUAL_CHANGES},
                {"ADULT_RED_PREG_HYPERTENSION", "SBP >=160 or DBP >=110", PREG_SEVERE_HYPERTENSION},
                {"ADULT_RED_PREG_ACTIVE_LABOUR", "Active labour", PREG_ACTIVE_LABOUR},
                {"ADULT_RED_PREG_TRAUMA", "Trauma", PREG_TRAUMA}}) {
            c.add(sign(p[0], TriagePriority.RED, "PREGNANT", p[1], p[2]));
        }
        return List.copyOf(c);
    }

    public static List<IittCriterion> adultYellow() {
        List<IittCriterion> c = new ArrayList<>();
        c.add(sign("ADULT_YEL_SWELLING", TriagePriority.YELLOW, "AIRWAY_BREATHING", "Any swelling/mass of mouth, throat or neck", MOUTH_THROAT_NECK_SWELLING));
        c.add(sign("ADULT_YEL_WHEEZING", TriagePriority.YELLOW, "AIRWAY_BREATHING", "Wheezing (no red criteria)", WHEEZING));
        c.add(anyOf("ADULT_YEL_VOMIT_OR_DIARRHOEA", TriagePriority.YELLOW, "CIRCULATION", "Vomits everything or ongoing diarrhoea", VOMITS_EVERYTHING, ONGOING_DIARRHOEA));
        c.add(sign("ADULT_YEL_UNABLE_TO_FEED", TriagePriority.YELLOW, "CIRCULATION", "Unable to feed or drink", UNABLE_TO_FEED_OR_DRINK));
        c.add(sign("ADULT_YEL_SEVERE_PALLOR", TriagePriority.YELLOW, "CIRCULATION", "Severe pallor (no red criteria)", SEVERE_PALLOR));
        c.add(sign("ADULT_YEL_ONGOING_BLEEDING", TriagePriority.YELLOW, "CIRCULATION", "Ongoing bleeding (no red criteria)", ONGOING_BLEEDING));
        c.add(sign("ADULT_YEL_RECENT_FAINTING", TriagePriority.YELLOW, "CIRCULATION", "Recent fainting", RECENT_FAINTING));
        c.add(anyOf("ADULT_YEL_ALTERED_OR_AGITATION", TriagePriority.YELLOW, "DISABILITY", "Altered mental status or agitation (no red criteria)", ALTERED_MENTAL_STATUS, AGITATION));
        c.add(sign("ADULT_YEL_GENERAL_WEAKNESS", TriagePriority.YELLOW, "DISABILITY", "Acute general weakness", ACUTE_GENERAL_WEAKNESS));
        c.add(sign("ADULT_YEL_FOCAL_NEURO", TriagePriority.YELLOW, "DISABILITY", "Acute focal neurologic complaint", ACUTE_FOCAL_NEURO));
        c.add(sign("ADULT_YEL_VISUAL_DISTURBANCE", TriagePriority.YELLOW, "DISABILITY", "Acute visual disturbance", ACUTE_VISUAL_DISTURBANCE));
        c.add(sign("ADULT_YEL_SEVERE_PAIN", TriagePriority.YELLOW, "DISABILITY", "Severe pain (no red criteria)", SEVERE_PAIN));
        for (String[] p : new String[][]{
                {"ADULT_YEL_NEW_RASH", "New rash worsening over hours or peeling (no red criteria)", NEW_RASH_WORSENING_OR_PEELING},
                {"ADULT_YEL_LIMB_DEFORMITY", "Visible acute limb deformity", VISIBLE_LIMB_DEFORMITY},
                {"ADULT_YEL_OPEN_FRACTURE", "Open fracture", OPEN_FRACTURE},
                {"ADULT_YEL_DISLOCATION", "Suspected dislocation", SUSPECTED_DISLOCATION},
                {"ADULT_YEL_OTHER_TRAUMA_BURNS", "Other trauma/burns (no red criteria)", OTHER_TRAUMA_OR_BURNS},
                {"ADULT_YEL_URGENT_SURGICAL", "Known diagnosis requiring urgent surgical intervention", URGENT_SURGICAL_DIAGNOSIS},
                {"ADULT_YEL_SEXUAL_ASSAULT", "Sexual assault", SEXUAL_ASSAULT},
                {"ADULT_YEL_TESTICULAR_PAIN", "Acute testicular/scrotal pain or priapism", ACUTE_TESTICULAR_PAIN_OR_PRIAPISM},
                {"ADULT_YEL_UNABLE_TO_PASS_URINE", "Unable to pass urine", UNABLE_TO_PASS_URINE},
                {"ADULT_YEL_PROPHYLAXIS", "Exposure requiring time-sensitive prophylaxis (eg. animal bite, needlestick)", TIME_SENSITIVE_PROPHYLAXIS},
                {"ADULT_YEL_PREGNANCY_REFERRED", "Pregnancy, referred for complications", PREGNANCY_REFERRED_FOR_COMPLICATIONS}}) {
            c.add(sign(p[0], TriagePriority.YELLOW, "OTHER", p[1], p[2]));
        }
        return List.copyOf(c);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    //  PAEDIATRIC — Age < 12
    // ─────────────────────────────────────────────────────────────────────────────────────────

    public static List<IittCriterion> paediatricRed() {
        List<IittCriterion> c = new ArrayList<>();
        c.add(sign("PAED_RED_UNRESPONSIVE", TriagePriority.RED, "GENERAL", "Unresponsive", UNRESPONSIVE));
        c.add(sign("PAED_RED_STRIDOR", TriagePriority.RED, "AIRWAY_BREATHING", "Stridor", STRIDOR));
        c.add(anyOf("PAED_RED_RESP_DISTRESS_OR_CYANOSIS", TriagePriority.RED, "AIRWAY_BREATHING",
                "Respiratory distress or central cyanosis", RESPIRATORY_DISTRESS, CENTRAL_CYANOSIS));
        c.add(sign("PAED_RED_CAP_REFILL", TriagePriority.RED, "CIRCULATION", "Capillary refill >3 sec", CAPILLARY_REFILL_OVER_3S));
        c.add(sign("PAED_RED_WEAK_FAST_PULSE", TriagePriority.RED, "CIRCULATION", "Weak and fast pulse", WEAK_AND_FAST_PULSE));
        c.add(sign("PAED_RED_HEAVY_BLEEDING", TriagePriority.RED, "CIRCULATION", "Heavy bleeding", HEAVY_BLEEDING));
        c.add(sign("PAED_RED_COLD_EXTREMITIES", TriagePriority.RED, "CIRCULATION", "Cold extremities", COLD_EXTREMITIES));
        // Paediatric "any two of" is a DEHYDRATION cluster under CIRCULATION, not the adult
        // neurological one. Same shape, different findings, different chart section.
        c.add(new IittCriterion("PAED_RED_ANY_TWO_DEHYDRATION", TriagePriority.RED, "CIRCULATION",
                "Any two of: lethargy, sunken eyes, very slow skin pinch, drinks poorly",
                List.of(LETHARGY, SUNKEN_EYES, VERY_SLOW_SKIN_PINCH, DRINKS_POORLY),
                f -> countTrue(f, LETHARGY, SUNKEN_EYES, VERY_SLOW_SKIN_PINCH, DRINKS_POORLY) >= 2));
        c.add(sign("PAED_RED_ACTIVE_CONVULSIONS", TriagePriority.RED, "DISABILITY", "Active convulsions", ACTIVE_CONVULSIONS));
        // A CONJUNCTION, not "any two of". Altered mental status alone does not fire this.
        c.add(new IittCriterion("PAED_RED_ALTERED_WITH_MENINGISM", TriagePriority.RED, "DISABILITY",
                "Altered mental status (confused, restless, continuously irritable or lethargic) with stiff neck, hypothermia or fever",
                List.of(ALTERED_MENTAL_STATUS, STIFF_NECK, HYPOTHERMIA_OR_FEVER),
                f -> f.sign(ALTERED_MENTAL_STATUS).isTrue()
                        && (f.sign(STIFF_NECK).isTrue() || f.sign(HYPOTHERMIA_OR_FEVER).isTrue())));
        c.add(sign("PAED_RED_HYPOGLYCAEMIA", TriagePriority.RED, "DISABILITY", "Hypoglycaemia (if known)", HYPOGLYCAEMIA));
        c.add(sign("PAED_RED_INFANT_UNDER_8_DAYS", TriagePriority.RED, "OTHER", "Any infant <8 days old", INFANT_UNDER_8_DAYS));
        c.add(sign("PAED_RED_UNDER_2M_ABNORMAL_TEMP", TriagePriority.RED, "OTHER", "Age <2 months and temp <36 or >39C", AGE_UNDER_2_MONTHS_ABNORMAL_TEMP));
        c.add(sign("PAED_RED_HIGH_RISK_TRAUMA", TriagePriority.RED, "OTHER", "High-risk trauma", HIGH_RISK_TRAUMA));
        c.add(sign("PAED_RED_THREATENED_LIMB", TriagePriority.RED, "OTHER", "Threatened limb", THREATENED_LIMB));
        c.add(sign("PAED_RED_TESTICULAR_PAIN", TriagePriority.RED, "OTHER", "Acute testicular/scrotal pain or priapism", ACUTE_TESTICULAR_PAIN_OR_PRIAPISM));
        c.add(sign("PAED_RED_SNAKE_BITE", TriagePriority.RED, "OTHER", "Snake bite", SNAKE_BITE));
        c.add(sign("PAED_RED_POISONING", TriagePriority.RED, "OTHER", "Poisoning/ingestion or dangerous chemical exposure", POISONING_OR_EXPOSURE));
        // "Pregnant with adult red criteria" — the adult pregnancy cluster applies to a pregnant child.
        c.add(new IittCriterion("PAED_RED_PREGNANT_WITH_ADULT_RED", TriagePriority.RED, "OTHER",
                "Pregnant with adult red criteria", List.of(),
                f -> f.pregnant().isTrue() && anyPregnancyRedFires(f)));
        return List.copyOf(c);
    }

    public static List<IittCriterion> paediatricYellow() {
        List<IittCriterion> c = new ArrayList<>();
        c.add(sign("PAED_YEL_SWELLING", TriagePriority.YELLOW, "AIRWAY_BREATHING", "Any swelling/mass of mouth, throat or neck", MOUTH_THROAT_NECK_SWELLING));
        c.add(sign("PAED_YEL_WHEEZING", TriagePriority.YELLOW, "AIRWAY_BREATHING", "Wheezing (no red criteria)", WHEEZING));
        c.add(sign("PAED_YEL_UNABLE_TO_FEED", TriagePriority.YELLOW, "CIRCULATION", "Unable to feed or drink", UNABLE_TO_FEED_OR_DRINK));
        c.add(sign("PAED_YEL_VOMITS_EVERYTHING", TriagePriority.YELLOW, "CIRCULATION", "Vomits everything", VOMITS_EVERYTHING));
        c.add(sign("PAED_YEL_ONGOING_DIARRHOEA", TriagePriority.YELLOW, "CIRCULATION", "Ongoing diarrhoea", ONGOING_DIARRHOEA));
        c.add(sign("PAED_YEL_DEHYDRATION", TriagePriority.YELLOW, "CIRCULATION", "Dehydration", DEHYDRATION));
        c.add(sign("PAED_YEL_SEVERE_PALLOR", TriagePriority.YELLOW, "CIRCULATION", "Severe pallor (no red criteria)", SEVERE_PALLOR));
        c.add(sign("PAED_YEL_RESTLESS_IRRITABLE", TriagePriority.YELLOW, "DISABILITY", "Restless, continuously irritable or lethargy", RESTLESS_IRRITABLE_OR_LETHARGIC));
        c.add(sign("PAED_YEL_SEVERE_PAIN", TriagePriority.YELLOW, "DISABILITY", "Severe pain", SEVERE_PAIN));
        for (String[] p : new String[][]{
                {"PAED_YEL_INFANT_8D_6M", "Any infant 8 days to 6 months old", INFANT_8_DAYS_TO_6_MONTHS},
                {"PAED_YEL_MALNUTRITION", "Malnutrition with visible severe wasting OR oedema of both feet", MALNUTRITION_WASTING_OR_OEDEMA},
                {"PAED_YEL_TRAUMA_BURN", "Trauma/burn (no red criteria)", OTHER_TRAUMA_OR_BURNS},
                {"PAED_YEL_SEXUAL_ASSAULT", "Sexual assault", SEXUAL_ASSAULT},
                {"PAED_YEL_URGENT_SURGICAL", "Known diagnosis requiring urgent surgical intervention", URGENT_SURGICAL_DIAGNOSIS},
                {"PAED_YEL_NEW_RASH", "New rash worsening over hours or peeling (no red criteria)", NEW_RASH_WORSENING_OR_PEELING},
                {"PAED_YEL_PROPHYLAXIS", "Exposure requiring time-sensitive prophylaxis (e.g. animal bite)", TIME_SENSITIVE_PROPHYLAXIS},
                {"PAED_YEL_PREGNANCY", "Pregnancy (no red criteria)", PREGNANCY_REFERRED_FOR_COMPLICATIONS},
                {"PAED_YEL_HEADACHE", "Headache (no red criteria)", HEADACHE}}) {
            c.add(sign(p[0], TriagePriority.YELLOW, "OTHER", p[1], p[2]));
        }
        return List.copyOf(c);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private static IittCriterion sign(String code, TriagePriority tier, String group, String label, String signCode) {
        return new IittCriterion(code, tier, group, label, List.of(signCode),
                f -> f.sign(signCode).isTrue());
    }

    private static IittCriterion anyOf(String code, TriagePriority tier, String group, String label, String... signCodes) {
        return new IittCriterion(code, tier, group, label, List.of(signCodes),
                f -> {
                    for (String s : signCodes) {
                        if (f.sign(s).isTrue()) return true;
                    }
                    return false;
                });
    }

    private static int countTrue(TriageFacts f, String... codes) {
        int n = 0;
        for (String c : codes) {
            if (f.sign(c).isTrue()) n++;
        }
        return n;
    }

    private static boolean anyPregnancyRedFires(TriageFacts f) {
        return f.sign(PREG_HEAVY_BLEEDING).isTrue() || f.sign(PREG_SEVERE_ABDO_PAIN).isTrue()
                || f.sign(PREG_SEIZURES_OR_ALTERED).isTrue() || f.sign(PREG_SEVERE_HEADACHE).isTrue()
                || f.sign(PREG_VISUAL_CHANGES).isTrue() || f.sign(PREG_SEVERE_HYPERTENSION).isTrue()
                || f.sign(PREG_ACTIVE_LABOUR).isTrue() || f.sign(PREG_TRAUMA).isTrue();
    }
}
