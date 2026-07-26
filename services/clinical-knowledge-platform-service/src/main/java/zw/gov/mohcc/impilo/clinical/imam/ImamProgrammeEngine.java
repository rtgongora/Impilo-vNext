package zw.gov.mohcc.impilo.clinical.imam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The IMAM programme engine: which programme a child is admitted to, and — once admitted — whether
 * they may be discharged, whether they have stopped coming, and whether they are responding.
 *
 * <p>Stateless, in the same way as the growth and immunisation engines. pct-service owns the episode
 * and its reviews and passes them in, so the same evaluation runs unchanged against an offline copy
 * and this service can never disagree with the clinical record because it holds none.</p>
 *
 * <h2>Three refusals</h2>
 * <ul>
 *   <li><strong>One good measurement does not discharge a child.</strong> The anthropometric
 *       criterion must hold across the number of consecutive reviews the content demands. A child
 *       with a single arm circumference above the cut-off is reported as not yet dischargeable, with
 *       the reason.</li>
 *   <li><strong>Not assessed is not absent.</strong> A discharge criterion whose input was never
 *       recorded is unmet, not met. Oedema nobody looked for cannot certify fourteen oedema-free
 *       days.</li>
 *   <li><strong>Silence is never reassurance.</strong> An episode with no recorded review is not a
 *       child doing well. It is a child nobody has seen, and the assessment says exactly that.</li>
 * </ul>
 */
public final class ImamProgrammeEngine {

    private ImamProgrammeEngine() {
    }

    // ── eligibility ────────────────────────────────────────────────────────────────────────────

    /**
     * @param classification an IMNCI acute-malnutrition tier code; when present it routes directly,
     *                       so the instruction the classification engine issued ("enrol in outpatient
     *                       therapeutic care") reaches a programme without a second judgement
     */
    public record EligibilityFacts(
            Integer ageDays,
            BigDecimal muacCm,
            String oedema,
            String appetiteTest,
            BigDecimal weightForHeightZ,
            Boolean medicalComplication,
            Boolean dangerSignsPresent,
            String classification) {
    }

    public record Eligibility(
            boolean assessable,
            String notAssessableReason,
            boolean eligible,
            String programme,
            String programmeName,
            String admissionCriterion,
            String admissionCriterionDescription,
            List<String> criteriaMet,
            List<String> missingInputs,
            Integer reviewIntervalDays,
            Integer attendanceGraceDays,
            Integer traceAfterMissedVisits,
            Integer defaulterMissedVisits,
            String therapeuticFood,
            String rationale,
            String contentPackId,
            String contentVersion,
            String approvalStatus,
            String note) {
    }

    public static Eligibility eligibility(ImamProgrammeContent content, EligibilityFacts facts) {
        String version = content.version();
        String approval = content.approvalStatus();
        String seedNote = "Engineering seed content pending MoHCC ratification: confirm against the "
                + "national IMAM protocol before this drives treatment.";

        // A classification already carries the clinical judgement. Re-deriving it here would create a
        // second opinion about the same child that could disagree with the one on the chart.
        ImamProgrammeContent.Programme byClassification = content.forClassification(facts.classification());
        if (byClassification != null) {
            return admitted(content, byClassification, "IMNCI_CLASSIFICATION",
                    "Admitted on the IMNCI classification " + facts.classification().trim().toUpperCase(Locale.ROOT)
                            + ", which this programme treats.",
                    List.of(), seedNote);
        }

        Oedema oedema = Oedema.of(facts.oedema());
        boolean muacUsable = facts.muacCm() != null && facts.muacCm().signum() > 0;
        boolean wfhUsable = facts.weightForHeightZ() != null;

        List<String> missing = new ArrayList<>();
        if (!muacUsable) {
            missing.add("muacCm");
        }
        if (oedema == Oedema.NOT_ASSESSED) {
            missing.add("bilateralPittingOedema");
        }
        if (!wfhUsable) {
            missing.add("weightForHeightZ");
        }

        // Under six months MUAC is not a valid indicator, so a low reading admits nothing and a
        // normal one rules nothing out. Only oedema and weight-for-length can answer, and saying so
        // is the honest response — "not eligible" would be an all-clear drawn from the wrong tape.
        boolean muacInterpretable = muacUsable && withinAge(facts.ageDays(), 183, 1824);
        if (oedema != Oedema.PRESENT && !wfhUsable && !muacInterpretable) {
            String reason = muacUsable && !withinAge(facts.ageDays(), 183, 1824)
                    ? "Mid-upper-arm circumference is only interpretable between 6 and 59 months. An infant "
                      + "under 6 months is assessed on weight-for-length and clinical signs, neither of which "
                      + "was supplied."
                    : "No nutritional measurement was supplied: acute malnutrition cannot be ruled in or out "
                      + "from an unmeasured child.";
            return new Eligibility(false, reason, false, null, null, null, null,
                    List.of(), missing, null, null, null, null, null, null,
                    content.packId(), version, approval, seedNote);
        }

        for (ImamProgrammeContent.Programme programme : content.programmes()) {
            for (ImamProgrammeContent.AdmissionCriterion criterion : programme.admissionCriteria()) {
                if (!withinAge(facts.ageDays(), criterion.minAgeDays(), criterion.maxAgeDays())) {
                    continue;
                }
                if (!criterionMet(criterion, facts, oedema, muacInterpretable)) {
                    continue;
                }
                if (criterion.requiresComplication() && !complicationPresent(facts)) {
                    continue;
                }
                return admitted(content, programme, criterion.code(), criterion.description(), missing, seedNote);
            }
        }

        return new Eligibility(true, null, false, null, null, null, null,
                List.of(), missing, null, null, null, null, null,
                "No admission criterion for a nutrition programme is met on the measurements supplied.",
                content.packId(), version, approval, seedNote);
    }

