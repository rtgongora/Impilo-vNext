package zw.gov.mohcc.impilo.medicine.criticalcare;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sequential Organ Failure Assessment.
 *
 * <p><strong>Source.</strong> Vincent JL, Moreno R, Takala J, et al. <em>The SOFA (Sepsis-related
 * Organ Failure Assessment) score to describe organ dysfunction/failure.</em> Intensive Care Med
 * 1996;22:707–710, with the vasopressor and respiratory-support conventions in current use. The
 * primary texts are not vendored under {@code docs/reference}; the cut-points are transcribed
 * engineering seeds pending MoHCC ratification.</p>
 *
 * <p><strong>A missing organ system is not a healthy one.</strong> The routine failure mode is to
 * score unmeasured systems as zero and present the result as a SOFA. In a district hospital without
 * blood gases that turns a respiratory 4 into a 0 and reports a dying patient as having mild organ
 * dysfunction. This class refuses the total when any of the six systems is unavailable, and returns
 * the systems it could score plus a named list of those it could not, so the surface can show real
 * partial information without dressing it up as a total.</p>
 *
 * <p><strong>Respiratory points 3 and 4 require respiratory support.</strong> A PaO₂/FiO₂ below 200
 * scores 3 only if the patient is ventilated or otherwise supported; unsupported, the item caps at 2
 * however low the ratio. The support flag is therefore required, not optional, whenever a ratio in
 * that range is supplied.</p>
 *
 * <p><strong>What this class will not do.</strong> It scores six organ systems and sums them. It does
 * not diagnose sepsis — that requires the infection criterion, which is a clinical judgement outside
 * this score — does not predict this patient's mortality, and does not triage. A delta from baseline,
 * not an absolute total, is what the sepsis definitions turn on, and establishing the baseline is the
 * caller's responsibility.</p>
 */
public final class SofaScore {

    /** Bumped whenever a cut-point or scoring rule changes a published score. */
    public static final String CONTENT_VERSION = "impilo-sofa-1996-1.0.0";

    /** The highest attainable total: 4 points on each of the six systems. */
    public static final int MAX_SCORE = 24;

    /**
     * Platelet cut-points in ×10⁹/L, ordered from the 4-point band to the 1-point band. Lower is
     * worse: below 20 scores 4, below 50 scores 3, below 100 scores 2, below 150 scores 1.
     */
    private static final double[] PLATELET_THRESHOLDS_10E9_PER_L = {20, 50, 100, 150};

    /**
     * Bilirubin cut-points in µmol/L, ordered from the 4-point band down. The published table is in
     * mg/dL (12.0, 6.0, 2.0, 1.2); these are the standard µmol/L renderings, and the 4-point band
     * begins at 205 rather than 204 so that a bilirubin of exactly 204 scores the 3 the table
     * assigns it.
     */
    private static final double[] BILIRUBIN_THRESHOLDS_UMOL_PER_L = {205, 102, 33, 20};

    /**
     * Creatinine cut-points in µmol/L, ordered from the 4-point band down. As with bilirubin the
     * published table is in mg/dL (5.0, 3.5, 2.0, 1.2); the 4-point band begins at 441 so that a
     * creatinine of exactly 440 scores 3.
     */
    private static final double[] CREATININE_THRESHOLDS_UMOL_PER_L = {441, 300, 171, 110};

    private SofaScore() {
    }

    /** The six organ systems, each scored 0–4. */
    public enum OrganSystem {

        /** Oxygenation, by PaO₂/FiO₂ with the respiratory-support qualifier. */
        RESPIRATION("Respiration"),

        /** Coagulation, by platelet count. */
        COAGULATION("Coagulation"),

        /** Liver, by bilirubin. */
        LIVER("Liver"),

        /** Cardiovascular, by mean arterial pressure or vasopressor requirement. */
        CARDIOVASCULAR("Cardiovascular"),

        /** Central nervous system, by Glasgow Coma Scale. */
        CENTRAL_NERVOUS_SYSTEM("Central nervous system"),

        /** Renal, by creatinine or urine output, whichever scores worse. */
        RENAL("Renal");

        private final String label;

        OrganSystem(String label) {
            this.label = label;
        }

        /** The system name as it should be presented. */
        public String label() {
            return label;
        }
    }

    /** Which vasopressor a patient is receiving, and at what dose band. */
    public enum VasopressorSupport {

        /** No vasopressor. Cardiovascular scoring then falls to mean arterial pressure. */
        NONE,

        /** Dopamine 5 µg/kg/min or less, or any dobutamine dose. Scores 2. */
        LOW_DOSE_DOPAMINE_OR_ANY_DOBUTAMINE,

        /**
         * Dopamine above 5, or adrenaline/noradrenaline at 0.1 µg/kg/min or less. Scores 3.
         */
        MEDIUM_DOSE,

        /**
         * Dopamine above 15, or adrenaline/noradrenaline above 0.1 µg/kg/min. Scores 4.
         */
        HIGH_DOSE
    }

