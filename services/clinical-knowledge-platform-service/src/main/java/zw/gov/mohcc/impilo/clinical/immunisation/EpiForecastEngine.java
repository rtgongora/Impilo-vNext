package zw.gov.mohcc.impilo.clinical.immunisation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Forecasts which immunisation doses a child is due, overdue or ineligible for.
 *
 * <p>A forecast is a safety instrument, not a reminder list. The failure modes are asymmetric and
 * both are real:</p>
 *
 * <ul>
 *   <li><strong>Saying a dose is due when it is not yet valid.</strong> A dose given before its
 *       minimum age, or too soon after the previous one, does not immunise — it is discarded and
 *       the child must be re-vaccinated. Age alone is not eligibility: a child who arrives late and
 *       receives dose 1 today is not eligible for dose 2 tomorrow simply because they are old
 *       enough for it. The interval since the previous dose is a separate gate, and the engine
 *       reports {@link DoseStatus#BLOCKED_BY_INTERVAL} with the date the gate opens.</li>
 *   <li><strong>Saying a dose is still available when the window has closed.</strong> The
 *       rotavirus ceilings are a safety limit rather than a convenience, because the small excess
 *       risk of intussusception rises with age at first dose. An incomplete rotavirus course is
 *       closed, never caught up.</li>
 * </ul>
 *
 * <p>The engine is pure and takes its evaluation date as an argument, so a forecast printed on a
 * child's card can be reproduced exactly during a later review. It holds no schedule of its own:
 * the schedule is governed content, because the EPI programme changes antigens and timings and
 * that must never require a code change.</p>
 */
public final class EpiForecastEngine {

    private EpiForecastEngine() {
    }

    public enum DoseStatus {
        /** Already given and valid. */
        GIVEN,
        /** Given, but before the minimum age or too soon after the previous dose — repeat it. */
        INVALID_REPEAT_REQUIRED,
        /** Given, but the source is a card or report that nobody has verified. */
        GIVEN_UNVERIFIED,
        /** Eligible now. */
        DUE,
        /** Eligible and past the age it should have been given. */
        OVERDUE,
        /** Not yet old enough. */
        NOT_YET_DUE,
        /** Old enough, but the minimum interval since the previous dose has not elapsed. */
        BLOCKED_BY_INTERVAL,
        /** The previous dose in the series has not been given, so this one cannot be. */
        AWAITING_PREVIOUS_DOSE,
        /** The window has closed; this dose will not be given. */
        AGED_OUT,
        /** Recorded as contraindicated for this child. */
        CONTRAINDICATED,
        /** This antigen does not apply to this child. */
        NOT_APPLICABLE
    }

    /**
     * A dose already on the record.
     *
     * @param status the PCT immunisation status: COMPLETED, NOT_DONE_CONTRAINDICATED,
     *               RECORDED_ELSEWHERE_PENDING_VERIFICATION and so on
     */
    public record AdministeredDose(String series, Integer doseNumber, LocalDate occurredOn, String status) {
    }

    public record ForecastRow(
            String series,
            String antigenName,
            Integer doseNumber,
            DoseStatus status,
            LocalDate earliestDate,
            LocalDate recommendedDate,
            LocalDate overdueAfter,
            LocalDate latestUsefulDate,
            Integer daysOverdue,
            LocalDate givenOn,
            String rationale) {

        public boolean actionableToday() {
            return status == DoseStatus.DUE || status == DoseStatus.OVERDUE
                   || status == DoseStatus.INVALID_REPEAT_REQUIRED;
        }
    }

    public record Forecast(
            LocalDate dateOfBirth,
            LocalDate asOf,
            Integer ageDays,
            List<ForecastRow> rows,
            List<ForecastRow> dueNow,
            boolean anyOverdue,
            boolean provisional,
            List<String> provisionalReasons,
            String scheduleId,
            String scheduleVersion,
            String approvalStatus,
            String note) {
    }

    /**
     * @param sex used only to scope antigens that are sex-specific in the national schedule. When
     *            it is unknown, such an antigen is reported as NOT_APPLICABLE with the reason
     *            stated, rather than being silently omitted from the card.
     */
    public static Forecast forecast(EpiSchedule schedule,
                                    LocalDate dateOfBirth,
                                    String sex,
                                    List<AdministeredDose> administered,
                                    LocalDate asOf) {
        List<ForecastRow> rows = new ArrayList<>();
        List<String> provisionalReasons = new ArrayList<>();
        int ageDays = (int) ChronoUnit.DAYS.between(dateOfBirth, asOf);

        List<AdministeredDose> given = administered == null ? List.of() : administered;

        for (EpiSchedule.Antigen antigen : schedule.antigens()) {
            boolean sexApplicable = antigen.appliesToSex() == null
                    || antigen.appliesToSex().equalsIgnoreCase(sex);
            boolean sexUnknown = antigen.appliesToSex() != null && (sex == null || sex.isBlank());

            LocalDate previousValidDoseDate = null;

            for (EpiSchedule.Dose dose : antigen.doses()) {
                LocalDate earliest = dateOfBirth.plusDays(dose.minimumAgeDays());
                LocalDate recommended = dateOfBirth.plusDays(dose.recommendedAgeDays());
                LocalDate overdueAfter = dose.overdueAfterAgeDays() == null
                        ? null : dateOfBirth.plusDays(dose.overdueAfterAgeDays());
                LocalDate latestUseful = dose.latestUsefulAgeDays() == null
                        ? null : dateOfBirth.plusDays(dose.latestUsefulAgeDays());

                if (sexUnknown) {
                    rows.add(row(antigen, dose, DoseStatus.NOT_APPLICABLE, earliest, recommended,
                            overdueAfter, latestUseful, null, null,
                            "This antigen is scheduled for " + antigen.appliesToSex().toLowerCase()
                            + " children and the child's sex is not recorded, so eligibility cannot "
                            + "be determined."));
                    continue;
                }
                if (!sexApplicable) {
                    rows.add(row(antigen, dose, DoseStatus.NOT_APPLICABLE, earliest, recommended,
                            overdueAfter, latestUseful, null, null,
                            "Scheduled for " + antigen.appliesToSex().toLowerCase() + " children."));
                    continue;
                }

                Optional<AdministeredDose> match = given.stream()
                        .filter(d -> antigen.series().equalsIgnoreCase(d.series()))
                        .filter(d -> dose.doseNumber().equals(d.doseNumber()))
                        .filter(d -> !"ENTERED_IN_ERROR".equalsIgnoreCase(d.status()))
                        .max(Comparator.comparing(AdministeredDose::occurredOn,
                                Comparator.nullsFirst(Comparator.naturalOrder())));

                if (match.isPresent()) {
                    AdministeredDose actual = match.get();

                    if ("NOT_DONE_CONTRAINDICATED".equalsIgnoreCase(actual.status())) {
                        rows.add(row(antigen, dose, DoseStatus.CONTRAINDICATED, earliest, recommended,
                                overdueAfter, latestUseful, null, actual.occurredOn(),
                                "Recorded as contraindicated for this child. It will not be forecast "
                                + "as due; a documented contraindication needs review, not a reminder."));
                        continue;
                    }

                    // Validity: a dose given too early does not immunise.
                    String invalidity = validityProblem(actual, dose, earliest, previousValidDoseDate);
                    if (invalidity != null) {
                        rows.add(row(antigen, dose, DoseStatus.INVALID_REPEAT_REQUIRED, earliest,
                                recommended, overdueAfter, latestUseful, null, actual.occurredOn(),
                                invalidity));
                        // An invalid dose does not advance the series, so the interval clock for the
                        // next dose does not start from it.
                        continue;
                    }

                    boolean unverified = "RECORDED_ELSEWHERE_PENDING_VERIFICATION".equalsIgnoreCase(actual.status());
                    if (unverified) {
                        provisionalReasons.add(antigen.series() + " dose " + dose.doseNumber()
                                + " is a reported dose that has not been verified");
                    }
                    previousValidDoseDate = actual.occurredOn();
                    rows.add(row(antigen, dose,
                            unverified ? DoseStatus.GIVEN_UNVERIFIED : DoseStatus.GIVEN,
                            earliest, recommended, overdueAfter, latestUseful, null, actual.occurredOn(),
                            unverified
                                    ? "Reported as given on " + actual.occurredOn() + " but not yet "
                                      + "verified against a card or register. Counted so the child is "
                                      + "not re-vaccinated unnecessarily; verify before relying on it."
                                    : "Given on " + actual.occurredOn() + "."));
                    continue;
                }

                // Not given. Work out whether it can be given now.
                if (latestUseful != null && asOf.isAfter(latestUseful)) {
                    rows.add(row(antigen, dose, DoseStatus.AGED_OUT, earliest, recommended,
                            overdueAfter, latestUseful, null, null,
                            agedOutReason(antigen, dose, latestUseful)));
                    continue;
                }

                // A dose depends on the previous one exactly when the schedule gives it a minimum
                // interval. Dose numbering alone is the wrong test: the polio birth dose is dose 0,
                // and a child who missed it still starts the primary series on time at six weeks.
                // Treating every higher dose number as dependent would strand the whole polio
                // series for every baby not born in a facility.
                if (dose.minimumIntervalDays() != null && previousValidDoseDate == null) {
                    rows.add(row(antigen, dose, DoseStatus.AWAITING_PREVIOUS_DOSE, earliest, recommended,
                            overdueAfter, latestUseful, null, null,
                            "The previous dose in this series has not been validly given, so this dose "
                            + "cannot be scheduled yet."));
                    continue;
                }

                LocalDate intervalGate = dose.minimumIntervalDays() != null && previousValidDoseDate != null
                        ? previousValidDoseDate.plusDays(dose.minimumIntervalDays())
                        : null;
                LocalDate eligibleFrom = intervalGate != null && intervalGate.isAfter(earliest)
                        ? intervalGate : earliest;

                if (asOf.isBefore(eligibleFrom)) {
                    boolean blockedByInterval = intervalGate != null && !asOf.isBefore(earliest);
                    rows.add(row(antigen, dose,
                            blockedByInterval ? DoseStatus.BLOCKED_BY_INTERVAL : DoseStatus.NOT_YET_DUE,
                            eligibleFrom, recommended, overdueAfter, latestUseful, null, null,
                            blockedByInterval
                                    ? "Old enough, but only " + ChronoUnit.DAYS.between(previousValidDoseDate, asOf)
                                      + " days have passed since the previous dose and "
                                      + dose.minimumIntervalDays() + " are required. Eligible from "
                                      + eligibleFrom + ". Giving it earlier would not immunise."
                                    : "Not yet due; eligible from " + eligibleFrom + "."));
                    continue;
                }

                boolean overdue = overdueAfter != null && asOf.isAfter(overdueAfter);
                Integer daysOverdue = overdue ? (int) ChronoUnit.DAYS.between(overdueAfter, asOf) : null;
                rows.add(row(antigen, dose, overdue ? DoseStatus.OVERDUE : DoseStatus.DUE,
                        eligibleFrom, recommended, overdueAfter, latestUseful, daysOverdue, null,
                        overdue
                                ? "Overdue by " + daysOverdue + " days. Give it at this contact; there is "
                                  + "no need to restart the series."
                                : "Due now."));
            }
        }

        List<ForecastRow> dueNow = rows.stream().filter(ForecastRow::actionableToday).toList();
        boolean anyOverdue = rows.stream().anyMatch(r -> r.status() == DoseStatus.OVERDUE);

        return new Forecast(
                dateOfBirth, asOf, ageDays, List.copyOf(rows), dueNow, anyOverdue,
                !provisionalReasons.isEmpty(), List.copyOf(provisionalReasons),
                schedule.scheduleId(), schedule.version(), schedule.approvalStatus(),
                provisionalReasons.isEmpty()
                        ? null
                        : "This forecast counts doses that were reported but never verified. Verify "
                          + "them against the child's card before treating the schedule as complete.");
    }

    /** Why a recorded dose does not count, or null when it is valid. */
    private static String validityProblem(AdministeredDose actual, EpiSchedule.Dose dose,
                                          LocalDate earliest, LocalDate previousValidDoseDate) {
        if (actual.occurredOn() == null) {
            return null;
        }
        if (actual.occurredOn().isBefore(earliest)) {
            return "Given on " + actual.occurredOn() + ", before the minimum age (" + earliest
                   + "). A dose given too early does not immunise and must be repeated.";
        }
        if (dose.minimumIntervalDays() != null && previousValidDoseDate != null) {
            long gap = ChronoUnit.DAYS.between(previousValidDoseDate, actual.occurredOn());
            if (gap < dose.minimumIntervalDays()) {
                return "Given " + gap + " days after the previous dose, which is less than the "
                       + dose.minimumIntervalDays() + " days required. This dose does not count and "
                       + "must be repeated.";
            }
        }
        return null;
    }

    private static String agedOutReason(EpiSchedule.Antigen antigen, EpiSchedule.Dose dose,
                                        LocalDate latestUseful) {
        String base = "The window for this dose closed on " + latestUseful + ".";
        if (antigen.safetyNote() != null) {
            return base + " " + antigen.safetyNote();
        }
        if (dose.note() != null) {
            return base + " " + dose.note();
        }
        return base + " It is not given late and the series is not restarted for it.";
    }

    private static ForecastRow row(EpiSchedule.Antigen antigen, EpiSchedule.Dose dose, DoseStatus status,
                                   LocalDate earliest, LocalDate recommended, LocalDate overdueAfter,
                                   LocalDate latestUseful, Integer daysOverdue, LocalDate givenOn,
                                   String rationale) {
        return new ForecastRow(antigen.series(), antigen.name(), dose.doseNumber(), status,
                earliest, recommended, overdueAfter, latestUseful, daysOverdue, givenOn, rationale);
    }

    /** Convenience for callers holding PCT immunisation rows as maps. */
    public static AdministeredDose fromMap(Map<String, Object> row) {
        Object occurrence = row.get("occurrence_at") != null ? row.get("occurrence_at") : row.get("occurredOn");
        LocalDate date = null;
        if (occurrence != null) {
            String text = String.valueOf(occurrence);
            date = LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
        }
        Integer doseNumber = row.get("dose_number") instanceof Number n ? n.intValue()
                : row.get("doseNumber") instanceof Number n2 ? n2.intValue() : null;
        String series = row.get("series") != null ? String.valueOf(row.get("series"))
                : row.get("vaccine_code") != null ? String.valueOf(row.get("vaccine_code")) : null;
        String status = row.get("status") != null ? String.valueOf(row.get("status")) : "COMPLETED";
        return new AdministeredDose(series, doseNumber, date, status);
    }
}
