package zw.gov.mohcc.impilo.reproductive.contraception;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Whether a method is still protecting, and until when.
 *
 * <p>Pure and static. Every method returns null or {@link CoverageStatus#UNKNOWN} rather than a
 * default when the inputs cannot support an answer.
 *
 * <p><b>The one rule this class exists to enforce: it never returns COVERED by default.</b> An
 * implant with no insertion date, an injection with no last dose, a method the catalogue does not
 * describe — all of them are UNKNOWN. A woman shown as protected by a method nobody can date is a
 * confident answer produced from an absence, and she acts on it. "We do not know whether you are
 * still covered" is a prompt to check; "covered" is a reason not to.
 */
public final class ContraceptiveCoverageCalculator {

    public static final String CONTENT_VERSION = "impilo-contraceptive-coverage-1.0.0";

    private ContraceptiveCoverageCalculator() {
    }

    /**
     * When a device or implant stops protecting. Null when the method does not expire or the start
     * date is unknown.
     */
    public static LocalDate expiresOn(ContraceptiveMethodCatalog.ContraceptiveMethodProfile profile,
                                      LocalDate startedOn) {
        if (profile == null || startedOn == null || !profile.expires()) {
            return null;
        }
        return startedOn.plusMonths(profile.effectiveDurationMonths());
    }

    /** When the next dose of a scheduled method is due. Null when not scheduled or undatable. */
    public static LocalDate nextDoseDueOn(ContraceptiveMethodCatalog.ContraceptiveMethodProfile profile,
                                          LocalDate lastDoseOn) {
        if (profile == null || lastDoseOn == null || !profile.scheduled()) {
            return null;
        }
        return lastDoseOn.plusDays(profile.reinjectionIntervalDays());
    }

    /**
     * The window in which a repeat dose may be given.
     *
     * <p>The late bound is not administrative tolerance — it is the period in which the method is
     * still working, which is why a woman inside it needs no backup and a woman past it does.
     */
    public static DoseWindow doseWindow(ContraceptiveMethodCatalog.ContraceptiveMethodProfile profile,
                                        LocalDate lastDoseOn) {
        LocalDate due = nextDoseDueOn(profile, lastDoseOn);
        if (due == null) {
            return null;
        }
        int early = profile.graceWindowEarlyDays() == null ? 0 : profile.graceWindowEarlyDays();
        int late = profile.graceWindowLateDays() == null ? 0 : profile.graceWindowLateDays();
        return new DoseWindow(due, due.minusDays(early), due.plusDays(late));
    }

    /**
     * Coverage on a given date.
     *
     * @param startedOn  when the method began — insertion, first dose, procedure
     * @param lastDoseOn the most recent dose for a scheduled method; ignored otherwise
     */
    public static CoverageStatus coverageOn(ContraceptiveMethodCatalog.ContraceptiveMethodProfile profile,
                                            LocalDate startedOn, LocalDate lastDoseOn, LocalDate on) {
        if (profile == null || on == null) {
            return CoverageStatus.UNKNOWN;
        }

        if (profile.scheduled()) {
            // A scheduled method with no recorded dose cannot be assessed. Falling back to the start
            // date would treat a woman four months past her injection as covered because she once
            // started the method.
            if (lastDoseOn == null) {
                return CoverageStatus.UNKNOWN;
            }
            DoseWindow window = doseWindow(profile, lastDoseOn);
            if (on.isBefore(window.dueOn())) {
                return CoverageStatus.COVERED;
            }
            if (on.isEqual(window.dueOn())) {
                return CoverageStatus.DUE;
            }
            return on.isAfter(window.closesOn())
                    ? CoverageStatus.LAPSED
                    : CoverageStatus.LATE_WITHIN_GRACE;
        }

        if (profile.expires()) {
            if (startedOn == null) {
                return CoverageStatus.UNKNOWN;
            }
            LocalDate expiry = expiresOn(profile, startedOn);
            if (on.isAfter(expiry)) {
                return CoverageStatus.EXPIRED;
            }
            return on.isEqual(expiry) ? CoverageStatus.DUE : CoverageStatus.COVERED;
        }

        // Condoms, sterilisation, withdrawal, emergency pills: nothing to compute. Deliberately not
        // COVERED — a condom protects only when it is used, and asserting continuous coverage from
        // a recorded method would be a claim about behaviour the record cannot see.
        return CoverageStatus.NOT_APPLICABLE;
    }

    /**
     * Days of protection left. Negative once lapsed or expired; null when unknowable.
     *
     * <p>Separate from the status because a number and a state answer different questions: the
     * status decides whether she needs backup today, the number decides whether this visit is the
     * one to resupply her at.
     */
    public static Integer daysOfProtectionRemaining(
            ContraceptiveMethodCatalog.ContraceptiveMethodProfile profile,
            LocalDate startedOn, LocalDate lastDoseOn, LocalDate on) {
        if (profile == null || on == null) {
            return null;
        }
        if (profile.scheduled()) {
            DoseWindow window = doseWindow(profile, lastDoseOn);
            return window == null ? null : (int) ChronoUnit.DAYS.between(on, window.closesOn());
        }
        LocalDate expiry = expiresOn(profile, startedOn);
        return expiry == null ? null : (int) ChronoUnit.DAYS.between(on, expiry);
    }

    /**
     * @param dueOn    the nominal date the next dose is owed
     * @param opensOn  the earliest it may be given
     * @param closesOn the last date at which protection is still retained
     */
    public record DoseWindow(LocalDate dueOn, LocalDate opensOn, LocalDate closesOn) {

        public boolean covers(LocalDate date) {
            return date != null && !date.isBefore(opensOn) && !date.isAfter(closesOn);
        }
    }
}
