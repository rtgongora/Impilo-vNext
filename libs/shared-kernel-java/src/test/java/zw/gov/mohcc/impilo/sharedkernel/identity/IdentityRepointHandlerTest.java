package zw.gov.mohcc.impilo.sharedkernel.identity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for the {@code vito.merge.executed} propagation channel skeleton:
 * payload parsing, hook dispatch, and idempotency.
 */
class IdentityRepointHandlerTest {

    private static final String SURVIVOR = "11111111-1111-1111-1111-111111111111";
    private static final String MERGED = "22222222-2222-2222-2222-222222222222";
    private static final String TENANT = "00000000-0000-0000-0000-0000000000aa";

    /** Recording hook: counts invocations and returns a fixed row count. */
    static final class RecordingHook implements IdentityRepointHook {
        final List<IdentityRepointCommand> calls = new ArrayList<>();
        final int rows;
        RecordingHook(int rows) { this.rows = rows; }
        @Override public int repoint(IdentityRepointCommand c) { calls.add(c); return rows; }
        @Override public String participant() { return "test-service"; }
    }

    private String mergePayload() {
        return "{\"tenantId\":\"" + TENANT + "\",\"survivor\":\"crid-s\",\"merged\":\"crid-m\","
                + "\"survivorHealthId\":\"" + SURVIVOR + "\",\"mergedHealthId\":\"" + MERGED + "\"}";
    }

    @Test
    void parsesFlatMergePayloadAndDispatchesToHook() {
        RecordingHook hook = new RecordingHook(3);
        IdentityRepointHandler handler = new IdentityRepointHandler(hook, cmd -> false);

        IdentityRepointHandler.RepointOutcome outcome = handler.handle(mergePayload());

        assertEquals(1, hook.calls.size());
        IdentityRepointCommand cmd = hook.calls.get(0);
        assertEquals(TENANT, cmd.tenantId());
        assertEquals(MERGED, cmd.mergedHealthId());
        assertEquals(SURVIVOR, cmd.survivorHealthId());
        assertEquals(3, outcome.rowsRepointed());
        assertTrue(outcome.applied());
        assertFalse(outcome.skippedDuplicate());
        assertEquals("test-service", outcome.participant());
    }

    @Test
    void unwrapsEnvelopePayloadObject() {
        RecordingHook hook = new RecordingHook(1);
        IdentityRepointHandler handler = new IdentityRepointHandler(hook, cmd -> false);
        String envelope = "{\"eventType\":\"vito.merge.executed\",\"aggregateId\":\"77\","
                + "\"correlationId\":\"corr-1\",\"payload\":{\"tenantId\":\"" + TENANT + "\","
                + "\"survivorHealthId\":\"" + SURVIVOR + "\",\"mergedHealthId\":\"" + MERGED + "\"}}";

        handler.handle(envelope);

        IdentityRepointCommand cmd = hook.calls.get(0);
        assertEquals(SURVIVOR, cmd.survivorHealthId());
        assertEquals("77", cmd.mergeId());
        assertEquals("corr-1", cmd.correlationId());
    }

    @Test
    void idempotencyGuardSkipsAlreadyAppliedMerge() {
        RecordingHook hook = new RecordingHook(2);
        Set<String> seen = new HashSet<>();
        Predicate<IdentityRepointCommand> guard = cmd -> !seen.add(cmd.idempotencyKey());
        IdentityRepointHandler handler = new IdentityRepointHandler(hook, guard);

        IdentityRepointHandler.RepointOutcome first = handler.handle(mergePayload());
        IdentityRepointHandler.RepointOutcome second = handler.handle(mergePayload());

        assertFalse(first.skippedDuplicate());
        assertTrue(second.skippedDuplicate());
        assertEquals(0, second.rowsRepointed());
        assertEquals(1, hook.calls.size(), "hook must run exactly once for a duplicate merge");
    }

    @Test
    void noopHookReportsZeroRowsButProvesChannelReached() {
        IdentityRepointHook noop = IdentityRepointHook.noop("pct-service");
        IdentityRepointHandler handler = new IdentityRepointHandler(noop, cmd -> false);

        IdentityRepointHandler.RepointOutcome outcome = handler.handle(mergePayload());

        assertEquals("pct-service", outcome.participant());
        assertEquals(0, outcome.rowsRepointed());
        assertTrue(outcome.applied());
    }

    @Test
    void rejectsPayloadMissingRequiredMergeFields() {
        IdentityRepointHandler handler =
                new IdentityRepointHandler(new RecordingHook(0), cmd -> false);
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle("{\"tenantId\":\"" + TENANT + "\"}"));
    }

    @Test
    void rejectsCommandWithIdenticalHealthIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityRepointCommand(TENANT, SURVIVOR, SURVIVOR, "1", null));
    }
}
