package zw.gov.mohcc.impilo.nhume.integration.writeback;

/**
 * Result of one delivery write-back to an owning service. Failure is a
 * first-class state: it is recorded on the delivery, never swallowed, and
 * never blocks the courier-facing sign-off.
 */
public record WriteBackOutcome(Status status, String detail) {

    public enum Status {
        /** Owning-service transition confirmed. */
        OK,
        /** Call attempted and rejected/errored — needs operator follow-up. */
        FAILED,
        /** No matching lifecycle transition exists in the owning service yet. */
        SKIPPED_NO_TRANSITION,
        /** Nothing to do (e.g. all specimens already received). */
        NOOP
    }

    public static WriteBackOutcome ok(String detail) {
        return new WriteBackOutcome(Status.OK, detail);
    }

    public static WriteBackOutcome failed(String detail) {
        return new WriteBackOutcome(Status.FAILED, detail);
    }

    public static WriteBackOutcome skippedNoTransition(String detail) {
        return new WriteBackOutcome(Status.SKIPPED_NO_TRANSITION, detail);
    }

    public static WriteBackOutcome noop(String detail) {
        return new WriteBackOutcome(Status.NOOP, detail);
    }
}