    /**
     * The schedule and the defaulter definition travel with the admission decision, because the
     * enrolling service stores them on the episode: a child is held to the rules they were admitted
     * under, and the tracing worklist can then be one indexed query rather than one network call per
     * open episode.
     */
    private static Eligibility admitted(ImamProgrammeContent content, ImamProgrammeContent.Programme programme,
                                        String criterionCode, String criterionDescription,
                                        List<String> missing, String seedNote) {
        return new Eligibility(true, null, true, programme.code(), programme.name(),
                criterionCode, criterionDescription,
                List.of(criterionCode), missing,
                programme.reviewIntervalDays(),
                programme.attendanceGraceDays(),
                programme.traceAfterMissedVisits(),
                programme.defaulterMissedVisits(),
                programme.therapeuticFood() == null ? null : programme.therapeuticFood().productCode(),
                programme.treats(), content.packId(), content.version(), content.approvalStatus(), seedNote);
    }

    /**
     * A criterion declares one or more conditions. {@code ANY} is the severe-malnutrition shape —
     * a low arm circumference <em>or</em> oedema <em>or</em> a low weight-for-height each admit on
     * their own — while {@code ALL} is the banded shape used by moderate malnutrition, where the
     * measurement must fall between two cut-offs.
     */
    private static boolean criterionMet(ImamProgrammeContent.AdmissionCriterion c, EligibilityFacts facts,
                                        Oedema oedema, boolean muacInterpretable) {
        List<Boolean> conditions = new ArrayList<>();

        if (c.oedemaPresent()) {
            conditions.add(oedema == Oedema.PRESENT);
        }
        if (c.muacBelowCm() != null || c.muacAtLeastCm() != null) {
            BigDecimal muac = facts.muacCm();
            boolean met = muacInterpretable
                    && (c.muacBelowCm() == null || muac.compareTo(c.muacBelowCm()) < 0)
                    && (c.muacAtLeastCm() == null || muac.compareTo(c.muacAtLeastCm()) >= 0);
            conditions.add(met);
        }
        if (c.weightForHeightZBelow() != null || c.weightForHeightZAtLeast() != null) {
            BigDecimal z = facts.weightForHeightZ();
            boolean met = z != null
                    && (c.weightForHeightZBelow() == null || z.compareTo(c.weightForHeightZBelow()) < 0)
                    && (c.weightForHeightZAtLeast() == null || z.compareTo(c.weightForHeightZAtLeast()) >= 0);
            conditions.add(met);
        }

        if (conditions.isEmpty()) {
            return false;
        }
        return "ANY".equalsIgnoreCase(c.combinator())
                ? conditions.contains(Boolean.TRUE)
                : !conditions.contains(Boolean.FALSE);
    }

    /**
     * The stabilisation criterion is severe acute malnutrition <em>with</em> a complication, so the
     * anthropometric arm is any of the three severe routes rather than the one named on the criterion.
     */
    private static boolean complicationPresent(EligibilityFacts facts) {
        return Boolean.TRUE.equals(facts.medicalComplication())
                || Boolean.TRUE.equals(facts.dangerSignsPresent())
                || "FAILED".equalsIgnoreCase(String.valueOf(facts.appetiteTest()));
    }

