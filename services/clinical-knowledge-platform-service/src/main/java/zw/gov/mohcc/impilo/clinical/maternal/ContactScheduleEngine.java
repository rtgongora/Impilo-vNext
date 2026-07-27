package zw.gov.mohcc.impilo.clinical.maternal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The antenatal contact schedule: which of the eight WHO contacts a woman is due, overdue, or has
 * had, given how many weeks pregnant she is.
 *
 * <p>Pure and takes gestational age as an argument, so a schedule printed at one contact reproduces
 * exactly when reviewed at the next. It holds no schedule of its own — the eight contacts and their
 * windows are governed content, because the timing is a policy the ministry sets.
 *
 * <p><b>Every gestation returns a defined forecast.</b> The engine is written so there is no
 * gestational age from 0 to post-term that yields nothing — the band-hole failure where a heart rate
 * at exactly the boundary between two bands matched neither and the patient fell through. A woman at
 * any point in her pregnancy has a defined next contact, and a woman past term is in an explicit
 * post-term state rather than an undefined one.
 *
 * <p><b>Overdue is computed against gestation, not against a diary.</b> A contact is overdue because
 * the pregnancy has advanced past the window, and that is true whether or not anyone booked an
 * appointment. A schedule keyed on missed appointments would report a woman who never booked as
 * having missed nothing.
 */
public final class ContactScheduleEngine {

    private ContactScheduleEngine() {
    }

    public enum ContactStatus {
        /** Recorded as done. */
        COMPLETED,
        /** The window is open now and this contact has not been done — see her today. */
        DUE,
        /** The window has passed and it was not done. Not a closed door: catch it up now. */
        OVERDUE,
        /** The window has not opened yet. */
        UPCOMING,
        /**
         * The pregnancy has advanced well past this contact and it was never done. Distinct from
         * OVERDUE, which is recent enough to slot back into the normal schedule; this is a contact
         * that will be folded into the current one rather than held separately.
         */
        MISSED
    }

    /**
     * @param contactNumber   1..totalContacts
     * @param status          where this contact stands for this gestation
     * @param targetWeek      the gestational week it is aimed at
     * @param opensWeek       the earliest gestation it may count from
     * @param overdueAfterWeek the gestation past which it is overdue
     * @param keyActions      what this contact is for — carried so a late catch-up knows what to cover
     */
    public record ContactForecast(
            int contactNumber,
            String name,
            ContactStatus status,
            int targetWeek,
            int opensWeek,
            int overdueAfterWeek,
            List<String> keyActions,
            String note) {
    }

    /**
     * @param contacts        one forecast per scheduled contact, always the full set so a caller can
     *                        show the whole pathway rather than only the next step
     * @param nextContact     the contact to act on now, or null when post-term or all done
     * @param postTerm        true when the gestation is past the schedule and needs a birth plan
     * @param overdueContacts contacts open or passed and not done — the reason for an outreach call
     * @param gestationKnown  false when no gestational age was supplied; the schedule cannot be
     *                        placed and says so rather than guessing week 1
     */
    public record ScheduleForecast(
            List<ContactForecast> contacts,
            ContactForecast nextContact,
            boolean postTerm,
            String postTermMessage,
            List<Integer> overdueContacts,
            boolean gestationKnown,
            String note) {
    }

    /** One contact's timing, from governed content. */
    public record ContactDefinition(
            int contactNumber,
            String name,
            int targetWeek,
            int opensWeek,
            int overdueAfterWeek,
            List<String> keyActions,
            String note) {
    }

    /** The schedule as governed content. */
    public record Schedule(
            int totalContacts,
            List<ContactDefinition> contacts,
            int postTermFromWeek,
            String postTermMessage) {
    }

