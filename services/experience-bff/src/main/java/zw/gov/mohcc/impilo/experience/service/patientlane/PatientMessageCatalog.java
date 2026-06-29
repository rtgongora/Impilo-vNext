package zw.gov.mohcc.impilo.experience.service.patientlane;

import java.util.Map;

/**
 * Plain-language patient message catalog for the patient lane (SYS-3 / G-CT-01).
 *
 * <p>Maps the canonical PCT {@code JourneyState} and inpatient admission status to a
 * person-centered, plain-language status the citizen sees — keyed to the person-journey
 * stage taxonomy ({@code core-transaction.ts} {@code getPersonJourneyStageForTransactionState}).
 * Each message carries a stable {@code messageKey} so it can be localised later without
 * changing the composition code (multilingual-ready). The catalog never invents clinical
 * detail; it translates a known lifecycle state into language a patient understands.
 */
public final class PatientMessageCatalog {

    private PatientMessageCatalog() {
    }

    /** A patient-facing status line. */
    public record PatientStatusMessage(String stage, String label, String message, String messageKey) {
    }

    private static final PatientStatusMessage UNKNOWN = new PatientStatusMessage(
            "UNKNOWN", "Status unavailable",
            "We can't show this visit's status right now. Please check with the front desk.",
            "patient.visit.unknown");

    // Keyed by PCT JourneyState name. Conservative + person-centered; sensitive terminal states
    // (death) are surfaced neutrally to the patient lane, never with a chirpy message.
    private static final Map<String, PatientStatusMessage> JOURNEY = Map.ofEntries(
            Map.entry("REGISTRATION_PENDING", new PatientStatusMessage(
                    "IDENTIFY_ME", "Checking you in",
                    "We're getting you checked in. This won't take long.",
                    "patient.visit.registration_pending")),
            Map.entry("ARRIVED", new PatientStatusMessage(
                    "CHECK_IN", "Checked in",
                    "You're checked in. Please wait to be called for triage.",
                    "patient.visit.arrived")),
            Map.entry("TRIAGED", new PatientStatusMessage(
                    "PREPARE_FOR_CARE", "Triage done",
                    "Your triage is complete. You're in the queue to see a provider.",
                    "patient.visit.triaged")),
            Map.entry("QUEUED", new PatientStatusMessage(
                    "PREPARE_FOR_CARE", "In the queue",
                    "You're in the queue. Please stay nearby — we'll call you soon.",
                    "patient.visit.queued")),
            Map.entry("IN_SERVICE", new PatientStatusMessage(
                    "RECEIVE_CARE", "Being seen",
                    "You're being seen now.",
                    "patient.visit.in_service")),
            Map.entry("ADMITTED", new PatientStatusMessage(
                    "RECEIVE_CARE", "Admitted",
                    "You've been admitted. Your care continues on the ward.",
                    "patient.visit.admitted")),
            Map.entry("TRANSFERRED", new PatientStatusMessage(
                    "RECEIVE_CARE", "Transferred",
                    "You've been moved to continue your care.",
                    "patient.visit.transferred")),
            Map.entry("DISCHARGE_PENDING", new PatientStatusMessage(
                    "KNOW_WHAT_HAPPENED", "Getting ready to go home",
                    "Your discharge is being prepared. Please wait for your instructions.",
                    "patient.visit.discharge_pending")),
            Map.entry("DISCHARGED", new PatientStatusMessage(
                    "CONTINUE_CARE", "Discharged",
                    "You've been discharged. Please follow any instructions you were given.",
                    "patient.visit.discharged")),
            Map.entry("DEATH_RECORDED", new PatientStatusMessage(
                    "CONTINUE_CARE", "Visit closed",
                    "This visit is now closed.",
                    "patient.visit.closed")),
            Map.entry("CANCELLED", new PatientStatusMessage(
                    "CONTINUE_CARE", "Cancelled",
                    "This visit was cancelled.",
                    "patient.visit.cancelled")),
            Map.entry("NO_SHOW", new PatientStatusMessage(
                    "CONTINUE_CARE", "Missed",
                    "This visit was marked as missed. You can book again when you're ready.",
                    "patient.visit.no_show")),
            Map.entry("LEFT_WITHOUT_BEING_SEEN", new PatientStatusMessage(
                    "CONTINUE_CARE", "Left before being seen",
                    "This visit ended before you were seen. You can book again when you're ready.",
                    "patient.visit.lwbs")));

    /** Resolves the patient-facing message for a PCT journey state (null/unknown -> a safe default). */
    public static PatientStatusMessage forJourneyState(String state) {
        if (state == null) {
            return UNKNOWN;
        }
        return JOURNEY.getOrDefault(state.trim().toUpperCase(), UNKNOWN);
    }

    /** Resolves the patient-facing message for an inpatient admission (active vs discharged). */
    public static PatientStatusMessage forInpatient(boolean active) {
        return active
                ? new PatientStatusMessage("RECEIVE_CARE", "On the ward",
                "You're admitted and being cared for on the ward.", "patient.inpatient.active")
                : new PatientStatusMessage("CONTINUE_CARE", "Discharged",
                "Your inpatient stay has ended.", "patient.inpatient.discharged");
    }
}