    private static boolean withinAge(Integer ageDays, Integer min, Integer max) {
        if (min == null && max == null) {
            return true;
        }
        if (ageDays == null) {
            return false;
        }
        return (min == null || ageDays >= min) && (max == null || ageDays <= max);
    }

    // ── progress, discharge readiness, attendance and response ──────────────────────────────────

    public record Visit(
            Integer visitNumber,
            LocalDate visitDate,
            boolean attended,
            BigDecimal muacCm,
            BigDecimal weightKg,
            String oedema,
            String appetiteTest,
            Boolean dangerSignsPresent) {
    }

    public record Criterion(String code, String name, boolean met, String detail) {
    }

    public record Progress(
            String programme,
            String programmeName,
            boolean assessable,
            String notAssessableReason,
            int reviewsRecorded,
            boolean noReviewRecorded,
            int daysInProgramme,
            boolean dischargeEligible,
            List<Criterion> dischargeCriteria,
            List<String> unmetCriteria,
            String dischargeOutcome,
            String dischargeDestination,
            String attendanceStatus,
            int consecutiveMissedVisits,
            Integer daysSinceLastContact,
            LocalDate nextReviewDue,
            Integer daysOverdue,
            boolean tracingRequired,
            String responseStatus,
            BigDecimal weightGainGramsPerKgPerDay,
            String escalation,
            String escalationAction,
            BigDecimal recommendedSachetsPerWeek,
            String therapeuticFood,
            List<String> actions,
            String contentVersion,
            String approvalStatus,
            String note) {
    }