    /**
     * Forecast the schedule at a gestational age.
     *
     * @param gestationalAgeDays completed days of gestation, or null when unknown
     * @param completedContacts  contact numbers already recorded as done
     */
    public static ScheduleForecast forecast(Schedule schedule, Integer gestationalAgeDays,
                                            Set<Integer> completedContacts) {
        if (schedule == null || schedule.contacts().isEmpty()) {
            return new ScheduleForecast(List.of(), null, false, null, List.of(), false,
                    "No antenatal contact schedule is loaded, so no forecast could be made.");
        }
        Set<Integer> done = completedContacts == null ? Set.of() : completedContacts;

        if (gestationalAgeDays == null) {
            // No gestation. The schedule cannot be placed on the timeline, but the pathway is still
            // worth showing with its status left honest — every contact UPCOMING except those
            // recorded done — rather than silently assuming she is at week 1.
            List<ContactForecast> contacts = new ArrayList<>();
            for (ContactDefinition c : schedule.contacts()) {
                contacts.add(new ContactForecast(c.contactNumber(), c.name(),
                        done.contains(c.contactNumber()) ? ContactStatus.COMPLETED
                                : ContactStatus.UPCOMING,
                        c.targetWeek(), c.opensWeek(), c.overdueAfterWeek(), c.keyActions(), c.note()));
            }
            return new ScheduleForecast(List.copyOf(contacts), null, false, null, List.of(), false,
                    "Gestational age is not recorded, so the schedule cannot be placed. Date the "
                            + "pregnancy first — this is not a statement that no contact is due.");
        }

        int week = gestationalAgeDays / 7;
        boolean postTerm = week >= schedule.postTermFromWeek();

        List<ContactForecast> contacts = new ArrayList<>();
        List<Integer> overdue = new ArrayList<>();
        ContactForecast next = null;

        for (ContactDefinition c : schedule.contacts()) {
            ContactStatus status;
            if (done.contains(c.contactNumber())) {
                status = ContactStatus.COMPLETED;
            } else if (week < c.opensWeek()) {
                status = ContactStatus.UPCOMING;
            } else if (week <= c.overdueAfterWeek()) {
                status = ContactStatus.DUE;
            } else if (postTerm || farPast(week, c.overdueAfterWeek())) {
                status = ContactStatus.MISSED;
            } else {
                status = ContactStatus.OVERDUE;
            }

            ContactForecast forecast = new ContactForecast(c.contactNumber(), c.name(), status,
                    c.targetWeek(), c.opensWeek(), c.overdueAfterWeek(), c.keyActions(), c.note());
            contacts.add(forecast);

            if (status == ContactStatus.OVERDUE || status == ContactStatus.MISSED) {
                overdue.add(c.contactNumber());
            }
            // The next actionable contact is the earliest one that is due or overdue, or failing
            // that the earliest upcoming one — so a woman with everything done so far still sees
            // what is coming.
            if (next == null && (status == ContactStatus.DUE || status == ContactStatus.OVERDUE)) {
                next = forecast;
            }
        }
        if (next == null && !postTerm) {
            next = contacts.stream()
                    .filter(f -> f.status() == ContactStatus.UPCOMING)
                    .findFirst().orElse(null);
        }

        return new ScheduleForecast(
                List.copyOf(contacts),
                postTerm ? null : next,
                postTerm,
                postTerm ? schedule.postTermMessage() : null,
                List.copyOf(overdue),
                true,
                note(postTerm, next, overdue));
    }

    /**
     * A contact more than four weeks past its overdue point is folded into the current contact
     * rather than chased as a separate overdue item — the pregnancy has moved on and the earlier
     * contact's window cannot be recreated. Four weeks is an engineering seed.
     */
    private static boolean farPast(int week, int overdueAfterWeek) {
        return week > overdueAfterWeek + 4;
    }

    private static String note(boolean postTerm, ContactForecast next, List<Integer> overdue) {
        if (postTerm) {
            return "Post-term. The routine schedule no longer applies; she needs an active plan for "
                    + "birth and increased fetal surveillance.";
        }
        if (!overdue.isEmpty()) {
            return "Contact(s) " + overdue + " are overdue. Catch up the missed content at the next "
                    + "contact rather than treating it as a fresh start.";
        }
        if (next != null) {
            return "On schedule. Next: " + next.name() + " around week " + next.targetWeek() + ".";
        }
        return "All scheduled contacts are recorded as done.";
    }
}
