package zw.gov.mohcc.impilo.clinical.maternal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates a time-critical treatment bundle — postpartum haemorrhage, eclampsia, sepsis — against
 * what has actually been done, and decides whether it may be closed.
 *
 * <p>Pure and stateless. The episode is a caller-persisted record; this engine is handed the bundle
 * definition, the steps recorded so far, and the temporal facts already computed in Java (minutes
 * since the bundle was triggered, minutes since the last observation), and it returns a verdict. No
 * clock, no store, no temporal operator added to the shared predicate evaluator — a review of a past
 * emergency reproduces exactly.
 *
 * <p>Three properties, each a way an emergency checklist lies, and each one the plan singled out:
 *
 * <ul>
 *   <li><b>A freshly-triggered bundle reports every mandatory step outstanding — never "0
 *       outstanding".</b> The moment PPH is called, nothing has been done yet, and a display that
 *       shows an empty checklist reads as a completed one. This is a bug that shipped here once; the
 *       engine makes the opposite impossible.</li>
 *   <li><b>It never closes itself, and UNKNOWN never closes it.</b> Closure requires every mandatory
 *       step done AND the control predicate explicitly TRUE (bleeding stopped, confirmed) AND an
 *       explicit clinician confirmation. A missing observation is not "controlled".</li>
 *   <li><b>An open bleeding episode that stops being documented is an escalation, not a
 *       resolution.</b> With no observation for the lapse window the status is
 *       {@link BundleStatus#LAPSED_UNRESOLVED}, never CLOSED — silence in an active haemorrhage is
 *       the most dangerous state, not the reassuring one.</li>
 * </ul>
 */
public final class EmergencyBundleEngine {

    private EmergencyBundleEngine() {
    }

    public enum StepStatus {
        DONE,
        /** Its window is open and it is not done — do it now. */
        DUE,
        /** Its window has passed and it is not done. */
        OVERDUE,
        /** Its window has not opened yet (a later step in the sequence). */
        NOT_YET_DUE
    }

    public enum BundleStatus {
        /** Triggered and in progress — mandatory steps outstanding. */
        ACTIVE,
        /** Every mandatory step done and control confirmed — a clinician may now close it. */
        CLOSABLE,
        /** No observation for the lapse window on an unclosed episode. An escalation, not a close. */
        LAPSED_UNRESOLVED
    }

    /**
     * @param dueWithinMinutes minutes from trigger within which this step should be done
     * @param mandatory        a bundle cannot close while a mandatory step is undone
     */
    public record StepDefinition(String code, String name, int dueWithinMinutes, boolean mandatory,
                                 String action) {
    }

    public record Bundle(String bundleId, String name, int lapseWindowMinutes,
                         List<StepDefinition> steps) {
    }

    public record StepVerdict(String code, String name, StepStatus status, boolean mandatory,
                              String action) {
    }

    /**
     * @param status              overall bundle state
     * @param steps               every step with its status — the full checklist, never a filtered one
     * @param outstandingMandatory mandatory steps not yet done, most-overdue framing
     * @param mayClose            true only when every mandatory step is done, control is confirmed TRUE,
     *                            and a clinician confirmation was supplied
     * @param note                what to do now
     */
    public record Assessment(
            BundleStatus status,
            List<StepVerdict> steps,
            List<String> outstandingMandatory,
            boolean mayClose,
            String note) {
    }

    /**
     * @param completedStepCodes    step codes recorded as done
     * @param minutesSinceTrigger   computed in Java and injected
     * @param minutesSinceLastObservation minutes since anything was documented on this episode; a
     *                              value greater than the lapse window with mandatory steps still
     *                              open is the escalation case
     * @param controlConfirmedTrue  the control predicate (e.g. bleeding stopped) evaluated to TRUE —
     *                              distinct from a null/unknown, which must never permit closure
     * @param clinicianConfirmedClose a clinician has explicitly confirmed closure
     */
    public static Assessment assess(Bundle bundle,
                                    Set<String> completedStepCodes,
                                    long minutesSinceTrigger,
                                    long minutesSinceLastObservation,
                                    Boolean controlConfirmedTrue,
                                    boolean clinicianConfirmedClose) {
        if (bundle == null || bundle.steps().isEmpty()) {
            return new Assessment(BundleStatus.ACTIVE, List.of(), List.of(), false,
                    "No bundle definition is loaded, so no step could be assessed. This is not a "
                            + "statement that the emergency is resolved.");
        }
        Set<String> done = completedStepCodes == null ? Set.of() : completedStepCodes;

        List<StepVerdict> verdicts = new ArrayList<>();
        List<String> outstandingMandatory = new ArrayList<>();
        for (StepDefinition step : bundle.steps()) {
            StepStatus status;
            if (done.contains(step.code())) {
                status = StepStatus.DONE;
            } else if (minutesSinceTrigger >= step.dueWithinMinutes()) {
                // Past its window and not done. At trigger (minutesSinceTrigger = 0) a step due
                // "within 0 minutes" is already OVERDUE, which is the point: a freshly-called PPH
                // shows its immediate steps outstanding, not an empty checklist.
                status = StepStatus.OVERDUE;
            } else if (minutesSinceTrigger >= 0) {
                status = StepStatus.DUE;
            } else {
                status = StepStatus.NOT_YET_DUE;
            }
            verdicts.add(new StepVerdict(step.code(), step.name(), status, step.mandatory(),
                    step.action()));
            if (step.mandatory() && status != StepStatus.DONE) {
                outstandingMandatory.add(step.code());
            }
        }

        // Lapse: an unclosed episode with mandatory steps outstanding and nothing documented for the
        // lapse window is an escalation. Documented silence in an active haemorrhage is the worst
        // state, and it must not be mistaken for control.
        if (!outstandingMandatory.isEmpty()
                && minutesSinceLastObservation > bundle.lapseWindowMinutes()) {
            return new Assessment(BundleStatus.LAPSED_UNRESOLVED, List.copyOf(verdicts),
                    List.copyOf(outstandingMandatory), false,
                    "No observation for " + minutesSinceLastObservation + " minutes on an open "
                            + bundle.name() + " with " + outstandingMandatory.size()
                            + " mandatory step(s) outstanding. Escalate now — this is not a resolved "
                            + "episode, it is an undocumented one.");
        }

        boolean allMandatoryDone = outstandingMandatory.isEmpty();
        // Closure needs the control predicate explicitly TRUE — a null/unknown never closes it —
        // AND a clinician's explicit confirmation. The engine never closes on its own.
        boolean mayClose = allMandatoryDone
                && Boolean.TRUE.equals(controlConfirmedTrue)
                && clinicianConfirmedClose;

        BundleStatus status = mayClose ? BundleStatus.CLOSABLE : BundleStatus.ACTIVE;
        return new Assessment(status, List.copyOf(verdicts), List.copyOf(outstandingMandatory),
                mayClose, note(allMandatoryDone, controlConfirmedTrue, clinicianConfirmedClose,
                outstandingMandatory));
    }

    private static String note(boolean allMandatoryDone, Boolean controlConfirmedTrue,
                               boolean clinicianConfirmedClose, List<String> outstanding) {
        if (!allMandatoryDone) {
            return outstanding.size() + " mandatory step(s) still outstanding. The bundle cannot be "
                    + "closed until they are done.";
        }
        if (!Boolean.TRUE.equals(controlConfirmedTrue)) {
            return "Every step is done, but control is not confirmed (bleeding stopped / stable). "
                    + "The bundle stays open until control is explicitly confirmed — an unconfirmed "
                    + "control is not a resolved emergency.";
        }
        if (!clinicianConfirmedClose) {
            return "Steps done and control confirmed. A clinician must explicitly close the bundle; "
                    + "it does not close itself.";
        }
        return "All mandatory steps done, control confirmed, closure confirmed — the bundle may close.";
    }
}