    public static Progress progress(ImamProgrammeContent content, String programmeCode, LocalDate admittedOn,
                                    String admissionOedema, List<Visit> visits, LocalDate asOf) {
        String version = content.version();
        String approval = content.approvalStatus();
        ImamProgrammeContent.Programme programme = content.programme(programmeCode);
        if (programme == null) {
            return notAssessable(programmeCode, "Unknown IMAM programme: " + programmeCode, version, approval);
        }
        if (admittedOn == null) {
            return notAssessable(programme.code(),
                    "The admission date is unknown, so length of stay, review schedule and defaulter status "
                            + "cannot be evaluated.", version, approval);
        }
        LocalDate today = asOf == null ? LocalDate.now() : asOf;

        List<Visit> attended = new ArrayList<>(visits == null ? List.<Visit>of() : visits).stream()
                .filter(v -> v != null && v.attended() && v.visitDate() != null)
                .sorted(Comparator.comparing(Visit::visitDate))
                .toList();

        int daysInProgramme = (int) ChronoUnit.DAYS.between(admittedOn, today);
        Visit latest = attended.isEmpty() ? null : attended.get(attended.size() - 1);

        // --- attendance -----------------------------------------------------------------------
        LocalDate lastContact = latest == null ? admittedOn : latest.visitDate();
        int daysSinceLastContact = (int) ChronoUnit.DAYS.between(lastContact, today);
        LocalDate nextReviewDue = lastContact.plusDays(programme.reviewIntervalDays());
        int daysOverdue = (int) Math.max(0, ChronoUnit.DAYS.between(nextReviewDue, today));

        // Missed reviews are counted from elapsed time rather than from rows, because the commonest
        // way a defaulter goes unnoticed is that nobody created the row for the visit they missed.
        int elapsedMissed = Math.max(0,
                (daysSinceLastContact - programme.attendanceGraceDays()) / programme.reviewIntervalDays());
        int explicitMissed = (int) (visits == null ? List.<Visit>of() : visits).stream()
                .filter(v -> v != null && !v.attended())
                .filter(v -> latest == null || v.visitDate() == null || v.visitDate().isAfter(latest.visitDate()))
                .count();
        int missed = Math.max(elapsedMissed, explicitMissed);

        String attendanceStatus;
        if (missed >= programme.defaulterMissedVisits()) {
            attendanceStatus = "DEFAULTER";
        } else if (missed >= programme.traceAfterMissedVisits()) {
            attendanceStatus = "TRACE_REQUIRED";
        } else {
            attendanceStatus = "ATTENDING";
        }
        boolean tracingRequired = missed >= programme.traceAfterMissedVisits();

        // --- discharge criteria ----------------------------------------------------------------
        ImamProgrammeContent.DischargeCriteria dc = programme.dischargeCriteria();
        List<Criterion> criteria = new ArrayList<>();

        if (dc.muacAtLeastCm() != null) {
            criteria.add(muacSustained(dc, attended));
        }
        if (dc.oedemaFreeMinimumDays() > 0) {
            criteria.add(oedemaFree(dc, admissionOedema, admittedOn, attended, today));
        }
        if (dc.minimumStayDays() > 0) {
            boolean met = daysInProgramme >= dc.minimumStayDays();
            criteria.add(new Criterion("MINIMUM_STAY", "Minimum length of stay", met,
                    met ? daysInProgramme + " days in the programme, at least " + dc.minimumStayDays() + " required."
                        : daysInProgramme + " days in the programme; " + dc.minimumStayDays() + " are required."));
        }
        if (dc.requiresNoAcuteMedicalProblem()) {
            criteria.add(noAcuteProblem(latest));
        }
        if (dc.requiresAppetiteReturned()) {
            boolean met = latest != null && "PASSED".equalsIgnoreCase(String.valueOf(latest.appetiteTest()));
            criteria.add(new Criterion("APPETITE_RETURNED", "Appetite returned", met,
                    latest == null ? "No review recorded, so appetite has not been demonstrated."
                            : met ? "The appetite test was passed at the last review."
                                  : "The appetite test has not been recorded as passed at the last review."));
        }
        if (dc.requiresOedemaResolving()) {
            boolean met = latest != null && Oedema.of(latest.oedema()) == Oedema.ABSENT;
            criteria.add(new Criterion("OEDEMA_RESOLVED", "Oedema resolved", met,
                    met ? "No oedema at the last review."
                        : "Oedema is not recorded as absent at the last review."));
        }

        List<String> unmet = criteria.stream().filter(c -> !c.met()).map(Criterion::code).toList();
        boolean dischargeEligible = unmet.isEmpty() && !attended.isEmpty();

        // --- escalation --------------------------------------------------------------------------
        ImamProgrammeContent.Escalation escalation = escalationFor(content, programme, latest);

        // --- response ------------------------------------------------------------------------------
        BigDecimal gain = weightGain(attended);
        String responseStatus = responseStatus(programme, dischargeEligible, daysInProgramme, attended, gain);

        // --- ration ----------------------------------------------------------------------------------
        BigDecimal sachetsPerWeek = null;
        if (programme.therapeuticFood() != null && latest != null) {
            ImamProgrammeContent.RationBand band = programme.therapeuticFood().bandFor(latest.weightKg());
            sachetsPerWeek = band == null ? null : band.sachetsPerWeek();
        }

        // --- actions -----------------------------------------------------------------------------------
        List<String> actions = new ArrayList<>();
        if (attended.isEmpty()) {
            actions.add("No review has ever been recorded for this child since enrolment on " + admittedOn
                    + ". An episode with no recorded review is not a child doing well — it is a child nobody "
                    + "has seen since they were found to be malnourished. Trace today.");
        }
        if (escalation != null) {
            actions.add(escalation.action());
        }
        if ("DEFAULTER".equals(attendanceStatus)) {
            actions.add("Absent for " + missed + " consecutive scheduled reviews: this child meets the defaulter "
                    + "definition. Trace, and record the outcome of the trace rather than closing the episode blind.");
        } else if ("TRACE_REQUIRED".equals(attendanceStatus)) {
            actions.add("A scheduled review was missed " + daysOverdue + " days ago. Trace now — waiting until the "
                    + "child meets the defaulter definition wastes the window in which a home visit brings them back.");
        }
        if (dischargeEligible) {
            actions.add(dc.dischargeOutcomeIsCure()
                    ? "Discharge criteria are met. Discharge as cured and counsel on continued feeding."
                    : "Stabilisation criteria are met. Transfer to " + dc.dischargeDestination()
                      + " — this is a transfer of setting, not a cure.");
        } else if (!attended.isEmpty() && !unmet.isEmpty()) {
            actions.add("Continue treatment. Not yet dischargeable: " + String.join(", ", unmet) + ".");
        }
        if ("NON_RESPONDER".equals(responseStatus)) {
            actions.add("Has not reached discharge criteria within " + programme.nonResponderAfterDays()
                    + " days. Failure to respond is itself the finding: investigate for tuberculosis, HIV and other "
                    + "underlying illness rather than continuing the same ration.");
        } else if ("STATIC".equals(responseStatus)) {
            actions.add("Weight gain is below the programme target of "
                    + programme.targetWeightGainGramsPerKgPerDay() + " g/kg/day. Check the ration is being eaten "
                    + "by the child rather than shared, and look for illness.");
        } else if ("DETERIORATING".equals(responseStatus)) {
            actions.add("Weight is below the admission weight. Assess today for illness and feeding, and consider "
                    + "inpatient care.");
        }
        if (sachetsPerWeek != null) {
            actions.add("Issue " + sachetsPerWeek + " sachets of "
                    + programme.therapeuticFood().productCode() + " for the coming review interval.");
        }

        return new Progress(
                programme.code(), programme.name(), true, null,
                attended.size(), attended.isEmpty(), daysInProgramme,
                dischargeEligible, criteria, unmet,
                dischargeEligible ? (dc.dischargeOutcomeIsCure() ? "CURED" : "TRANSFERRED_OUT") : null,
                dc.dischargeDestination(),
                attendanceStatus, missed, daysSinceLastContact, nextReviewDue, daysOverdue, tracingRequired,
                responseStatus, gain,
                escalation == null ? null : escalation.code(),
                escalation == null ? null : escalation.action(),
                sachetsPerWeek,
                programme.therapeuticFood() == null ? null : programme.therapeuticFood().productCode(),
                actions, version, approval,
                "Engineering seed content pending MoHCC ratification: confirm the discharge criteria, the "
                        + "defaulter definition and the ration schedule against the national IMAM protocol.");
    }

