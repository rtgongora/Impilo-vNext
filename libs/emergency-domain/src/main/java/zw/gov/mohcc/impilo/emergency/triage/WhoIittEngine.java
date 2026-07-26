package zw.gov.mohcc.impilo.emergency.triage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates the WHO/ICRC/MSF Interagency Integrated Triage Tool.
 *
 * <p>Three steps, in the chart's order, and the order is not an implementation detail: a patient who
 * meets a red criterion is red regardless of any yellow criterion, and step 3 is reached only when
 * neither fired.
 *
 * <ol>
 *   <li><b>Red criteria</b> → high acuity resuscitation area immediately.</li>
 *   <li><b>Yellow criteria</b> → clinical treatment area.</li>
 *   <li><b>High-risk vital signs</b> → "up-triage or immediate review by supervising clinician",
 *       in the chart's own words. Otherwise → low acuity or waiting area.</li>
 * </ol>
 *
 * <p><b>The engine never returns GREEN on an unassessed patient.</b> GREEN is a positive claim —
 * that every red criterion, every yellow criterion and every high-risk vital sign was checked and
 * none was present. Where the assessment is too thin to support that claim the result is
 * {@link TriagePriority#NOT_TRIAGEABLE}, carrying the list of what was not assessed. The engine this
 * replaces did the opposite twice over: unmeasured vitals were compared as zero behind
 * {@code hr > 0 &&} guards so an unmeasured patient scored as not-in-danger, and a patient with no
 * triage data at all was written down as acuity 3.
 *
 * <p>An unknown age raises rather than defaulting, because age selects the chart and the two charts
 * disagree on both criteria and thresholds.
 */
public final class WhoIittEngine {

    /**
     * The minimum a triage assessment must cover before GREEN is a defensible answer. These are the
     * step-3 vital signs plus responsiveness — the smallest set that distinguishes "looked well on
     * assessment" from "nobody looked".
     */
    private static final List<String> MINIMUM_VITALS =
            List.of(IittChart.HR, IittChart.RR, IittChart.TEMP, IittChart.SPO2, IittChart.AVPU);

    private WhoIittEngine() {
    }

    public static TriageResult evaluate(TriageFacts facts) {
        Integer ageDays = facts.ageDays();
        if (ageDays == null) {
            throw new IllegalArgumentException(
                    "Age is required to triage: the IITT has separate charts for age >=12 and age <12 "
                            + "which differ in both criteria and vital-sign thresholds, and there is no "
                            + "safe default chart.");
        }
        if (ageDays < 0) {
            throw new IllegalArgumentException("Age in days cannot be negative: " + ageDays);
        }

        boolean adult = IittChart.isAdultChart(ageDays);
        String chart = adult ? "IITT_ADULT_AGE_12_AND_OVER" : "IITT_PAEDIATRIC_AGE_UNDER_12";

        List<IittCriterion> red = adult ? IittChart.adultRed() : IittChart.paediatricRed();
        List<IittCriterion> yellow = adult ? IittChart.adultYellow() : IittChart.paediatricYellow();

        List<IittCriterion> firedRed = red.stream().filter(c -> c.fires(facts)).toList();
        if (!firedRed.isEmpty()) {
            return new TriageResult(TriagePriority.RED, chart, firedRed, List.of(),
                    unassessed(facts, red, yellow), false);
        }

        List<IittCriterion> firedYellow = yellow.stream().filter(c -> c.fires(facts)).toList();
        if (!firedYellow.isEmpty()) {
            return new TriageResult(TriagePriority.YELLOW, chart, firedYellow, List.of(),
                    unassessed(facts, red, yellow), false);
        }

        List<String> highRisk = highRiskVitalSigns(facts, adult, ageDays);
        if (!highRisk.isEmpty()) {
            // The chart does not assign a colour here — it says up-triage or get a clinician. We
            // report YELLOW as the holding destination with the review flag set, rather than
            // silently promoting to RED, because the chart gives that judgement to a human.
            return new TriageResult(TriagePriority.YELLOW, chart, List.of(), highRisk,
                    unassessed(facts, red, yellow), true);
        }

        List<String> notAssessed = unassessed(facts, red, yellow);
        if (!notAssessed.isEmpty()) {
            return new TriageResult(TriagePriority.NOT_TRIAGEABLE, chart, List.of(), List.of(),
                    notAssessed, false);
        }

        return new TriageResult(TriagePriority.GREEN, chart, List.of(), List.of(), List.of(), false);
    }

    /**
     * Step 3. An unmeasured vital sign is NOT a normal one and never contributes a negative — it is
     * reported as unassessed instead, which is what prevents GREEN.
     */
    private static List<String> highRiskVitalSigns(TriageFacts facts, boolean adult, int ageDays) {
        List<String> hits = new ArrayList<>();

        if (facts.vitalMeasured(IittChart.TEMP)) {
            double t = facts.vital(IittChart.TEMP);
            if (t < 36 || t > 39) hits.add("Temp <36 or >39 (" + t + ")");
        }
        if (facts.vitalMeasured(IittChart.SPO2)) {
            double s = facts.vital(IittChart.SPO2);
            if (s < 92) hits.add("SpO2 <92% (" + s + ")");
        }
        if (facts.vitalMeasured(IittChart.AVPU)) {
            double a = facts.vital(IittChart.AVPU);
            if (a > 0) hits.add("AVPU other than A");
        }

        if (adult) {
            if (facts.vitalMeasured(IittChart.HR)) {
                double hr = facts.vital(IittChart.HR);
                if (hr < 60 || hr > 130) hits.add("HR <60 or >130 (" + hr + ")");
            }
            if (facts.vitalMeasured(IittChart.RR)) {
                double rr = facts.vital(IittChart.RR);
                if (rr < 10 || rr > 30) hits.add("RR <10 or >30 (" + rr + ")");
            }
        } else {
            PaediatricVitalBand band = PaediatricVitalBand.forAgeDays(ageDays);
            if (facts.vitalMeasured(IittChart.HR)) {
                double hr = facts.vital(IittChart.HR);
                if (hr > band.hrHigh() || hr < band.hrLow()) {
                    hits.add("HR outside " + band.hrLow() + "-" + band.hrHigh()
                            + " for " + band.label() + " (" + hr + ")");
                }
            }
            if (facts.vitalMeasured(IittChart.RR)) {
                double rr = facts.vital(IittChart.RR);
                if (rr > band.rrHigh() || rr < band.rrLow()) {
                    hits.add("RR outside " + band.rrLow() + "-" + band.rrHigh()
                            + " for " + band.label() + " (" + rr + ")");
                }
            }
        }
        return List.copyOf(hits);
    }

    /**
     * What was not assessed: every step-3 vital that was not measured, plus every sign named by a
     * criterion on this chart that was never recorded either way.
     */
    private static List<String> unassessed(TriageFacts facts,
                                           List<IittCriterion> red,
                                           List<IittCriterion> yellow) {
        Set<String> missing = new LinkedHashSet<>();
        for (String v : MINIMUM_VITALS) {
            if (!facts.vitalMeasured(v)) missing.add(v);
        }
        for (List<IittCriterion> set : List.of(red, yellow)) {
            for (IittCriterion c : set) {
                for (String s : c.requiredSigns()) {
                    if (facts.sign(s) == TriageFacts.Ternary.UNKNOWN) missing.add(s);
                }
            }
        }
        return List.copyOf(missing);
    }
}
