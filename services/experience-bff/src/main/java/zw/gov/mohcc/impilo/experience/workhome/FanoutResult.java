package zw.gov.mohcc.impilo.experience.workhome;

/**
 * Result of one {@link ContextPropagatingFanout} task. Always present for every submitted
 * {@link FanoutTask} — a slow or failing downstream never removes its section from the
 * response, it degrades it. Callers render {@code TIMEOUT}/{@code ERROR} as an in-band
 * {@code degraded} section (Phase E3), never a 5xx.
 */
public record FanoutResult<T>(String sectionId, FanoutStatus status, T value, String errorMessage,
                               long elapsedMillis) {

    public static <T> FanoutResult<T> ok(String sectionId, T value, long elapsedMillis) {
        return new FanoutResult<>(sectionId, FanoutStatus.OK, value, null, elapsedMillis);
    }

    public static <T> FanoutResult<T> timeout(String sectionId, long elapsedMillis) {
        return new FanoutResult<>(sectionId, FanoutStatus.TIMEOUT, null, "section timed out", elapsedMillis);
    }

    public static <T> FanoutResult<T> error(String sectionId, String errorMessage, long elapsedMillis) {
        return new FanoutResult<>(sectionId, FanoutStatus.ERROR, null, errorMessage, elapsedMillis);
    }

    public boolean isOk() {
        return status == FanoutStatus.OK;
    }
}