    /**
     * The criterion the brief exists for. The last {@code n} reviews must <em>all</em> clear the
     * cut-off; fewer than {@code n} reviews cannot satisfy it however good they are.
     */
    private static Criterion muacSustained(ImamProgrammeContent.DischargeCriteria dc, List<Visit> attended) {
        int need = Math.max(1, dc.sustainedConsecutiveVisits());
        String name = "Arm circumference at or above " + dc.muacAtLeastCm() + " cm on " + need
                + " consecutive reviews";

        if (attended.size() < need) {
            return new Criterion("MUAC_SUSTAINED", name, false,
                    attended.size() + " review(s) recorded; " + need + " consecutive are required. A single "
                            + "measurement above the cut-off can be a mis-measurement and must not discharge a child.");
        }
        List<Visit> window = attended.subList(attended.size() - need, attended.size());
        List<String> failures = new ArrayList<>();
        for (Visit v : window) {
            if (v.muacCm() == null) {
                failures.add("no arm circumference recorded on " + v.visitDate());
            } else if (v.muacCm().compareTo(dc.muacAtLeastCm()) < 0) {
                failures.add(v.muacCm() + " cm on " + v.visitDate());
            }
        }
        if (failures.isEmpty()) {
            return new Criterion("MUAC_SUSTAINED", name, true,
                    "The last " + need + " reviews are all at or above " + dc.muacAtLeastCm() + " cm.");
        }
        return new Criterion("MUAC_SUSTAINED", name, false,
                "Not sustained across the last " + need + " reviews: " + String.join("; ", failures) + ".");
    }

    /**
     * Oedema must be recorded <em>absent</em> across the window. Never having looked is not the same
     * as having looked and found nothing, and only one of the two can certify a discharge.
     */
    private static Criterion oedemaFree(ImamProgrammeContent.DischargeCriteria dc, String admissionOedema,
                                        LocalDate admittedOn, List<Visit> attended, LocalDate today) {
        int days = dc.oedemaFreeMinimumDays();
        String name = "No bilateral pitting oedema for " + days + " days";
        LocalDate windowStart = today.minusDays(days);

        LocalDate lastPresent = null;
        if (Oedema.of(admissionOedema) == Oedema.PRESENT) {
            lastPresent = admittedOn;
        }
        boolean assessedAbsentInWindow = false;
        for (Visit v : attended) {
            Oedema o = Oedema.of(v.oedema());
            if (o == Oedema.PRESENT) {
                lastPresent = v.visitDate();
            } else if (o == Oedema.ABSENT && !v.visitDate().isBefore(windowStart)) {
                assessedAbsentInWindow = true;
            }
        }

        if (lastPresent != null && !lastPresent.isBefore(windowStart)) {
            long since = ChronoUnit.DAYS.between(lastPresent, today);
            return new Criterion("OEDEMA_FREE", name, false,
                    "Oedema was present " + since + " days ago (" + lastPresent + "); " + days
                            + " oedema-free days are required.");
        }
        if (!assessedAbsentInWindow) {
            return new Criterion("OEDEMA_FREE", name, false,
                    "Oedema has not been assessed at any review in the last " + days + " days. An unrecorded "
                            + "assessment is not an absent finding, and cannot certify an oedema-free discharge.");
        }
        return new Criterion("OEDEMA_FREE", name, true,
                "Oedema recorded absent, with no oedema in the last " + days + " days.");
    }

