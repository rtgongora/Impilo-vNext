package zw.gov.mohcc.impilo.sharedkernel.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three branches of the emergency-access ruling, asserted separately.
 *
 * <pre>
 *   VALID        → allow
 *   NO_GRANT     → refuse
 *   UNREACHABLE  → allow AND record an ungoverned override
 * </pre>
 *
 * <p>The third is the one that gets got wrong, in two distinct ways: by folding a transport failure
 * into "no grant" (which would block a transfusion during an outage), or by allowing without writing
 * the record (which would make the ruling unauditable). Both are asserted here, and the second is
 * asserted on the <em>write</em>, not on the allow.</p>
 */
class EmergencyAccessGuardTest {

    private static final Class<EmergencyAccessGuard.EmergencyAccessDeniedException> DENIED =
            EmergencyAccessGuard.EmergencyAccessDeniedException.class;

    /** Collects overrides so the UNREACHABLE branch can be asserted on the record, not the return. */
    private final List<UngovernedOverrideRecorder.UngovernedOverride> recorded = new ArrayList<>();
    private final UngovernedOverrideRecorder recorder = recorded::add;

    private EmergencyAccessGuard guardThatAnswers(EscalationGrantValidator.Outcome outcome) {
        return new EmergencyAccessGuard((t, a, g) -> outcome, recorder);
    }

    private static EmergencyAccessGuard.EmergencyAccessRequest release(String grantId) {
        return new EmergencyAccessGuard.EmergencyAccessRequest(
                "tenant-1", "clinician-7", "BREAK_GLASS",
                "O_NEG_EMERGENCY_RELEASE", "CPID-42", grantId);
    }

    // ── VALID → allow ─────────────────────────────────────────────────────────

    @Test
    void aConfirmedGrantAllowsAndIsMarkedGoverned() {
        EmergencyAccessGuard.Decision d =
                guardThatAnswers(EscalationGrantValidator.Outcome.VALID).requireBreakGlass(release("g-1"));

        assertTrue(d.breakGlass());
        assertTrue(d.governed());
        assertEquals("BREAK_GLASS", d.mode());
        assertTrue(recorded.isEmpty(), "a governed override is not an ungoverned one");
    }

    // ── NO_GRANT → refuse ─────────────────────────────────────────────────────

    /** The forged-purpose hole. Purpose alone used to be the entire check. */
    @Test
    void anEmergencyPurposeWithNoGrantIsRefused() {
        var ex = assertThrows(DENIED, () ->
                guardThatAnswers(EscalationGrantValidator.Outcome.NO_GRANT)
                        .requireBreakGlass(release("g-1")));

        assertTrue(ex.getMessage().contains("requires an active escalation grant"), ex.getMessage());
        assertTrue(recorded.isEmpty(), "a refusal is not an override");
    }

    @Test
    void presentingNoGrantAtAllIsRefusedRatherThanSkippingTheCheck() {
        assertThrows(DENIED, () ->
                guardThatAnswers(EscalationGrantValidator.Outcome.NO_GRANT)
                        .requireBreakGlass(release(null)));
    }

    /**
     * The refusal has to tell a clinician what to do next. They are mid-emergency; a bare refusal is
     * not an instruction.
     */
    @Test
    void theRefusalNamesTheWayToGetAGrant() {
        var ex = assertThrows(DENIED, () ->
                guardThatAnswers(EscalationGrantValidator.Outcome.NO_GRANT)
                        .requireBreakGlass(release("g-1")));

        assertTrue(ex.getMessage().contains("break-glass workflow"), ex.getMessage());
    }

    // ── UNREACHABLE → allow AND record ────────────────────────────────────────

    /** The clinical half of the ruling: an outage must not block a transfusion. */
    @Test
    void anUnreachableTrustPlaneDoesNotBlockTheEmergencyAction() {
        EmergencyAccessGuard.Decision d =
                guardThatAnswers(EscalationGrantValidator.Outcome.UNREACHABLE)
                        .requireBreakGlass(release("g-1"));

        assertTrue(d.breakGlass());
    }

    /**
     * The governance half, and the assertion that actually matters on this branch. Allowing without
     * recording would make the ruling unauditable — nobody could find these to review them.
     */
    @Test
    void anUnreachableTrustPlaneRecordsAnUngovernedOverride() {
        guardThatAnswers(EscalationGrantValidator.Outcome.UNREACHABLE).requireBreakGlass(release("g-1"));

        assertEquals(1, recorded.size(), "the allow must be accompanied by a record");
        UngovernedOverrideRecorder.UngovernedOverride o = recorded.get(0);
        assertEquals("tenant-1", o.tenantId());
        assertEquals("clinician-7", o.actorId());
        assertEquals("O_NEG_EMERGENCY_RELEASE", o.action());
        assertEquals("CPID-42", o.subjectRef());
        assertEquals("g-1", o.grantId(),
                "presented-but-unverifiable differs from presented-nothing, for a reviewer");
        assertTrue(o.reason().contains("unreachable"), o.reason());
        assertTrue(o.reason().contains("post-hoc review"), o.reason());
    }

    @Test
    void theUngovernedDecisionSaysItWasNotGoverned() {
        EmergencyAccessGuard.Decision d =
                guardThatAnswers(EscalationGrantValidator.Outcome.UNREACHABLE)
                        .requireBreakGlass(release("g-1"));

        assertFalse(d.governed());
        assertEquals("BREAK_GLASS_UNGOVERNED", d.mode());
    }

    /**
     * An override that cannot be recorded must be loud rather than silently permissive: the record
     * is the reason this branch is allowed at all, so losing it is not a detail.
     */
    @Test
    void anOverrideThatCannotBeRecordedFailsRatherThanAllowingSilently() {
        EmergencyAccessGuard guard = new EmergencyAccessGuard(
                (t, a, g) -> EscalationGrantValidator.Outcome.UNREACHABLE,
                o -> {
                    throw new IllegalStateException("outbox down");
                });

        assertThrows(IllegalStateException.class, () -> guard.requireBreakGlass(release("g-1")));
    }

    // ── the purpose gate still applies, and short-circuits ────────────────────

    /**
     * Also proves the ordering: a non-emergency purpose is refused without the trust plane being
     * consulted at all, so a grant service outage cannot turn an ordinary request into an override.
     */
    @ParameterizedTest
    @ValueSource(strings = {"TREATMENT", "PAYMENT", "OPERATIONS"})
    void aNonEmergencyPurposeIsRefusedWithoutEvenAskingTheTrustPlane(String purpose) {
        EmergencyAccessGuard guard = new EmergencyAccessGuard(
                (t, a, g) -> {
                    throw new AssertionError("must not consult the grant service");
                },
                recorder);

        var ex = assertThrows(DENIED, () -> guard.requireBreakGlass(
                new EmergencyAccessGuard.EmergencyAccessRequest(
                        "tenant-1", "clinician-7", purpose, "MHP_ACTIVATION", "CPID-42", "g-1")));

        assertTrue(ex.getMessage().contains("EMERGENCY/BREAK_GLASS"), ex.getMessage());
        assertTrue(recorded.isEmpty());
    }

    @Test
    void bothCollaboratorsAreRequired() {
        assertThrows(NullPointerException.class,
                () -> new EmergencyAccessGuard((t, a, g) -> EscalationGrantValidator.Outcome.VALID, null));
        assertThrows(NullPointerException.class,
                () -> new EmergencyAccessGuard(null, recorder));
    }
}