    /**
     * The inputs. Every field is nullable: a null field means that measurement was not available,
     * which is recorded as an unscored system rather than silently treated as normal.
     *
     * @param pao2Fio2Ratio            PaO₂/FiO₂ in mmHg; null when no gas is available
     * @param respiratorySupport       whether the patient is mechanically ventilated or otherwise
     *                                 supported. Required once {@code pao2Fio2Ratio} is below 200,
     *                                 because points 3 and 4 are only available with support
     * @param plateletsTimes10e9PerL   platelet count in ×10⁹/L
     * @param bilirubinMicromolPerLitre total bilirubin in µmol/L
     * @param meanArterialPressureMmHg mean arterial pressure in mmHg; used only when
     *                                 {@code vasopressorSupport} is {@link VasopressorSupport#NONE}
     * @param vasopressorSupport       the vasopressor band; null means the question was not answered,
     *                                 which leaves cardiovascular unscored rather than assuming none
     * @param glasgowComaScore         GCS total 3–15; null when unavailable or when a component was
     *                                 untestable, in which case CNS is left unscored
     * @param creatinineMicromolPerLitre serum creatinine in µmol/L
     * @param urineOutputMlPerDay      urine output in mL over 24 hours; scored alongside creatinine,
     *                                 with the worse of the two taken
     */
    public record Inputs(BigDecimal pao2Fio2Ratio,
                         Boolean respiratorySupport,
                         BigDecimal plateletsTimes10e9PerL,
                         BigDecimal bilirubinMicromolPerLitre,
                         BigDecimal meanArterialPressureMmHg,
                         VasopressorSupport vasopressorSupport,
                         Integer glasgowComaScore,
                         BigDecimal creatinineMicromolPerLitre,
                         BigDecimal urineOutputMlPerDay) {
    }

    /**
     * SOFA from whatever measurements are available.
     *
     * <p>Returns a total only when all six systems could be scored. Otherwise returns the scored
     * systems and names the unscored ones, refusing the total.</p>
     */
    public static Scoring of(Inputs inputs) {
        if (inputs == null) {
            return new Scoring(null, Map.of(), List.of(), RefusalReason.INPUT_MISSING,
                    "No measurements were supplied.", CONTENT_VERSION);
        }

        Map<OrganSystem, Integer> scored = new LinkedHashMap<>();
        List<String> unscored = new ArrayList<>();

        Integer respiration = respirationPoints(inputs, unscored);
        if (respiration != null) {
            scored.put(OrganSystem.RESPIRATION, respiration);
        }

        if (inputs.plateletsTimes10e9PerL() == null) {
            unscored.add("Coagulation — no platelet count");
        } else {
            scored.put(OrganSystem.COAGULATION,
                    band(inputs.plateletsTimes10e9PerL(), PLATELET_THRESHOLDS_10E9_PER_L, true));
        }

        if (inputs.bilirubinMicromolPerLitre() == null) {
            unscored.add("Liver — no bilirubin");
        } else {
            scored.put(OrganSystem.LIVER,
                    band(inputs.bilirubinMicromolPerLitre(), BILIRUBIN_THRESHOLDS_UMOL_PER_L, false));
        }

        Integer cardiovascular = cardiovascularPoints(inputs, unscored);
        if (cardiovascular != null) {
            scored.put(OrganSystem.CARDIOVASCULAR, cardiovascular);
        }

        if (inputs.glasgowComaScore() == null) {
            unscored.add("Central nervous system — no Glasgow Coma Scale total");
        } else {
            int gcs = inputs.glasgowComaScore();
            if (gcs < 3 || gcs > 15) {
                unscored.add("Central nervous system — a Glasgow Coma Scale total of " + gcs
                        + " is outside the possible range of 3 to 15");
            } else {
                scored.put(OrganSystem.CENTRAL_NERVOUS_SYSTEM,
                        gcs < 6 ? 4 : gcs < 10 ? 3 : gcs < 13 ? 2 : gcs < 15 ? 1 : 0);
            }
        }

        Integer renal = renalPoints(inputs, unscored);
        if (renal != null) {
            scored.put(OrganSystem.RENAL, renal);
        }

        if (!unscored.isEmpty()) {
            return new Scoring(null, Map.copyOf(scored), List.copyOf(unscored),
                    RefusalReason.INPUT_MISSING,
                    "SOFA needs all six organ systems and " + unscored.size() + " could not be scored."
                            + " Scoring an unmeasured system as zero would report a patient with"
                            + " unrecognised organ failure as having none. The systems that could be"
                            + " scored are returned and may be recorded individually.",
                    CONTENT_VERSION);
        }

        int total = scored.values().stream().mapToInt(Integer::intValue).sum();
        return new Scoring(total, Map.copyOf(scored), List.of(), null, null, CONTENT_VERSION);
    }