    private static Criterion noAcuteProblem(Visit latest) {
        String name = "No acute medical problem";
        if (latest == null) {
            return new Criterion("NO_ACUTE_MEDICAL_PROBLEM", name, false,
                    "No review recorded, so the child's clinical state at discharge is unknown.");
        }
        if (latest.dangerSignsPresent() == null) {
            return new Criterion("NO_ACUTE_MEDICAL_PROBLEM", name, false,
                    "Danger signs were not assessed at the last review on " + latest.visitDate() + ".");
        }
        boolean met = !latest.dangerSignsPresent();
        return new Criterion("NO_ACUTE_MEDICAL_PROBLEM", name, met,
                met ? "No danger signs at the last review." : "A danger sign was present at the last review.");
    }

    private static ImamProgrammeContent.Escalation escalationFor(ImamProgrammeContent content,
                                                                 ImamProgrammeContent.Programme programme,
                                                                 Visit latest) {
        if (latest == null) {
            return null;
        }
        for (ImamProgrammeContent.Escalation e : content.escalations()) {
            if (!programme.code().equalsIgnoreCase(e.fromProgramme())) {
                continue;
            }
            boolean fires = switch (e.when()) {
                case "oedemaPresent" -> Oedema.of(latest.oedema()) == Oedema.PRESENT;
                case "muacBelowSevere" -> latest.muacCm() != null
                        && latest.muacCm().compareTo(BigDecimal.valueOf(11.5)) < 0;
                case "dangerSignsOrFailedAppetite" -> Boolean.TRUE.equals(latest.dangerSignsPresent())
                        || "FAILED".equalsIgnoreCase(String.valueOf(latest.appetiteTest()));
                default -> false;
            };
            if (fires) {
                return e;
            }
        }
        return null;
    }

    /** Grams gained per kilogram of body weight per day, measured from the lowest recorded weight. */
    private static BigDecimal weightGain(List<Visit> attended) {
        List<Visit> weighed = attended.stream().filter(v -> v.weightKg() != null && v.weightKg().signum() > 0).toList();
        if (weighed.size() < 2) {
            return null;
        }
        Visit first = weighed.get(0);
        Visit last = weighed.get(weighed.size() - 1);
        long days = ChronoUnit.DAYS.between(first.visitDate(), last.visitDate());
        if (days <= 0) {
            return null;
        }
        BigDecimal grams = last.weightKg().subtract(first.weightKg()).multiply(BigDecimal.valueOf(1000));
        return grams.divide(first.weightKg().multiply(BigDecimal.valueOf(days)), 2, RoundingMode.HALF_UP);
    }

    private static String responseStatus(ImamProgrammeContent.Programme programme, boolean dischargeEligible,
                                         int daysInProgramme, List<Visit> attended, BigDecimal gain) {
        if (dischargeEligible) {
            return "RECOVERED";
        }
        if (daysInProgramme > programme.nonResponderAfterDays()) {
            return "NON_RESPONDER";
        }
        if (attended.isEmpty()) {
            return "UNKNOWN";
        }
        if (gain == null) {
            return "UNKNOWN";
        }
        if (gain.signum() < 0) {
            return "DETERIORATING";
        }
        if (programme.targetWeightGainGramsPerKgPerDay() != null
                && gain.compareTo(programme.targetWeightGainGramsPerKgPerDay()) < 0) {
            return "STATIC";
        }
        return "RESPONDING";
    }

    private static Progress notAssessable(String programme, String reason, String version, String approval) {
        return new Progress(programme, null, false, reason, 0, true, 0, false, List.of(), List.of(),
                null, null, "UNKNOWN", 0, null, null, null, false, "UNKNOWN", null, null, null, null, null,
                List.of(), version, approval, null);
    }

    /** A finding that must distinguish "absent" from "nobody looked". */
    enum Oedema {
        PRESENT, ABSENT, NOT_ASSESSED;

        static Oedema of(String value) {
            if (value == null) {
                return NOT_ASSESSED;
            }
            return switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "PRESENT", "YES", "TRUE" -> PRESENT;
                case "ABSENT", "NO", "FALSE" -> ABSENT;
                default -> NOT_ASSESSED;
            };
        }
    }
}
