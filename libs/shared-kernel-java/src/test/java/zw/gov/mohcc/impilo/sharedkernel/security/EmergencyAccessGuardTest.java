package zw.gov.mohcc.impilo.sharedkernel.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * All three branches of the break-glass guard.
 *
 * <p>The third — trust plane unreachable — is the one that will be got wrong, and the assertion
 * that matters there is on the <b>audit write</b>, not on the allow. An allow without a record
 * passes a naive test and is precisely the ungoverned override this mechanism exists to make
 * visible.</p>
 */
class EmergencyAccessGuardTest {

    private static final String TENANT = "00000000-0000-0000-4000-800000000001";
    private static final String ACTOR = "actor-1";
    private static final String SUBJECT = "HID-1";
    private static final String ACTION = "O_NEG_EMERGENCY_RELEASE";

    /** Collects what the guard recorded, so a test can assert the write actually happened. */
    private static final class CapturingRecorder implements UngovernedOverrideRecorder {
        private final List<UngovernedOverride> recorded = new ArrayList<>();

        @Override
        public void record(UngovernedOverride override) {
            recorded.add(override);
        }
    }

    /** A recorder whose durable store is gone. */
    private static final class FailingRecorder implements UngovernedOverrideRecorder {
        @Override
        public void record(UngovernedOverride override) {
            throw new IllegalStateException("outbox write failed");
        }
    }

    private static BreakGlassGrantQuery query(String purposeOfUse) {
        return new BreakGlassGrantQuery(TENANT, ACTOR, null, purposeOfUse, ACTION, SUBJECT);
    }

    private static BreakGlassGrantClient clientReturning(BreakGlassGrantCheck check) {
        return q -> check;
    }

    // ════════════════════════════════════════════════════════════════════
    // purpose-of-use — the pre-existing gate, still first
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("isEmergencyPurpose recognises EMERGENCY and BREAK_GLASS, case-insensitively")
    void recognisesEmergencyPurposes() {
        assertTrue(EmergencyAccessGuard.isEmergencyPurpose("EMERGENCY"));
        assertTrue(EmergencyAccessGuard.isEmergencyPurpose("break_glass"));
        assertFalse(EmergencyAccessGuard.isEmergencyPurpose("TREATMENT"));
        assertFalse(EmergencyAccessGuard.isEmergencyPurpose(null));
    }

    @Test
    @DisplayName("non-emergency purpose is refused without ever consulting the trust plane")
    void nonEmergencyPurposeRefusedBeforeGrantCheck() {
        CapturingRecorder recorder = new CapturingRecorder();
        boolean[] consulted = {false};
        BreakGlassGrantClient client = q -> {
            consulted[0] = true;
            return BreakGlassGrantCheck.valid("BREAK_GLASS_REQUEST", "grant-1");
        };

        assertThrows(EmergencyAccessGuard.EmergencyAccessDeniedException.class,
                () -> EmergencyAccessGuard.requireBreakGlass(client, recorder, query("TREATMENT")));
        assertThrows(EmergencyAccessGuard.EmergencyAccessDeniedException.class,
                () -> EmergencyAccessGuard.requireBreakGlass(client, recorder, query(null)));

        assertFalse(consulted[0], "a non-emergency purpose should not reach the trust plane at all");
        assertTrue(recorder.recorded.isEmpty(), "a refusal is not an ungoverned override");
    }

    // ════════════════════════════════════════════════════════════════════
    // branch 1 — valid grant → allowed
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("VALID grant: allowed, marked governed, grant reference carried into the decision")
    void validGrantIsAllowed() {
        CapturingRecorder recorder = new CapturingRecorder();
        BreakGlassGrantClient client =
                clientReturning(BreakGlassGrantCheck.valid("BREAK_GLASS_REQUEST", "grant-42"));

        EmergencyAccessGuard.Decision d =
                EmergencyAccessGuard.requireBreakGlass(client, recorder, query("EMERGENCY"));

        assertTrue(d.breakGlass());
        assertTrue(d.governed(), "a validated grant is a governed override");
        assertEquals("BREAK_GLASS", d.mode());
        assertEquals(ACTION, d.action());
        assertEquals(SUBJECT, d.subjectRef());
        assertEquals("grant-42", d.grantRef());
        assertTrue(recorder.recorded.isEmpty(), "a governed override is not an ungoverned one");
    }

    // ════════════════════════════════════════════════════════════════════
    // branch 2 — no grant → refused
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("NO_GRANT: refused, even though the purpose-of-use said EMERGENCY")
    void noGrantIsRefused() {
        CapturingRecorder recorder = new CapturingRecorder();
        BreakGlassGrantClient client = clientReturning(BreakGlassGrantCheck.noGrant());

        EmergencyAccessGuard.EmergencyAccessDeniedException e = assertThrows(
                EmergencyAccessGuard.EmergencyAccessDeniedException.class,
                () -> EmergencyAccessGuard.requireBreakGlass(client, recorder, query("BREAK_GLASS")));

        // This is the forged-purpose hole closing: the caller asserted the emergency purpose and
        // still did not get through.
        assertTrue(e.getMessage().contains("active break-glass grant"));
        assertTrue(recorder.recorded.isEmpty(),
                "a refusal must not be recorded as an ungoverned override — nothing was overridden");
    }

