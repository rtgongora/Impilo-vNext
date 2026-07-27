package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The postpartum-haemorrhage first-response bundle.
 *
 * <p>Written around the three ways an emergency checklist lies, each the plan singled out: a
 * freshly-called PPH must not read as done; it must never close itself or close on an unconfirmed
 * control; and an active bleed that stops being documented is an escalation, not a resolution.
 */
class PphBundleTest {

    private final EmergencyBundleService service = new EmergencyBundleService(new ObjectMapper());

    private EmergencyBundleEngine.Assessment freshlyCalled() {
        // Just triggered: nothing done, nothing yet lapsed.
        return service.assessPph(Set.of(), 0, 0, null, false);
    }

    // ---------------------------------------------------------------- fresh trigger is not "done"

    @Test
    @DisplayName("a freshly-called PPH shows every mandatory step outstanding, never zero")
    void freshlyCalledIsNotComplete() {
        var a = freshlyCalled();

        assertThat(a.status()).isEqualTo(EmergencyBundleEngine.BundleStatus.ACTIVE);
        assertThat(a.outstandingMandatory())
                .as("an empty checklist at trigger would read as completed")
                .isNotEmpty();
        // The immediate elements (due within 0 minutes) are already OVERDUE at trigger — the point
        // of the bundle is that they happen at once.
        assertThat(a.steps()).anyMatch(s -> s.status() == EmergencyBundleEngine.StepStatus.OVERDUE);
        assertThat(a.mayClose()).isFalse();
    }

    @Test
    @DisplayName("the full checklist is always returned, mandatory steps flagged")
    void theWholeChecklistIsReturned() {
        var a = freshlyCalled();
        assertThat(a.steps()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(a.steps()).anyMatch(s -> "PPH_UTEROTONIC".equals(s.code()) && s.mandatory());
    }

    // ---------------------------------------------------------------- never closes itself

    @Test
    @DisplayName("all mandatory steps done but control not confirmed does NOT close")
    void allStepsDoneButControlUnconfirmedDoesNotClose() {
        Set<String> done = Set.of("PPH_MASSAGE", "PPH_UTEROTONIC", "PPH_TXA", "PPH_IV_FLUIDS",
                "PPH_EXAMINE_CAUSE", "PPH_ESCALATE");
        var a = service.assessPph(done, 20, 2, null, true);

        // Control predicate is null (unknown), so it may not close even with a clinician confirm.
        assertThat(a.mayClose()).isFalse();
        assertThat(a.status()).isEqualTo(EmergencyBundleEngine.BundleStatus.ACTIVE);
        assertThat(a.note()).contains("control is not confirmed");
    }

    @Test
    @DisplayName("steps done and control confirmed but no clinician confirmation does NOT close")
    void needsExplicitClinicianClose() {
        Set<String> done = Set.of("PPH_MASSAGE", "PPH_UTEROTONIC", "PPH_TXA", "PPH_IV_FLUIDS",
                "PPH_EXAMINE_CAUSE", "PPH_ESCALATE");
        var a = service.assessPph(done, 20, 2, true, false);

        assertThat(a.mayClose()).isFalse();
        assertThat(a.note()).contains("must explicitly close");
    }

    @Test
    @DisplayName("only when every mandatory step is done AND control confirmed AND clinician confirms may it close")
    void closesOnlyWithEverything() {
        Set<String> done = Set.of("PPH_MASSAGE", "PPH_UTEROTONIC", "PPH_TXA", "PPH_IV_FLUIDS",
                "PPH_EXAMINE_CAUSE", "PPH_ESCALATE");
        var a = service.assessPph(done, 25, 2, true, true);

        assertThat(a.status()).isEqualTo(EmergencyBundleEngine.BundleStatus.CLOSABLE);
        assertThat(a.mayClose()).isTrue();
    }

    // ---------------------------------------------------------------- silence is escalation

    @Test
    @DisplayName("an open PPH with no observation for the lapse window is LAPSED_UNRESOLVED, not closed")
    void undocumentedActiveBleedIsEscalation() {
        // Mandatory steps outstanding, and nothing documented for longer than the 15-minute window.
        var a = service.assessPph(Set.of("PPH_MASSAGE"), 40, 25, null, false);

        assertThat(a.status()).isEqualTo(EmergencyBundleEngine.BundleStatus.LAPSED_UNRESOLVED);
        assertThat(a.mayClose()).isFalse();
        assertThat(a.note()).contains("Escalate now");
        assertThat(a.note()).contains("not a resolved episode");
    }

    @Test
    @DisplayName("a lapse cannot masquerade as control — it never reports CLOSABLE")
    void lapseIsNeverClosable() {
        var a = service.assessPph(Set.of("PPH_MASSAGE"), 40, 25, true, true);
        // Even with control+clinician confirm flags set, outstanding mandatory steps + a documentation
        // lapse escalate rather than close — you cannot confirm control of a bleed nobody is watching.
        assertThat(a.status()).isEqualTo(EmergencyBundleEngine.BundleStatus.LAPSED_UNRESOLVED);
    }

    // ---------------------------------------------------------------- missing content

    @Test
    @DisplayName("a missing bundle reports active-unresolved, never that the emergency is over")
    void missingBundleIsNeverResolved() {
        var a = EmergencyBundleEngine.assess(service.bundle("clinical/does-not-exist.json"),
                Set.of(), 0, 0, true, true);
        assertThat(a.mayClose()).isFalse();
        assertThat(a.note()).contains("not a statement that the emergency is resolved");
    }
}
