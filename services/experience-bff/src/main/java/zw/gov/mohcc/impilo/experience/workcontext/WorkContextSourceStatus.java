package zw.gov.mohcc.impilo.experience.workcontext;

/**
 * Per-source-adapter degradation record. An outage in one source must never
 * blank the whole picker, and — the distinction this type exists to
 * preserve — "all sources degraded" must read differently from "genuinely
 * no assignments", which the pre-Phase-C code collapsed (both rendered as
 * {@code no_active_work_assignment}).
 */
public record WorkContextSourceStatus(String system, State state, String message) {
    public enum State { LIVE, EMPTY, DEGRADED }

    public static WorkContextSourceStatus live(String system) {
        return new WorkContextSourceStatus(system, State.LIVE, null);
    }

    public static WorkContextSourceStatus empty(String system) {
        return new WorkContextSourceStatus(system, State.EMPTY, null);
    }

    public static WorkContextSourceStatus degraded(String system, String message) {
        return new WorkContextSourceStatus(system, State.DEGRADED, message);
    }
}