    // ════════════════════════════════════════════════════════════════════
    // branch 3 — unreachable → allowed AND recorded
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("UNREACHABLE: allowed AND an ungoverned override is durably recorded")
    void unreachableIsAllowedAndRecorded() {
        CapturingRecorder recorder = new CapturingRecorder();
        BreakGlassGrantClient client = clientReturning(
                BreakGlassGrantCheck.unreachable("connect timed out after 2000ms"));

        EmergencyAccessGuard.Decision d =
                EmergencyAccessGuard.requireBreakGlass(client, recorder, query("EMERGENCY"));

        // The allow — emergency care is not blocked by a policy-service outage.
        assertTrue(d.breakGlass());

        // The part a naive test would miss. An allow that produced no record is the ungoverned
        // override occurring silently, which is the whole thing this exists to prevent.
        assertEquals(1, recorder.recorded.size(), "the audit record MUST be written, not just the allow");

        UngovernedOverride o = recorder.recorded.get(0);
        assertEquals(ACTOR, o.actorId());
        assertEquals(ACTION, o.action());
        assertEquals(SUBJECT, o.subjectRef());
        assertEquals(TENANT, o.tenantId());
        assertEquals("EMERGENCY", o.purposeOfUse());
        assertEquals("connect timed out after 2000ms", o.reason(),
                "the reviewer needs to know WHY it could not be checked");
        assertNotNull(o.reviewObligation());
        assertTrue(o.reviewObligation().contains("REVIEW"),
                "an override with no named review obligation is a record nobody owns");
        assertNotNull(o.occurredAt());
    }

    @Test
    @DisplayName("UNREACHABLE: the decision is marked ungoverned, distinguishable from a validated one")
    void unreachableDecisionIsMarkedUngoverned() {
        BreakGlassGrantClient client =
                clientReturning(BreakGlassGrantCheck.unreachable("connection refused"));

        EmergencyAccessGuard.Decision d = EmergencyAccessGuard.requireBreakGlass(
                client, new CapturingRecorder(), query("EMERGENCY"));

        assertFalse(d.governed(), "nothing was validated; the decision must not claim it was");
        assertEquals("BREAK_GLASS_UNGOVERNED", d.mode());
        assertNull(d.grantRef(), "there is no grant to reference");
    }

    @Test
    @DisplayName("UNREACHABLE with a recorder that cannot write: the action is REFUSED, never silently allowed")
    void unreachableWithUnwritableRecorderRefuses() {
        BreakGlassGrantClient client =
                clientReturning(BreakGlassGrantCheck.unreachable("connect timed out"));

        // If the recorder's failure were swallowed, this would return a Decision and the override
        // would happen with no trace anywhere. The guard must not have a catch here.
        assertThrows(IllegalStateException.class,
                () -> EmergencyAccessGuard.requireBreakGlass(client, new FailingRecorder(), query("EMERGENCY")));
    }

    // ════════════════════════════════════════════════════════════════════
    // the distinction itself
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("NO_GRANT and UNREACHABLE reach opposite outcomes — the distinction is load-bearing")
    void refusedAndCouldNotAskAreNotTheSameBranch() {
        CapturingRecorder refusedRecorder = new CapturingRecorder();
        assertThrows(EmergencyAccessGuard.EmergencyAccessDeniedException.class,
                () -> EmergencyAccessGuard.requireBreakGlass(
                        clientReturning(BreakGlassGrantCheck.noGrant()), refusedRecorder, query("EMERGENCY")));

        CapturingRecorder outageRecorder = new CapturingRecorder();
        EmergencyAccessGuard.Decision allowed = EmergencyAccessGuard.requireBreakGlass(
                clientReturning(BreakGlassGrantCheck.unreachable("no route to host")),
                outageRecorder, query("EMERGENCY"));

        // Same purpose, same actor, same action — opposite outcomes, decided solely by whether the
        // trust plane answered. Were these to collapse into one branch, the fix would be a
        // re-run of the defect it replaces.
        assertTrue(allowed.breakGlass());
        assertEquals(0, refusedRecorder.recorded.size());
        assertEquals(1, outageRecorder.recorded.size());
    }

    @Test
    @DisplayName("the guard refuses to be constructed without its collaborators")
    void collaboratorsAreRequired() {
        assertThrows(NullPointerException.class, () -> EmergencyAccessGuard.requireBreakGlass(
                null, new CapturingRecorder(), query("EMERGENCY")));
        assertThrows(NullPointerException.class, () -> EmergencyAccessGuard.requireBreakGlass(
                clientReturning(BreakGlassGrantCheck.noGrant()), null, query("EMERGENCY")));
    }
}
