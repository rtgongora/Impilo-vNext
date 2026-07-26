package zw.gov.mohcc.impilo.clinical.imam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Governed content for the integrated management of acute malnutrition.
 *
 * <p>Admission routes, discharge criteria, review cadence, the defaulter definition and the
 * therapeutic-food ration are national programme policy. They change on a protocol revision, not on
 * a code change, so they live here as data a clinician can read as a diff.</p>
 *
 * <p>The one thing worth understanding before editing: {@code sustainedConsecutiveVisits} is not a
 * tuning parameter. A single arm circumference at or above the cut-off can be a mis-measurement, a
 * tape held differently or a different measurer, and discharging on it sends a still-wasted child
 * home with their treatment stopped. Lowering it to 1 changes what "cured" means.</p>
 */
public record ImamProgrammeContent(
        String packId,
        String version,
        String approvalStatus,
        String adaptationAuthority,
        String provenance,
        List<Programme> programmes,
        List<Escalation> escalations,
        List<Outcome> outcomes,
        List<TestCase> testCases) {

    public Programme programme(String code) {
        if (code == null) {
            return null;
        }
        return programmes.stream().filter(p -> p.code().equalsIgnoreCase(code.trim())).findFirst().orElse(null);
    }

    /** The programme that admits a given IMNCI acute-malnutrition classification, or null. */
    public Programme forClassification(String classificationCode) {
        if (classificationCode == null) {
            return null;
        }
        String code = classificationCode.trim().toUpperCase(java.util.Locale.ROOT);
        return programmes.stream()
                .filter(p -> p.admitsClassifications().stream().anyMatch(c -> c.equalsIgnoreCase(code)))
                .findFirst()
                .orElse(null);
    }

    /**
     * One treatment programme.
     *
     * @param reviewIntervalDays        how often the child is seen; the defaulter clock counts in
     *                                  these, so it is the same number in both meanings
     * @param attendanceGraceDays       a child a day late has not missed a visit; without this every
     *                                  programme reports its whole caseload as missing appointments
     * @param traceAfterMissedVisits    tracing starts <em>before</em> the child is a defaulter — by
     *                                  the time a child meets the defaulter definition, the window in
     *                                  which a home visit would have brought them back has closed
     * @param defaulterMissedVisits     consecutive missed scheduled reviews that define a defaulter
     * @param nonResponderAfterDays     maximum length of stay before failure to recover is itself the
     *                                  finding and needs investigation rather than more of the same
     */
    public record Programme(
            String code,
            String name,
            String treats,
            List<String> admitsClassifications,
            List<AdmissionCriterion> admissionCriteria,
            int reviewIntervalDays,
            int attendanceGraceDays,
            int traceAfterMissedVisits,
            int defaulterMissedVisits,
            int nonResponderAfterDays,
            BigDecimal targetWeightGainGramsPerKgPerDay,
            TherapeuticFood therapeuticFood,
            DischargeCriteria dischargeCriteria) {
    }

    /**
     * @param combinator        {@code ANY} when each declared condition admits on its own (the
     *                          severe-malnutrition shape: low arm circumference <em>or</em> oedema
     *                          <em>or</em> low weight-for-height), {@code ALL} when the conditions
     *                          together describe one band. Defaults to {@code ALL}.
     * @param optionalCriterion a criterion depending on equipment a facility may not have (a height
     *                          board for weight-for-height). Its absence must not make every
     *                          assessment incomplete, but it may only ever be an <em>additional</em>
     *                          route to admission — never the only way to rule malnutrition out.
     */
    public record AdmissionCriterion(
            String code,
            String description,
            String combinator,
            BigDecimal muacBelowCm,
            BigDecimal muacAtLeastCm,
            boolean oedemaPresent,
            BigDecimal weightForHeightZBelow,
            BigDecimal weightForHeightZAtLeast,
            boolean requiresComplication,
            Integer minAgeDays,
            Integer maxAgeDays,
            boolean optionalCriterion) {
    }

    public record TherapeuticFood(
            String productCode,
            String productName,
            String rationBasis,
            String note,
            List<RationBand> rationBands) {

        public RationBand bandFor(BigDecimal weightKg) {
            if (weightKg == null) {
                return null;
            }
            return rationBands.stream()
                    .filter(b -> weightKg.compareTo(b.minWeightKg()) >= 0 && weightKg.compareTo(b.maxWeightKg()) <= 0)
                    .findFirst()
                    .orElse(null);
        }
    }

    public record RationBand(
            BigDecimal minWeightKg,
            BigDecimal maxWeightKg,
            BigDecimal sachetsPerDay,
            BigDecimal sachetsPerWeek) {
    }

    /**
     * @param sustainedConsecutiveVisits the number of consecutive reviews the anthropometric
     *                                   criterion must hold across before a child is cured
     * @param dischargeOutcomeIsCure     false for inpatient stabilisation: leaving the ward is a
     *                                   transfer to outpatient care, and recording it as a cure would
     *                                   close the episode of a child who is still severely malnourished
     */
    public record DischargeCriteria(
            String narrative,
            BigDecimal muacAtLeastCm,
            int sustainedConsecutiveVisits,
            int oedemaFreeMinimumDays,
            int minimumStayDays,
            boolean requiresNoAcuteMedicalProblem,
            boolean requiresAppetiteReturned,
            boolean requiresOedemaResolving,
            String dischargeDestination,
            boolean dischargeOutcomeIsCure) {
    }

    public record Escalation(
            String code,
            String fromProgramme,
            String when,
            String toProgramme,
            String action) {
    }

    public record Outcome(
            String code,
            String name,
            boolean requiresDischargeCriteria,
            String description) {
    }

    /** A fixture shipped with the content and executed against the engine as a build gate. */
    public record TestCase(
            String kind,
            String name,
            Map<String, Object> facts,
            Boolean expectEligible,
            Boolean expectAssessable,
            String expectProgramme,
            String expectAdmissionCriterion,
            String programme,
            String admittedOn,
            String admissionOedema,
            String asOf,
            List<Map<String, Object>> visits,
            Boolean expectDischargeEligible,
            List<String> expectUnmetCriteria,
            String expectAttendanceStatus,
            String expectResponseStatus,
            String expectEscalation,
            Boolean expectNoReviewRecorded) {
    }
}
