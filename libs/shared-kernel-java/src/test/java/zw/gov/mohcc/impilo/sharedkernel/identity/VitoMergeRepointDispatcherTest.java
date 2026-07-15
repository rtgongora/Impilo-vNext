package zw.gov.mohcc.impilo.sharedkernel.identity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The fan-out dispatcher runs every hook for one merge and tolerates a failing hook. */
class VitoMergeRepointDispatcherTest {

    private static final String SURV = "11111111-1111-1111-1111-111111111111";
    private static final String MERG = "22222222-2222-2222-2222-222222222222";
    private static final String TEN = "00000000-0000-0000-0000-0000000000aa";

    private String payload() {
        return "{\"tenantId\":\"" + TEN + "\",\"survivorHealthId\":\"" + SURV + "\",\"mergedHealthId\":\"" + MERG + "\"}";
    }

    static final class CountingHook implements IdentityRepointHook {
        final String name; final int rows; final List<IdentityRepointCommand> calls = new ArrayList<>();
        CountingHook(String name, int rows) { this.name = name; this.rows = rows; }
        @Override public int repoint(IdentityRepointCommand c) { calls.add(c); return rows; }
        @Override public String participant() { return name; }
    }

    @Test
    void fansOutToEveryHookAndReturnsPerParticipantCounts() {
        CountingHook a = new CountingHook("svc-a", 2);
        CountingHook b = new CountingHook("svc-b", 3);
        VitoMergeRepointDispatcher dispatcher = new VitoMergeRepointDispatcher(List.of(a, b));

        Map<String, Integer> result = dispatcher.dispatch(payload());

        assertEquals(2, dispatcher.hookCount());
        assertEquals(2, result.get("svc-a"));
        assertEquals(3, result.get("svc-b"));
        assertEquals(MERG, a.calls.get(0).mergedHealthId());
        assertEquals(SURV, b.calls.get(0).survivorHealthId());
    }

    @Test
    void oneFailingHookDoesNotBlockTheOthers() {
        IdentityRepointHook boom = new IdentityRepointHook() {
            @Override public int repoint(IdentityRepointCommand c) { throw new RuntimeException("db down"); }
            @Override public String participant() { return "svc-broken"; }
        };
        CountingHook ok = new CountingHook("svc-ok", 1);
        VitoMergeRepointDispatcher dispatcher = new VitoMergeRepointDispatcher(List.of(boom, ok));

        Map<String, Integer> result = dispatcher.dispatch(payload());

        assertEquals(-1, result.get("svc-broken")); // recorded as failed
        assertEquals(1, result.get("svc-ok"));       // still ran
    }
}