    private static Integer respirationPoints(Inputs inputs, List<String> unscored) {
        BigDecimal ratio = inputs.pao2Fio2Ratio();
        if (ratio == null) {
            unscored.add("Respiration — no PaO₂/FiO₂ ratio");
            return null;
        }
        if (ratio.signum() <= 0 || ratio.doubleValue() > 700) {
            unscored.add("Respiration — a PaO₂/FiO₂ ratio of " + ratio.toPlainString()
                    + " is outside the plausible range");
            return null;
        }

        double value = ratio.doubleValue();
        if (value >= 400) {
            return 0;
        }
        if (value >= 300) {
            return 1;
        }
        if (value >= 200) {
            return 2;
        }

        if (inputs.respiratorySupport() == null) {
            unscored.add("Respiration — the PaO₂/FiO₂ ratio is below 200, where 3 and 4 points require"
                    + " respiratory support, but whether the patient is supported was not recorded");
            return null;
        }
        if (!inputs.respiratorySupport()) {
            return 2;
        }
        return value >= 100 ? 3 : 4;
    }

    private static Integer cardiovascularPoints(Inputs inputs, List<String> unscored) {
        VasopressorSupport support = inputs.vasopressorSupport();
        if (support == null) {
            unscored.add("Cardiovascular — whether the patient is receiving a vasopressor was not"
                    + " recorded, and an unanswered question is not a 'no'");
            return null;
        }
        switch (support) {
            case HIGH_DOSE:
                return 4;
            case MEDIUM_DOSE:
                return 3;
            case LOW_DOSE_DOPAMINE_OR_ANY_DOBUTAMINE:
                return 2;
            case NONE:
            default:
                BigDecimal map = inputs.meanArterialPressureMmHg();
                if (map == null) {
                    unscored.add("Cardiovascular — no vasopressor is running, so the score depends on"
                            + " mean arterial pressure, which was not recorded");
                    return null;
                }
                if (map.signum() <= 0 || map.doubleValue() > 200) {
                    unscored.add("Cardiovascular — a mean arterial pressure of " + map.toPlainString()
                            + " mmHg is outside the plausible range");
                    return null;
                }
                return map.doubleValue() < 70 ? 1 : 0;
        }
    }

    private static Integer renalPoints(Inputs inputs, List<String> unscored) {
        BigDecimal creatinine = inputs.creatinineMicromolPerLitre();
        BigDecimal urine = inputs.urineOutputMlPerDay();
        if (creatinine == null && urine == null) {
            unscored.add("Renal — neither creatinine nor urine output");
            return null;
        }

        Integer byCreatinine = creatinine == null ? null
                : band(creatinine, CREATININE_THRESHOLDS_UMOL_PER_L, false);

        Integer byUrine = null;
        if (urine != null) {
            if (urine.signum() < 0) {
                unscored.add("Renal — a urine output of " + urine.toPlainString()
                        + " mL/day is not possible");
                return null;
            }
            double ml = urine.doubleValue();
            byUrine = ml < 200 ? 4 : ml < 500 ? 3 : 0;
        }

        if (byCreatinine == null) {
            return byUrine;
        }
        if (byUrine == null) {
            return byCreatinine;
        }
        return Math.max(byCreatinine, byUrine);
    }

    /**
     * Bands a value against four descending or ascending thresholds.
     *
     * @param thresholds four cut-points ordered from the 4-point band to the 1-point band
     * @param lowerIsWorse true when a smaller value is more abnormal, as for platelets
     */
    private static int band(BigDecimal value, double[] thresholds, boolean lowerIsWorse) {
        double v = value.setScale(6, RoundingMode.HALF_UP).doubleValue();
        for (int i = 0; i < thresholds.length; i++) {
            boolean hit = lowerIsWorse ? v < thresholds[i] : v >= thresholds[i];
            if (hit) {
                return 4 - i;
            }
        }
        return 0;
    }

    /**
     * A SOFA result, or a stated reason there is no total.
     *
     * <p>{@code systemScores} is populated even when the total is refused: those per-system scores
     * are real and may be recorded. What is refused is the claim that they add up to a SOFA.</p>
     *
     * <p>There is deliberately no severity band. SOFA is interpreted by change from a baseline, not
     * by an absolute cut-point, and a band here would invite exactly the absolute reading the score
     * does not support.</p>
     *
     * @param total            the sum 0–{@link #MAX_SCORE}; null unless all six systems were scored
     * @param systemScores     the per-system points that could be determined; never null, possibly
     *                         partial
     * @param unscoredSystems  clinician-readable reasons for each system that could not be scored;
     *                         empty when a total was produced
     * @param reason           machine-readable refusal code; null when a total was computed
     * @param explanation      clinician-readable refusal text; null when a total was computed
     * @param contentVersion   the {@link #CONTENT_VERSION} that produced this result
     */
    public record Scoring(Integer total,
                          Map<OrganSystem, Integer> systemScores,
                          List<String> unscoredSystems,
                          RefusalReason reason,
                          String explanation,
                          String contentVersion) {

        /** True when a total is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return total != null;
        }
    }
}
